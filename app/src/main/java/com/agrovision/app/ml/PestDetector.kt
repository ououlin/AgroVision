package com.agrovision.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.*
import com.agrovision.app.domain.model.CatalogType
import com.agrovision.app.domain.model.PestLabelCatalog
import com.agrovision.app.domain.model.RawDetection
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * V2.0 害虫检测头（PestDetector）
 *
 * ## 模型规格
 * - 文件：`model/pest_detector.onnx`（YOLO11n 检测头，10 类害虫）
 * - 输入：[1, 3, 640, 640] float32，ImageNet 归一化
 * - 输出：[1, 14, 8400]（4 box + 10 class），与 [YoloDetector] 的 YOLO11 输出格式一致
 *
 * ## 与 [OnnxDetector] 的分工
 * - [OnnxDetector] = DiseaseDetector（当前 MobileNetV3 8 类试验分类头）
 * - [PestDetector] = 害虫检测头（YOLO11n，10 类害虫）
 * - 由 [ModelRouter] 决定调用哪个，或并发调用后融合
 *
 * ## 置信度校准
 * - 推理后做温度缩放（Temperature Scaling），温度参数 T 从 `model/pest_temperature.txt` 加载
 * - 未找到温度文件时 T=1.0（即不校准）
 */
@Singleton
class PestDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_FILE = "model/pest_detector.onnx"
        private const val LABELS_FILE = "model/labels_pest.txt"
        private const val INPUT_SIZE = 640
        private const val NUM_CLASSES = 10
        private const val NUM_BOXES = 8400
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val NMS_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 5

        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var session: OrtSession? = null
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var labels: List<String> = emptyList()
    private var temperature: Float = 1.0f
    private var initialized = false
    private val sessionLock = Any()

    fun initialize(): Result<Unit> {
        return try {
            // 若害虫模型未随包发布，则视为该检测头不可用（不报错，由 ModelRouter 降级）
            val modelBytes = try {
                context.assets.open(MODEL_FILE).use { it.readBytes() }
            } catch (e: Exception) {
                Timber.w("PestDetector 模型未找到：$MODEL_FILE，跳过初始化（V2.0 双头降级到病害单头）")
                return Result.failure(e)
            }

            val options = OrtSession.SessionOptions().apply { addCPU(true) }
            session = env.createSession(modelBytes, options)
            labels = loadLabels()
            temperature = loadTemperature()
            initialized = true
            Timber.d("PestDetector loaded. labels=${labels.size}, T=$temperature")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "PestDetector init failed")
            Result.failure(e)
        }
    }

    suspend fun detect(bitmap: Bitmap): List<RawDetection> {
        if (!initialized) return emptyList()

        val s = session ?: return emptyList()
        val input = preprocess(bitmap)
        val inputName = s.inputNames.iterator().next()

        val start = System.currentTimeMillis()
        val outArr = input.use { inputTensor ->
            synchronized(sessionLock) {
                s.run(Collections.singletonMap(inputName, inputTensor))
            }.use { outputs ->
                // YOLO11 输出 [1, 4+NC, 8400]
                val outTensor = outputs.get(0) as OnnxTensor
                val raw = outTensor.floatBuffer
                FloatArray(raw.limit()).also { raw.get(it) }
            }
        }
        Timber.d("PestDetector 推理 ${System.currentTimeMillis() - start}ms")

        // 重排为 [8400, 4+NC]
        val numCols = 4 + NUM_CLASSES
        val detections = mutableListOf<RawDetection>()
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        for (i in 0 until NUM_BOXES) {
            val cx = outArr[0 * NUM_BOXES + i] / INPUT_SIZE * imgW
            val cy = outArr[1 * NUM_BOXES + i] / INPUT_SIZE * imgH
            val w = outArr[2 * NUM_BOXES + i] / INPUT_SIZE * imgW
            val h = outArr[3 * NUM_BOXES + i] / INPUT_SIZE * imgH

            // 取类别最大置信度
            var maxClassIdx = 0
            var maxConf = 0f
            for (c in 0 until NUM_CLASSES) {
                val conf = outArr[(4 + c) * NUM_BOXES + i]
                if (conf > maxConf) {
                    maxConf = conf
                    maxClassIdx = c
                }
            }

            // 温度缩放：calibrated = sigmoid(logit / T)
            val calibrated = sigmoid(logit(maxConf) / temperature)

            if (calibrated < CONFIDENCE_THRESHOLD) continue

            val label = if (maxClassIdx in labels.indices) labels[maxClassIdx]
                else PestLabelCatalog.allPests().getOrNull(maxClassIdx)?.code ?: "pest_$maxClassIdx"

            detections.add(RawDetection(
                label = label,
                confidence = calibrated,
                boundingBox = RectF(
                    (cx - w / 2).coerceAtLeast(0f),
                    (cy - h / 2).coerceAtLeast(0f),
                    (cx + w / 2).coerceAtMost(imgW),
                    (cy + h / 2).coerceAtMost(imgH)
                )
            ))
        }

        val nmsed = nms(detections)
        return nmsed.take(MAX_DETECTIONS).sortedByDescending { it.confidence }
    }

    private fun nms(detections: List<RawDetection>): List<RawDetection> {
        val kept = mutableListOf<RawDetection>()
        val sorted = detections.sortedByDescending { it.confidence }
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (iou(sorted[i].boundingBox, sorted[j].boundingBox) > NMS_THRESHOLD) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val x1 = maxOf(a.left, b.left)
        val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.right, b.right)
        val y2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0) inter / union else 0f
    }

    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(1 * 3 * INPUT_SIZE * INPUT_SIZE * 4)
            .order(ByteOrder.nativeOrder())
        val floatBuf = buffer.asFloatBuffer()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (c in 0 until 3) {
            val shift = when (c) { 0 -> 16; 1 -> 8; else -> 0 }
            for (i in pixels.indices) {
                val pixel = (pixels[i] shr shift and 0xFF) / 255.0f
                floatBuf.put(c * pixels.size + i, (pixel - MEAN[c]) / STD[c])
            }
        }
        buffer.rewind()
        return OnnxTensor.createTensor(
            env, buffer,
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
            OnnxJavaType.FLOAT
        )
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(LABELS_FILE).bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            // 兜底：从 PestLabelCatalog 取前 10 类害虫的 code
            PestLabelCatalog.allPests().map { it.code }
        }
    }

    private fun loadTemperature(): Float {
        return try {
            context.assets.open("model/pest_temperature.txt").bufferedReader()
                .readLine().trim().toFloatOrNull() ?: 1.0f
        } catch (e: Exception) {
            1.0f
        }
    }

    fun isReady(): Boolean = initialized

    fun close() {
        session?.close()
        session = null
        initialized = false
    }

    private fun sigmoid(x: Float): Float = 1.0f / (1.0f + exp(-x))
    private fun logit(p: Float): Float {
        val pClamp = p.coerceIn(1e-5f, 1 - 1e-5f)
        return kotlin.math.ln(pClamp / (1 - pClamp))
    }
}
