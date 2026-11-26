package com.example.ghtrending.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ghtrending.ui.component.ErrorStateView
import com.example.ghtrending.ui.component.OfflineBanner
import com.example.ghtrending.ui.component.TranslatableDescription
import com.example.ghtrending.ui.component.TranslationNotConfiguredDialog
import com.example.ghtrending.ui.component.formatCount
import com.example.ghtrending.ui.theme.languageColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RepoDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val translation by viewModel.translation.collectAsStateWithLifecycle()
    val readmeTranslation by viewModel.readmeTranslation.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var notConfiguredProvider by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.translationEvents.collect { event ->
            when (event) {
                is TranslationEvent.NotConfigured -> notConfiguredProvider = event.providerName
                is TranslationEvent.Failure -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    notConfiguredProvider?.let { providerName ->
        TranslationNotConfiguredDialog(
            providerName = providerName,
            onGoToSettings = {
                notConfiguredProvider = null
                onOpenSettings()
            },
            onDismiss = { notConfiguredProvider = null },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "仓库详情",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isBookmarked) "取消收藏" else "收藏",
                            tint = if (isBookmarked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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
            when (val state = uiState) {
                is RepoDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is RepoDetailUiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = viewModel::loadAll,
                    )
                }
                is RepoDetailUiState.Content -> {
                    val repo = state.repo
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (state.isOffline) {
                            OfflineBanner(text = "离线数据")
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = repo.fullName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )

                            val description = repo.description
                            if (!description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TranslatableDescription(
                                    text = description,
                                    translation = translation,
                                    onToggleTranslate = { viewModel.toggleTranslation(description) },
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!repo.language.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(languageColor(repo.language)),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = repo.language,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "星标数",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${formatCount(repo.stars)} 星标",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CallSplit,
                                        contentDescription = "分叉数",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${formatCount(repo.forks)} 分叉",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }

                            if (!repo.createdAt.isNullOrBlank() || !repo.pushedAt.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp),
                                        ),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (!repo.createdAt.isNullOrBlank()) {
                                            Text(
                                                text = "创建于：${repo.createdAt.take(10)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (!repo.pushedAt.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "最近推送：${repo.pushedAt.take(10)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = {
                                        openInCustomTabs(
                                            context,
                                            "https://github.com/${repo.fullName}",
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "在 GitHub 打开")
                                }

                                OutlinedButton(
                                    onClick = viewModel::toggleBookmark,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) {
                                            Icons.Filled.Bookmark
                                        } else {
                                            Icons.Outlined.BookmarkBorder
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = if (isBookmarked) "取消收藏" else "收藏")
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "README 文档",
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                if (!state.readmeLoading && !state.readmeHtml.isNullOrBlank()) {
                                    val showingTranslated =
                                        readmeTranslation.showTranslated && readmeTranslation.translatedHtml != null
                                    TextButton(
                                        onClick = viewModel::toggleReadmeTranslation,
                                        enabled = !readmeTranslation.isLoading,
                                    ) {
                                        if (readmeTranslation.isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val progressLabel = if (readmeTranslation.totalBatches > 0) {
                                                "翻译中 ${readmeTranslation.completedBatches}/${readmeTranslation.totalBatches}"
                                            } else {
                                                "翻译中..."
                                            }
                                            Text(text = progressLabel)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Translate,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = if (showingTranslated) "查看原文" else "翻译文档")
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            when {
                                state.readmeLoading -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "正在加载 README...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                !state.readmeError.isNullOrBlank() -> {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(8.dp),
                                            ),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = state.readmeError,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                            TextButton(onClick = viewModel::loadReadmeOnly) {
                                                Text(text = "重试")
                                            }
                                        }
                                    }
                                }
                                !state.readmeHtml.isNullOrBlank() -> {
                                    val displayHtml = readmeTranslation.translatedHtml
                                        ?.takeIf { readmeTranslation.showTranslated }
                                        ?: state.readmeHtml
                                    ReadmeWebView(
                                        repoFullName = repo.fullName,
                                        sanitizedHtml = displayHtml,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReadmeWebView(
    repoFullName: String,
    sanitizedHtml: String,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val styledHtml = remember(sanitizedHtml, isDark) {
        val bg = if (isDark) "#0D1117" else "#FFFFFF"
        val fg = if (isDark) "#E6EDF3" else "#1F2328"
        val muted = if (isDark) "#8B949E" else "#656D76"
        val codeBg = if (isDark) "#161B22" else "#F6F8FA"
        val border = if (isDark) "#30363D" else "#D0D7DE"
        val link = if (isDark) "#58A6FF" else "#0969DA"
        """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
              body {
                margin: 0;
                padding: 4px 0 24px 0;
                background-color: $bg;
                color: $fg;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                font-size: 14px;
                line-height: 1.6;
                word-wrap: break-word;
              }
              a { color: $link; text-decoration: none; }
              img { max-width: 100%; height: auto; }
              pre, code {
                background-color: $codeBg;
                border-radius: 6px;
                font-family: monospace;
                font-size: 12.5px;
              }
              pre { padding: 12px; overflow-x: auto; border: 1px solid $border; }
              code { padding: 2px 5px; }
              pre code { padding: 0; }
              blockquote {
                margin: 0;
                padding: 0 12px;
                color: $muted;
                border-left: 3px solid $border;
              }
              table { border-collapse: collapse; width: 100%; display: block; overflow-x: auto; }
              th, td { border: 1px solid $border; padding: 6px 10px; }
              h1, h2, h3 { border-bottom: 1px solid $border; padding-bottom: 4px; }
            </style>
          </head>
          <body>$sanitizedHtml</body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                // 双保险客户端沙箱：严格禁用 JS 与本地文件/内容协议访问
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.domStorageEnabled = false
                settings.loadsImagesAutomatically = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url ?: return true
                        val scheme = url.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https") {
                            openInCustomTabs(context, url.toString())
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != styledHtml) {
                webView.tag = styledHtml
                webView.loadDataWithBaseURL(
                    "https://github.com/$repoFullName/",
                    styledHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

fun openInCustomTabs(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }
}
