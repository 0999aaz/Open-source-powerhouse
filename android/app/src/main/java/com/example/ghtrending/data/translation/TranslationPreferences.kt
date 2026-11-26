package com.example.ghtrending.data.translation

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 SharedPreferences 的翻译配置存储，对外暴露 [StateFlow] 以便 UI / 业务层实时感知变更。
 */
@Singleton
class TranslationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<TranslationSettings> = _settings.asStateFlow()

    fun save(settings: TranslationSettings) {
        _settings.value = settings
        prefs.edit()
            .putString(KEY_PROVIDER, settings.provider.name)
            .putString(KEY_NIUTRANS_API_KEY, settings.niuTransApiKey)
            .putString(KEY_AZURE_KEY, settings.azureSubscriptionKey)
            .putString(KEY_AZURE_REGION, settings.azureRegion)
            .putString(KEY_TENCENT_SECRET_ID, settings.tencentSecretId)
            .putString(KEY_TENCENT_SECRET_KEY, settings.tencentSecretKey)
            .putString(KEY_TENCENT_REGION, settings.tencentRegion)
            .apply()
    }

    private fun read(): TranslationSettings = TranslationSettings(
        provider = TranslationProvider.fromName(prefs.getString(KEY_PROVIDER, null)),
        niuTransApiKey = prefs.getString(KEY_NIUTRANS_API_KEY, null).orEmpty(),
        azureSubscriptionKey = prefs.getString(KEY_AZURE_KEY, null).orEmpty(),
        azureRegion = prefs.getString(KEY_AZURE_REGION, null).orEmpty(),
        tencentSecretId = prefs.getString(KEY_TENCENT_SECRET_ID, null).orEmpty(),
        tencentSecretKey = prefs.getString(KEY_TENCENT_SECRET_KEY, null).orEmpty(),
        tencentRegion = prefs.getString(KEY_TENCENT_REGION, null)
            ?: TranslationSettings.DEFAULT_TENCENT_REGION,
    )

    private companion object {
        const val PREFS_NAME = "translation_prefs"
        const val KEY_PROVIDER = "provider"
        const val KEY_NIUTRANS_API_KEY = "niutrans_api_key"
        const val KEY_AZURE_KEY = "azure_subscription_key"
        const val KEY_AZURE_REGION = "azure_region"
        const val KEY_TENCENT_SECRET_ID = "tencent_secret_id"
        const val KEY_TENCENT_SECRET_KEY = "tencent_secret_key"
        const val KEY_TENCENT_REGION = "tencent_region"
    }
}
