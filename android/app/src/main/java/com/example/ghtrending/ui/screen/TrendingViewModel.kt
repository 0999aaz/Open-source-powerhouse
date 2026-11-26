package com.example.ghtrending.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.ghtrending.data.local.LanguageEntity
import com.example.ghtrending.data.local.TrendingRepoRow
import com.example.ghtrending.data.remote.ApiFailure
import com.example.ghtrending.data.repo.TrendingRepository
import com.example.ghtrending.data.translation.TranslationManager
import com.example.ghtrending.ui.component.DescriptionTranslation
import com.example.ghtrending.ui.component.TrendingPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TrendingUiState {
    data object Loading : TrendingUiState
    data class Content(
        val isOffline: Boolean,
        val bannerMessage: String? = null,
    ) : TrendingUiState
    data object Empty : TrendingUiState
    data class Error(val message: String) : TrendingUiState
}

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val repository: TrendingRepository,
    translationManager: TranslationManager,
) : ViewModel() {

    private val translationHolder = DescriptionTranslationHolder(translationManager, viewModelScope)

    /** repoId -> 简介翻译状态 */
    val translations: StateFlow<Map<Long, DescriptionTranslation>> = translationHolder.states

    val translationEvents: Flow<TranslationEvent> = translationHolder.events

    private val _selectedPeriod = MutableStateFlow(TrendingPeriod.DAILY)
    val selectedPeriod: StateFlow<TrendingPeriod> = _selectedPeriod.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<String?>(null)
    val selectedLanguage: StateFlow<String?> = _selectedLanguage.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _rateLimited = MutableStateFlow(false)
    val rateLimited: StateFlow<Boolean> = _rateLimited.asStateFlow()

    val languages: StateFlow<List<LanguageEntity>> = repository.observeLanguages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingFlow: Flow<PagingData<TrendingRepoRow>> = combine(
        _selectedPeriod,
        _selectedLanguage,
    ) { period, language ->
        period to language
    }.flatMapLatest { (period, language) ->
        _isOffline.value = false
        _rateLimited.value = false
        repository.pagedTrending(
            period = period.apiValue,
            language = language,
            onSyncStatusChanged = { offline, limited ->
                _isOffline.value = offline
                _rateLimited.value = limited
            },
        )
    }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            repository.refreshLanguages()
        }
    }

    fun selectPeriod(period: TrendingPeriod) {
        _selectedPeriod.value = period
    }

    fun selectLanguage(languageCode: String?) {
        _selectedLanguage.value = languageCode
    }

    fun toggleBookmark(repoId: Long) {
        viewModelScope.launch {
            repository.toggleBookmark(repoId)
        }
    }

    fun toggleTranslation(repoId: Long, description: String) {
        translationHolder.toggle(repoId, description)
    }

    fun resolveUiState(
        loadStates: CombinedLoadStates,
        itemCount: Int,
        offlineFlag: Boolean,
        rateLimitedFlag: Boolean,
    ): TrendingUiState {
        val refresh = loadStates.refresh
        return when {
            refresh is LoadState.Loading && itemCount == 0 -> TrendingUiState.Loading
            refresh is LoadState.Error && itemCount == 0 -> {
                val msg = when (val err = refresh.error) {
                    is ApiFailure.RateLimited -> err.message
                    is ApiFailure -> err.message
                    else -> "加载失败，请检查网络后重试"
                }
                TrendingUiState.Error(msg)
            }
            refresh is LoadState.NotLoading && itemCount == 0 -> TrendingUiState.Empty
            else -> {
                val banner = when {
                    rateLimitedFlag -> "请求过于频繁，请稍后再试"
                    offlineFlag || refresh is LoadState.Error -> "离线数据"
                    else -> null
                }
                TrendingUiState.Content(
                    isOffline = offlineFlag || refresh is LoadState.Error,
                    bannerMessage = banner,
                )
            }
        }
    }
}
