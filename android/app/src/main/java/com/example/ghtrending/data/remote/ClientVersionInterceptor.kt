package com.example.ghtrending.data.remote

import com.example.ghtrending.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class UpgradePromptState(
    val message: String,
    val downloadUrl: String,
)

@Singleton
class UpgradeEventBus @Inject constructor() {
    private val _upgradeState = MutableStateFlow<UpgradePromptState?>(null)
    val upgradeState: StateFlow<UpgradePromptState?> = _upgradeState.asStateFlow()

    fun notifyUpgradeRequired(message: String, downloadUrl: String) {
        _upgradeState.value = UpgradePromptState(
            message = message.ifBlank { "当前客户端版本过低，请更新至最新版本" },
            downloadUrl = downloadUrl,
        )
    }
}

class ClientVersionInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Client-Version", BuildConfig.VERSION_NAME)
            .build()
        return chain.proceed(request)
    }
}

class ApiStatusInterceptor @Inject constructor(
    private val json: Json,
    private val upgradeEventBus: UpgradeEventBus,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 426) {
            val rawBody = response.peekBody(32 * 1024).string()
            val detail = runCatching {
                json.decodeFromString<ApiErrorEnvelopeDto>(rawBody).error
            }.getOrNull()
            upgradeEventBus.notifyUpgradeRequired(
                message = detail?.message ?: "当前客户端版本过低，请更新至最新版本",
                downloadUrl = detail?.downloadUrl ?: "",
            )
        }
        return response
    }
}

sealed class ApiFailure(override val message: String) : Exception(message) {
    class RateLimited : ApiFailure("请求过于频繁，请稍后再试")
    class UpgradeRequired(val downloadUrl: String, msg: String) : ApiFailure(msg)
    class NetworkUnavailable : ApiFailure("加载失败，请检查网络后重试")
    class ServerError(msg: String) : ApiFailure(msg)
}

fun Throwable.toApiFailure(json: Json): ApiFailure {
    if (this is ApiFailure) return this
    if (this is HttpException) {
        val errorBody = response()?.errorBody()?.string()
        val detail = errorBody?.let {
            runCatching { json.decodeFromString<ApiErrorEnvelopeDto>(it).error }.getOrNull()
        }
        return when (code()) {
            426 -> ApiFailure.UpgradeRequired(
                downloadUrl = detail?.downloadUrl.orEmpty(),
                msg = detail?.message ?: "当前客户端版本过低，请更新至最新版本",
            )
            429 -> ApiFailure.RateLimited()
            else -> ApiFailure.ServerError(
                detail?.message ?: "加载失败，请检查网络后重试"
            )
        }
    }
    return ApiFailure.NetworkUnavailable()
}
