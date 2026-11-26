package com.example.ghtrending.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ghtrending.ui.component.EmptyStateView
import com.example.ghtrending.ui.component.RepoCard
import com.example.ghtrending.ui.component.SkeletonList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onRepoClick: (Long) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "我的收藏",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            when (val state = uiState) {
                is BookmarksUiState.Loading -> SkeletonList()
                is BookmarksUiState.Empty -> EmptyStateView(message = "暂无收藏仓库")
                is BookmarksUiState.Content -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = state.items,
                            key = { it.id },
                        ) { repo ->
                            RepoCard(
                                rank = null,
                                fullName = repo.fullName,
                                description = repo.description,
                                language = repo.language,
                                stars = repo.stars,
                                periodStars = null,
                                isBookmarked = true,
                                onClick = { onRepoClick(repo.id) },
                                onToggleBookmark = { viewModel.removeBookmark(repo.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
