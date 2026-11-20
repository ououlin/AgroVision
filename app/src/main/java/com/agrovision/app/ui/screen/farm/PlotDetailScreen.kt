package com.agrovision.app.ui.screen.farm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.local.entity.TreatmentEntity
import com.agrovision.app.data.repository.PlotRepository
import com.agrovision.app.data.repository.TreatmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PlotDetailUiState(
    val plot: PlotEntity? = null,
    val treatments: List<TreatmentEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PlotDetailViewModel @Inject constructor(
    private val plotRepository: PlotRepository,
    private val treatmentRepository: TreatmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlotDetailUiState())
    val uiState: StateFlow<PlotDetailUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun load(plotId: Long) {
        viewModelScope.launch {
            val plot = plotRepository.getPlot(plotId)
            _uiState.value = _uiState.value.copy(plot = plot, isLoading = false)

            treatmentRepository.getTreatmentsByPlot(plotId).collect { treatments ->
                _uiState.value = _uiState.value.copy(treatments = treatments)
            }
        }
    }

    fun markHarvested(id: Long) {
        viewModelScope.launch { treatmentRepository.markHarvested(id) }
    }

    fun formatDate(ts: Long): String = dateFormat.format(Date(ts))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotDetailScreen(
    plotId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToTreatmentCreate: (Long) -> Unit,
    viewModel: PlotDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(plotId) { viewModel.load(plotId) }
    val uiState by viewModel.uiState.collectAsState()
    val plot = uiState.plot

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plot?.name ?: "地块详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToTreatmentCreate(plotId) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加用药记录")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (plot == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("地块不存在")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 地块摘要卡
            item {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(plot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("作物：${plot.cropType}", style = MaterialTheme.typography.bodyMedium)
                        plot.area?.let { Text("面积：$it 亩", style = MaterialTheme.typography.bodyMedium) }
                        if (plot.phenologyStage.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            AssistChip(onClick = {}, label = { Text("物候期：${plot.phenologyStage}") })
                        }
                        if (plot.note.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(plot.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 用药记录时间线标题
            item {
                Text(
                    "用药记录 (${uiState.treatments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (uiState.treatments.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Healing, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("暂无用药记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("点击右下角 + 添加第一次施药", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(uiState.treatments) { treatment ->
                    TreatmentCard(
                        treatment = treatment,
                        formatDate = viewModel::formatDate,
                        onMarkHarvested = { viewModel.markHarvested(treatment.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TreatmentCard(
    treatment: TreatmentEntity,
    formatDate: (Long) -> String,
    onMarkHarvested: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(treatment.chemicalName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val statusLabel = when (treatment.status) {
                    "applied" -> "已施药"
                    "done" -> "已采收"
                    "pending" -> "待施药"
                    else -> treatment.status
                }
                AssistChip(onClick = {}, label = { Text(statusLabel) })
            }
            Spacer(Modifier.height(4.dp))
            Text("用量：${treatment.dosage}", style = MaterialTheme.typography.bodyMedium)
            Text("施用日期：${formatDate(treatment.appliedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (treatment.safetyIntervalDays > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "安全间隔期：${treatment.safetyIntervalDays} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                treatment.expectedHarvestAt?.let {
                    Text(
                        "预计采收：${formatDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (treatment.status != "done") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onMarkHarvested) { Text("标记已采收") }
                    }
                }
            }
            if (treatment.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(treatment.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
