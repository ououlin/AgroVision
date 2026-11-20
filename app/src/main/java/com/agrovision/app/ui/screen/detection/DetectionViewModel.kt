package com.agrovision.app.ui.screen.detection

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.data.repository.DetectionRepository
import com.agrovision.app.data.repository.FeedbackRepository
import com.agrovision.app.data.repository.HistoryRepository
import com.agrovision.app.domain.model.DetectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

data class DetectionUiState(
    val isModelReady: Boolean = false,
    val isDetecting: Boolean = false,
    val results: List<DetectionResult> = emptyList(),
    val error: String? = null,
    val capturedImagePath: String? = null,
    val selectedCropType: String = "自动识别",
    val previewMode: PreviewMode = PreviewMode.CAMERA,
    val currentHistoryId: Long? = null,
    val isFavorite: Boolean = false,
    val feedbackSubmitted: Boolean = false,
    /** 是否已发起过一次检测（用于区分初始空状态与"检测完成但无结果"） */
    val hasAttemptedDetection: Boolean = false
)

enum class PreviewMode {
    CAMERA,    // 实时识别
    GALLERY    // 图片识别
}

@HiltViewModel
class DetectionViewModel @Inject constructor(
    private val detectionRepository: DetectionRepository,
    private val historyRepository: HistoryRepository,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetectionUiState())
    val uiState: StateFlow<DetectionUiState> = _uiState.asStateFlow()

    private val detectMutex = Mutex()

    init {
        initializeModel()
    }

    private fun initializeModel() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    detectionRepository.initializeModel()
                }
                if (result.isFailure) {
                    Timber.e("模型加载失败: ${result.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(
                        isModelReady = false,
                        error = "模型加载失败: ${result.exceptionOrNull()?.message}\n请确保模型文件已放入 assets/model/ 目录"
                    )
                } else {
                    Timber.d("模型加载成功")
                    _uiState.value = _uiState.value.copy(isModelReady = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "模型加载异常")
                _uiState.value = _uiState.value.copy(
                    isModelReady = false,
                    error = "模型未找到: ${e.message}\n请确保模型文件已放入 assets/model/ 目录"
                )
            }
        }
    }

    fun setPreviewMode(mode: PreviewMode) {
        _uiState.value = _uiState.value.copy(previewMode = mode)
    }

    fun setSelectedCropType(cropType: String) {
        _uiState.value = _uiState.value.copy(selectedCropType = cropType)
    }

    /**
     * 执行检测推理
     * Bitmap 在推理完成后自动 recycle 以防止内存泄漏
     */
    fun detectImage(bitmap: Bitmap, cropType: String = _uiState.value.selectedCropType) {
        Timber.d("detectImage() 调用，图片: ${bitmap.width}x${bitmap.height}")
        viewModelScope.launch {
            detectMutex.withLock {
                if (_uiState.value.isDetecting) {
                    Timber.w("已有识别任务进行中，跳过")
                    bitmap.recycle()
                    return@withLock
                }
                _uiState.value = _uiState.value.copy(
                    isDetecting = true, error = null,
                    currentHistoryId = null, isFavorite = false, feedbackSubmitted = false,
                    hasAttemptedDetection = true
                )
                try {
                    Timber.d("开始推理...")
                    val results = withContext(Dispatchers.Default) {
                        detectionRepository.detect(bitmap, cropType = cropType)
                    }
                    Timber.d("推理完成: ${results.size}个结果, 更新UI")

                    // 保存到历史记录
                    val topResult = results.firstOrNull()
                    val imagePath = _uiState.value.capturedImagePath ?: ""
                    val allResultsJson = serializeTopResults(results.take(3))
                    var historyId: Long? = null
                    if (topResult != null) {
                        try {
                            historyId = historyRepository.insert(
                                HistoryEntity(
                                    imagePath = imagePath,
                                    diseaseNameEn = topResult.label,
                                    diseaseNameCn = topResult.cnName,
                                    confidence = topResult.confidence,
                                    allResultsJson = allResultsJson
                                )
                            )
                            Timber.d("历史记录已保存: id=$historyId, top3=$allResultsJson")
                        } catch (e: Exception) {
                            Timber.e(e, "保存历史记录失败")
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isDetecting = false,
                        results = results,
                        currentHistoryId = historyId
                    )
                } catch (e: Exception) {
                    Timber.e(e, "检测失败")
                    _uiState.value = _uiState.value.copy(
                        isDetecting = false,
                        error = "检测失败: ${e.message}"
                    )
                } finally {
                    // 推理完成后回收 bitmap，防止内存泄漏
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    /** 切换收藏状态 */
    fun toggleFavorite() {
        val historyId = _uiState.value.currentHistoryId ?: return
        viewModelScope.launch {
            historyRepository.toggleFavorite(historyId)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }

    /** 保存用户反馈（识别正确/不正确） */
    fun saveFeedback(isCorrect: Boolean) {
        val historyId = _uiState.value.currentHistoryId ?: return
        if (_uiState.value.feedbackSubmitted) return
        viewModelScope.launch {
            feedbackRepository.saveFeedback(historyId, isCorrect)
            _uiState.value = _uiState.value.copy(feedbackSubmitted = true)
        }
    }

    fun setCapturedImagePath(path: String) {
        _uiState.value = _uiState.value.copy(capturedImagePath = path)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearResults() {
        _uiState.value = _uiState.value.copy(results = emptyList(), hasAttemptedDetection = false)
    }

    /**
     * 将 Top-N 检测结果序列化为 JSON 字符串，用于持久化到历史记录。
     * 格式：[{"label":"...","cnName":"...","confidence":0.9}, ...]
     */
    private fun serializeTopResults(results: List<DetectionResult>): String {
        if (results.isEmpty()) return ""
        return try {
            val arr = JSONArray()
            results.forEach { r ->
                arr.put(JSONObject().apply {
                    put("label", r.label)
                    put("cnName", r.cnName)
                    put("confidence", r.confidence)
                })
            }
            arr.toString()
        } catch (e: Exception) {
            Timber.e(e, "序列化 Top-3 结果失败")
            ""
        }
    }
}
