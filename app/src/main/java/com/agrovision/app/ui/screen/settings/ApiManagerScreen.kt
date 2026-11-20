package com.agrovision.app.ui.screen.settings

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.data.local.datastore.AiProviderConfig
import com.agrovision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiManagerScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "新增 Provider")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // 当前激活的 Provider
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = Green50.copy(alpha = 0.06f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = Green50)
                        Spacer(Modifier.width(8.dp))
                        Text("当前 AI 服务", style = MaterialTheme.typography.labelMedium, color = Green50)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(settings.aiProvider.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(settings.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text("模型: ${settings.aiModel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text("全部 Provider", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))

            providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    isActive = provider.baseUrl == settings.baseUrl,
                    onActivate = {
                        viewModel.updateAiProvider(provider.id)
                        viewModel.updateBaseUrl(provider.baseUrl)
                    },
                    onDelete = { viewModel.removeProvider(provider.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showAddDialog) {
        AddProviderDialog(onDismiss = { showAddDialog = false }) { p ->
            viewModel.addProvider(p)
            showAddDialog = false
        }
    }
}

@Composable
private fun ProviderCard(
    provider: AiProviderConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Green50.copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isActive) Green50.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(provider.name.take(1), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold, color = if (isActive) Green50 else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.name, fontWeight = FontWeight.SemiBold)
                    Text(provider.baseUrl, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isActive) {
                    Surface(shape = MaterialTheme.shapes.small, color = Green50.copy(alpha = 0.1f)) {
                        Text("使用中", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = Green50)
                    }
                }
            }
            if (provider.models.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("可用模型:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    provider.models.take(3).forEach { m ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                            Text(m, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (provider.models.size > 3) Text("+${provider.models.size - 3}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isActive) {
                    FilledTonalButton(onClick = onActivate, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("选择")
                    }
                }
                OutlinedButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red50)) {
                    Text("删除", color = Red50)
                }
            }
        }
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (AiProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增 AI Provider", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("https://api.xxx.com/v1") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && url.isNotBlank()) {
                    onAdd(AiProviderConfig(name = name, baseUrl = url))
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
