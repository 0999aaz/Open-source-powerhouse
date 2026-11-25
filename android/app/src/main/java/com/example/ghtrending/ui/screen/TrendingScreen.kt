package com.example.ghtrending.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.ghtrending.data.remote.ApiFailure
import com.example.ghtrending.ui.component.EmptyStateView
import com.example.ghtrending.ui.component.ErrorStateView
import com.example.ghtrending.ui.component.LanguageSelector
import com.example.ghtrending.ui.component.OfflineBanner
import com.example.ghtrending.ui.component.PeriodTabRow
import com.example.ghtrending.ui.component.RepoCard
import com.example.ghtrending.ui.component.SkeletonList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScreen(
    onRepoClick: (Long) -> Unit,
    viewModel: TrendingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val rateLimited by viewModel.rateLimited.collectAsStateWithLifecycle()
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()

    val uiState = viewModel.resolveUiState(
        loadStates = pagingItems.loadState,
        itemCount = pagingItems.itemCount,
        offlineFlag = isOffline,
        rateLimitedFlag = rateLimited,
    )

    LaunchedEffect(rateLimited) {
        if (rateLimited) {
            Toast.makeText(context, "请求过于频繁，请稍后再试", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "GitHub 热门榜",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        Box(modifier = Modifier.padding(end = 12.dp)) {
                            LanguageSelector(
                                languages = languages,
                                selectedLanguageCode = selectedLanguage,
                                onSelectLanguage = viewModel::selectLanguage,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                PeriodTabRow(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = viewModel::selectPeriod,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val isRefreshing = pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount > 0

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { pagingItems.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                is TrendingUiState.Loading -> {
                    SkeletonList()
                }
                is TrendingUiState.Empty -> {
                    EmptyStateView(message = "该条件下暂无数据")
                }
                is TrendingUiState.Error -> {
                    ErrorStateView(
                        message = uiState.message,
                        onRetry = { pagingItems.retry() },
                    )
                }
                is TrendingUiState.Content -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (uiState.bannerMessage != null) {
                            OfflineBanner(text = uiState.bannerMessage)
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                count = pagingItems.itemCount,
                                key = pagingItems.itemKey { it.id },
                            ) { index ->
                                val item = pagingItems[index] ?: return@items
                                RepoCard(
                                    rank = item.rank,
                                    fullName = item.fullName,
                                    description = item.description,
                                    language = item.language,
                                    stars = item.stars,
                                    periodStars = item.periodStars,
                                    isBookmarked = item.isBookmarked,
                                    onClick = { onRepoClick(item.id) },
                                    onToggleBookmark = { viewModel.toggleBookmark(item.id) },
                                )
                            }

                            when (val appendState = pagingItems.loadState.append) {
                                is LoadState.Loading -> {
                                    item(key = "append_loading") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                is LoadState.Error -> {
                                    item(key = "append_error") {
                                        val msg = when (appendState.error) {
                                            is ApiFailure.RateLimited -> "请求过于频繁，请稍后再试"
                                            else -> "加载失败，请检查网络后重试"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = msg,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
