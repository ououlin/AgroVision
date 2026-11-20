package com.agrovision.app.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrovision.app.ui.theme.*

@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToEncyclopedia: () -> Unit,
    onNavigateToOcr: () -> Unit = {},
    onNavigateToCloudSync: () -> Unit = {},
    onNavigateToGis: () -> Unit = {},
    onNavigateToInspection: () -> Unit = {},
    onNavigateToAlert: () -> Unit = {},
    onNavigateToMonitor: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(40.dp))

        // 头像区
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(Modifier.size(80.dp), CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                Icon(Icons.Filled.Person, null, Modifier.padding(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text("农智眼用户", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("AgroVision", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Text("MobileNetV3 · DeepSeek",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }

        Spacer(Modifier.height(48.dp))

        // 菜单
        Column(Modifier.padding(horizontal = 20.dp)) {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                MenuRow(Icons.Filled.History, "历史记录", "查看所有检测记录", onNavigateToHistory)
                Div(Modifier.padding(horizontal = 56.dp))
                MenuRow(Icons.AutoMirrored.Filled.MenuBook, "病虫百科", "病虫害知识库", onNavigateToEncyclopedia)
                Div(Modifier.padding(horizontal = 56.dp))
                MenuRow(Icons.Filled.Settings, "设置", "深色模式、模型、API Key", onNavigateToSettings)
                Div(Modifier.padding(horizontal = 56.dp))
                MenuRow(Icons.Filled.Info, "关于", "版本与开源许可", onNavigateToAbout)
            }
        }

        Spacer(Modifier.height(48.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("农智眼 AgroVision", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("果园病虫害智能识别 · AI 农技助手", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2026 AgroVision Team", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(34.dp), RoundedCornerShape(8.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
            Icon(icon, null, Modifier.padding(7.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Div(modifier: Modifier) {
    HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)
}
