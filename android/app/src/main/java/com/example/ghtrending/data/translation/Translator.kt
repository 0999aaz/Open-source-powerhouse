package com.example.ghtrending.data.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 统一的机器翻译接口。
 *
 * @param targetLang 通用语言代码（如 "zh"、"en"），由各实现自行映射为服务商要求的代码。
 */
interface Translator {
    val provider: TranslationProvider

    suspend fun translate(text: String, targetLang: String = "zh"): Result<String>
}

internal data class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** 以可取消的方式执行 OkHttp 请求，并读取完整响应体。 */
internal suspend fun OkHttpClient.awaitBody(request: Request): HttpResult =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use { HttpResult(it.code, it.body?.string().orEmpty()) }
                    continuation.resume(result)
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

/**
 * 将翻译过程中的异常统一折叠为 [TranslationException]，同时保留协程取消语义。
 */
internal inline fun <T> translationCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: TranslationException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(TranslationException.Network(e))
    } catch (e: Exception) {
        Result.failure(TranslationException.InvalidResponse(e))
    }

internal fun Json.parseOrNull(raw: String): JsonElement? =
    runCatching { parseToJsonElement(raw) }.getOrNull()

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
