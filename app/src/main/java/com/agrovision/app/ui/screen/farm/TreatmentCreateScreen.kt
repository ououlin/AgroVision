package com.agrovision.app.ui.screen.farm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.data.repository.PlotRepository
import com.agrovision.app.data.repository.TreatmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class TreatmentCreateUiState(
    val plots: List<PlotEntity> = emptyList(),
    val selectedPlotId: Long = 0L,
    val chemicalName: String = "",
    val dosage: String = "",
    val appliedAt: Long = System.currentTimeMillis(),
    val safetyIntervalDays: String = "14",
    val expectedHarvestAt: Long? = null,
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class TreatmentCreateViewModel @Inject constructor(
    private val treatmentRepository: TreatmentRepository,
    private val plotRepository: PlotRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialPlotId = savedStateHandle.get<Long>("plotId") ?: 0L
    private val initialSafetyDays = (savedStateHandle.get<Int>("safetyDays") ?: 14).coerceAtLeast(0)
    private val _uiState = MutableStateFlow(
        TreatmentCreateUiState(
            selectedPlotId = initialPlotId,
            chemicalName = savedStateHandle.get<String>("chemicalName").orEmpty(),
            dosage = savedStateHandle.get<String>("dosage").orEmpty(),
            safetyIntervalDays = initialSafetyDays.toString(),
            expectedHarvestAt = System.currentTimeMillis() + initialSafetyDays * 24L * 60 * 60 * 1000
        )
    )
    val uiState: StateFlow<TreatmentCreateUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    init {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.userId ?: "local_user"
            plotRepository.getPlots(userId).collect { plots ->
                val current = _uiState.value
                val selected = when {
                    plots.any { it.id == current.selectedPlotId } -> current.selectedPlotId
                    plots.any { it.id == initialPlotId } -> initialPlotId
                    else -> plots.firstOrNull()?.id ?: 0L
                }
                _uiState.value = current.copy(plots = plots, selectedPlotId = selected)
            }
        }
    }

    fun onChemicalNameChange(v: String) { _uiState.value = _uiState.value.copy(chemicalName = v, error = null) }
    fun onDosageChange(v: String) { _uiState.value = _uiState.value.copy(dosage = v, error = null) }
    fun onSafetyIntervalChange(v: String) {
        val newHarvest = v.toIntOrNull()?.let { days ->
            System.currentTimeMillis() + days * 24L * 60 * 60 * 1000
        }
        _uiState.value = _uiState.value.copy(safetyIntervalDays = v, expectedHarvestAt = newHarvest)
    }
    fun onNoteChange(v: String) { _uiState.value = _uiState.value.copy(note = v) }
    fun onPlotSelected(id: Long) { _uiState.value = _uiState.value.copy(selectedPlotId = id, error = null) }

    fun save(plotId: Long) {
        val s = _uiState.value
        val targetPlotId = plotId.takeIf { it > 0 } ?: s.selectedPlotId
        if (targetPlotId <= 0) {
            _uiState.value = s.copy(error = "请先选择或新建地块")
            return
        }
        if (s.chemicalName.isBlank()) {
            _uiState.value = s.copy(error = "请填写药剂名称")
            return
        }
        if (s.dosage.isBlank()) {
            _uiState.value = s.copy(error = "请填写用量")
            return
        }

        viewModelScope.launch {
            _uiState.value = s.copy(isSaving = true, error = null)
            try {
                treatmentRepository.addTreatment(
                    plotId = targetPlotId,
                    chemicalName = s.chemicalName.trim(),
                    dosage = s.dosage.trim(),
                    appliedAt = s.appliedAt,
                    safetyIntervalDays = s.safetyIntervalDays.toIntOrNull() ?: 14,
                    expectedHarvestAt = s.expectedHarvestAt,
                    note = s.note.trim()
                )
                _uiState.value = s.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = s.copy(isSaving = false, error = e.message ?: "保存失败")
            }
        }
    }

    fun formatDate(ts: Long): String = dateFormat.format(Date(ts))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentCreateScreen(
    plotId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCreatePlot: () -> Unit = {},
    viewModel: TreatmentCreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onNavigateBack()
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            val needsNotificationPermission =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
            if (needsNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加用药记录") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.plots.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("暂无地块，建立地块后才能保存用药记录", modifier = Modifier.weight(1f))
                        TextButton(onClick = onNavigateToCreatePlot) { Text("新建地块") }
                    }
                }
            } else {
                var plotExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = plotExpanded,
                    onExpandedChange = { plotExpanded = it }
                ) {
                    val selectedPlot = uiState.plots.firstOrNull { it.id == uiState.selectedPlotId }
                    OutlinedTextField(
                        value = selectedPlot?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("施药地块 *") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(plotExpanded) }
                    )
                    ExposedDropdownMenu(expanded = plotExpanded, onDismissRequest = { plotExpanded = false }) {
                        uiState.plots.forEach { plot ->
                            DropdownMenuItem(
                                text = { Text("${plot.name} · ${plot.cropType}") },
                                onClick = {
                                    viewModel.onPlotSelected(plot.id)
                                    plotExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = uiState.chemicalName,
                onValueChange = viewModel::onChemicalNameChange,
                label = { Text("药剂名称 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("如：阿维菌素、代森锰锌") }
            )
            OutlinedTextField(
                value = uiState.dosage,
                onValueChange = viewModel::onDosageChange,
                label = { Text("用量 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("如：30ml/亩、1:800 喷雾") }
            )
            OutlinedTextField(
                value = uiState.safetyIntervalDays,
                onValueChange = viewModel::onSafetyIntervalChange,
                label = { Text("安全间隔期（天）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            uiState.expectedHarvestAt?.let {
                Text(
                    "预计采收日期：${viewModel.formatDate(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save(plotId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
        }
    }
}
