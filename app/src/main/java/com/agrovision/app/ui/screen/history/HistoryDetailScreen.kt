package com.agrovision.app.ui.screen.history

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agrovision.app.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    historyId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnosis: (String, Float, String) -> Unit = { _, _, _ -> },
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.detailState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isFavorite by remember { mutableStateOf(false) }

    /** 分享历史记录文本 */
    fun shareHistory() {
        val entity = uiState.history ?: return
        val disease = entity.diseaseNameCn.ifEmpty { entity.diseaseNameEn }
        val top3 = parseTopResults(entity.allResultsJson)
        val text = buildString {
            append("【识别记录】$disease\n")
            append("置信度：${(entity.confidence * 100).toInt()}%\n")
            append("时间：${formatDetailTime(entity.timestamp)}\n")
            if (top3.size > 1) {
                append("\nTop-3 识别结果：\n")
                top3.forEachIndexed { i, r ->
                    append("${i + 1}. ${r.first}（${(r.second * 100).toInt()}%）\n")
                }
            }
            append("\n—— 农智眼 AgroVision")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "识别记录 - $disease")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享识别记录"))
    }

    LaunchedEffect(historyId) {
        viewModel.loadDetail(historyId)
    }

    // 同步收藏状态
    LaunchedEffect(uiState.history) {
        uiState.history?.let { isFavorite = it.isFavorite }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史详情", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = {
                        isFavorite = !isFavorite
                        uiState.history?.let { viewModel.toggleFavorite(it.id) }
                    }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            "收藏", tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { shareHistory() }) { Icon(Icons.Outlined.Share, "分享") }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                    DropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("重新分析") }, onClick = {
                            showMenu = false
                            uiState.history?.let {
                                val name = it.diseaseNameCn.ifEmpty { it.diseaseNameEn }
                                onNavigateToDiagnosis(name, it.confidence, it.imagePath)
                            }
                        }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
                        DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = {
                            uiState.history?.let { viewModel.deleteById(it.id) }
                            showMenu = false
                            onNavigateBack()
                        }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = brandGreen())
                }
            }
            uiState.history == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("记录不存在", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                val entity = uiState.history!!
                val disease = entity.diseaseNameCn.ifEmpty { entity.diseaseNameEn }
                val enName = entity.diseaseNameEn
                val conf = entity.confidence
                val confColor = confidenceColor(conf)

                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                ) {
                    // 图片区
                    Box(Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 20.dp)) {
                        Surface(Modifier.fillMaxSize(), RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            if (entity.imagePath.isNotEmpty()) {
                                AsyncImage(model = entity.imagePath, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 核心信息卡
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(44.dp), RoundedCornerShape(10.dp),
                                    confColor.copy(alpha = 0.12f)) {
                                    Icon(Icons.Filled.BugReport, null, Modifier.padding(9.dp), tint = confColor)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(disease, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(enName, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${(conf * 100).toInt()}%",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold, color = confColor)
                                    Text("识别概率", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(progress = conf ,
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = confColor, trackColor = confColor.copy(alpha = 0.08f))
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniChip("置信度", "${(conf * 100).toInt()}%", confColor)
                                MiniChip("时间", formatDetailTime(entity.timestamp), brandBlue())
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Top-3 识别结果（all_results_json 非空时展示）
                    val top3 = remember(entity.allResultsJson) { parseTopResults(entity.allResultsJson) }
                    if (top3.size > 1) {
                        Text("Top-3 识别结果", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                top3.forEachIndexed { i, (name, conf) ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold, color = brandBlue(),
                                            modifier = Modifier.width(24.dp))
                                        Text(name, style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f))
                                        Text("${(conf * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold, color = confidenceColor(conf))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedButton(onClick = onNavigateBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("重新识别")
                        }
                        Button(onClick = { shareHistory() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("分享")
                        }
                    }

                    // 识别反馈入口（用于模型迭代数据收集）
                    Spacer(Modifier.height(20.dp))
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            if (uiState.feedbackSubmitted) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = brandGreen())
                                    Spacer(Modifier.width(6.dp))
                                    Text("感谢反馈", style = MaterialTheme.typography.labelMedium, color = brandGreen())
                                }
                            } else {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("识别对吗？", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { viewModel.saveFeedback(entity.id, true) },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Filled.ThumbUp, null, Modifier.size(18.dp), tint = brandGreen())
                                            Spacer(Modifier.width(4.dp))
                                            Text("正确", style = MaterialTheme.typography.labelMedium, color = brandGreen())
                                        }
                                        TextButton(
                                            onClick = { viewModel.saveFeedback(entity.id, false) },
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

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniChip(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.08f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("$label ", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun formatDetailTime(ts: Long): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ts))
}

/**
 * 解析 all_results_json 为 (名称, 置信度) 列表。
 * 格式：[{"label":"...","cnName":"...","confidence":0.9}, ...]
 */
private fun parseTopResults(json: String): List<Pair<String, Float>> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<Pair<String, Float>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cnName = obj.optString("cnName", "")
            val label = obj.optString("label", "")
            val conf = obj.optDouble("confidence", 0.0).toFloat()
            val name = cnName.ifEmpty { label }
            list.add(name to conf)
        }
        list
    } catch (e: Exception) {
        Timber.w(e, "解析 all_results_json 失败")
        emptyList()
    }
}
