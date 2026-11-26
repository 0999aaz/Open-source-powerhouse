package com.example.ghtrending.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghtrending.data.local.RepoEntity
import com.example.ghtrending.data.remote.ApiFailure
import com.example.ghtrending.data.repo.TrendingRepository
import com.example.ghtrending.data.translation.TranslationException
import com.example.ghtrending.data.translation.TranslationManager
import com.example.ghtrending.ui.component.DescriptionTranslation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** README 文档翻译状态 */
data class ReadmeTranslationState(
    val translatedHtml: String? = null,
    val isLoading: Boolean = false,
    val showTranslated: Boolean = false,
    val completedBatches: Int = 0,
    val totalBatches: Int = 0,
)

sealed interface RepoDetailUiState {
    data object Loading : RepoDetailUiState
    data class Content(
        val repo: RepoEntity,
        val isOffline: Boolean,
        val readmeHtml: String?,
        val readmeLoading: Boolean,
        val readmeError: String?,
    ) : RepoDetailUiState
    data class Error(val message: String) : RepoDetailUiState
}

@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TrendingRepository,
    private val translationManager: TranslationManager,
) : ViewModel() {

    private val repoId: Long = checkNotNull(savedStateHandle["repoId"])

    private val translationHolder = DescriptionTranslationHolder(translationManager, viewModelScope)

    /** 当前仓库简介的翻译状态 */
    val translation: StateFlow<DescriptionTranslation?> = translationHolder.states
        .map { it[repoId] }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _readmeTranslation = MutableStateFlow(ReadmeTranslationState())
    val readmeTranslation: StateFlow<ReadmeTranslationState> = _readmeTranslation.asStateFlow()

    private val _readmeTranslationEvents = Channel<TranslationEvent>(Channel.BUFFERED)
    val translationEvents: Flow<TranslationEvent> = merge(
        translationHolder.events,
        _readmeTranslationEvents.receiveAsFlow(),
    )

    private var readmeTranslateJob: Job? = null

    val isBookmarked: StateFlow<Boolean> = repository.observeIsBookmarked(repoId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val _uiState = MutableStateFlow<RepoDetailUiState>(RepoDetailUiState.Loading)
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            val detailRes = repository.fetchRepoDetail(repoId)
            val repo = detailRes.repo
            if (repo == null) {
                val message = when (val err = detailRes.error) {
                    is ApiFailure.RateLimited -> err.message
                    else -> "加载失败，请检查网络后重试"
                }
                _uiState.value = RepoDetailUiState.Error(message)
                return@launch
            }

            _uiState.value = RepoDetailUiState.Content(
                repo = repo,
                isOffline = detailRes.isOffline,
                readmeHtml = repo.readmeHtml,
                readmeLoading = repo.readmeHtml.isNullOrBlank(),
                readmeError = null,
            )

            loadReadmeOnly()
        }
    }

    fun loadReadmeOnly() {
        viewModelScope.launch {
            val current = _uiState.value as? RepoDetailUiState.Content ?: return@launch
            _uiState.value = current.copy(readmeLoading = true, readmeError = null)
            val readmeRes = repository.fetchRepoReadme(repoId)
            val latest = _uiState.value as? RepoDetailUiState.Content ?: return@launch
            val updatedHtml = readmeRes.html ?: latest.readmeHtml
            if (updatedHtml != latest.readmeHtml) {
                readmeTranslateJob?.cancel()
                _readmeTranslation.value = ReadmeTranslationState()
            }
            _uiState.value = latest.copy(
                readmeHtml = updatedHtml,
                readmeLoading = false,
                readmeError = if (readmeRes.html == null && latest.readmeHtml == null) {
                    readmeRes.errorMessage
                } else {
                    null
                },
            )
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            repository.toggleBookmark(repoId)
        }
    }

    fun toggleTranslation(description: String) {
        translationHolder.toggle(repoId, description)
    }

    fun toggleReadmeTranslation() {
        val currentContent = _uiState.value as? RepoDetailUiState.Content ?: return
        val sourceHtml = currentContent.readmeHtml?.takeIf { it.isNotBlank() } ?: return
        val current = _readmeTranslation.value

        if (current.isLoading) return

        // 已有译文：直接切换原文 / 译文
        if (current.translatedHtml != null) {
            _readmeTranslation.update { it.copy(showTranslated = !it.showTranslated) }
            return
        }

        // 命中会话级缓存
        translationManager.cachedReadmeTranslation(sourceHtml)?.let { cached ->
            _readmeTranslation.value = ReadmeTranslationState(
                translatedHtml = cached,
                showTranslated = true,
            )
            return
        }

        if (!translationManager.isConfigured()) {
            _readmeTranslationEvents.trySend(
                TranslationEvent.NotConfigured(translationManager.currentProvider.displayName),
            )
            return
        }

        readmeTranslateJob?.cancel()
        _readmeTranslation.value = ReadmeTranslationState(isLoading = true)
        readmeTranslateJob = viewModelScope.launch {
            translationManager.translateHtml(
                html = sourceHtml,
                onProgress = { completed, total ->
                    _readmeTranslation.update {
                        it.copy(completedBatches = completed, totalBatches = total)
                    }
                },
            ).onSuccess { translated ->
                _readmeTranslation.value = ReadmeTranslationState(
                    translatedHtml = translated,
                    showTranslated = true,
                )
            }.onFailure { error ->
                _readmeTranslation.update { it.copy(isLoading = false) }
                val event = when (error) {
                    is TranslationException.NotConfigured ->
                        TranslationEvent.NotConfigured(error.provider.displayName)
                    else -> TranslationEvent.Failure(
                        "README 翻译失败：${error.message ?: "请稍后重试"}",
                    )
                }
                _readmeTranslationEvents.trySend(event)
            }
        }
    }
}
