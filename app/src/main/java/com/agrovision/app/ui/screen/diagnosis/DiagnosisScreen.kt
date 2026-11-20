package com.agrovision.app.ui.screen.diagnosis

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agrovision.app.ui.theme.*

/**
 * 问诊结果页 — 识别后自动生成
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisScreen(
    diseaseName: String = "",
    confidence: Float = 0f,
    imagePath: String = "",
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToFarm: () -> Unit = {},
    onNavigateToPesticideCenter: (String) -> Unit = {},
    viewModel: DiagnosisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun shareReport() {
        val report = uiState.report ?: return
        val text = buildString {
            append("【问诊结果】${report.diseaseName}\n")
            append("严重程度：${report.dangerLevel}\n\n")
            append("病害分析：${report.analysis}\n\n")
            append("发病原因：${report.causes}\n\n")
            append("今天先做：${report.prevention}\n\n")
            if (report.pesticides.isNotEmpty()) {
                append("推荐药剂：\n")
                report.pesticides.forEach { append("· $it\n") }
                append("\n")
            }
            append("预计恢复：${report.recoveryPeriod}\n\n")
            append("—— 农智眼 AgroVision")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AI诊断报告 - ${report.diseaseName}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享诊断报告"))
    }

    LaunchedEffect(Unit) {
        if (diseaseName.isNotEmpty() && uiState.report == null) {
            viewModel.generateDiagnosis(diseaseName, confidence, imagePath)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("问诊结果", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    if (!uiState.isOnline) {
                        IconButton(onClick = { }, enabled = false) {
                            Icon(Icons.Outlined.CloudOff, "离线模式",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { shareReport() }) { Icon(Icons.Outlined.Share, "分享") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                uiState.isGenerating -> {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = brandGreen(), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("正在整理处理建议", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!uiState.isOnline) {
                                Text("当前使用本地农业知识",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                uiState.report != null -> {
                    val report = uiState.report!!

                    // 原图展示（诊断因果链回溯）
                    if (imagePath.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imagePath,
                                contentDescription = "诊断原图",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // 顶部摘要：用自然语言替代模型置信度术语
                    val dc = dangerColor(report.dangerLevel)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = dc.copy(alpha = 0.06f)
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(Modifier.size(56.dp), RoundedCornerShape(14.dp), dc.copy(alpha = 0.12f)) {
                                Icon(Icons.Filled.BugReport, null, Modifier.padding(12.dp), tint = dc)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(report.diseaseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    confidence >= 0.8f -> "判断较确定"
                                    confidence >= 0.55f -> "初步判断，请结合田间情况确认"
                                    else -> "判断依据有限，建议补拍确认"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = dc) {
                                Text("严重程度：${report.dangerLevel}", style = MaterialTheme.typography.labelMedium, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    ReportCard("今天先做", Icons.Filled.TaskAlt) {
                        Text(report.prevention, style = MaterialTheme.typography.bodyLarge)
                    }
                    ReportCard("为什么会这样", Icons.Filled.QuestionAnswer) {
                        Text(report.causes, style = MaterialTheme.typography.bodyMedium)
                    }
                    ReportCard("判断依据", Icons.Filled.Analytics) {
                        Text(report.analysis, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (report.pesticides.isNotEmpty()) {
                        ReportCard("用药参考", Icons.Filled.Medication) {
                            report.pesticides.forEach { p ->
                                Row(Modifier.padding(vertical = 2.dp)) {
                                    Icon(Icons.Filled.Circle, null, Modifier.size(4.dp).padding(top = 7.dp), tint = accentOrange())
                                    Spacer(Modifier.width(8.dp))
                                    Text(p, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    ReportCard("什么时候复查", Icons.Filled.Schedule) {
                        Text(report.recoveryPeriod, style = MaterialTheme.typography.bodyMedium)
                    }

                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        shape = ShapeMD,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.HealthAndSafety, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "药剂请以产品登记标签和当地农技指导为准，注意防护、混配禁忌和采收安全间隔期。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Button(
                            onClick = {
                                onNavigateToChat("刚刚识别到${report.diseaseName}，请结合这个结果告诉我今天应该先做什么，并提醒需要进一步确认的信息。")
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = ShapeMD
                        ) {
                            Icon(Icons.Outlined.RecordVoiceOver, null)
                            Spacer(Modifier.width(8.dp))
                            Text("继续问农技")
                        }
                        Spacer(Modifier.height(10.dp))
                        if (report.pesticides.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { onNavigateToPesticideCenter(report.diseaseName) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                shape = ShapeMD
                            ) {
                                Icon(Icons.Outlined.Science, null)
                                Spacer(Modifier.width(8.dp))
                                Text("生成配药方案")
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onNavigateToFarm, modifier = Modifier.weight(1f), shape = ShapeMD) {
                                Icon(Icons.Outlined.Agriculture, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("进入农事")
                            }
                            OutlinedButton(onClick = { shareReport() }, modifier = Modifier.weight(1f), shape = ShapeMD) {
                                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("分享")
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }

                uiState.error != null -> {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                else -> {
                    // 兜底：未在生成、无报告、无错误时，提供手动触发入口，避免页面空白
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Psychology, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(12.dp))
                            Text("准备整理处理建议", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.generateDiagnosis(diseaseName, confidence, imagePath) },
                                shape = RoundedCornerShape(14.dp),
                                enabled = diseaseName.isNotEmpty()
                            ) { Text("查看建议") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            val green = brandGreen()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(26.dp), RoundedCornerShape(6.dp), green.copy(alpha = 0.08f)) {
                    Icon(icon, null, Modifier.padding(5.dp), tint = green)
                }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
