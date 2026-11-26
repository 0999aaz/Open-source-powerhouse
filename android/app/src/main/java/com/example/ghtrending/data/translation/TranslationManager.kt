package com.example.ghtrending.data.translation

import com.example.ghtrending.di.TranslationHttpClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据配置创建对应服务商的 [Translator]，共享注入的 OkHttpClient。
 */
@Singleton
class TranslatorFactory @Inject constructor(
    @TranslationHttpClient private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    fun create(settings: TranslationSettings): Translator = when (settings.provider) {
        TranslationProvider.NIUTRANS -> NiuTransTranslator(
            client = okHttpClient,
            json = json,
            apiKey = settings.niuTransApiKey.trim(),
        )
        TranslationProvider.AZURE -> AzureTranslator(
            client = okHttpClient,
            json = json,
            subscriptionKey = settings.azureSubscriptionKey.trim(),
            region = settings.azureRegion.trim(),
        )
        TranslationProvider.TENCENT -> TencentTranslator(
            client = okHttpClient,
            json = json,
            secretId = settings.tencentSecretId.trim(),
            secretKey = settings.tencentSecretKey.trim(),
            region = settings.tencentRegion.trim(),
        )
    }
}

/**
 * 翻译入口：按用户当前设置动态分发请求，并维护会话级内存缓存，避免重复消耗 API 额度。
 */
@Singleton
class TranslationManager @Inject constructor(
    private val preferences: TranslationPreferences,
    private val translatorFactory: TranslatorFactory,
) {
    /** key = 服务商|目标语言|原文 */
    private val cache: MutableMap<String, String> = ConcurrentHashMap()

    /** key = 服务商|目标语言|HTML指纹，用于缓存整篇 README 译文 */
    private val readmeCache: MutableMap<String, String> = ConcurrentHashMap()

    val currentProvider: TranslationProvider
        get() = preferences.settings.value.provider

    fun isConfigured(): Boolean = preferences.settings.value.isConfigured()

    fun cachedTranslation(text: String, targetLang: String = DEFAULT_TARGET_LANG): String? =
        cache[cacheKey(currentProvider, targetLang, text.trim())]

    fun cachedReadmeTranslation(html: String, targetLang: String = DEFAULT_TARGET_LANG): String? =
        readmeCache[htmlCacheKey(currentProvider, targetLang, html)]

    suspend fun translate(text: String, targetLang: String = DEFAULT_TARGET_LANG): Result<String> {
        val source = text.trim()
        if (source.isEmpty()) return Result.success(text)

        val settings = preferences.settings.value
        if (!settings.isConfigured()) {
            return Result.failure(TranslationException.NotConfigured(settings.provider))
        }

        val key = cacheKey(settings.provider, targetLang, source)
        cache[key]?.let { return Result.success(it) }

        return translatorFactory.create(settings)
            .translate(source, targetLang)
            .onSuccess { cache[key] = it }
    }

    /** 翻译 README HTML 文档，保留排版与代码块，并在会话内缓存结果。 */
    suspend fun translateHtml(
        html: String,
        targetLang: String = DEFAULT_TARGET_LANG,
        onProgress: (completedBatches: Int, totalBatches: Int) -> Unit = { _, _ -> },
    ): Result<String> {
        if (html.isBlank()) return Result.success(html)

        val settings = preferences.settings.value
        if (!settings.isConfigured()) {
            return Result.failure(TranslationException.NotConfigured(settings.provider))
        }

        val key = htmlCacheKey(settings.provider, targetLang, html)
        readmeCache[key]?.let { return Result.success(it) }

        val translator = translatorFactory.create(settings)
        return HtmlTranslator.translateHtml(html, translator, targetLang, onProgress)
            .onSuccess { readmeCache[key] = it }
    }

    /** 使用（可能尚未生效的）指定配置做一次真实翻译请求，用于校验凭证。 */
    suspend fun testConnection(settings: TranslationSettings): Result<String> {
        if (!settings.isConfigured()) {
            return Result.failure(TranslationException.NotConfigured(settings.provider))
        }
        return translatorFactory.create(settings).translate(TEST_TEXT, DEFAULT_TARGET_LANG)
    }

    fun clearCache() {
        cache.clear()
        readmeCache.clear()
    }

    private fun cacheKey(provider: TranslationProvider, targetLang: String, text: String): String =
        "${provider.name}|$targetLang|$text"

    private fun htmlCacheKey(provider: TranslationProvider, targetLang: String, html: String): String =
        "${provider.name}|$targetLang|${html.length}|${html.hashCode()}"

    companion object {
        const val DEFAULT_TARGET_LANG = "zh"
        const val TEST_TEXT = "Hello, world!"
    }
}
