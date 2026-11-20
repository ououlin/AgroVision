package com.agrovision.app.ui.screen.monitor

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrovision.app.data.agent.AgentStatus
import com.agrovision.app.data.agent.AgentType
import com.agrovision.app.data.agent.SystemStatus
import com.agrovision.app.ui.theme.*

/**
 * 系统运行监控大屏 — V3.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(onNavigateBack: () -> Unit) {
    // 模拟数据
    val status = remember {
        SystemStatus(
            appVersion = "3.0.0",
            modelVersion = "YOLO11n v1.0",
            modelStatus = "运行中",
            robotCount = 3, robotOnlineCount = 2,
            totalInspections = 156, todayInspections = 8,
            totalDetections = 423, activeAlerts = 3,
            gpuStatus = "正常", memoryUsage = "128MB / 512MB",
            storageUsage = "2.3GB / 8GB"
        )
    }

    val agentStatuses = remember {
        mapOf(
            AgentType.DISEASE_RECOGNITION to AgentStatus.IDLE,
            AgentType.AGRICULTURE_DIAGNOSIS to AgentStatus.IDLE,
            AgentType.KNOWLEDGE_RETRIEVAL to AgentStatus.IDLE,
            AgentType.INSPECTION_SCHEDULER to AgentStatus.BUSY,
            AgentType.REPORT_GENERATOR to AgentStatus.IDLE
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统监控", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { }) { Icon(Icons.Filled.Refresh, "刷新") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // 系统状态概览
            Spacer(Modifier.height(8.dp))
            MonitorSection("系统状态") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonitorCard(Modifier.weight(1f), Icons.Filled.Memory, "模型", status.modelStatus, Green50)
                    MonitorCard(Modifier.weight(1f), Icons.Filled.Speed, "GPU", status.gpuStatus, Green50)
                    MonitorCard(Modifier.weight(1f), Icons.Filled.Storage, "存储", "2.3/8GB", Blue50)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("内存: ${status.memoryUsage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp))
                }
            }

            // Agent 状态
            Spacer(Modifier.height(12.dp))
            MonitorSection("AI 智能体") {
                agentStatuses.forEach { (type, agentStatus) ->
                    AgentStatusRow(type = type, status = agentStatus)
                }
            }

            // 机器人状态
            Spacer(Modifier.height(12.dp))
            MonitorSection("机器人") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RobotStatusCard(Modifier.weight(1f), "Go2-W", true, 85)
                    RobotStatusCard(Modifier.weight(1f), "ROS2小车", false, 45)
                    RobotStatusCard(Modifier.weight(1f), "无人机", true, 92)
                }
            }

            // 巡检统计
            Spacer(Modifier.height(12.dp))
            MonitorSection("巡检统计") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatMini(Modifier.weight(1f), "总巡检", "${status.totalInspections}", Green50)
                    StatMini(Modifier.weight(1f), "今日", "${status.todayInspections}", Blue50)
                    StatMini(Modifier.weight(1f), "总检测", "${status.totalDetections}", Red50)
                    StatMini(Modifier.weight(1f), "告警", "${status.activeAlerts}", Orange50)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonitorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        content()
    }
}

@Composable
private fun MonitorCard(modifier: Modifier, icon: ImageVector, label: String, value: String, color: Color) {
    Card(modifier, RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(28.dp), RoundedCornerShape(8.dp), color.copy(alpha = 0.1f)) {
                Icon(icon, null, Modifier.padding(6.dp), tint = color)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgentStatusRow(type: AgentType, status: AgentStatus) {
    val statusColor = when (status) {
        AgentStatus.IDLE -> Success50
        AgentStatus.BUSY -> Orange50
        AgentStatus.ERROR -> Red50
        AgentStatus.OFFLINE -> Color.Gray
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(7.dp), RoundedCornerShape(3.dp), statusColor) {}
        Spacer(Modifier.width(8.dp))
        Text(type.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = statusColor)
    }
}

@Composable
private fun RobotStatusCard(modifier: Modifier, name: String, online: Boolean, battery: Int) {
    val color = if (online) Green50 else Color.Gray
    Card(modifier, RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PrecisionManufacturing, null, Modifier.size(24.dp), tint = color
            )
            Spacer(Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(if (online) "在线" else "离线",
                style = MaterialTheme.typography.labelSmall, color = color)
            Text("电池 $battery%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatMini(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier, RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
