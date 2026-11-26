package com.example.ghtrending.data.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 小牛翻译文本翻译 API。
 * 文档：POST https://api.niutrans.com/NiuTransServer/translation
 */
class NiuTransTranslator(
    private val client: OkHttpClient,
    private val json: Json,
    private val apiKey: String,
) : Translator {

    override val provider: TranslationProvider = TranslationProvider.NIUTRANS

    override suspend fun translate(text: String, targetLang: String): Result<String> =
        translationCatching {
            val body = FormBody.Builder()
                .add("from", "auto")
                .add("to", targetLang.toNiuTransLang())
                .add("apikey", apiKey)
                .add("src_text", text)
                .build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .build()

            val response = client.awaitBody(request)
            val root = json.parseOrNull(response.body) as? JsonObject
                ?: throw failureFor(response)

            val errorCode = root.stringOrNull("error_code")
            if (!errorCode.isNullOrBlank()) {
                val errorMsg = root.stringOrNull("error_msg") ?: "未知错误"
                throw TranslationException.Api("小牛翻译：$errorMsg（$errorCode）")
            }
            if (!response.isSuccessful) throw failureFor(response)

            root.stringOrNull("tgt_text")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw TranslationException.InvalidResponse()
        }

    private fun failureFor(response: HttpResult): TranslationException =
        if (response.isSuccessful) {
            TranslationException.InvalidResponse()
        } else {
            TranslationException.Api("小牛翻译请求失败（HTTP ${response.code}）")
        }

    private fun String.toNiuTransLang(): String = when (lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "zh"
        "zh-tw", "zh-hant" -> "cht"
        else -> this
    }

    private companion object {
        const val ENDPOINT = "https://api.niutrans.com/NiuTransServer/translation"
    }
}
