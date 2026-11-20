package com.agrovision.app.ml

import android.graphics.Bitmap
import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.domain.model.CatalogType
import com.agrovision.app.domain.model.DetectionResult
import com.agrovision.app.domain.model.PestLabelCatalog
import com.agrovision.app.domain.model.PestLabelEntry
import com.agrovision.app.domain.model.RawDetection
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2.0 双头模型路由器
 *
 * ## 路由策略
 * 1. **V2.0 双头开关关闭**：直接走 [OnnxDetector]（Disease 单头），与 V1.0 行为一致
 * 2. **双头开启**：
 *    - 并发调用 [OnnxDetector]（病害） + [PestDetector]（害虫）
 *    - 病害结果置信度高（≥0.5）→ 直接返回病害
 *    - 病害置信度低、害虫置信度高 → 返回害虫
 *    - 两者都高 → 合并返回，由 UI 展示 Top-K 候选
 * 3. **PestDetector 未就绪**（模型未随包发布）：降级到 Disease 单头
 *
 * ## 标签统一
 * 所有结果都通过 [PestLabelCatalog] 查询，返回 [CatalogType] 标识，方便 UI 展示区分。
 */
@Singleton
class ModelRouter @Inject constructor(
    private val onnxDetector: OnnxDetector,
    private val pestDetector: PestDetector
) {

    private var diseaseReady = false
    private var pestReady = false

    /**
     * 初始化双头模型
     *
     * Disease 头是核心，失败则整体失败；
     * Pest 头失败仅 warning，降级为 Disease 单头。
     */
    suspend fun initialize(): Result<Unit> {
        val diseaseResult = onnxDetector.initialize()
        if (diseaseResult.isSuccess) {
            diseaseReady = true
            Timber.d("ModelRouter: Disease head ready")
        } else {
            Timber.e(diseaseResult.exceptionOrNull(), "ModelRouter: Disease head failed")
            return diseaseResult
        }

        if (FeatureFlag.isV2DualModelEnabled) {
            val pestResult = pestDetector.initialize()
            if (pestResult.isSuccess) {
                pestReady = true
                Timber.d("ModelRouter: Pest head ready (dual model)")
            } else {
                Timber.w(pestResult.exceptionOrNull(), "ModelRouter: Pest head unavailable, degrade to disease-only")
                pestReady = false
            }
        }

        return Result.success(Unit)
    }

    suspend fun isReady(): Boolean = diseaseReady || pestReady

    /**
     * 双头检测 + 路由融合
     *
     * @param bitmap 待识别图片
     * @param encyclopediaLookup 百科查询回调（由 DetectionRepository 注入）
     * @return 排序后的检测结果列表
     */
    suspend fun detect(
        bitmap: Bitmap,
        cropType: String? = null,
        encyclopediaLookup: (PestLabelEntry?) -> com.agrovision.app.domain.model.PestInfo?
    ): List<DetectionResult> {
        val diseaseResults = if (diseaseReady) {
            try { onnxDetector.detect(bitmap) } catch (e: Exception) {
                Timber.w(e, "Disease head detect failed"); emptyList()
            }
        } else emptyList()

        val pestResults = if (FeatureFlag.isV2DualModelEnabled && pestReady) {
            try { pestDetector.detect(bitmap) } catch (e: Exception) {
                Timber.w(e, "Pest head detect failed"); emptyList()
            }
        } else emptyList()

        // 路由决策
        val merged = route(diseaseResults, pestResults)

        // 统一映射到 DetectionResult
        val selectedCrop = DetectionResultPolicy.normalizeCropType(cropType)
        return merged
            .filter { it.confidence >= DISPLAY_THRESHOLD }
            .filterNot { DetectionResultPolicy.isBackground(it.label) }
            .map { raw ->
                val entry = lookupEntry(raw.label)
                if (selectedCrop != null && entry?.crop != selectedCrop) {
                    return@map null
                }
                val pestInfo = encyclopediaLookup(entry)
                DetectionResult(
                    label = raw.label,
                    cnName = entry?.cnName ?: PestLabelCatalog.toCnName(raw.label),
                    confidence = raw.confidence,
                    boundingBox = raw.boundingBox,
                    pestInfo = pestInfo
                )
            }
            .filterNotNull()
            .sortedByDescending { it.confidence }
    }

    /**
     * 路由策略实现
     *
     * 规则：
     * 1. 若病害 Top-1 置信度 ≥ 0.5，直接返回病害 Top-3
     * 2. 若害虫 Top-1 置信度 ≥ 病害 Top-1，返回害虫 + 病害 Top-1
     * 3. 否则返回病害 Top-3 + 害虫 Top-1
     */
    private fun route(
        disease: List<RawDetection>,
        pest: List<RawDetection>
    ): List<RawDetection> {
        if (disease.isEmpty()) return pest
        if (pest.isEmpty()) return disease

        val diseaseTop = disease.first()
        val pestTop = pest.first()

        return when {
            diseaseTop.confidence >= 0.5f -> {
                // 病害置信度足够，以病害为主，害虫 Top-1 作为补充
                disease.take(3) + listOf(pestTop)
            }
            pestTop.confidence >= diseaseTop.confidence -> {
                // 害虫更可信
                listOf(pestTop) + disease.take(2)
            }
            else -> {
                disease.take(3) + listOf(pestTop)
            }
        }
    }

    /**
     * 查询 [PestLabelCatalog] 获取条目
     *
     * 优先 V2.0 code 查询，失败回落 V1.0 legacyLabel
     */
    private fun lookupEntry(label: String): PestLabelEntry? {
        return PestLabelCatalog.fromCode(label)
            ?: PestLabelCatalog.fromLegacyLabel(label)
            ?: PestLabelCatalog.fromCnName(label)
    }

    companion object {
        /** 展示阈值：低于此值视为噪声过滤 */
        const val DISPLAY_THRESHOLD = 0.35f
    }

    fun getModelInfo(): Map<String, String> = mapOf(
        "model" to buildString {
            if (diseaseReady) append("Disease(MobileNetV3)")
            if (pestReady) {
                if (isNotEmpty()) append(" + ")
                append("Pest(YOLO11n)")
            }
            if (isEmpty()) append("未加载")
        },
        "mode" to if (pestReady) "双头路由 (Dual-head)" else "单头分类 (Disease only)",
        "version" to "2.0.0"
    )
}
