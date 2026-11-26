package com.example.ghtrending.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TranslationHttpClient

@Module
@InstallIn(SingletonComponent::class)
object TranslationModule {

    /**
     * 复用 [NetworkModule] 注入的 OkHttpClient（共享连接池、线程池与超时配置），
     * 但移除面向自有后端的拦截器：
     * - 不向第三方翻译服务发送 X-Client-Version；
     * - 不经过 426 升级检测；
     * - 避免 Debug 下 HttpLoggingInterceptor 把 API Key / Authorization 打印到 Logcat。
     */
    @Provides
    @Singleton
    @TranslationHttpClient
    fun provideTranslationHttpClient(okHttpClient: OkHttpClient): OkHttpClient =
        okHttpClient.newBuilder()
            .apply { interceptors().clear() }
            .build()
}
