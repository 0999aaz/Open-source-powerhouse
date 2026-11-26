package com.example.ghtrending.ui.screen

import com.example.ghtrending.data.translation.TranslationException
import com.example.ghtrending.data.translation.TranslationManager
import com.example.ghtrending.ui.component.DescriptionTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 翻译相关的一次性 UI 事件。 */
sealed interface TranslationEvent {
    data class NotConfigured(val providerName: String) : TranslationEvent
    data class Failure(val message: String) : TranslationEvent
}

/**
 * ViewModel 内复用的「项目简介翻译」状态持有者，按 repoId 维护每条简介的加载 / 译文 / 原文切换状态。
 */
class DescriptionTranslationHolder(
    private val translationManager: TranslationManager,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<Long, DescriptionTranslation>>(emptyMap())
    val states: StateFlow<Map<Long, DescriptionTranslation>> = _states.asStateFlow()

    private val _events = Channel<TranslationEvent>(Channel.BUFFERED)
    val events: Flow<TranslationEvent> = _events.receiveAsFlow()

    fun toggle(repoId: Long, text: String) {
        if (text.isBlank()) return
        val current = _states.value[repoId]

        // 正在请求中：忽略重复点击
        if (current?.isLoading == true) return

        // 已有译文：仅在原文 / 译文之间切换
        if (current?.translatedText != null) {
            updateState(repoId) { it.copy(showTranslated = !it.showTranslated) }
            return
        }

        // 命中会话缓存（例如列表页已翻译过，再进详情页）
        translationManager.cachedTranslation(text)?.let { cached ->
            updateState(repoId) { DescriptionTranslation(translatedText = cached, showTranslated = true) }
            return
        }

        if (!translationManager.isConfigured()) {
            _events.trySend(TranslationEvent.NotConfigured(translationManager.currentProvider.displayName))
            return
        }

        updateState(repoId) { it.copy(isLoading = true) }
        scope.launch {
            translationManager.translate(text)
                .onSuccess { translated ->
                    updateState(repoId) {
                        DescriptionTranslation(translatedText = translated, showTranslated = true)
                    }
                }
                .onFailure { error ->
                    updateState(repoId) { it.copy(isLoading = false) }
                    val event = when (error) {
                        is TranslationException.NotConfigured ->
                            TranslationEvent.NotConfigured(error.provider.displayName)
                        else -> TranslationEvent.Failure(
                            "翻译失败：${error.message ?: "请稍后重试"}",
                        )
                    }
                    _events.trySend(event)
                }
        }
    }

    private fun updateState(
        repoId: Long,
        transform: (DescriptionTranslation) -> DescriptionTranslation,
    ) {
        _states.update { map ->
            map + (repoId to transform(map[repoId] ?: DescriptionTranslation()))
        }
    }
}
