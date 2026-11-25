package com.example.ghtrending.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghtrending.data.local.RepoEntity
import com.example.ghtrending.data.remote.ApiFailure
import com.example.ghtrending.data.repo.TrendingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
) : ViewModel() {

    private val repoId: Long = checkNotNull(savedStateHandle["repoId"])

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
            _uiState.value = latest.copy(
                readmeHtml = readmeRes.html ?: latest.readmeHtml,
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
}
