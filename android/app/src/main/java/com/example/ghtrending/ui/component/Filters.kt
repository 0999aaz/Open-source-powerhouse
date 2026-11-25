package com.example.ghtrending.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ghtrending.data.local.LanguageEntity

enum class TrendingPeriod(val apiValue: String, val labelZh: String) {
    DAILY("daily", "今日"),
    WEEKLY("weekly", "本周"),
    MONTHLY("monthly", "本月"),
}

@Composable
fun PeriodTabRow(
    selectedPeriod: TrendingPeriod,
    onSelectPeriod: (TrendingPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = TrendingPeriod.entries
    TabRow(
        selectedTabIndex = periods.indexOf(selectedPeriod),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        periods.forEach { period ->
            Tab(
                selected = period == selectedPeriod,
                onClick = { onSelectPeriod(period) },
                text = {
                    Text(
                        text = period.labelZh,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        }
    }
}

@Composable
fun LanguageSelector(
    languages: List<LanguageEntity>,
    selectedLanguageCode: String?,
    onSelectLanguage: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = remember(languages, selectedLanguageCode) {
        if (selectedLanguageCode == null) {
            "全部语言"
        } else {
            languages.firstOrNull { it.code == selectedLanguageCode }?.label
                ?: selectedLanguageCode
        }
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { expanded = true },
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = "编程语言筛选",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "展开语言列表",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 360.dp)
                .width(220.dp),
        ) {
            DropdownMenuItem(
                text = { Text("全部语言") },
                trailingIcon = {
                    if (selectedLanguageCode == null) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onSelectLanguage(null)
                },
            )
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.label) },
                    trailingIcon = {
                        if (selectedLanguageCode == lang.code) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectLanguage(lang.code)
                    },
                )
            }
        }
    }
}
