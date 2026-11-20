package com.agrovision.app.ui.screen.settings

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.BuildConfig
import com.agrovision.app.data.local.datastore.AppSettings
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit = {},
    onNavigateToManual: () -> Unit = {},
    onNavigateToApiManager: () -> Unit = {},
    onNavigateToDevCenter: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val cacheSizes by viewModel.cacheSizes.collectAsStateWithLifecycle()
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    val accountAction by viewModel.accountAction.collectAsStateWithLifecycle()

    // 对话框状态
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showBindPhoneDialog by remember { mutableStateOf(false) }

    // 账号操作结果处理
    // 退出登录 / 注销账号后，AuthRepository 状态会切换为 NotLoggedIn，
    // AgroVisionNavHost 会自动重组并显示 AuthNavHost，无需手动导航。
    LaunchedEffect(accountAction) {
        when (accountAction) {
            is AccountAction.LoggedOut, is AccountAction.Deleted -> {
                viewModel.clearAccountAction()
            }
            else -> {}
        }
    }

    // ===== 对话框 =====

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录", fontWeight = FontWeight.Bold) },
            text = { Text("将清除 API Key 等凭据并返回登录页，是否继续？") },
            confirmButton = {
                Button(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("取消") } }
        )
    }

    if (showDeleteAccountDialog) {
        val dangerRed = brandRed()
        val dangerBtnBg = brandRed()
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("注销账号", fontWeight = FontWeight.Bold, color = dangerRed) },
            text = {
                Text("此操作不可逆！将永久清除：识别历史、反馈、API 配置、缓存等全部用户数据。是否确认注销？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dangerBtnBg)
                ) { Text("确认注销") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("取消") } }
        )
    }

    if (showBindPhoneDialog) {
        BindPhoneDialog(
            currentPhone = phone ?: "",
            onDismiss = { showBindPhoneDialog = false },
            onConfirm = { number ->
                viewModel.bindPhone(number)
                showBindPhoneDialog = false
            }
        )
    }

    // 账号操作失败/成功提示
    accountAction?.let { action ->
        if (action is AccountAction.Failed || action is AccountAction.Success) {
            val msg = when (action) {
                is AccountAction.Failed -> action.message
                is AccountAction.Success -> action.message
                else -> ""
            }
            LaunchedEffect(action) {
                viewModel.clearAccountAction()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置中心", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // ========================================
            // 一、账号与个人信息
            // ========================================
            if (FeatureFlag.isV2AccountEnabled) {
                SectionHeader("账号与个人信息")
                SettingsCard {
                    ProfileRow(Icons.Filled.AccountCircle, "昵称", settings.nickname) {
                        viewModel.updateNickname(it)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { showBindPhoneDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Phone, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("手机号", style = MaterialTheme.typography.bodyMedium)
                            Text(phone?.ifEmpty { "未绑定" } ?: "未绑定",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Edit, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    EditRow(Icons.Filled.Email, "邮箱", settings.email.ifEmpty { "未设置" }) {
                        viewModel.updateEmail(it)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    InfoRow(Icons.Filled.VerifiedUser, "账号类型", "已登录用户")
                }

                Spacer(Modifier.height(8.dp))
                SettingsCard {
                    ClickRow(Icons.AutoMirrored.Filled.Logout, "退出登录", "清除凭据并切换到登录页") {
                        showLogoutDialog = true
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    val dangerRed = brandRed()
                    ClickRow(Icons.Filled.Delete, "注销账号", "清空全部数据并永久删除账号",
                        iconTint = dangerRed, valueColor = dangerRed) {
                        showDeleteAccountDialog = true
                    }
                }
            } else {
                SectionHeader("本地资料")
                SettingsCard {
                    ProfileRow(Icons.Filled.AccountCircle, "昵称", settings.nickname) {
                        viewModel.updateNickname(it)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    InfoRow(Icons.Filled.PhoneAndroid, "运行模式", "本地模式")
                }
            }

            // ========================================
            // 二、AI 模型设置
            // ========================================
            SectionHeader("AI 模型设置")
            SettingsCard {
                DropdownRow(Icons.Filled.Hub, "AI Provider", settings.aiProvider,
                    listOf("deepseek" to "DeepSeek", "qwen" to "Qwen 通义千问", "openai" to "OpenAI", "gemini" to "Gemini", "ollama" to "Ollama 本地", "custom" to "自定义 API")
                ) { viewModel.updateAiProvider(it) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                // API Key 安全输入
                PasswordRow(Icons.Filled.Key, "API Key", settings.apiKey) { viewModel.updateApiKey(it) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                EditRow(Icons.Filled.Link, "Base URL", settings.baseUrl) { viewModel.updateBaseUrl(it) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DropdownRow(Icons.Filled.ModelTraining, "AI 模型", settings.aiModel,
                    listOf(
                        "deepseek-chat" to "DeepSeek-V3",
                        "deepseek-reasoner" to "DeepSeek-R1",
                        "qwen-max" to "Qwen-Max",
                        "qwen-plus" to "Qwen-Plus",
                        "gpt-4o" to "GPT-4o",
                        "gpt-4.1" to "GPT-4.1",
                        "gemini-2.0-flash" to "Gemini 2.0 Flash",
                        "gemini-2.0-pro" to "Gemini 2.0 Pro"
                    )
                ) { viewModel.updateAiModel(it) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SliderRow("Temperature", settings.temperature, 0f, 2f) { viewModel.updateTemperature(it) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SliderRow("Top P", settings.topP, 0f, 1f) { viewModel.updateTopP(it) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DropdownRow(Icons.Filled.TextFields, "Max Tokens", settings.maxTokens.toString(),
                    listOf("512" to "512", "1024" to "1024", "2048" to "2048", "4096" to "4096", "8192" to "8192", "16384" to "16384", "32768" to "32768")
                ) { viewModel.updateMaxTokens(it.toIntOrNull() ?: 4096) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DropdownRow(Icons.Filled.Storage, "Context Length", settings.contextLength.toString(),
                    listOf("4096" to "4096", "8192" to "8192", "16384" to "16384", "32768" to "32768")
                ) { viewModel.updateContextLength(it.toIntOrNull() ?: 8192) }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SwitchRow(Icons.AutoMirrored.Filled.MenuBook, "启用 RAG 知识库", settings.enableRag) { viewModel.updateRag(it) }
            }

            // 跳转 API 管理页
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                val brandBlue = brandBlue()
                ClickRow(Icons.Filled.Api, "API 管理", "管理全部 AI Provider 配置", iconTint = brandBlue, valueColor = brandBlue) {
                    onNavigateToApiManager()
                }
            }

            // ========================================
            // 二点五、天气服务
            // ========================================
            SectionHeader("天气服务")
            SettingsCard {
                EditRow(Icons.Filled.Cloud, "和风天气 API Key", settings.weatherApiKey,
                    hint = "留空则使用免费天气服务") { viewModel.updateWeatherApiKey(it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                val weatherSource = if (settings.weatherApiKey.isNotBlank()) "和风天气（已配置Key）" else "wttr.in（免费兜底）"
                InfoRow(Icons.Filled.Info, "当前天气源", weatherSource)
            }

            // ========================================
            // 三、识别模型信息（只读 — 实际模型为 MobileNetV3，非 YOLO）
            // ========================================
            SectionHeader("识别模型信息")
            SettingsCard {
                InfoRow(Icons.Filled.Visibility, "模型架构", "MobileNetV3-Small")
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                InfoRow(Icons.Filled.Memory, "推理引擎", "ONNX Runtime 1.17")
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                InfoRow(Icons.Filled.AspectRatio, "输入尺寸", "224 × 224")
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                InfoRow(Icons.Filled.Speed, "推理方式", "CPU")
            }

            // ========================================
            // 四、服务器配置（V2.0 云同步）
            // ========================================
            if (FeatureFlag.isV2CloudSyncEnabled) {
                SectionHeader("服务器配置")
                SettingsCard {
                    SwitchRow(Icons.Filled.Cloud, "启用服务器", settings.serverEnabled) { viewModel.updateServerEnabled(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    EditRow(Icons.Filled.Dns, "API 服务器地址", settings.serverBaseUrl, hint = "http://192.168.1.10:8000") {
                        viewModel.updateServerUrl(it)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // 测试连接按钮
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NetworkCheck, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text("测试连接", style = MaterialTheme.typography.bodyMedium)
                        }
                        when (val s = serverStatus) {
                            is ServerStatus.Testing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            is ServerStatus.Success -> {
                                val brandGreen = brandGreen()
                                Button(onClick = { viewModel.testConnection() }, colors = ButtonDefaults.buttonColors(containerColor = brandGreen), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${s.latencyMs}ms", color = Color.White)
                                }
                            }
                            is ServerStatus.Failed -> {
                                val dangerRed = brandRed()
                                Button(onClick = { viewModel.testConnection() }, colors = ButtonDefaults.buttonColors(containerColor = dangerRed.copy(alpha = 0.08f), contentColor = dangerRed), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                                    Text("重试", color = dangerRed)
                                }
                            }
                            null -> {
                                Button(onClick = { viewModel.testConnection() }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                                    Text("Ping")
                                }
                            }
                        }
                    }
                }
            }

            // ========================================
            // 五、机器人设置（仅 debug 版可见）
            // ========================================
            if (FeatureFlag.isV3Enabled) {
                SectionHeader("机器人设置（V3.0）")
                SettingsCard {
                    EditRow(Icons.Filled.Router, "ROS IP", settings.rosIp, hint = "192.168.1.200") { viewModel.updateRosIp(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    EditRow(Icons.Filled.SettingsEthernet, "ROS Port", settings.rosPort, hint = "9090") { viewModel.updateRosPort(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    EditRow(Icons.Filled.Cable, "MQTT Broker", settings.mqttBroker, hint = "tcp://192.168.1.100:1883") { viewModel.updateMqttBroker(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    DropdownRow(Icons.Filled.PrecisionManufacturing, "机器人型号", settings.robotName,
                        listOf("go2" to "Go2-W 四足", "ros_car" to "ROS2 小车", "leju" to "乐聚机器人")
                    ) { viewModel.updateRobotName(it) }
                }
            }

            // ========================================
            // 六、地图（仅 debug 版可见）
            // ========================================
            if (FeatureFlag.isV3Enabled) {
                SectionHeader("地图设置")
                SettingsCard {
                    val accentOrange = accentOrange()
                    val dangerRed = brandRed()
                    SwitchRow(Icons.Filled.GpsFixed, "记录GPS轨迹", settings.gpsRecordTrack, iconTint = accentOrange) { viewModel.updateGpsTrack(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Filled.Whatshot, "病害热力图", settings.heatmapEnabled, iconTint = dangerRed) { viewModel.updateHeatmap(it) }
                }
            }

            // ========================================
            // 七、数据同步（V2.0 云同步）
            // ========================================
            if (FeatureFlag.isV2CloudSyncEnabled) {
                SectionHeader("数据同步")
                SettingsCard {
                    SwitchRow(Icons.Filled.Sync, "自动同步", settings.autoSync) { viewModel.updateAutoSync(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Filled.Wifi, "仅在 WiFi 下同步", settings.syncWifiOnly) { viewModel.updateSyncWifiOnly(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Filled.SignalCellularAlt, "移动网络同步", settings.syncMobileNetwork) { viewModel.updateSyncMobile(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Filled.CloudUpload, "上传图片", settings.syncUploadImages) { viewModel.updateUploadImages(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Filled.Download, "下载模型", settings.syncDownloadModel) { viewModel.updateDownloadModel(it) }
                }
            }

            // ========================================
            // 八、缓存管理
            // ========================================
            SectionHeader("缓存管理")
            SettingsCard {
                cacheSizes.forEach { (name, size) ->
                    InfoRow(
                        when (name) {
                            "图片缓存" -> Icons.Filled.PhotoLibrary
                            "AI缓存" -> Icons.Filled.Psychology
                            "OCR缓存" -> Icons.Filled.Description
                            "日志缓存" -> Icons.Filled.Terminal
                            "模型缓存" -> Icons.Filled.Memory
                            else -> Icons.Filled.Folder
                        },
                        name, size
                    )
                    if (name != cacheSizes.keys.last()) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("总计", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val totalCacheMb = cacheSizes.values.sumOf { it.substringBefore(" ").toDoubleOrNull() ?: 0.0 }
                        Text("约 ${"%.1f".format(totalCacheMb)} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(
                            onClick = { viewModel.clearCache() }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("清除全部缓存") }
                    }
                }
            }

            // ========================================
            // 八、数据备份
            // ========================================
            SectionHeader("数据备份")
            SettingsCard {
                val context = LocalContext.current
                val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        viewModel.launch { viewModel.importData(uri) }
                    }
                }

                val brandGreen = brandGreen()
                ClickRow(Icons.Filled.Upload, "导出数据", "导出识别历史和反馈为 JSON 文件", iconTint = brandGreen) {
                    viewModel.launch {
                        val intent = viewModel.exportData()
                        if (intent != null) {
                            context.startActivity(android.content.Intent.createChooser(intent, "导出备份文件"))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                val brandBlue = brandBlue()
                ClickRow(Icons.Filled.Download, "导入数据", "从备份文件恢复数据", iconTint = brandBlue) {
                    filePickerLauncher.launch("application/json")
                }

                when (val status = backupStatus) {
                    is BackupStatus.Exporting -> {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在导出...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is BackupStatus.ExportSuccess -> {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        InfoRow(Icons.Filled.CheckCircle, "导出成功", "请选择保存位置")
                    }
                    is BackupStatus.Importing -> {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在导入...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is BackupStatus.ImportSuccess -> {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        InfoRow(
                            Icons.Filled.CheckCircle,
                            "导入成功",
                            "${status.plotCount} 个地块，${status.treatmentCount} 条用药，${status.historyCount} 条历史，${status.feedbackCount} 条反馈"
                        )
                    }
                    is BackupStatus.Failed -> {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        InfoRow(Icons.Filled.Error, "操作失败", status.message)
                    }
                    null -> {}
                }
            }

            // ========================================
            // 九、主题与外观
            // ========================================
            SectionHeader("主题与外观")
            SettingsCard {
                DropdownRow(Icons.Filled.DarkMode, "主题模式", settings.themeMode,
                    listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统")
                ) { viewModel.updateThemeMode(it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SwitchRow(Icons.Filled.Palette, "Material You 动态颜色", settings.materialYou) { viewModel.updateMaterialYou(it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DropdownRow(Icons.Filled.FormatSize, "字体大小", settings.fontSize,
                    listOf("small" to "小", "normal" to "标准", "large" to "大")
                ) { viewModel.updateFontSize(it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SwitchRow(Icons.Filled.Animation, "页面动画", settings.animationsEnabled) { viewModel.updateAnimations(it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DropdownRow(Icons.Filled.Language, "语言", settings.language,
                    listOf("zh" to "简体中文", "en" to "English")
                ) { viewModel.updateLanguage(it) }
            }

            // ========================================
            // 十、开发者模式
            // ========================================
            if (BuildConfig.DEBUG) {
                SectionHeader("开发者模式")
                SettingsCard {
                    SwitchRow(Icons.Filled.Terminal, "开发者模式", settings.devMode) { viewModel.updateDevMode(it) }
                    if (settings.devMode) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SwitchRow(Icons.Filled.Speed, "显示 FPS", settings.showFps) { viewModel.updateDevShowFps(it) }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SwitchRow(Icons.Filled.Memory, "显示 GPU 状态", settings.showGpuStatus) { viewModel.updateDevShowGpu(it) }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ClickRow(Icons.Filled.Build, "开发者中心", "调试工具 · 日志 · API测试") {
                            onNavigateToDevCenter()
                        }
                    }
                }
            }

            // ========================================
            // 十一、关于
            // ========================================
            SectionHeader("关于")
            SettingsCard {
                ClickRow(Icons.AutoMirrored.Filled.MenuBook, "用户使用手册", "功能说明 · 操作指南") { onNavigateToManual() }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ClickRow(Icons.Filled.Info, "关于 AgroVision", "许可证 · 团队") { onNavigateToAbout() }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                InfoRow(Icons.Filled.Smartphone, "版本号", BuildConfig.VERSION_NAME)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                InfoRow(Icons.Filled.BuildCircle, "Build", "2026.07.04-debug")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ======================== 可复用组件 ========================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(content = content) }
}

@Composable
private fun SwitchRow(
    icon: ImageVector, label: String, checked: Boolean,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, rangeFrom: Float, rangeTo: Float, onValueChange: (Float) -> Unit) {
    var showSlider by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable { showSlider = !showSlider },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(String.format("%.2f", value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(showSlider) {
            Slider(
                value = value, onValueChange = onValueChange,
                valueRange = rangeFrom..rangeTo,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DropdownRow(
    icon: ImageVector, label: String, current: String,
    options: List<Pair<String, String>>, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Box {
                FilledTonalButton(
                    onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(options.find { it.first == current }?.second ?: current, style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (key, labelItem) ->
                        DropdownMenuItem(
                            text = { Text(labelItem) },
                            onClick = { onSelect(key); expanded = false },
                            leadingIcon = if (key == current) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditRow(
    icon: ImageVector, label: String, value: String,
    hint: String = "", onValueChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }

    if (editing) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label) }, placeholder = { Text(hint) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onValueChange(text); editing = false }) {
                        Icon(Icons.Filled.Check, "确认")
                    }
                }
            )
        }
    } else {
        Row(Modifier.fillMaxWidth().clickable { editing = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(value.ifEmpty { hint }, style = MaterialTheme.typography.labelSmall,
                    color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun PasswordRow(icon: ImageVector, label: String, value: String, onValueChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }

    if (editing) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                        IconButton(onClick = { onValueChange(text); editing = false }) {
                            Icon(Icons.Filled.Check, "确认")
                        }
                    }
                }
            )
        }
    } else {
        Row(Modifier.fillMaxWidth().clickable { editing = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(if (value.isNotEmpty()) "●●●●●●●●" else "未设置", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector, label: String, value: String,
    editable: Boolean = true, onValueChange: (String) -> Unit = {}
) {
    if (editable) {
        EditRow(icon, label, value) { onValueChange(it) }
    } else {
        InfoRow(icon, label, value)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClickRow(
    icon: ImageVector, label: String, desc: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onClick() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = valueColor.copy(alpha = 0.5f))
    }
}

@Composable
private fun BindPhoneDialog(
    currentPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var phone by remember { mutableStateOf(currentPhone) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绑定手机号", fontWeight = FontWeight.Bold) },
        text = {
            val dangerRed = brandRed()
            Column {
                OutlinedTextField(
                    value = phone, onValueChange = {
                        phone = it
                        error = ""
                    },
                    label = { Text("手机号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) {
                        { Text(error, color = dangerRed) }
                    } else null,
                    placeholder = { Text("请输入 11 位手机号") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmed = phone.trim()
                if (!trimmed.matches(Regex("^1[3-9]\\d{9}$"))) {
                    error = "请输入有效的 11 位手机号"
                } else {
                    onConfirm(trimmed)
                }
            }) { Text("绑定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
