package com.agrovision.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.*
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
 * ONNX 分类推理器 (MobileNetV3-Small)
 *
 * 输入: [1, 3, 224, 224] float32, ImageNet 归一化
 * 输出: [1, N] float32 logits → softmax → Top-K 类别
 *
 * 将分类结果包装为 RawDetection（boundingBox 为全图），
 * 以兼容上层 DetectionRepository / DetectionViewModel 的检测模式接口。
 */
@Singleton
class OnnxDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_FILE = "model/yolo11n.onnx"
        private const val LABELS_FILE = "model/labels_onnx.txt"
        private const val INPUT_SIZE = 224
        private const val TOP_K = 3            // 返回 Top-3 结果
        private const val MIN_CONFIDENCE = 0.05f

        // ImageNet 归一化参数 (与训练时 transforms.Normalize 一致)
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var session: OrtSession? = null

    // OrtEnvironment 是进程级单例，不可 close，用 lazy 延迟获取
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private var labels: List<String> = emptyList()
    private var initialized = false

    // session.run() 非线程安全，实时识别模式下可能并发调用
    private val sessionLock = Any()

    fun initialize(): Result<Unit> {
        return try {
            Timber.d("Loading ONNX classification model: $MODEL_FILE")

            val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
            Timber.d("Model size: ${modelBytes.size} bytes")

            val options = OrtSession.SessionOptions().apply {
                addCPU(true)
            }

            session = env.createSession(modelBytes, options)
            labels = loadLabels()
            initialized = true
            Timber.d("ONNX model loaded. Input: ${session!!.inputInfo}, Output: ${session!!.outputInfo}")
            Timber.d("Labels (${labels.size}): $labels")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load ONNX model")
            Result.failure(e)
        }
    }

    suspend fun detect(bitmap: Bitmap): List<RawDetection> {
        val s = session ?: throw IllegalStateException("ONNX session not initialized")

        Timber.d("=== ONNX 分类推理开始 ===")
        Timber.d("输入: ${bitmap.width}x${bitmap.height}")

        // 1. 预处理
        val input = preprocess(bitmap)
        val inputName = s.inputNames.iterator().next()

        // 2. 推理 — synchronized 防止并发 session.run()
        val start = System.currentTimeMillis()
        val logits = input.use { inputTensor ->
            synchronized(sessionLock) {
                s.run(Collections.singletonMap(inputName, inputTensor))
            }.use { outputs ->
                // 3. 解析输出 [1, N] logits
                val outTensor = outputs.get(0) as OnnxTensor
                val rawArray = outTensor.floatBuffer
                FloatArray(rawArray.limit()).also { rawArray.get(it) }
            }
        }
        val elapsed = System.currentTimeMillis() - start
        Timber.d("推理完成: ${elapsed}ms")
        Timber.d("输出长度: ${logits.size} (类别数)")

        // 4. Softmax + Top-K
        val probs = softmax(logits)
        val results = topK(probs, bitmap.width.toFloat(), bitmap.height.toFloat())

        Timber.d("Top-${results.size} 结果:")
        results.forEachIndexed { i, r ->
            Timber.d("  #${i}: ${r.label} conf=${"%.2f".format(r.confidence)}")
        }

        return results
    }

    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(1 * 3 * INPUT_SIZE * INPUT_SIZE * 4)
            .order(ByteOrder.nativeOrder())
        val floatBuf = buffer.asFloatBuffer()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // CHW 排列, ImageNet 归一化: (pixel/255 - mean) / std
        for (c in 0 until 3) {
            val shift = when (c) {
                0 -> 16  // R
                1 -> 8   // G
                else -> 0 // B
            }
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

    private fun topK(probs: FloatArray, imgW: Float, imgH: Float): List<RawDetection> {
        // 防御性 numClasses：取 labels 与 probs 的较小值，避免任一侧维度不匹配时越界
        val numClasses = when {
            labels.isEmpty() -> probs.size
            else -> minOf(labels.size, probs.size)
        }
        val indexed = probs.indices
            .filter { it < numClasses }
            .map { it to probs[it] }
            .sortedByDescending { it.second }
            .take(TOP_K)
            .filter { it.second >= MIN_CONFIDENCE }

        // 分类模型无检测框, 使用全图作为 boundingBox
        return indexed.map { (idx, prob) ->
            val label = if (idx in labels.indices) labels[idx] else "class_$idx"
            RawDetection(label, prob, RectF(0f, 0f, imgW, imgH))
        }
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(LABELS_FILE).bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.w(e, "No $LABELS_FILE found")
            emptyList()
        }
    }

    fun isReady(): Boolean = initialized

    fun close() {
        // 只关闭 session，不关闭 OrtEnvironment（进程级单例，close 后无法恢复）
        session?.close()
        session = null
        initialized = false
    }
}
