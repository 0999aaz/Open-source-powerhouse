package com.example.ghtrending.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghtrending.data.translation.TranslationException
import com.example.ghtrending.data.translation.TranslationManager
import com.example.ghtrending.data.translation.TranslationPreferences
import com.example.ghtrending.data.translation.TranslationProvider
import com.example.ghtrending.data.translation.TranslationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionTestResult {
    data class Success(val source: String, val translated: String) : ConnectionTestResult
    data class Failure(val message: String) : ConnectionTestResult
}

data class SettingsUiState(
    val settings: TranslationSettings,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: TranslationPreferences,
    private val translationManager: TranslationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(settings = preferences.settings.value))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    fun selectProvider(provider: TranslationProvider) = edit { it.copy(provider = provider) }

    fun updateNiuTransApiKey(value: String) = edit { it.copy(niuTransApiKey = value) }

    fun updateAzureSubscriptionKey(value: String) = edit { it.copy(azureSubscriptionKey = value) }

    fun updateAzureRegion(value: String) = edit { it.copy(azureRegion = value) }

    fun updateTencentSecretId(value: String) = edit { it.copy(tencentSecretId = value) }

    fun updateTencentSecretKey(value: String) = edit { it.copy(tencentSecretKey = value) }

    fun updateTencentRegion(value: String) = edit { it.copy(tencentRegion = value) }

    fun testConnection() {
        testJob?.cancel()
        val settings = _uiState.value.settings
        _uiState.update { it.copy(isTesting = true, testResult = null) }
        testJob = viewModelScope.launch {
            val result = translationManager.testConnection(settings)
            _uiState.update { state ->
                state.copy(
                    isTesting = false,
                    testResult = result.fold(
                        onSuccess = { translated ->
                            ConnectionTestResult.Success(
                                source = TranslationManager.TEST_TEXT,
                                translated = translated,
                            )
                        },
                        onFailure = { error ->
                            val message = when (error) {
                                is TranslationException -> error.message
                                else -> null
                            }
                            ConnectionTestResult.Failure(message ?: "连接失败，请检查配置后重试")
                        },
                    ),
                )
            }
        }
    }

    /** 每次编辑即时持久化；配置变更后之前的测试结果不再可信，一并清除。 */
    private fun edit(transform: (TranslationSettings) -> TranslationSettings) {
        testJob?.cancel()
        val updated = transform(_uiState.value.settings)
        _uiState.update { it.copy(settings = updated, isTesting = false, testResult = null) }
        preferences.save(updated)
    }
}
