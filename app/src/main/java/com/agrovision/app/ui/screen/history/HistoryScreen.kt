package com.agrovision.app.ui.screen.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.ui.components.AsyncContent
import com.agrovision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.listState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        AsyncContent(
            isLoading = uiState.isLoading,
            data = uiState.histories,
            error = null,
            modifier = Modifier.padding(padding),
            emptyIcon = Icons.Outlined.History,
            emptyText = "暂无记录",
            emptyContent = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.History, null, Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                    Spacer(Modifier.height(16.dp))
                    Text("暂无记录", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    Text("拍照识别来创建第一条记录", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }
        ) { histories ->
            // 按日期分组渲染，组标题显示"今天/昨天/日期"，组内只显示时间
            val grouped = remember(histories) {
                histories.groupBy { formatDateGroup(it.timestamp) }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (group, list) ->
                    item(key = "header_$group") {
                        Text(group, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                    }
                    items(list, key = { it.id }) { item ->
                        TimelineCard(
                            history = item,
                            onClick = { onNavigateToDetail(item.id) },
                            onFav = { viewModel.toggleFavorite(item.id) },
                            onDel = { showDeleteDialog = item.id }
                        )
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteById(id)
                    showDeleteDialog = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun TimelineCard(
    history: HistoryEntity,
    onClick: () -> Unit,
    onFav: () -> Unit,
    onDel: () -> Unit
) {
    Column {
        Surface(shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)) {
            Text(formatTime(history.timestamp), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(64.dp), RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    if (history.imagePath.isNotEmpty()) {
                        AsyncImage(
                            model = history.imagePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Outlined.Image, null, Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(history.diseaseNameCn.ifEmpty { history.diseaseNameEn },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(history.diseaseNameEn, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val confColor = confidenceColor(history.confidence)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = history.confidence ,
                            modifier = Modifier.weight(1f).height(5.dp),
                            color = confColor, trackColor = confColor.copy(alpha = 0.08f))
                        Spacer(Modifier.width(8.dp))
                        Text("${(history.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = confColor)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    IconButton(onClick = onFav) {
                        Icon(
                            if (history.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            null, Modifier.size(20.dp),
                            tint = if (history.isFavorite) Color(0xFFFFB300)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDel) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ts))
}

private fun formatDateGroup(ts: Long): String {
    val now = java.util.Calendar.getInstance()
    val date = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val isToday = now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == date.get(java.util.Calendar.DAY_OF_YEAR)
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
        yesterday.get(java.util.Calendar.DAY_OF_YEAR) == date.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        isToday -> "今天"
        isYesterday -> "昨天"
        else -> java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
    }
}
