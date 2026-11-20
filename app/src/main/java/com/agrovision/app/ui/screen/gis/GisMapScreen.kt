package com.agrovision.app.ui.screen.gis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrovision.app.data.agent.AlertLevel
import com.agrovision.app.data.agent.GeoPoint
import com.agrovision.app.ui.theme.*

/**
 * GIS 地图页面 — 病害热力图 + GPS分布 + 风险区域
 * V3.0: 地图SDK预留接口，当前展示GPS数据列表和热力分布概览
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisMapScreen(onNavigateBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    // 模拟数据
    val detectionPoints = remember {
        listOf(
            GeoPoint(39.9142, 116.4074, 0.0, "番茄晚疫病·高"),
            GeoPoint(39.9182, 116.4104, 0.0, "黄瓜霜霉病·中"),
            GeoPoint(39.9082, 116.3994, 0.0, "棉花蚜虫·中"),
            GeoPoint(39.9202, 116.4134, 0.0, "水稻稻瘟病·高"),
            GeoPoint(39.9062, 116.4204, 0.0, "小麦锈病·中"),
            GeoPoint(39.9152, 116.4044, 0.0, "苹果炭疽病·低"),
            GeoPoint(39.9122, 116.4154, 0.0, "玉米大斑病·低"),
        )
    }

    val riskZones = remember {
        listOf(
            Triple("A区", AlertLevel.CRITICAL, "番茄晚疫病、稻瘟病集中爆发"),
            Triple("B区", AlertLevel.WARNING, "蚜虫活动频繁，需密切监控"),
            Triple("C区", AlertLevel.INFO, "目前正常，建议定期巡检"),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GIS 病害地图", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { }) { Icon(Icons.Outlined.Layers, "图层") }
                    IconButton(onClick = { }) { Icon(Icons.Outlined.Map, "切换") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 地图占位区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE8F5E9),
                                Color(0xFFC8E6C9),
                                Color(0xFFA5D6A7)
                            )
                        )
                    )
            ) {
                // 地图提示
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Map, null, Modifier.size(48.dp),
                        tint = Green50.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("GIS 病害分布地图", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Green60)
                    Text("${detectionPoints.size} 个监测点 · ${riskZones.size} 个风险区",
                        style = MaterialTheme.typography.labelMedium, color = Green50)

                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = Green50.copy(alpha = 0.1f)) {
                        Text("地图SDK接入预留\n请集成高德/百度地图" +
                            "\n支持热力图+聚类+轨迹",
                            style = MaterialTheme.typography.labelSmall,
                            color = Green60, modifier = Modifier.padding(12.dp))
                    }
                }

                // 模拟标记点
                detectionPoints.forEachIndexed { i, point ->
                    val danger = when {
                        point.label.contains("高") -> Red50
                        point.label.contains("中") -> Orange50
                        else -> Success50
                    }
                    Box(
                        Modifier
                            .offset(
                                x = (20 + i * 40 + (i % 3) * 20).dp,
                                y = (60 + i * 25 - (i % 2) * 30).dp
                            )
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(danger.copy(alpha = 0.7f))
                    )
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("监测点") })
                Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("风险区域") })
                Tab(selectedTab == 2, { selectedTab = 2 }, text = { Text("统计概览") })
            }

            // Content
            when (selectedTab) {
                0 -> DetectionPointList(points = detectionPoints)
                1 -> RiskZoneList(zones = riskZones)
                2 -> GisStatsOverview(points = detectionPoints.size, zones = riskZones.size)
            }
        }
    }
}

@Composable
private fun DetectionPointList(points: List<GeoPoint>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(points) { point ->
            val danger = when {
                point.label.contains("高") -> Red50
                point.label.contains("中") -> Orange50
                else -> Success50
            }
            Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(8.dp), CircleShape, danger) {}
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(point.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${point.latitude}, ${point.longitude}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.MyLocation, null, Modifier.size(18.dp), tint = danger)
                }
            }
        }
    }
}

@Composable
private fun RiskZoneList(zones: List<Triple<String, AlertLevel, String>>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(zones) { (name, level, desc) ->
            val color = when (level) {
                AlertLevel.CRITICAL -> Red50
                AlertLevel.WARNING -> Orange50
                AlertLevel.INFO -> Blue50
            }
            Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(40.dp), RoundedCornerShape(10.dp), color.copy(alpha = 0.12f)) {
                        Icon(Icons.Filled.Warning, null, Modifier.padding(8.dp), tint = color)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = color) {
                        Text(level.label, style = MaterialTheme.typography.labelSmall, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GisStatsOverview(points: Int, zones: Int) {
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "监测点", "$points", Green50, Icons.Filled.LocationOn)
            StatCard(Modifier.weight(1f), "风险区", "$zones", Red50, Icons.Filled.Warning)
        }
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("热力图覆盖范围", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("当前监测网络覆盖 3 个区域", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("• A区 (红色): 高频病害区，建议增加巡检频次",
                    style = MaterialTheme.typography.labelSmall, color = Red50)
                Text("• B区 (橙色): 中频病害区，建议常规巡检",
                    style = MaterialTheme.typography.labelSmall, color = Orange50)
                Text("• C区 (蓝色): 低风险区，建议定期检查",
                    style = MaterialTheme.typography.labelSmall, color = Blue50)
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, tone: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier, RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(14.dp)) {
            Surface(Modifier.size(30.dp), RoundedCornerShape(8.dp), tone.copy(alpha = 0.1f)) {
                Icon(icon, null, Modifier.padding(6.dp), tint = tone)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tone)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
