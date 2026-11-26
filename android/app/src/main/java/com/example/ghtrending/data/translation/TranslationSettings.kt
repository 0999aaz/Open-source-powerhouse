package com.example.ghtrending.data.translation

/**
 * 支持的机器翻译服务商。
 */
enum class TranslationProvider(val displayName: String) {
    NIUTRANS("小牛翻译"),
    AZURE("Azure AI Translator"),
    TENCENT("腾讯云机器翻译"),
    ;

    companion object {
        fun fromName(name: String?): TranslationProvider =
            entries.firstOrNull { it.name == name } ?: NIUTRANS
    }
}

/**
 * 用户自带的翻译引擎配置（当前选中的服务商 + 各服务商凭证）。
 */
data class TranslationSettings(
    val provider: TranslationProvider = TranslationProvider.NIUTRANS,
    val niuTransApiKey: String = "",
    val azureSubscriptionKey: String = "",
    val azureRegion: String = "",
    val tencentSecretId: String = "",
    val tencentSecretKey: String = "",
    val tencentRegion: String = DEFAULT_TENCENT_REGION,
) {
    /** 指定服务商（默认为当前选中服务商）的必填凭证是否已填写。 */
    fun isConfigured(target: TranslationProvider = provider): Boolean = when (target) {
        TranslationProvider.NIUTRANS -> niuTransApiKey.isNotBlank()
        // Azure 全局（Global）单服务资源无需 Region，多服务/区域资源才需要
        TranslationProvider.AZURE -> azureSubscriptionKey.isNotBlank()
        TranslationProvider.TENCENT -> tencentSecretId.isNotBlank() &&
            tencentSecretKey.isNotBlank() &&
            tencentRegion.isNotBlank()
    }

    companion object {
        const val DEFAULT_TENCENT_REGION = "ap-guangzhou"
    }
}

/**
 * 翻译相关的统一异常类型，message 均为可直接展示给用户的中文文案。
 */
sealed class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConfigured(val provider: TranslationProvider) :
        TranslationException("尚未配置「${provider.displayName}」的密钥")

    class Network(cause: Throwable? = null) :
        TranslationException("网络异常，请检查网络后重试", cause)

    class Api(message: String) : TranslationException(message)

    class InvalidResponse(cause: Throwable? = null) :
        TranslationException("翻译服务返回了无法解析的结果", cause)
}
