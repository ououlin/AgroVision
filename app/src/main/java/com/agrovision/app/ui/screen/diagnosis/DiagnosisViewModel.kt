package com.agrovision.app.ui.screen.diagnosis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.llm.DiagnosisReportData
import com.agrovision.app.data.repository.DiagnosisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosisUiState(
    val report: DiagnosisReportData? = null,
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val isOnline: Boolean = false
)

@HiltViewModel
class DiagnosisViewModel @Inject constructor(
    private val diagnosisRepository: DiagnosisRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosisUiState())
    val uiState: StateFlow<DiagnosisUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(isOnline = diagnosisRepository.isOnline())
    }

    /**
     * 根据检测结果生成诊断报告
     */
    fun generateDiagnosis(
        diseaseName: String,
        confidence: Float,
        imagePath: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            val result = diagnosisRepository.generateReport(diseaseName, confidence, imagePath)
            result.fold(
                onSuccess = { report ->
                    _uiState.value = _uiState.value.copy(
                        report = report,
                        isGenerating = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = "诊断生成失败：${e.message}"
                    )
                }
            )
        }
    }

    fun clearReport() {
        _uiState.value = _uiState.value.copy(report = null, error = null)
    }
}
