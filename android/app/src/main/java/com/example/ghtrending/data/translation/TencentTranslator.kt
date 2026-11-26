package com.example.ghtrending.data.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 腾讯云机器翻译（TMT）TextTranslate 接口，使用 TC3-HMAC-SHA256 签名。
 * 文档：https://cloud.tencent.com/document/api/551/15619
 */
class TencentTranslator(
    private val client: OkHttpClient,
    private val json: Json,
    private val secretId: String,
    private val secretKey: String,
    private val region: String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : Translator {

    override val provider: TranslationProvider = TranslationProvider.TENCENT

    override suspend fun translate(text: String, targetLang: String): Result<String> =
        translationCatching {
            val payload = buildJsonObject {
                put("SourceText", text)
                put("Source", "auto")
                put("Target", targetLang.toTencentLang())
                put("ProjectId", 0)
            }.toString()

            val timestamp = clock()
            val authorization = Tc3Signer.authorization(
                secretId = secretId,
                secretKey = secretKey,
                service = SERVICE,
                host = HOST,
                contentType = CONTENT_TYPE,
                payload = payload,
                timestamp = timestamp,
            )

            // 请求体字节与 Content-Type 必须与签名时使用的完全一致
            val body = payload.toByteArray(Charsets.UTF_8).toRequestBody(CONTENT_TYPE.toMediaType())
            val request = Request.Builder()
                .url("https://$HOST/")
                .header("Authorization", authorization)
                .header("Host", HOST)
                .header("X-TC-Action", ACTION)
                .header("X-TC-Version", VERSION)
                .header("X-TC-Timestamp", timestamp.toString())
                .header("X-TC-Region", region)
                .header("X-TC-Language", "zh-CN")
                .post(body)
                .build()

            val response = client.awaitBody(request)
            val root = json.parseOrNull(response.body) as? JsonObject
            val responseObj = root?.get("Response") as? JsonObject
                ?: throw if (response.isSuccessful) {
                    TranslationException.InvalidResponse()
                } else {
                    TranslationException.Api("腾讯云翻译请求失败（HTTP ${response.code}）")
                }

            val error = responseObj["Error"] as? JsonObject
            if (error != null) {
                val code = error.stringOrNull("Code") ?: "Unknown"
                val message = error.stringOrNull("Message") ?: "未知错误"
                throw TranslationException.Api("腾讯云翻译：$message（$code）")
            }

            responseObj.stringOrNull("TargetText")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw TranslationException.InvalidResponse()
        }

    private fun String.toTencentLang(): String = when (lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "zh"
        "zh-tw", "zh-hant" -> "zh-TW"
        else -> this
    }

    private companion object {
        const val HOST = "tmt.tencentcloudapi.com"
        const val SERVICE = "tmt"
        const val ACTION = "TextTranslate"
        const val VERSION = "2018-03-21"
        const val CONTENT_TYPE = "application/json; charset=utf-8"
    }
}
