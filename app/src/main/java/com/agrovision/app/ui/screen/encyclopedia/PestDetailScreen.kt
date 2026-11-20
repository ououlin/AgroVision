package com.agrovision.app.ui.screen.encyclopedia

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.agrovision.app.ui.theme.*

/**
 * 病虫详情 — 从百科仓库加载真实数据
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PestDetailScreen(
    pestName: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetection: () -> Unit = onNavigateBack,
    onNavigateToPesticideCenter: (String) -> Unit = {}
) {
    val viewModel: PestDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val pestInfo = state.pestInfo
    val displayName = pestInfo?.cnName ?: pestName
    val enName = pestInfo?.enName ?: ""
    val dangerLevel = pestInfo?.dangerLevel ?: "中"
    val dangerClr = dangerColor(dangerLevel)

    fun buildShareText(): String {
        val info = pestInfo ?: return ""
        return buildString {
            append("【${info.cnName}】${info.enName}\n")
            append("危险等级：${info.dangerLevel}\n")
            if (info.category.isNotEmpty()) append("类别：${info.category}\n")
            append("\n")
            if (info.description.isNotEmpty()) { append("简介：${info.description}\n\n") }
            if (info.symptoms.isNotEmpty()) {
                append("症状表现：\n")
                info.symptoms.forEach { append("· $it\n") }
                append("\n")
            }
            if (info.causes.isNotEmpty()) { append("发生原因：${info.causes}\n\n") }
            if (info.prevention.isNotEmpty()) { append("防治方法：${info.prevention}\n\n") }
            if (info.pesticides.isNotEmpty()) {
                append("推荐药剂：\n")
                info.pesticides.forEach { append("· $it\n") }
            }
            append("\n—— 农智眼 AgroVision")
        }
    }

    fun shareInfo() {
        val text = buildShareText()
        if (text.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$displayName 病虫详情")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享病虫详情"))
    }

    fun copyInfo() {
        val text = buildShareText()
        if (text.isEmpty()) {
            Toast.makeText(context, "暂无可复制的信息", Toast.LENGTH_SHORT).show()
            return
        }
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("病虫详情", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { shareInfo() }) { Icon(Icons.Outlined.Share, "分享") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 图片展示区（imageUrl 非空时加载网络图，空时显示类别图标占位）
                item {
                    val imgUrl = pestInfo?.imageUrl ?: ""
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imgUrl.isNotEmpty()) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = "${displayName}图片",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Image,
                                    null,
                                    Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "暂无图片 · ${pestInfo?.category ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // 标题头部
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), color = dangerClr.copy(alpha = 0.06f)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(Modifier.size(60.dp), RoundedCornerShape(14.dp), color = dangerClr.copy(alpha = 0.12f)) {
                                Icon(Icons.Filled.BugReport, null, Modifier.padding(13.dp), tint = dangerClr)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (enName.isNotEmpty())
                                Text(enName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = dangerClr) {
                                Text(dangerLevel, style = MaterialTheme.typography.labelMedium, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(18.dp)) }

                if (pestInfo == null) {
                    item {
                        DetailBlock("提示", Icons.Filled.Info) {
                            Text("暂无「$displayName」的详细百科信息。您可以使用 AI 诊断获取更多帮助。",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    // 简介
                    if (pestInfo.description.isNotEmpty()) {
                        item {
                            DetailBlock("病害简介", Icons.Filled.Description) {
                                Text(pestInfo.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // 症状
                    if (pestInfo.symptoms.isNotEmpty()) {
                        item {
                            DetailBlock("症状表现", Icons.Filled.Visibility) {
                                pestInfo.symptoms.forEach { Bullet(it) }
                            }
                        }
                    }

                    // 传播/病因
                    if (pestInfo.causes.isNotEmpty()) {
                        item {
                            DetailBlock("发生原因与传播", Icons.Filled.SwapHoriz) {
                                Text(pestInfo.causes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // 推荐药剂
                    if (pestInfo.pesticides.isNotEmpty()) {
                        item {
                            DetailBlock("推荐药剂", Icons.Filled.Medication) {
                                pestInfo.pesticides.forEach { MedRow(it) }
                            }
                        }
                    }

                    // 防治方法
                    if (pestInfo.prevention.isNotEmpty()) {
                        item {
                            DetailBlock("防治方法", Icons.Filled.Eco) {
                                Text(pestInfo.prevention, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }

                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        if (pestInfo?.pesticides?.isNotEmpty() == true) {
                            Button(
                                onClick = { onNavigateToPesticideCenter(displayName) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = ShapeMD
                            ) {
                                Icon(Icons.Outlined.Science, null)
                                Spacer(Modifier.width(8.dp))
                                Text("生成配药方案")
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedButton(onClick = { copyInfo() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("复制")
                            }
                            OutlinedButton(onClick = onNavigateToDetection, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("重新识别")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(5.dp).padding(top = 7.dp), tint = brandGreen())
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MedRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Icon(Icons.Filled.Circle, null, Modifier.size(4.dp).padding(top = 7.dp), tint = accentOrange())
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
