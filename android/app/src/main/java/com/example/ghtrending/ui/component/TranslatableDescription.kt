package com.example.ghtrending.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 单条项目简介的翻译状态。
 */
@Immutable
data class DescriptionTranslation(
    val translatedText: String? = null,
    val isLoading: Boolean = false,
    val showTranslated: Boolean = false,
)

/**
 * 带「翻译」切换按钮的项目简介。[onToggleTranslate] 为 null 时不显示翻译按钮。
 */
@Composable
fun TranslatableDescription(
    text: String,
    translation: DescriptionTranslation?,
    onToggleTranslate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val shownTranslation = translation?.takeIf { it.showTranslated }?.translatedText
    val isLoading = translation?.isLoading == true

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shownTranslation ?: text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (shownTranslation != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "机器翻译 · 再次点击查看原文",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (onToggleTranslate != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onToggleTranslate,
                enabled = !isLoading,
                modifier = Modifier.size(28.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = if (shownTranslation != null) "显示原文" else "翻译简介",
                        tint = if (shownTranslation != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * 未配置翻译密钥时的引导弹窗。
 */
@Composable
fun TranslationNotConfiguredDialog(
    providerName: String,
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "未配置翻译服务") },
        text = {
            Text(
                text = "当前翻译服务为「$providerName」，但尚未填写密钥。请前往设置页填写你自己的 API Key 后再使用翻译功能。",
            )
        },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text(text = "去设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}
