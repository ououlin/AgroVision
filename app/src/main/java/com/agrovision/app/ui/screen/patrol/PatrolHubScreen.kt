package com.agrovision.app.ui.screen.patrol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrovision.app.data.agent.*
import com.agrovision.app.ui.theme.*

/**
 * 巡检中心 — V3.0 核心页面
 * 合并：机器人状态 / GIS / 巡检任务 / 告警 / 统计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolHubScreen(
    onNavigateToGis: () -> Unit,
    onNavigateToInspection: () -> Unit,
    onNavigateToAlert: () -> Unit,
    onNavigateToMonitor: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToEncyclopedia: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("概览", "巡检", "GIS", "统计", "告警")

    // 模拟数据
    val robotStatuses = remember {
        mapOf("Go2-W" to Pair(true, 85), "ROS2" to Pair(false, 45), "无人机" to Pair(true, 92))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 标题
        Text("巡检中心", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))

        // 机器人状态卡片
        RobotStatusBar(robotStatuses)

        Spacer(Modifier.height(12.dp))

        // 快速入口 2×3
        QuickEntryGrid(
            onGis = onNavigateToGis,
            onInspection = onNavigateToInspection,
            onAlert = onNavigateToAlert,
            onMonitor = onNavigateToMonitor,
            onHistory = onNavigateToHistory,
            onEncyclopedia = onNavigateToEncyclopedia
        )

        Spacer(Modifier.height(12.dp))

        // 今日统计
        TodayStatsRow()

        Spacer(Modifier.height(16.dp))

        // Tab 内容
        TabRow(selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Green50, divider = {}) {
            tabs.forEachIndexed { i, t ->
                Tab(i == selectedTab, { selectedTab = i },
                    text = { Text(t, fontWeight = if (i == selectedTab) FontWeight.Bold else FontWeight.Normal) })
            }
        }

        when (selectedTab) {
            0 -> OverviewTab(onInspection = onNavigateToInspection, onAlert = onNavigateToAlert)
            1 -> InspectionTab(onInspection = onNavigateToInspection)
            2 -> GisTab(onGis = onNavigateToGis)
            3 -> StatsTab()
            4 -> AlertTab(onAlert = onNavigateToAlert)
        }
    }
}

@Composable
private fun RobotStatusBar(robots: Map<String, Pair<Boolean, Int>>) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            robots.forEach { (name, status) ->
                val color = if (status.first) Green40 else Color.Gray
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(8.dp), CircleShape, color) {}
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (status.first) "在线 ${status.second}%" else "离线",
                        style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}

@Composable
private fun QuickEntryGrid(
    onGis: () -> Unit, onInspection: () -> Unit, onAlert: () -> Unit,
    onMonitor: () -> Unit, onHistory: () -> Unit, onEncyclopedia: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryItem(Modifier.weight(1f), Icons.Filled.Map, "GIS地图", Green50, onGis)
            EntryItem(Modifier.weight(1f), Icons.Filled.PrecisionManufacturing, "巡检任务", Blue50, onInspection)
            EntryItem(Modifier.weight(1f), Icons.Filled.Notifications, "告警", Orange50, onAlert)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryItem(Modifier.weight(1f), Icons.Filled.Dashboard, "监控", Blue50, onMonitor)
            EntryItem(Modifier.weight(1f), Icons.Filled.History, "历史", Color(0xFF795548), onHistory)
            EntryItem(Modifier.weight(1f), Icons.AutoMirrored.Filled.MenuBook, "百科", Green40, onEncyclopedia)
        }
    }
}

@Composable
private fun EntryItem(mod: Modifier, icon: ImageVector, label: String, tone: Color, onClick: () -> Unit) {
    Card(mod.clickable(onClick = onClick), RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(36.dp), RoundedCornerShape(10.dp), tone.copy(alpha = 0.10f)) {
                Icon(icon, null, Modifier.padding(7.dp), tint = tone)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TodayStatsRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBadge(Modifier.weight(1f), "156", "累计巡检", Green50)
        StatBadge(Modifier.weight(1f), "8", "今日任务", Blue50)
        StatBadge(Modifier.weight(1f), "423", "累计识别", Orange50)
        StatBadge(Modifier.weight(1f), "3", "活跃告警", Red50)
    }
}

@Composable
private fun StatBadge(mod: Modifier, value: String, label: String, color: Color) {
    Card(mod, RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ========== Tab Contents ==========

@Composable
private fun OverviewTab(onInspection: () -> Unit, onAlert: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp)) {
                    Text("快速操作", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onInspection, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("新建巡检")
                        }
                        OutlinedButton(onInspection, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Text("查看任务")
                        }
                    }
                }
            }
        }
        item {
            PatrolTip("今日巡检建议：A区番茄田需重点关注，建议增加巡检频次。B区水稻田长势良好。", Orange50)
        }
    }
}

@Composable
private fun InspectionTab(onInspection: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp)) {
                    Row {
                        Text("最近巡检", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onInspection) { Text("查看全部") }
                    }
                    PatrolItem("A区番茄田", "Go2-W", TaskStatus.COMPLETED, "发现3处病害")
                    PatrolItem("B区水稻田", "无人机", TaskStatus.IN_PROGRESS, "巡检中...")
                    PatrolItem("C区果园", "ROS2", TaskStatus.PENDING, "待执行")
                }
            }
        }
    }
}

@Composable
private fun PatrolItem(title: String, robot: String, status: TaskStatus, desc: String) {
    val color = when (status) { TaskStatus.COMPLETED -> Green40; TaskStatus.IN_PROGRESS -> Blue50; else -> Orange50 }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(6.dp), CircleShape, color) {}
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("$robot · $desc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.10f)) {
            Text(status.label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun GisTab(onGis: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp)) {
                    Text("病害GIS地图", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(Green5),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Map, null, Modifier.size(48.dp), tint = Green50.copy(alpha = 0.3f))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onGis, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("打开完整地图") }
                }
            }
        }
        item { PatrolTip("覆盖3个区域 · 7个监测点 · A区风险等级：高", Red50) }
    }
}

@Composable
private fun StatsTab() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp)) {
                    Text("月度统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    val months = listOf("1月","2月","3月","4月","5月","6月")
                    val data = listOf(12,8,18,25,32,40)
                    val maxVal = data.max().toFloat()
                    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        months.zip(data).forEach { (m, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(22.dp).height((100 * v / maxVal).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(Green50))
                                Spacer(Modifier.height(4.dp))
                                Text(m, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertTab(onAlert: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp)) {
                    Row {
                        Text("实时告警", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onAlert) { Text("全部") }
                    }
                    AlertItem("病害检测告警", "A区发现晚疫病", AlertLevel.CRITICAL)
                    AlertItem("机器人离线", "ROS2小车电量不足", AlertLevel.WARNING)
                    AlertItem("巡检完成", "B区水稻田巡检完成", AlertLevel.INFO)
                }
            }
        }
    }
}

@Composable
private fun AlertItem(title: String, desc: String, level: AlertLevel) {
    val color = when (level) { AlertLevel.CRITICAL -> Red50; AlertLevel.WARNING -> Orange50; else -> Blue50 }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Circle, null, Modifier.size(6.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(level.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PatrolTip(msg: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.06f)) {
        Row(Modifier.padding(14.dp)) {
            Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
