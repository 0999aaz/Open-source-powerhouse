package com.example.ghtrending.data.translation

import kotlinx.coroutines.delay

/**
 * 面向 sanitized README HTML 的结构保持翻译器。
 *
 * - 只翻译标签外的可读文本，保留所有 HTML 标签、属性、链接与图片；
 * - 自动跳过 `<pre>`、`<code>`、`<kbd>`、`<samp>`、`<script>`、`<style>`、`<svg>` 等代码/非文本区域；
 * - 将多段单行文本按批次以 `\n` 合并调用 [Translator]，减少请求次数并规避单次字数上限；
 * - 若个别批次返回行数与输入不一致，自动二分降级，保证文本与 HTML 结构严格对齐。
 */
internal object HtmlTranslator {

    private val SKIP_TAGS = setOf("pre", "code", "kbd", "samp", "script", "style", "svg", "var")
    private val WHITESPACE_RUN = Regex("\\s+")
    private val NUMERIC_ENTITY = Regex("&#(?:x([0-9a-fA-F]+)|([0-9]+));")
    private val URL_LIKE = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    private val DOMAIN_LIKE = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?$", RegexOption.IGNORE_CASE)

    private const val MAX_BATCH_CHARS = 1500
    private const val MAX_BATCH_ITEMS = 30
    private const val BATCH_INTERVAL_MS = 180L

    private sealed interface Segment {
        data class Raw(val content: String) : Segment
        data class Translatable(
            val leadingSpace: String,
            val normalizedText: String,
            val trailingSpace: String,
        ) : Segment
    }

    suspend fun translateHtml(
        html: String,
        translator: Translator,
        targetLang: String,
        onProgress: (completedBatches: Int, totalBatches: Int) -> Unit = { _, _ -> },
    ): Result<String> {
        val segments = tokenize(html)
        val uniqueTexts = LinkedHashSet<String>()
        for (segment in segments) {
            if (segment is Segment.Translatable) {
                uniqueTexts.add(segment.normalizedText)
            }
        }
        if (uniqueTexts.isEmpty()) return Result.success(html)

        val batches = buildBatches(uniqueTexts.toList())
        val totalBatches = batches.size
        val translatedMap = HashMap<String, String>(uniqueTexts.size)

        onProgress(0, totalBatches)
        for ((index, batch) in batches.withIndex()) {
            if (index > 0) delay(BATCH_INTERVAL_MS)
            val batchResult = translateBatchWithFallback(batch, translator, targetLang)
            val mapped = batchResult.getOrElse { return Result.failure(it) }
            translatedMap.putAll(mapped)
            onProgress(index + 1, totalBatches)
        }

        val rebuilt = StringBuilder(html.length + 256)
        for (segment in segments) {
            when (segment) {
                is Segment.Raw -> rebuilt.append(segment.content)
                is Segment.Translatable -> {
                    val translated = translatedMap[segment.normalizedText] ?: segment.normalizedText
                    rebuilt.append(segment.leadingSpace)
                    rebuilt.append(escapeHtml(translated))
                    rebuilt.append(segment.trailingSpace)
                }
            }
        }
        return Result.success(rebuilt.toString())
    }

    private suspend fun translateBatchWithFallback(
        items: List<String>,
        translator: Translator,
        targetLang: String,
    ): Result<Map<String, String>> {
        if (items.isEmpty()) return Result.success(emptyMap())
        if (items.size == 1) {
            val single = items[0]
            return translator.translate(single, targetLang).map { mapOf(single to it.trim()) }
        }

        val joined = items.joinToString("\n")
        val translated = translator.translate(joined, targetLang).getOrElse { return Result.failure(it) }
        val lines = translated.replace("\r\n", "\n").split("\n")

        if (lines.size == items.size) {
            val result = HashMap<String, String>(items.size)
            for (i in items.indices) {
                result[items[i]] = lines[i].trim().ifEmpty { items[i] }
            }
            return Result.success(result)
        }

        // 翻译引擎改变了行数时，二分拆分后重试，保证严格一一对应
        val mid = items.size / 2
        val left = translateBatchWithFallback(items.subList(0, mid), translator, targetLang)
            .getOrElse { return Result.failure(it) }
        delay(BATCH_INTERVAL_MS)
        val right = translateBatchWithFallback(items.subList(mid, items.size), translator, targetLang)
            .getOrElse { return Result.failure(it) }
        return Result.success(left + right)
    }

    private fun buildBatches(texts: List<String>): List<List<String>> {
        val batches = ArrayList<List<String>>()
        var current = ArrayList<String>()
        var currentChars = 0

        for (text in texts) {
            val added = text.length + 1
            if (current.isNotEmpty() &&
                (currentChars + added > MAX_BATCH_CHARS || current.size >= MAX_BATCH_ITEMS)
            ) {
                batches.add(current)
                current = ArrayList()
                currentChars = 0
            }
            current.add(text)
            currentChars += added
        }
        if (current.isNotEmpty()) batches.add(current)
        return batches
    }

    private fun tokenize(html: String): List<Segment> {
        val segments = ArrayList<Segment>()
        val length = html.length
        var index = 0
        var skipDepth = 0

        while (index < length) {
            val lt = html.indexOf('<', index)
            if (lt < 0) {
                appendTextSegment(segments, html.substring(index), skipDepth > 0)
                break
            }
            if (lt > index) {
                appendTextSegment(segments, html.substring(index, lt), skipDepth > 0)
            }

            // HTML 注释 <!-- ... -->
            if (html.startsWith("<!--", lt)) {
                val endComment = html.indexOf("-->", lt + 4)
                val end = if (endComment >= 0) endComment + 3 else length
                segments.add(Segment.Raw(html.substring(lt, end)))
                index = end
                continue
            }

            val gt = findTagEnd(html, lt)
            if (gt < 0) {
                appendTextSegment(segments, html.substring(lt), skipDepth > 0)
                break
            }

            val tag = html.substring(lt, gt + 1)
            segments.add(Segment.Raw(tag))
            skipDepth = updateSkipDepth(tag, skipDepth)
            index = gt + 1
        }
        return segments
    }

    private fun findTagEnd(html: String, start: Int): Int {
        var inSingle = false
        var inDouble = false
        for (i in (start + 1) until html.length) {
            when (html[i]) {
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"' -> if (!inSingle) inDouble = !inDouble
                '>' -> if (!inSingle && !inDouble) return i
            }
        }
        return -1
    }

    private fun updateSkipDepth(tag: String, currentDepth: Int): Int {
        if (tag.length < 3) return currentDepth
        val inner = tag.substring(1, tag.length - 1).trim()
        if (inner.isEmpty() || inner.startsWith("!") || inner.startsWith("?")) return currentDepth

        val isClosing = inner.startsWith("/")
        val isSelfClosing = !isClosing && inner.endsWith("/")
        val nameStart = if (isClosing) 1 else 0
        var nameEnd = nameStart
        while (nameEnd < inner.length && !inner[nameEnd].isWhitespace() && inner[nameEnd] != '/') {
            nameEnd++
        }
        val tagName = inner.substring(nameStart, nameEnd).lowercase()
        if (tagName !in SKIP_TAGS) return currentDepth

        return when {
            isClosing -> (currentDepth - 1).coerceAtLeast(0)
            isSelfClosing -> currentDepth
            else -> currentDepth + 1
        }
    }

    private fun appendTextSegment(
        segments: MutableList<Segment>,
        rawText: String,
        insideSkipTag: Boolean,
    ) {
        if (insideSkipTag || rawText.isBlank()) {
            segments.add(Segment.Raw(rawText))
            return
        }

        val firstNonWs = rawText.indexOfFirst { !it.isWhitespace() }
        val lastNonWs = rawText.indexOfLast { !it.isWhitespace() }
        val leading = rawText.substring(0, firstNonWs)
        val coreRaw = rawText.substring(firstNonWs, lastNonWs + 1)
        val trailing = rawText.substring(lastNonWs + 1)

        val decoded = decodeHtmlEntities(coreRaw)
        val normalized = WHITESPACE_RUN.replace(decoded, " ").trim()
        if (!shouldTranslate(normalized)) {
            segments.add(Segment.Raw(rawText))
            return
        }

        segments.add(
            Segment.Translatable(
                leadingSpace = leading,
                normalizedText = normalized,
                trailingSpace = trailing,
            ),
        )
    }

    private fun shouldTranslate(text: String): Boolean {
        if (text.isEmpty()) return false
        if (URL_LIKE.matches(text) || DOMAIN_LIKE.matches(text)) return false
        // 至少包含一个字母或文字字符（过滤纯数字、纯标点、纯符号/emoji）
        return text.any { it.isLetter() }
    }

    private fun decodeHtmlEntities(text: String): String {
        if ('&' !in text) return text
        val withNumeric = NUMERIC_ENTITY.replace(text) { match ->
            val hex = match.groupValues[1]
            val dec = match.groupValues[2]
            val codePoint = if (hex.isNotEmpty()) hex.toIntOrNull(16) else dec.toIntOrNull(10)
            if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                match.value
            }
        }
        return withNumeric
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun escapeHtml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }
}
