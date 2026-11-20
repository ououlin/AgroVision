package com.agrovision.app.ui.screen.farm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.repository.PlotRepository
import com.agrovision.app.domain.model.PhenologyCatalog
import com.agrovision.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlotCreateUiState(
    val name: String = "",
    /** V2.0: 统一使用中文名，与 PhenologyCatalog.forCrop 的 key 对齐 */
    val cropType: String = "苹果",
    val area: String = "",
    val longitude: String = "",
    val latitude: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class PlotCreateViewModel @Inject constructor(
    private val plotRepository: PlotRepository,
    private val authRepository: com.agrovision.app.data.repository.AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlotCreateUiState())
    val uiState: StateFlow<PlotCreateUiState> = _uiState.asStateFlow()

    /**
     * V2.0: 作物下拉源改为 PhenologyCatalog.supportedCrops（6 种主作物中文名）
     * — 与 PhenologyCatalog.forCrop 的查找 key 完全对齐，避免选择后物候期返回 null
     * — 排除了非支持作物（蓝莓/樱桃/番茄等）和 general 杂项
     */
    val cropOptions: List<String> = PhenologyCatalog.supportedCrops

    fun onNameChange(v: String) { _uiState.value = _uiState.value.copy(name = v, error = null) }
    fun onCropTypeChange(v: String) { _uiState.value = _uiState.value.copy(cropType = v) }
    fun onAreaChange(v: String) { _uiState.value = _uiState.value.copy(area = v) }
    fun onLongitudeChange(v: String) { _uiState.value = _uiState.value.copy(longitude = v) }
    fun onLatitudeChange(v: String) { _uiState.value = _uiState.value.copy(latitude = v) }
    fun onNoteChange(v: String) { _uiState.value = _uiState.value.copy(note = v) }

    fun save() {
        val s = _uiState.value
        if (s.name.isBlank()) {
            _uiState.value = s.copy(error = "请填写地块名称")
            return
        }
        if (s.cropType.isBlank()) {
            _uiState.value = s.copy(error = "请选择作物类型")
            return
        }

        val userId = authRepository.getCurrentUser()?.userId ?: "guest_local"
        val area = s.area.toDoubleOrNull()
        val lng = s.longitude.toDoubleOrNull()
        val lat = s.latitude.toDoubleOrNull()

        viewModelScope.launch {
            _uiState.value = s.copy(isSaving = true, error = null)
            try {
                plotRepository.createPlot(
                    userId = userId,
                    name = s.name.trim(),
                    cropType = s.cropType,
                    area = area,
                    longitude = lng,
                    latitude = lat,
                    note = s.note.trim()
                )
                _uiState.value = s.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = s.copy(isSaving = false, error = e.message ?: "保存失败")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotCreateScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlotCreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建地块") },
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
                .padding(Spacing.LG.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("地块名称 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("如：东边苹果园") }
            )

            // 作物类型下拉
            var cropMenuExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = cropMenuExpanded,
                onExpandedChange = { cropMenuExpanded = !cropMenuExpanded }
            ) {
                OutlinedTextField(
                    value = uiState.cropType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("作物类型 *") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cropMenuExpanded) }
                )
                ExposedDropdownMenu(expanded = cropMenuExpanded, onDismissRequest = { cropMenuExpanded = false }) {
                    viewModel.cropOptions.forEach { crop ->
                        DropdownMenuItem(
                            text = { Text(crop) },
                            onClick = {
                                viewModel.onCropTypeChange(crop)
                                cropMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.area,
                onValueChange = viewModel::onAreaChange,
                label = { Text("面积（亩，可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)
            ) {
                OutlinedTextField(
                    value = uiState.longitude,
                    onValueChange = viewModel::onLongitudeChange,
                    label = { Text("经度（可选）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = uiState.latitude,
                    onValueChange = viewModel::onLatitudeChange,
                    label = { Text("纬度（可选）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
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
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        // FE Review: 18→最接近令牌 Spacing.LG 16 稍小，用 XXL 24 过大；保留 18 并加注释为规格常量
                        modifier = Modifier.size(SAVE_BUTTON_INDICATOR_SIZE.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        // 2dp 属于 Material 设计 stroke 规范（非间距令牌），保留
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    // FE Review: 4→Spacing.XS
                    Spacer(Modifier.width(Spacing.XS.dp))
                    Text("保存")
                }
            }
        }
    }
}

// FE Review: 保存按钮加载指示器规格（组件级常量）
private const val SAVE_BUTTON_INDICATOR_SIZE = 18
