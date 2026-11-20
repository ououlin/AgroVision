package com.agrovision.app.ui.screen.inspection

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.data.agent.*
import com.agrovision.app.ui.theme.*

/**
 * 巡检任务管理页 — V3.0 机器人/无人机任务调度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: InspectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("巡检任务", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, "新建任务")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = Green50) {
                Icon(Icons.Filled.Add, "新建", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 机器人状态概览
            item {
                Text("机器人状态", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RobotChip(Modifier.weight(1f), Icons.Filled.PrecisionManufacturing, "Go2-W", "在线", Success50)
                    RobotChip(Modifier.weight(1f), Icons.Filled.DirectionsCar, "ROS2", "离线", Color.Gray)
                    RobotChip(Modifier.weight(1f), Icons.Filled.Flight, "无人机", "待命", Orange50)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // 任务列表
            item {
                Text("任务列表", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            }

            if (uiState.tasks.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Assignment, null, Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(12.dp))
                            Text("暂无巡检任务", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Text("点击 + 创建新任务", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            } else {
                items(uiState.tasks) { task ->
                    TaskCard(task, onClick = { viewModel.executeTask(task.id) })
                }
            }
        }
    }

    // 创建任务对话框
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建巡检任务") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTaskTitle, onValueChange = { newTaskTitle = it },
                        label = { Text("任务名称") },
                        placeholder = { Text("例如：A区番茄田巡检") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("巡检类型", style = MaterialTheme.typography.labelMedium)
                    var type by remember { mutableStateOf(InspectionType.ROBOT_CRUISE) }
                    Row {
                        InspectionType.entries.forEach { t ->
                            FilterChip(t == type, { type = t }, label = { Text(t.label) },
                                modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("执行机器人", style = MaterialTheme.typography.labelMedium)
                    var robot by remember { mutableStateOf(RobotType.GO2_W) }
                    Row {
                        RobotType.entries.forEach { r ->
                            FilterChip(r == robot, { robot = r }, label = { Text(r.label) },
                                modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newTaskTitle.isNotBlank()) {
                        viewModel.createTask(newTaskTitle, InspectionType.ROBOT_CRUISE, RobotType.GO2_W)
                        newTaskTitle = ""
                        showCreateDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun RobotChip(modifier: Modifier, icon: ImageVector, name: String, status: String, color: Color) {
    Card(modifier, RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(24.dp), tint = color)
            Spacer(Modifier.height(6.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(6.dp), RoundedCornerShape(3.dp), color) {}
                Spacer(Modifier.width(4.dp))
                Text(status, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun TaskCard(task: InspectionTask, onClick: () -> Unit) {
    val statusColor = when (task.status) {
        TaskStatus.PENDING -> Orange50
        TaskStatus.IN_PROGRESS -> Blue50
        TaskStatus.COMPLETED -> Success50
        TaskStatus.FAILED -> Red50
        TaskStatus.CANCELLED -> Color.Gray
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), RoundedCornerShape(10.dp), statusColor.copy(alpha = 0.12f)) {
                Icon(
                    when (task.type) {
                        InspectionType.DRONE_PATROL -> Icons.Filled.Flight
                        else -> Icons.Filled.PrecisionManufacturing
                    }, null, Modifier.padding(9.dp), tint = statusColor
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row {
                    Text(task.type.label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(task.assignee.label, style = MaterialTheme.typography.labelSmall, color = Blue50)
                }
                if (task.detectedDiseases.isNotEmpty()) {
                    Text("检测: ${task.detectedDiseases.size}处",
                        style = MaterialTheme.typography.labelSmall, color = Red50)
                }
            }
            Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(alpha = 0.15f)) {
                Text(task.status.label, style = MaterialTheme.typography.labelSmall, color = statusColor,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}
