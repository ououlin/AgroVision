package com.agrovision.app.ui.screen.data

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.domain.model.PestInfo
import com.agrovision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    onNavigateToEncyclopedia: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPestDetail: (String) -> Unit,
    viewModel: DataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            Text(
                "数据中心",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
            )
        }

        item { StatsRow(uiState) }

        item { Spacer(modifier = Modifier.height(40.dp)) }

        item { ChartTabs(uiState, viewModel::setChartTab) }

        item { Spacer(modifier = Modifier.height(40.dp)) }

        item {
            EntryCards(
                pestCount = uiState.diseaseCount,
                historyCount = uiState.totalDetections,
                onEncyclopedia = onNavigateToEncyclopedia,
                onHistory = onNavigateToHistory
            )
        }

        if (uiState.topDiseases.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(36.dp)) }
            item { RankingSection(uiState.topDiseases) }
        }

        if (uiState.pests.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(36.dp)) }
            item {
                Text("病虫害库", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
            }
            items(uiState.pests.take(6)) { pest ->
                PestRow(pest, onClick = { onNavigateToPestDetail(pest.cnName) })
            }
            if (uiState.pests.size > 6) {
                item {
                    TextButton(
                        onClick = onNavigateToEncyclopedia,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("查看全部 ${uiState.pests.size} 种")
                        Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(state: DataUiState) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Modifier.weight(1f), Icons.Filled.CameraAlt, "${state.totalDetections}", "累计识别", Blue50)
            MiniStat(Modifier.weight(1f), Icons.Filled.CheckCircle, "${(state.accuracyRate * 100).toInt()}%", "识别准确率", Success50)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Modifier.weight(1f), Icons.Filled.BugReport, "${state.diseaseCount}", "病害数量", Red50)
            MiniStat(Modifier.weight(1f), Icons.Filled.Memory, state.modelVersion, "模型版本", Green50)
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, icon: ImageVector, value: String, label: String, tone: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(modifier = Modifier.size(30.dp), shape = RoundedCornerShape(8.dp), color = tone.copy(alpha = 0.10f)) {
                Icon(icon, null, modifier = Modifier.padding(6.dp), tint = tone)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tone)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartTabs(state: DataUiState, onTab: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        TabRow(selectedTabIndex = state.selectedChartTab, containerColor = Color.Transparent, contentColor = Green50,
            divider = {}) {
            Tab(state.selectedChartTab == 0, { onTab(0) },
                text = { Text("识别趋势", fontWeight = if (state.selectedChartTab == 0) FontWeight.SemiBold else FontWeight.Normal) })
            Tab(state.selectedChartTab == 1, { onTab(1) },
                text = { Text("病害排行", fontWeight = if (state.selectedChartTab == 1) FontWeight.SemiBold else FontWeight.Normal) })
            Tab(state.selectedChartTab == 2, { onTab(2) },
                text = { Text("作物分类", fontWeight = if (state.selectedChartTab == 2) FontWeight.SemiBold else FontWeight.Normal) })
        }
        Spacer(modifier = Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            when (state.selectedChartTab) {
                0 -> TrendChart(data = state.monthlyTrend)
                1 -> BarRankChart(rankings = state.topDiseases)
                2 -> PieDistribution(dist = state.categoryDistribution)
            }
        }
    }
}

@Composable
private fun TrendChart(data: List<Pair<String, Int>>) {
    Column(modifier = Modifier.padding(18.dp)) {
        Text("月度识别趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(18.dp))
        val maxVal = (data.maxOfOrNull { it.second } ?: 1).toFloat()
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (m, c) ->
                val ratio = c / maxVal
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height((120 * ratio.coerceAtLeast(0.04f)).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (ratio > 0.7f) Green50 else Green50.copy(alpha = 0.45f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(m, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BarRankChart(rankings: List<Pair<String, Int>>) {
    Column(modifier = Modifier.padding(18.dp)) {
        Text("病害识别排行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))
        if (rankings.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BarChart, null, modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            }
        } else {
            val maxCount = (rankings.maxOfOrNull { it.second } ?: 1).toFloat()
            rankings.forEachIndexed { i, (name, cnt) ->
                val tone = when (i) { 0 -> Red50; 1 -> Orange50; 2 -> Blue50; else -> Green50 }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tone, modifier = Modifier.width(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f).height(24.dp)) {
                        Box(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth((cnt / maxCount).coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(6.dp)).background(tone.copy(alpha = 0.15f))
                        ) {
                            Text("$cnt 次", style = MaterialTheme.typography.labelSmall, color = tone,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
                        }
                        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PieDistribution(dist: List<Pair<String, Float>>) {
    Column(modifier = Modifier.padding(18.dp)) {
        Text("作物分类分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))
        if (dist.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PieChart, null, modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            }
        } else {
            val tones = listOf(Green50, Blue50, Orange50, Red50, Success50)
            dist.forEachIndexed { i, (cat, pct) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(10.dp), RoundedCornerShape(2.dp), color = tones[i % tones.size]) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(cat, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = tones[i % tones.size])
                }
            }
        }
    }
}

@Composable
private fun EntryCards(
    pestCount: Int, historyCount: Int,
    onEncyclopedia: () -> Unit, onHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EntryCard(Modifier.weight(1f), Icons.Filled.MenuBook, "病虫百科", "$pestCount 种", Success50, onEncyclopedia)
        EntryCard(Modifier.weight(1f), Icons.Filled.History, "历史记录", "$historyCount 条", Orange50, onHistory)
    }
}

@Composable
private fun EntryCard(modifier: Modifier, icon: ImageVector, title: String, sub: String, tone: Color, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), color = tone.copy(alpha = 0.10f)) {
                Icon(icon, null, modifier = Modifier.padding(7.dp), tint = tone)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RankingSection(rankings: List<Pair<String, Int>>) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("病害排行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(14.dp)) {
                rankings.forEachIndexed { i, (name, cnt) ->
                    val medal = when (i) { 0 -> "1"; 1 -> "2"; 2 -> "3"; else -> "${i + 1}" }
                    val tone = when (i) { 0 -> Red50; 1 -> Orange50; 2 -> Blue50; else -> Color.Gray }
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(medal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tone, modifier = Modifier.width(28.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("$cnt 次", style = MaterialTheme.typography.labelMedium, color = tone, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PestRow(pest: PestInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(46.dp), RoundedCornerShape(10.dp), Green50.copy(alpha = 0.08f)) {
                Icon(Icons.Filled.BugReport, null, modifier = Modifier.padding(10.dp), tint = Green50)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pest.cnName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(pest.description.take(50) + "...", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
