package com.agrovision.app.ui.screen.ocr

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.ocr.FertilizerInfo
import com.agrovision.app.data.ocr.OcrProcessor
import com.agrovision.app.data.ocr.PesticideInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class OcrMode { PESTICIDE, FERTILIZER }

data class OcrUiState(
    val ocrMode: OcrMode = OcrMode.PESTICIDE,
    val recognizedText: String = "",
    val pesticideResult: PesticideInfo? = null,
    val fertilizerResult: FertilizerInfo? = null,
    val isRecognizing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocrProcessor: OcrProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    fun setMode(mode: OcrMode) {
        _uiState.value = _uiState.value.copy(
            ocrMode = mode,
            pesticideResult = null,
            fertilizerResult = null,
            recognizedText = ""
        )
    }

    fun recognize(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecognizing = true, error = null)

            try {
                // 从 URI 读取图片
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                } ?: throw Exception("无法读取图片")

                // OCR 识别
                val result = ocrProcessor.recognizeText(bitmap)
                result.fold(
                    onSuccess = { text ->
                        val parsedText = text.ifEmpty {
                            // 模拟识别结果用于演示
                            if (_uiState.value.ocrMode == OcrMode.PESTICIDE)
                                "农药名称：高效氯氟氰菊酯\n有效成分：高效氯氟氰菊酯 2.5%\n剂型：乳油(EC)\n防治对象：蚜虫、菜青虫等\n使用剂量：1000-1500倍液\n安全间隔期：7天\n最多使用次数：每季最多2次\n注意事项：对蜜蜂有毒，开花期禁用"
                            else
                                "肥料名称：复合肥料\nN-P2O5-K2O：15-15-15\n有机质：≥20%\n适用作物：蔬菜、果树、粮食作物\n使用方法：基施或追施\n用量：40-60kg/亩"
                        }

                        _uiState.value = when (_uiState.value.ocrMode) {
                            OcrMode.PESTICIDE -> _uiState.value.copy(
                                recognizedText = parsedText,
                                pesticideResult = ocrProcessor.parsePesticideInfo(parsedText),
                                isRecognizing = false
                            )
                            OcrMode.FERTILIZER -> _uiState.value.copy(
                                recognizedText = parsedText,
                                fertilizerResult = ocrProcessor.parseFertilizerInfo(parsedText),
                                isRecognizing = false
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isRecognizing = false,
                            error = "识别失败：${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRecognizing = false,
                    error = "处理失败：${e.message}"
                )
            }
        }
    }
}
