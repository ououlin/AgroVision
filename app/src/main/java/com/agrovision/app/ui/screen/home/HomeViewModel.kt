package com.agrovision.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.agrovision.app.data.repository.FeedbackRepository
import com.agrovision.app.data.repository.HistoryRepository
import com.agrovision.app.data.repository.WeatherRepository
import com.agrovision.app.domain.model.PestInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val todayDetections: Int = 0,
    val totalDetections: Int = 0,
    val todayDiseases: Int = 0,
    val successRate: Float = 0f,
    val modelVersion: String = "MobileNetV3 v1.0",
    val pestCount: Int = 0,
    val recentDetections: List<DetectionSummary> = emptyList(),
    val weather: String = "--",
    val location: String = "定位中..."
)

data class DetectionSummary(
    val id: Long = 0,
    val imagePath: String = "",
    val diseaseName: String = "",
    val confidence: Float = 0f,
    val timestamp: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val encyclopediaRepository: EncyclopediaRepository,
    private val feedbackRepository: FeedbackRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val todayStart = getStartOfDay()

                // 并行加载 6 个数据源，首屏耗时降为最慢数据源耗时
                val pestCountDef = async { encyclopediaRepository.getAllPests().size }
                val totalCountDef = async { historyRepository.getCount() }
                val todayCountDef = async { historyRepository.getTodayCount(todayStart) }
                val recentDef = async { historyRepository.getRecentHistory(5) }
                val accuracyDef = async { feedbackRepository.getAccuracy() }
                val weatherDef = async { weatherRepository.getWeatherAndLocation() }

                val pestCount = pestCountDef.await()
                val totalCount = totalCountDef.await()
                val todayCount = todayCountDef.await()
                val recent = recentDef.await()
                val accuracy = accuracyDef.await()
                val (weather, location) = weatherDef.await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pestCount = pestCount,
                    todayDetections = todayCount,
                    totalDetections = totalCount,
                    todayDiseases = recent.map { it.diseaseNameCn }.distinct().size,
                    successRate = accuracy ?: 0f,
                    weather = weather,
                    location = location,
                    recentDetections = recent.map { entity ->
                        DetectionSummary(
                            id = entity.id,
                            imagePath = entity.imagePath,
                            diseaseName = entity.diseaseNameCn.ifEmpty { entity.diseaseNameEn },
                            confidence = entity.confidence,
                            timestamp = entity.timestamp
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun getStartOfDay(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
