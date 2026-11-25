package com.example.ghtrending

import com.example.ghtrending.data.remote.ClientVersionInterceptor
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ClientVersionInterceptorTest {

    @Test
    fun injectsClientVersionHeaderOnEveryRequest() {
        val interceptor = ClientVersionInterceptor()
        var capturedRequest: Request? = null

        val fakeChain = object : Interceptor.Chain {
            private val initialRequest = Request.Builder()
                .url("https://example.com/v1/trending?period=daily")
                .build()

            override fun request(): Request = initialRequest

            override fun proceed(request: Request): Response {
                capturedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection(): Connection? = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis(): Int = 1000
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
            override fun readTimeoutMillis(): Int = 1000
            override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
            override fun writeTimeoutMillis(): Int = 1000
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
        }

        interceptor.intercept(fakeChain)
        assertEquals(BuildConfig.VERSION_NAME, capturedRequest?.header("X-Client-Version"))
    }
}
