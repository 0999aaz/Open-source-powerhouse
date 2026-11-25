package com.example.ghtrending.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghtrending.data.local.BookmarkedRepoRow
import com.example.ghtrending.data.repo.TrendingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BookmarksUiState {
    data object Loading : BookmarksUiState
    data object Empty : BookmarksUiState
    data class Content(val items: List<BookmarkedRepoRow>) : BookmarksUiState
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: TrendingRepository,
) : ViewModel() {

    val uiState: StateFlow<BookmarksUiState> = repository.observeBookmarks()
        .map { list ->
            if (list.isEmpty()) BookmarksUiState.Empty else BookmarksUiState.Content(list)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookmarksUiState.Loading,
        )

    fun removeBookmark(repoId: Long) {
        viewModelScope.launch {
            repository.removeBookmark(repoId)
        }
    }
}
