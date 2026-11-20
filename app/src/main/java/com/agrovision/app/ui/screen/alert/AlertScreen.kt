package com.agrovision.app.ui.screen.alert

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.data.agent.Alert
import com.agrovision.app.data.agent.AlertLevel
import com.agrovision.app.ui.theme.*


/**
 * 实时告警中心 — V3.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertScreen(
    onNavigateBack: () -> Unit,
    viewModel: AlertViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var filterLevel by remember { mutableStateOf<AlertLevel?>(null) }

    val filtered = remember(uiState.alerts, filterLevel) {
        if (filterLevel != null) uiState.alerts.filter { it.level == filterLevel }
        else uiState.alerts
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        Text("告警中心", fontWeight = FontWeight.SemiBold)
                        if (uiState.activeCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge { Text("${uiState.activeCount}") }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { viewModel.markAllRead() }) {
                        Icon(Icons.Outlined.DoneAll, "全部已读")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 筛选栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(filterLevel == null, { filterLevel = null }, label = { Text("全部") })
                AlertLevel.entries.forEach { level ->
                    FilterChip(
                        filterLevel == level, { filterLevel = level },
                        label = { Text(level.label) },
                        leadingIcon = {
                            Icon(Icons.Filled.Circle, null, Modifier.size(8.dp),
                                tint = when (level) {
                                    AlertLevel.CRITICAL -> Red50
                                    AlertLevel.WARNING -> Orange50
                                    AlertLevel.INFO -> Blue50
                                })
                        }
                    )
                }
            }

            Divider()

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("暂无告警", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { alert ->
                        AlertCard(alert, onClick = { viewModel.markRead(alert.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, onClick: () -> Unit) {
    val levelColor = when (alert.level) {
        AlertLevel.CRITICAL -> Red50
        AlertLevel.WARNING -> Orange50
        AlertLevel.INFO -> Blue50
    }
    val levelIcon = when (alert.level) {
        AlertLevel.CRITICAL -> Icons.Filled.Error
        AlertLevel.WARNING -> Icons.Filled.Warning
        AlertLevel.INFO -> Icons.Filled.Info
    }

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!alert.isRead)
                levelColor.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(36.dp), RoundedCornerShape(8.dp), levelColor.copy(alpha = 0.12f)) {
                Icon(levelIcon, null, Modifier.padding(7.dp), tint = levelColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(4.dp), color = levelColor) {
                        Text(alert.level.label, style = MaterialTheme.typography.labelSmall, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(alert.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(alert.source, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.width(12.dp))
                    Text(formatAlertTime(alert.timestamp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun formatAlertTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        diff < 86400000 -> "${diff / 3600000}小时前"
        else -> java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
    }
}
