package com.agrovision.app.ui.screen.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.BuildConfig
import com.agrovision.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevCenterScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DevCenterViewModel = hiltViewModel()
) {
    val apiTest by viewModel.apiTest.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val dbStats by viewModel.dbStats.collectAsStateWithLifecycle()
    val modelInfo by viewModel.modelInfo.collectAsStateWithLifecycle()
    val mockMode by viewModel.mockMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 进入页面时刷新数据
    LaunchedEffect(Unit) {
        viewModel.refreshLogs()
        viewModel.refreshDbStats()
        viewModel.refreshModelInfo()
    }

    var showModelSheet by remember { mutableStateOf(false) }
    var showDbSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者中心", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

            // ========== 系统信息 ==========
            SectionHeader("系统信息")
            InfoCard(listOf(
                "应用版本" to "AgroVision ${BuildConfig.VERSION_NAME}",
                "Build" to "2026.07.04-debug",
                "Compose BOM" to "2024.09.03",
                "Material3" to "1.2.0",
                "识别模型" to "MobileNetV3-Small (ONNX Runtime 1.17)",
                "LLM" to "DeepSeek / Qwen / OpenAI / Gemini",
                "架构" to "MVVM + Clean Architecture",
                "DI" to "Hilt"
            ))

            // ========== API 测试 ==========
            SectionHeader("API 调试")
            ApiTesterCard(apiTest) { url, key, model ->
                viewModel.testApiConnection(url, key, model)
            }

            // ========== 日志 ==========
            SectionHeader("日志（${logs.size} 条）")
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("实时日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    viewModel.exportLogs()?.let { file ->
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "导出日志"))
                                    }
                                }
                            }) { Text("导出") }
                            TextButton(onClick = { viewModel.clearLogs() }) { Text("清除") }
                            TextButton(onClick = { viewModel.refreshLogs() }) { Text("刷新") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        if (logs.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("暂无日志", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                                logs.takeLast(200).forEach { entry ->
                                    LogLine(entry.level, entry.tag, entry.message, entry.time)
                                }
                            }
                        }
                    }
                }
            }

            // ========== 数据库统计 ==========
            SectionHeader("数据库")
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    InfoRowInline("识别历史", "${dbStats.historyCount} 条")
                    Divider(Modifier.padding(horizontal = 16.dp))
                    InfoRowInline("用户反馈", "${dbStats.feedbackCount} 条")
                    Divider(Modifier.padding(horizontal = 16.dp))
                    val accText = if (dbStats.feedbackAccuracy < 0) "暂无数据"
                    else "%.1f%%".format(dbStats.feedbackAccuracy * 100)
                    InfoRowInline("识别准确率", accText)
                    Divider(Modifier.padding(horizontal = 16.dp))
                    DevAction("刷新统计", Icons.Filled.Refresh) { viewModel.refreshDbStats() }
                    Divider(Modifier.padding(horizontal = 16.dp))
                    DevAction("查看数据库详情", Icons.Filled.Storage) { showDbSheet = true }
                }
            }

            // ========== 快捷操作 ==========
            SectionHeader("快捷操作")
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    DevAction("YOLO/识别 模型信息", Icons.Filled.ModelTraining) {
                        viewModel.refreshModelInfo()
                        showModelSheet = true
                    }
                    Divider(Modifier.padding(horizontal = 16.dp))
                    DevAction("导出诊断日志", Icons.Filled.SaveAlt) {
                        scope.launch {
                            viewModel.exportDiagnosticReport()?.let { file ->
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "导出诊断报告"))
                            }
                        }
                    }
                    Divider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.toggleMockMode() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Science, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Mock 模式", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(if (mockMode) "已开启" else "已关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mockMode) Green50 else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = mockMode, onCheckedChange = { viewModel.toggleMockMode() })
                    }
                }
            }

            // ========== 开源依赖 ==========
            SectionHeader("开源依赖")
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    DepRow("ONNX Runtime", "1.17.0", "")
                    DepRow("Jetpack Compose", "BOM 2024.09.03", "")
                    DepRow("Hilt", "2.50", "")
                    DepRow("Room", "2.6.1", "")
                    DepRow("OkHttp", "4.12.0", "")
                    DepRow("DataStore", "1.0.0", "")
                    DepRow("Timber", "5.0.1", "")
                    DepRow("Gson", "2.10.1", "")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ===== 模型信息 BottomSheet =====
    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("模型文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                modelInfo.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                            Text(m.path, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(m.sizeText, style = MaterialTheme.typography.labelSmall,
                            color = if (m.exists) Green50 else Red50)
                    }
                    Divider(Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ===== 数据库详情 BottomSheet =====
    if (showDbSheet) {
        ModalBottomSheet(onDismissRequest = { showDbSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("数据库统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                InfoRowInline("识别历史", "${dbStats.historyCount} 条")
                InfoRowInline("用户反馈", "${dbStats.feedbackCount} 条")
                val accText = if (dbStats.feedbackAccuracy < 0) "暂无数据"
                else "%.1f%%".format(dbStats.feedbackAccuracy * 100)
                InfoRowInline("识别准确率", accText)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun InfoCard(items: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column {
            items.forEachIndexed { i, (k, v) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                if (i < items.lastIndex) Divider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ApiTesterCard(
    state: DevCenterViewModel.ApiTestState,
    onTest: (String, String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("deepseek-chat") }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://api.deepseek.com") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("sk-xxx") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(model, { model = it }, label = { Text("模型") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("deepseek-chat") })
            Spacer(Modifier.height(12.dp))

            Button(onClick = { onTest(url, key, model) },
                modifier = Modifier.fillMaxWidth(), enabled = !state.testing) {
                if (state.testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Text("测试连接")
                }
            }

            if (state.result.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        state.success?.let { ok ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                                    Modifier.size(16.dp), tint = if (ok) Green50 else Red50)
                                Spacer(Modifier.width(6.dp))
                                Text(if (ok) "成功" else "失败",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (ok) Green50 else Red50, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(state.result, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(level: String, tag: String, msg: String, time: String) {
    Text("$time $level/$tag: $msg",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = when (level) {
            "E" -> Red50
            "W" -> Orange50
            "I" -> Green50
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        })
}

@Composable
private fun DevAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun InfoRowInline(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DepRow(name: String, version: String, url: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(version, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}
