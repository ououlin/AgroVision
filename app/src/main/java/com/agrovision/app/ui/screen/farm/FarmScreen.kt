package com.agrovision.app.ui.screen.farm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.data.repository.PlotRepository
import com.agrovision.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class FarmUiState(
    val plots: List<PlotEntity> = emptyList(),
    val isLoading: Boolean = true,
    val userId: String = ""
)

@HiltViewModel
class FarmViewModel @Inject constructor(
    private val plotRepository: PlotRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FarmUiState(isLoading = true))
    val uiState: StateFlow<FarmUiState> = _uiState.asStateFlow()

    init {
        // 监听当前用户 ID，加载地块列表
        val userId = authRepository.getCurrentUser()?.userId ?: "guest_local"
        _uiState.value = _uiState.value.copy(userId = userId)

        viewModelScope.launch {
            plotRepository.getPlots(userId).collect { plots ->
                _uiState.value = _uiState.value.copy(
                    plots = plots,
                    isLoading = false
                )
            }
        }
    }

    fun deletePlot(id: Long) {
        viewModelScope.launch { plotRepository.deletePlot(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmScreen(
    onNavigateToPlotDetail: (Long) -> Unit,
    onNavigateToPlotCreate: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: FarmViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("农事管理", fontWeight = FontWeight.Bold)
                        Text(
                            "地块、农时与种植档案",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToPlotCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建地块") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(Spacing.MD.dp))
                    Text(
                        "正在整理农事档案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = FARM_LIST_BOTTOM_PAD.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD.dp)
                ) {
                    item {
                        FarmOverview(plots = uiState.plots)
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = Spacing.XL.dp,
                                    end = Spacing.XL.dp,
                                    top = Spacing.XS.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "我的地块",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${uiState.plots.size} 个",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (uiState.plots.isEmpty()) {
                        item {
                            EmptyPlotState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.XXL3.dp)
                            )
                        }
                    }

                    items(uiState.plots) { plot ->
                        PlotCard(
                            plot = plot,
                            onClick = { onNavigateToPlotDetail(plot.id) },
                            modifier = Modifier.padding(horizontal = Spacing.XL.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlotState(modifier: Modifier = Modifier) {
    Column(
        // FE Review: 24→Spacing.XXL
        modifier = modifier.padding(Spacing.XXL.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Agriculture,
            contentDescription = null,
            // FE Review: 64→提取文件级常量 EMPTY_ICON_SIZE（空状态大图标属于展示规格非通用令牌）
            modifier = Modifier.size(EMPTY_ICON_SIZE.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        // FE Review: 16→Spacing.LG
        Spacer(Modifier.height(Spacing.LG.dp))
        Text(
            "还没有地块",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        // FE Review: 4→Spacing.XS
        Spacer(Modifier.height(Spacing.XS.dp))
        Text(
            "创建你的第一个地块，开始结构化管理\n识别记录、用药计划、农情提醒都将绑定到地块",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FarmOverview(plots: List<PlotEntity>) {
    val totalArea = plots.mapNotNull { it.area }.sum()
    val cropCount = plots.map { it.cropType.trim() }.filter { it.isNotEmpty() }.distinct().size
    val locatedCount = plots.count { it.latitude != null && it.longitude != null }
    val activeStageCount = plots.count { it.phenologyStage.isNotBlank() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.XL.dp, vertical = Spacing.XL.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(OVERVIEW_ICON_SIZE.dp),
                    shape = ShapeMD,
                    color = brandGreen().copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Landscape,
                            contentDescription = null,
                            modifier = Modifier.size(TouchTarget.ICON_LG.dp),
                            tint = brandGreen()
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.MD.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "种植概览",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            plots.isEmpty() -> "从一块地开始建立你的农事档案"
                            activeStageCount > 0 -> "$activeStageCount 个地块已记录当前物候"
                            else -> "地块档案已建立，物候信息待补充"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }
                Icon(
                    Icons.Filled.Agriculture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f),
                    modifier = Modifier.size(OVERVIEW_WATERMARK_SIZE.dp)
                )
            }

            Spacer(Modifier.height(Spacing.XL.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f))
            Spacer(Modifier.height(Spacing.LG.dp))

            Row(Modifier.fillMaxWidth()) {
                OverviewMetric(
                    icon = Icons.Filled.GridView,
                    value = plots.size.toString(),
                    label = "地块",
                    color = brandGreen(),
                    modifier = Modifier.weight(1f)
                )
                OverviewDivider()
                OverviewMetric(
                    icon = Icons.Filled.Straighten,
                    value = if (totalArea > 0.0) "${formatArea(totalArea)}亩" else "--",
                    label = "总面积",
                    color = brandBlue(),
                    modifier = Modifier.weight(1f)
                )
                OverviewDivider()
                OverviewMetric(
                    icon = Icons.Filled.Spa,
                    value = cropCount.toString(),
                    label = "作物",
                    color = accentOrange(),
                    modifier = Modifier.weight(1f)
                )
            }

            if (plots.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.LG.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(OVERVIEW_HINT_ICON.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.62f)
                    )
                    Spacer(Modifier.width(Spacing.XS.dp))
                    Text(
                        "$locatedCount/${plots.size} 个地块已完成定位",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(TouchTarget.ICON.dp),
            tint = color
        )
        Spacer(Modifier.width(Spacing.SM.dp))
        Column {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.62f)
            )
        }
    }
}

@Composable
private fun RowScope.OverviewDivider() {
    VerticalDivider(
        modifier = Modifier.height(OVERVIEW_DIVIDER_HEIGHT.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    )
    Spacer(Modifier.width(Spacing.MD.dp))
}

@Composable
private fun PlotCard(
    plot: PlotEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = cropAccent(plot.cropType)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.CARD.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.LG.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(PLOT_CROP_ICON_SIZE.dp),
                    shape = ShapeMD,
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Agriculture,
                            contentDescription = null,
                            modifier = Modifier.size(TouchTarget.ICON_LG.dp),
                            tint = accent
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.MD.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        plot.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        plot.cropType.ifBlank { "未填写作物" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(TouchTarget.ICON_LG.dp)
                )
            }

            if (plot.phenologyStage.isNotBlank()) {
                Spacer(Modifier.height(Spacing.MD.dp))
                Surface(
                    shape = ShapePill,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.SM.dp, vertical = Spacing.XS.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(PLOT_META_ICON.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(Spacing.XS.dp))
                        Text(
                            plot.phenologyStage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.MD.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            Spacer(Modifier.height(Spacing.MD.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.LG.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlotMeta(
                    icon = Icons.Filled.Straighten,
                    text = plot.area?.let { "${formatArea(it)} 亩" } ?: "面积待补充"
                )
                PlotMeta(
                    icon = Icons.Filled.LocationOn,
                    text = if (plot.latitude != null && plot.longitude != null) "已定位" else "未定位"
                )
                PlotMeta(
                    icon = if (plot.syncStatus == 1) Icons.Filled.CloudDone else Icons.Outlined.CloudOff,
                    text = if (plot.syncStatus == 1) "已同步" else "本地"
                )
            }

            if (plot.note.isNotBlank()) {
                Spacer(Modifier.height(Spacing.MD.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(PLOT_META_ICON.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(Spacing.SM.dp))
                    Text(
                        plot.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.PlotMeta(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(PLOT_META_ICON.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(Spacing.XS.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun cropAccent(crop: String): Color = when ((crop.hashCode() ushr 1) % 3) {
    0 -> brandGreen()
    1 -> brandBlue()
    else -> accentOrange()
}

private fun formatArea(area: Double): String =
    if (area % 1.0 == 0.0) area.toLong().toString()
    else String.format(Locale.CHINA, "%.1f", area)

private const val EMPTY_ICON_SIZE = 64
private const val OVERVIEW_ICON_SIZE = 48
private const val OVERVIEW_WATERMARK_SIZE = 44
private const val OVERVIEW_DIVIDER_HEIGHT = 36
private const val OVERVIEW_HINT_ICON = 16
private const val PLOT_CROP_ICON_SIZE = 48
private const val PLOT_META_ICON = 16
private const val FARM_LIST_BOTTOM_PAD = 104
