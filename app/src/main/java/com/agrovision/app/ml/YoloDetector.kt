package com.agrovision.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.agrovision.app.domain.model.RawDetection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Singleton
import kotlin.math.exp

/**
 * YOLO11n 检测器
 *
 * 支持的模型：
 * - 输入: [1, 640, 640, 3] float32
 * - 输出: [1, 84, 8400] float32 (YOLO11 标准输出格式)
 *
 * 输出格式说明：
 * - 8400 个候选框
 * - 前 4 列: [cx, cy, w, h]（0-640 像素坐标）
 * - 后 80 列: 80 个类别的置信度
 */
@Singleton
class YoloDetector(
    private val context: Context
) {
    companion object {
        private const val MODEL_FILE = "model/yolo11n.tflite"
        private const val LABELS_FILE = "model/labels_tflite.txt"
        private const val INPUT_SIZE = 640
        private const val NUM_CLASSES = 80 // COCO 类别数，可根据实际模型调整
        private const val NUM_BOXES = 8400 // 候选框数
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val NMS_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 10
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var labels: List<String> = emptyList()
    private var isGpuAvailable = false

    /**
     * 初始化模型
     * 优先使用 GPU Delegate，不可用时降级为 CPU
     */
    fun initialize(): Result<Unit> {
        return try {
            // 检查 GPU 可用性
            val compatList = CompatibilityList()
            isGpuAvailable = compatList.isDelegateSupportedOnThisDevice

            Timber.d("GPU delegate supported: $isGpuAvailable")

            // 加载模型选项
            val options = Interpreter.Options()

            if (isGpuAvailable) {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            } else {
                options.setNumThreads(4)
            }

            // 从 assets 加载模型
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            interpreter = Interpreter(modelBuffer, options)

            // 加载标签 — 使用独立的 TFLite 标签文件，避免与 ONNX 标签冲突
            labels = try {
                context.assets.open(LABELS_FILE)
                    .bufferedReader()
                    .use { it.readLines() }
                    .filter { line -> line.isNotBlank() }
            } catch (e: Exception) {
                // 使用 COCO 默认标签作为后备
                Timber.w(e, "Failed to load $LABELS_FILE, using default")
                getDefaultCocoLabels()
            }

            Timber.d("YOLO detector initialized with ${labels.size} classes, " +
                    "GPU: $isGpuAvailable")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize YOLO detector")
            Result.failure(e)
        }
    }

    /**
     * 执行检测
     * @param bitmap 输入图片
     * @return 检测结果列表
     */
    suspend fun detect(bitmap: Bitmap): List<RawDetection> {
        val interpreter = interpreter
            ?: throw IllegalStateException("YOLO detector not initialized")

        Timber.d("=== 开始推理 ===")
        Timber.d("输入图片: ${bitmap.width}x${bitmap.height}")

        // 1. 图片预处理
        Timber.d("预处理: 缩放至 ${INPUT_SIZE}x${INPUT_SIZE} + 归一化")
        val tensorImage = preprocess(bitmap)
        Timber.d("预处理完成")

        // 2. 准备输出缓冲区
        val outputSize = 1 * (4 + NUM_CLASSES) * NUM_BOXES
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // 3. 执行推理
        val startTime = System.currentTimeMillis()
        Timber.d("调用 Interpreter.run()...")
        synchronized(interpreter) {
            interpreter.run(tensorImage.buffer, outputBuffer)
        }
        val elapsed = System.currentTimeMillis() - startTime
        Timber.d("推理完成，耗时: ${elapsed}ms")

        // 4. 后处理
        val rawOutput = FloatArray(outputSize)
        outputBuffer.rewind()
        outputBuffer.get(rawOutput)

        Timber.d("后处理 + NMS...")
        val results = postprocess(rawOutput, bitmap.width.toFloat(), bitmap.height.toFloat())
        Timber.d("检测到 ${results.size} 个目标")

        results.forEachIndexed { i, r ->
            Timber.d("  #${i}: ${r.label} conf=${String.format("%.2f", r.confidence)} box=${r.boundingBox}")
        }
        Timber.d("=== 推理结束 ===")
        return results
    }

    /**
     * 图片预处理：缩放至 640×640 + 归一化
     */
    private fun preprocess(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage.fromBitmap(bitmap)
        tensorImage = imageProcessor.process(tensorImage)
        return tensorImage
    }

    /**
     * 后处理：解析输出 + NMS
     *
     * YOLO11 输出格式为 [1, 84, 8400]:
     * - 第 0 维: batch size = 1
     * - 第 1 维: [cx, cy, w, h, class0_conf, class1_conf, ..., class79_conf]
     * - 第 2 维: 8400 个检测框
     *
     * 注意: cx/cy/w/h 是 0-640 的像素坐标（相对于 INPUT_SIZE），
     * 需要按缩放比例转换回原图坐标。
     */
    private fun postprocess(
        rawOutput: FloatArray,
        imageWidth: Float,
        imageHeight: Float
    ): List<RawDetection> {
        val detections = mutableListOf<RawDetection>()
        val stride = NUM_CLASSES + 4

        // YOLO11 raw output 的 cx/cy/w/h 是 0-INPUT_SIZE 像素坐标，需按缩放比例转回原图
        val scaleX = imageWidth / INPUT_SIZE.toFloat()
        val scaleY = imageHeight / INPUT_SIZE.toFloat()

        for (boxIdx in 0 until NUM_BOXES) {
            val offset = boxIdx * stride

            // 提取 cx, cy, w, h (像素坐标 0-640)
            val cx = rawOutput[offset]
            val cy = rawOutput[offset + 1]
            val w = rawOutput[offset + 2]
            val h = rawOutput[offset + 3]

            // 提取类别置信度并找到最大类
            var maxConf = 0f
            var maxClassIdx = 0
            for (classIdx in 0 until NUM_CLASSES) {
                // YOLO11 使用 Sigmoid 激活
                val confidence = sigmoid(rawOutput[offset + 4 + classIdx])
                if (confidence > maxConf) {
                    maxConf = confidence
                    maxClassIdx = classIdx
                }
            }

            // 过滤低置信度
            if (maxConf < CONFIDENCE_THRESHOLD) continue

            // 坐标从模型像素坐标(0-640)转换到原图像素坐标
            val left = ((cx - w / 2f) * scaleX).coerceIn(0f, imageWidth)
            val top = ((cy - h / 2f) * scaleY).coerceIn(0f, imageHeight)
            val right = ((cx + w / 2f) * scaleX).coerceIn(0f, imageWidth)
            val bottom = ((cy + h / 2f) * scaleY).coerceIn(0f, imageHeight)

            val width = right - left
            val height = bottom - top

            // 过滤无效框
            if (width <= 0 || height <= 0) continue

            val label = if (maxClassIdx < labels.size) labels[maxClassIdx] else "class_$maxClassIdx"

            detections.add(
                RawDetection(
                    label = label,
                    confidence = maxConf,
                    boundingBox = RectF(left, top, right, bottom)
                )
            )
        }

        // NMS
        return nonMaxSuppression(detections, NMS_THRESHOLD, MAX_DETECTIONS)
    }

    /**
     * Sigmoid 激活函数
     */
    private fun sigmoid(x: Float): Float {
        return 1f / (1f + exp(-x))
    }

    /**
     * NMS (Non-Maximum Suppression)
     * 按置信度排序后，抑制重叠框
     */
    private fun nonMaxSuppression(
        detections: List<RawDetection>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<RawDetection> {
        if (detections.isEmpty()) return emptyList()

        // 按置信度降序排列
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<RawDetection>()

        while (sorted.isNotEmpty() && result.size < maxDetections) {
            val best = sorted.removeAt(0)
            result.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (iou(best.boundingBox, candidate.boundingBox) > iouThreshold) {
                    iterator.remove()
                }
            }
        }

        return result
    }

    /**
     * 计算两个框的 IoU
     */
    private fun iou(boxA: RectF, boxB: RectF): Float {
        val intersectLeft = maxOf(boxA.left, boxB.left)
        val intersectTop = maxOf(boxA.top, boxB.top)
        val intersectRight = minOf(boxA.right, boxB.right)
        val intersectBottom = minOf(boxA.bottom, boxB.bottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }

        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val areaA = boxA.width() * boxA.height()
        val areaB = boxB.width() * boxB.height()
        val unionArea = areaA + areaB - intersectArea

        return if (unionArea > 0f) intersectArea / unionArea else 0f
    }

    /**
     * 释放资源
     */
    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }

    /**
     * 默认 COCO 80 类标签
     */
    private fun getDefaultCocoLabels(): List<String> {
        return listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
            "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
            "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
            "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush"
        )
    }
}
