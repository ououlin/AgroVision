package com.agrovision.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/**
 * 核心推理算法的纯单元测试
 *
 * 验证 softmax、sigmoid、IoU、NMS 等算法逻辑的正确性。
 * 这些算法与 OnnxDetector / YoloDetector 中的实现保持一致，
 * 确保推理结果的数学正确性。
 */
class AlgorithmTest {

    // ===== Softmax 测试（与 OnnxDetector.softmax 实现一致）=====

    private fun softmax(logits: FloatArray): FloatArray {
        var maxVal = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > maxVal) maxVal = v

        var sum = 0.0
        val exps = FloatArray(logits.size)
        for (i in logits.indices) {
            exps[i] = exp((logits[i] - maxVal).toDouble()).toFloat()
            sum += exps[i]
        }
        for (i in exps.indices) exps[i] = (exps[i] / sum).toFloat()
        return exps
    }

    @Test
    fun `softmax outputs sum to 1`() {
        val logits = floatArrayOf(1.0f, 2.0f, 3.0f)
        val probs = softmax(logits)
        val sum = probs.sum()
        assertEquals(1.0f, sum, 0.0001f)
    }

    @Test
    fun `softmax preserves order`() {
        val logits = floatArrayOf(0.1f, 5.0f, 2.0f)
        val probs = softmax(logits)
        // 最大 logit 对应最大概率
        assertTrue(probs[1] > probs[2])
        assertTrue(probs[2] > probs[0])
    }

    @Test
    fun `softmax handles negative values`() {
        val logits = floatArrayOf(-5.0f, -1.0f, -3.0f)
        val probs = softmax(logits)
        val sum = probs.sum()
        assertEquals(1.0f, sum, 0.0001f)
        assertTrue(probs.all { it in 0f..1f })
    }

    @Test
    fun `softmax handles large values without overflow`() {
        val logits = floatArrayOf(1000f, 1001f, 1002f)
        val probs = softmax(logits)
        val sum = probs.sum()
        assertEquals(1.0f, sum, 0.0001f)
        assertTrue(probs.all { !it.isNaN() && !it.isInfinite() })
    }

    // ===== Sigmoid 测试（与 YoloDetector.sigmoid 实现一致）=====

    private fun sigmoid(x: Float): Float {
        return 1f / (1f + exp(-x))
    }

    @Test
    fun `sigmoid of zero is half`() {
        assertEquals(0.5f, sigmoid(0f), 0.0001f)
    }

    @Test
    fun `sigmoid output is in valid range`() {
        var x = -10f
        while (x <= 10f) {
            val result = sigmoid(x)
            assertTrue("sigmoid($x) = $result should be in (0, 1)", result in 0f..1f)
            x += 0.5f
        }
    }

    @Test
    fun `sigmoid is monotonically increasing`() {
        assertTrue(sigmoid(1f) > sigmoid(0f))
        assertTrue(sigmoid(0f) > sigmoid(-1f))
    }

    // ===== IoU 测试（与 YoloDetector.iou 实现一致）=====

    private fun iou(
        aLeft: Float, aTop: Float, aRight: Float, aBottom: Float,
        bLeft: Float, bTop: Float, bRight: Float, bBottom: Float
    ): Float {
        val intersectLeft = maxOf(aLeft, bLeft)
        val intersectTop = maxOf(aTop, bTop)
        val intersectRight = minOf(aRight, bRight)
        val intersectBottom = minOf(aBottom, bBottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) return 0f

        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val areaA = (aRight - aLeft) * (aBottom - aTop)
        val areaB = (bRight - bLeft) * (bBottom - bTop)
        val unionArea = areaA + areaB - intersectArea

        return if (unionArea > 0f) intersectArea / unionArea else 0f
    }

    @Test
    fun `IoU of identical boxes is 1`() {
        val result = iou(0f, 0f, 10f, 10f, 0f, 0f, 10f, 10f)
        assertEquals(1.0f, result, 0.0001f)
    }

    @Test
    fun `IoU of non-overlapping boxes is 0`() {
        val result = iou(0f, 0f, 10f, 10f, 20f, 20f, 30f, 30f)
        assertEquals(0.0f, result, 0.0001f)
    }

    @Test
    fun `IoU of partially overlapping boxes is between 0 and 1`() {
        val result = iou(0f, 0f, 10f, 10f, 5f, 5f, 15f, 15f)
        assertTrue("IoU should be in (0, 1), got $result", result in 0f..1f)
        assertTrue("IoU should be > 0", result > 0f)
        assertTrue("IoU should be < 1", result < 1f)
    }

    // ===== 坐标转换测试（验证 YOLO11 像素坐标 → 原图坐标）=====

    /**
     * 模拟 YoloDetector.postprocess 中的坐标转换逻辑
     * YOLO11 raw output 的 cx/cy/w/h 是 0-640 像素坐标
     */
    private fun convertBox(
        cx: Float, cy: Float, w: Float, h: Float,
        imageWidth: Float, imageHeight: Float,
        inputSize: Int = 640
    ): FloatArray {
        val scaleX = imageWidth / inputSize.toFloat()
        val scaleY = imageHeight / inputSize.toFloat()
        val left = ((cx - w / 2f) * scaleX).coerceIn(0f, imageWidth)
        val top = ((cy - h / 2f) * scaleY).coerceIn(0f, imageHeight)
        val right = ((cx + w / 2f) * scaleX).coerceIn(0f, imageWidth)
        val bottom = ((cy + h / 2f) * scaleY).coerceIn(0f, imageHeight)
        return floatArrayOf(left, top, right, bottom)
    }

    @Test
    fun `coordinate conversion scales correctly for 1280x720 image`() {
        // 中心框：cx=320, cy=320, w=100, h=100 (在 640x640 坐标系中)
        val (left, top, right, bottom) = convertBox(320f, 320f, 100f, 100f, 1280f, 720f)
        // 缩放比例: scaleX=2, scaleY=1.125
        assertEquals(540f, left, 0.1f)   // (320 - 50) * 2
        assertEquals(270f, top, 0.1f)    // (320 - 50) * 1.125
        assertEquals(740f, right, 0.1f)  // (320 + 50) * 2
        assertEquals(427.5f, bottom, 0.1f) // (320 + 50) * 1.125
    }

    @Test
    fun `coordinate conversion clamps to image bounds`() {
        // 超出边界的框应被 clamp
        val (left, top, right, bottom) = convertBox(0f, 0f, 100f, 100f, 1280f, 720f)
        assertEquals(0f, left, 0.1f)
        assertEquals(0f, top, 0.1f)
        // right = (0 + 50) * 2 = 100, bottom = (0 + 50) * 1.125 = 56.25
        assertEquals(100f, right, 0.1f)
        assertEquals(56.25f, bottom, 0.1f)
    }
}
