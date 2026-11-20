package com.agrovision.app.ui.screen.detection

import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agrovision.app.domain.model.DetectionResult
import com.agrovision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionResultScreen(
    imagePath: String,
    onNavigateToPestDetail: (String) -> Unit,
    onNavigateToDiagnosis: (String, Float, String) -> Unit = { _, _, _ -> },
    onNavigateBack: () -> Unit,
    viewModel: DetectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 分享识别结果
    fun shareResult() {
        val topResult = uiState.results.firstOrNull() ?: return
        val shareText = buildString {
            append("农智眼 AgroVision - 病虫害识别结果\n")
            append("病害: ${topResult.cnName.ifEmpty { topResult.label }}\n")
            append("置信度: ${topResult.confidencePercent}\n")
            append("\n—— 来自农智眼 AgroVision App")
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享识别结果"))
    }

    // 如果结果为空且未在检测中，尝试加载图片并重新检测
    var didRetry by remember { mutableStateOf(false) }
    LaunchedEffect(imagePath, didRetry) {
        if (uiState.results.isEmpty() && imagePath.isNotEmpty() && imagePath != "captured_image") {
            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                    ?: context.contentResolver.openInputStream(android.net.Uri.parse(imagePath))
                        ?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    viewModel.detectImage(bitmap)
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("检测结果", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { shareResult() }) { Icon(Icons.Outlined.Share, null) }
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (uiState.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isDetecting -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = brandGreen())
                            Spacer(Modifier.height(16.dp))
                            Text("AI 正在识别...", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                uiState.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Text("识别失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(uiState.error ?: "", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onNavigateBack, shape = RoundedCornerShape(14.dp)) {
                                    Text("返回")
                                }
                                Button(onClick = { didRetry = !didRetry }, shape = RoundedCornerShape(14.dp)) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }
                uiState.results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(56.dp),
                                tint = brandGreen())
                            Spacer(Modifier.height(12.dp))
                            Text("未检测到病虫害", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Text("叶片状态良好，或图片不够清晰",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("如怀疑有病害，可尝试近距离对准病斑重新拍摄",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onNavigateBack, shape = RoundedCornerShape(14.dp)) {
                                    Text("返回")
                                }
                                Button(onClick = { didRetry = !didRetry }, shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("重新拍摄")
                                }
                            }
                        }
                    }
                }
                else -> {
                    ResultContent(
                        imagePath = imagePath,
                        uiState = uiState,
                        onNavigateToPestDetail = onNavigateToPestDetail,
                        onNavigateToDiagnosis = onNavigateToDiagnosis,
                        onNavigateBack = onNavigateBack,
                        onShare = { shareResult() },
                        onFeedback = { isCorrect -> viewModel.saveFeedback(isCorrect) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultContent(
    imagePath: String,
    uiState: DetectionUiState,
    onNavigateToPestDetail: (String) -> Unit,
    onNavigateToDiagnosis: (String, Float, String) -> Unit,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onFeedback: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))

        // 图片预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (imagePath.isNotEmpty() && imagePath != "captured_image") {
                AsyncImage(
                    model = imagePath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 结果卡片
        uiState.results.forEach { result ->
            ResultCard(
                result = result,
                feedbackSubmitted = uiState.feedbackSubmitted,
                onClick = { onNavigateToPestDetail(result.label) },
                onFeedback = onFeedback
            )
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(24.dp))

        // AI 诊断按钮
        val topResult = uiState.results.firstOrNull()
        Button(
            onClick = {
                topResult?.let {
                    onNavigateToDiagnosis(it.cnName.ifEmpty { it.label }, it.confidence, imagePath)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = brandBlue())
        ) {
            Icon(Icons.Filled.Biotech, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("AI 智能诊断")
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重新识别")
            }
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("分享结果")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ResultCard(
    result: DetectionResult,
    feedbackSubmitted: Boolean,
    onClick: () -> Unit,
    onFeedback: (Boolean) -> Unit
) {
    val confColor = confidenceColor(result.confidence)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = confColor.copy(alpha = 0.10f)
                ) {
                    Icon(Icons.Filled.BugReport, null, Modifier.padding(10.dp), tint = confColor)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        result.cnName.ifEmpty { result.label },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        result.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = result.confidence,
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = confColor,
                trackColor = confColor.copy(alpha = 0.08f)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${result.confidencePercent} 置信度",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = confColor
                )
                Spacer(Modifier.weight(1f))
                Text("查看详情", style = MaterialTheme.typography.labelMedium, color = brandBlue())
                Icon(Icons.Filled.ChevronRight, null, tint = brandBlue(), modifier = Modifier.size(18.dp))
            }

            // 用户反馈区域
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            if (feedbackSubmitted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = brandGreen())
                    Spacer(Modifier.width(6.dp))
                    Text("感谢反馈", style = MaterialTheme.typography.labelMedium, color = brandGreen())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("识别对吗？", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onFeedback(true) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.ThumbUp, null, Modifier.size(18.dp), tint = brandGreen())
                            Spacer(Modifier.width(4.dp))
                            Text("正确", style = MaterialTheme.typography.labelMedium, color = brandGreen())
                        }
                        TextButton(
                            onClick = { onFeedback(false) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.ThumbDown, null, Modifier.size(18.dp), tint = accentOrange())
                            Spacer(Modifier.width(4.dp))
                            Text("不对", style = MaterialTheme.typography.labelMedium, color = accentOrange())
                        }
                    }
                }
            }
        }
    }
}
