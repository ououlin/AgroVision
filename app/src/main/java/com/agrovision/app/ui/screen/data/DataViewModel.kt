package com.agrovision.app.ui.screen.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.agrovision.app.data.repository.FeedbackRepository
import com.agrovision.app.data.repository.HistoryRepository
import com.agrovision.app.domain.model.PestInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataUiState(
    val totalDetections: Int = 0,
    val accuracyRate: Float = 0.92f,
    val diseaseCount: Int = 0,
    val modelVersion: String = "MobileNetV3 v1.0",
    val topDiseases: List<Pair<String, Int>> = emptyList(),
    val categoryDistribution: List<Pair<String, Float>> = emptyList(),
    val monthlyTrend: List<Pair<String, Int>> = emptyList(),
    val pests: List<PestInfo> = emptyList(),
    val selectedChartTab: Int = 0
)

@HiltViewModel
class DataViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val encyclopediaRepository: EncyclopediaRepository,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val totalCount = historyRepository.getCount()
            val pests = encyclopediaRepository.getAllPests()
            val recent = historyRepository.getRecentHistory(20)
            val accuracy = feedbackRepository.getAccuracy()

            // 病害排行
            val diseaseRanking = recent
                .groupBy { it.diseaseNameCn.ifEmpty { it.diseaseNameEn } }
                .mapValues { it.value.size }
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }

            // 分类分布
            val categories = pests.groupBy { it.category }
                .mapValues { it.value.size.toFloat() }
            val totalPests = pests.size.toFloat()
            val catDistribution = categories.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to (it.value / totalPests) }

            // 月度趋势（基于真实历史记录按月分组）
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            val trend = (1..12).map { month ->
                val count = recent.count { entity ->
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = entity.timestamp
                    }
                    cal.get(java.util.Calendar.YEAR) == currentYear &&
                            cal.get(java.util.Calendar.MONTH) + 1 == month
                }
                "${month}月" to count
            }

            _uiState.value = _uiState.value.copy(
                totalDetections = totalCount,
                accuracyRate = accuracy ?: 0f,
                diseaseCount = pests.size,
                topDiseases = diseaseRanking,
                categoryDistribution = catDistribution,
                monthlyTrend = trend,
                pests = pests
            )
        }
    }

    fun setChartTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedChartTab = tab)
    }
}
