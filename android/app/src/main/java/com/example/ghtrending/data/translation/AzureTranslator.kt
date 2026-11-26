package com.example.ghtrending.data.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Azure AI Translator（Text Translation v3.0）。
 * 文档：POST https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=zh-Hans
 */
class AzureTranslator(
    private val client: OkHttpClient,
    private val json: Json,
    private val subscriptionKey: String,
    private val region: String,
) : Translator {

    override val provider: TranslationProvider = TranslationProvider.AZURE

    override suspend fun translate(text: String, targetLang: String): Result<String> =
        translationCatching {
            val url = ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("api-version", "3.0")
                .addQueryParameter("to", targetLang.toAzureLang())
                .build()
            val payload = buildJsonArray {
                addJsonObject { put("Text", text) }
            }.toString()

            val request = Request.Builder()
                .url(url)
                .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .apply {
                    if (region.isNotBlank()) header("Ocp-Apim-Subscription-Region", region)
                }
                .header("X-ClientTraceId", UUID.randomUUID().toString())
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.awaitBody(request)
            val element = json.parseOrNull(response.body)

            if (!response.isSuccessful || element !is JsonArray) {
                val error = (element as? JsonObject)?.get("error") as? JsonObject
                val message = error?.stringOrNull("message")
                val code = error?.stringOrNull("code")
                throw when {
                    message != null -> TranslationException.Api("Azure 翻译：$message（$code）")
                    response.isSuccessful -> TranslationException.InvalidResponse()
                    else -> TranslationException.Api("Azure 翻译请求失败（HTTP ${response.code}）")
                }
            }

            val translations = (element.firstOrNull() as? JsonObject)
                ?.get("translations") as? JsonArray
            (translations?.firstOrNull() as? JsonObject)
                ?.stringOrNull("text")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw TranslationException.InvalidResponse()
        }

    private fun String.toAzureLang(): String = when (lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "zh-Hans"
        "zh-tw", "zh-hant" -> "zh-Hant"
        else -> this
    }

    private companion object {
        const val ENDPOINT = "https://api.cognitive.microsofttranslator.com/translate"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
