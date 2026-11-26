package com.example.ghtrending.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ghtrending.data.translation.TranslationProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = "机器翻译",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "使用你自己的 API Key 翻译项目简介。请求由本机直接发送至所选服务商，密钥仅保存在本机。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ProviderSelector(
                selected = settings.provider,
                onSelect = viewModel::selectProvider,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (settings.provider) {
                    TranslationProvider.NIUTRANS -> {
                        CredentialTextField(
                            value = settings.niuTransApiKey,
                            onValueChange = viewModel::updateNiuTransApiKey,
                            label = "API Key",
                            isSecret = true,
                            imeAction = ImeAction.Done,
                        )
                    }
                    TranslationProvider.AZURE -> {
                        CredentialTextField(
                            value = settings.azureSubscriptionKey,
                            onValueChange = viewModel::updateAzureSubscriptionKey,
                            label = "Subscription Key",
                            isSecret = true,
                        )
                        CredentialTextField(
                            value = settings.azureRegion,
                            onValueChange = viewModel::updateAzureRegion,
                            label = "Region",
                            isSecret = false,
                            placeholder = "eastasia",
                            supportingText = "资源所在区域；全局（Global）资源可留空",
                            imeAction = ImeAction.Done,
                        )
                    }
                    TranslationProvider.TENCENT -> {
                        CredentialTextField(
                            value = settings.tencentSecretId,
                            onValueChange = viewModel::updateTencentSecretId,
                            label = "SecretId",
                            isSecret = true,
                        )
                        CredentialTextField(
                            value = settings.tencentSecretKey,
                            onValueChange = viewModel::updateTencentSecretKey,
                            label = "SecretKey",
                            isSecret = true,
                        )
                        CredentialTextField(
                            value = settings.tencentRegion,
                            onValueChange = viewModel::updateTencentRegion,
                            label = "Region",
                            isSecret = false,
                            placeholder = "ap-guangzhou",
                            imeAction = ImeAction.Done,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = providerHint(settings.provider),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTesting && settings.isConfigured(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "正在测试...")
                } else {
                    Icon(
                        imageVector = Icons.Outlined.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "测试连通性")
                }
            }

            uiState.testResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                ConnectionTestResultView(result = result)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSelector(
    selected: TranslationProvider,
    onSelect: (TranslationProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(text = "翻译服务商") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TranslationProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(text = provider.displayName) },
                    onClick = {
                        onSelect(provider)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun CredentialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isSecret: Boolean,
    placeholder: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by rememberSaveable(label) { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        placeholder = if (placeholder != null) {
            { Text(text = placeholder) }
        } else {
            null
        },
        supportingText = if (supportingText != null) {
            { Text(text = supportingText) }
        } else {
            null
        },
        visualTransformation = if (isSecret && !visible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Ascii,
            imeAction = imeAction,
        ),
        trailingIcon = if (isSecret) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "隐藏" else "显示",
                    )
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectionTestResultView(result: ConnectionTestResult) {
    val isSuccess = result is ConnectionTestResult.Success
    val tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (isSuccess) "连接成功" else "连接失败",
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (result) {
                        is ConnectionTestResult.Success -> "${result.source} → ${result.translated}"
                        is ConnectionTestResult.Failure -> result.message
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun providerHint(provider: TranslationProvider): String = when (provider) {
    TranslationProvider.NIUTRANS -> "在小牛翻译开放平台「控制台 → API 应用」中获取 API Key。"
    TranslationProvider.AZURE -> "在 Azure 门户的 Translator 资源「密钥和终结点」页获取密钥与区域。"
    TranslationProvider.TENCENT -> "在腾讯云「访问管理 → API 密钥管理」中获取 SecretId / SecretKey，建议使用仅授权 TMT 的子账号密钥。"
}
