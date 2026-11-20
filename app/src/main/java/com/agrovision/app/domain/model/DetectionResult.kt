package com.agrovision.app.domain.model

import android.graphics.RectF

/**
 * 检测结果
 */
data class DetectionResult(
    /** 检测标签（病害英文名） */
    val label: String,

    /** 中文名称 */
    val cnName: String,

    /** 置信度 0.0~1.0 */
    val confidence: Float,

    /** 检测框坐标（相对坐标 0.0~1.0） */
    val boundingBox: RectF,

    /** 匹配的百科信息（可为空） */
    val pestInfo: PestInfo? = null
) {
    /** 格式化的置信度百分比 */
    val confidencePercent: String
        get() = "%.1f%%".format(confidence * 100f)

    /** 检测框左上角 X */
    val boxX: Float get() = boundingBox.left

    /** 检测框左上角 Y */
    val boxY: Float get() = boundingBox.top

    /** 检测框宽度 */
    val boxWidth: Float get() = boundingBox.width()

    /** 检测框高度 */
    val boxHeight: Float get() = boundingBox.height()
}

/**
 * YOLO 原始检测结果（未匹配百科信息）
 */
data class RawDetection(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)
