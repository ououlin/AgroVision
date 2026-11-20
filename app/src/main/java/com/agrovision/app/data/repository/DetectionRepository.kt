package com.agrovision.app.data.repository

import android.graphics.Bitmap
import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.data.local.encyclopedia.EncyclopediaDataSource
import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.domain.model.CatalogType
import com.agrovision.app.domain.model.DetectionResult
import com.agrovision.app.domain.model.PestInfo
import com.agrovision.app.domain.model.PestLabelCatalog
import com.agrovision.app.domain.model.PestLabelEntry
import com.agrovision.app.ml.ModelRouter
import com.agrovision.app.ml.OnnxDetector
import com.agrovision.app.ml.YoloDetector
import com.agrovision.app.ml.DetectionResultPolicy
import timber.log.Timber
import javax.inject.Singleton

/**
 * 检测仓库（V2.0 重构版）
 *
 * ## V2.0 改造点
 * - 主路径走 [ModelRouter] 双头路由（Disease + Pest）
 * - V2.0 双头开关关闭时，仍可走 [onnxDetector] 单头兜底
 * - 标签查询统一走 [PestLabelCatalog]（兼容 V1.0 legacyLabel）
 * - 检测结果绑定 [plotId]（可选，由 UI 层调用 [detect] 时传入）
 * - 持久化到 [HistoryDao]，写入 sync_status=0 触发云同步
 */
@Singleton
class DetectionRepository(
    private val modelRouter: ModelRouter,
    private val onnxDetector: OnnxDetector,
    private val yoloDetector: YoloDetector,
    private val encyclopediaDataSource: EncyclopediaDataSource,
    private val historyDao: HistoryDao
) {

    private var routerReady = false
    private var onnxReady = false
    private var tfliteReady = false

    /**
     * 初始化模型：V2.0 走 ModelRouter（双头），失败降级 V1.0 路径
     */
    suspend fun initializeModel(): Result<Unit> {
        // V2.0 主路径
        if (FeatureFlag.isV2DualModelEnabled) {
            val routerResult = modelRouter.initialize()
            if (routerResult.isSuccess) {
                routerReady = true
                Timber.d("V2.0 ModelRouter loaded (dual-head)")
                return Result.success(Unit)
            }
            Timber.w(routerResult.exceptionOrNull(), "V2.0 ModelRouter failed, degrade to V1.0 path")
        }

        // V1.0 兼容路径
        val onnxResult = onnxDetector.initialize()
        if (onnxResult.isSuccess) {
            onnxReady = true
            return Result.success(Unit)
        }

        val tfliteResult = yoloDetector.initialize()
        if (tfliteResult.isSuccess) {
            tfliteReady = true
            return Result.success(Unit)
        }

        return Result.failure(Exception("All model initializations failed"))
    }

    suspend fun isModelReady(): Boolean = routerReady || onnxReady || tfliteReady

    /**
     * V2.0 检测主入口
     *
     * @param bitmap 待识别图片
     * @param plotId 关联地块 ID（可空，老用户未建地块时为 null）
     * @param persist 是否持久化到 Room（实时识别可不落库，拍照识别落库）
     * @return 检测结果列表（已按置信度排序，已过滤非病虫害）
     */
    suspend fun detect(
        bitmap: Bitmap,
        cropType: String? = null,
        plotId: Long? = null,
        persist: Boolean = true
    ): List<DetectionResult> {
        val results = if (routerReady) {
            modelRouter.detect(bitmap, cropType) { entry -> lookupEncyclopedia(entry) }
        } else if (onnxReady) {
            mapRawResults(onnxDetector.detect(bitmap), cropType)
        } else if (tfliteReady) {
            mapRawResults(yoloDetector.detect(bitmap), cropType)
        } else {
            Timber.e("No model loaded")
            return emptyList()
        }

        // 持久化
        if (persist && results.isNotEmpty()) {
            val top = results.first()
            val allResultsJson = serializeTopK(results)
            val entity = HistoryEntity(
                imagePath = "",  // 由调用方填本地路径
                diseaseNameEn = top.label,
                diseaseNameCn = top.cnName,
                confidence = top.confidence,
                plotId = plotId,
                syncStatus = 0
            )
            val id = historyDao.insert(entity)
            Timber.d("detect persisted: id=$id, plotId=$plotId")
        }

        return results
    }

    /**
     * V1.0 兼容：不绑定地块、不显式控制持久化的检测
     */
    suspend fun detect(bitmap: Bitmap): List<DetectionResult> =
        detect(bitmap, cropType = null, plotId = null, persist = false)

    private fun mapRawResults(
        raw: List<com.agrovision.app.domain.model.RawDetection>,
        cropType: String? = null
    ): List<DetectionResult> {
        val cropCode = DetectionResultPolicy.normalizeCropType(cropType)
        return raw
            .filter { it.confidence >= DISPLAY_THRESHOLD }
            .filterNot { DetectionResultPolicy.isBackground(it.label) }
            .mapNotNull { r ->
                val entry = PestLabelCatalog.fromLegacyLabel(r.label)
                    ?: PestLabelCatalog.fromCode(r.label)
                if (cropCode != null && entry?.crop != cropCode) return@mapNotNull null
                val pestInfo = lookupEncyclopedia(entry)
                DetectionResult(
                    label = r.label,
                    cnName = entry?.cnName ?: PestLabelCatalog.toCnName(r.label),
                    confidence = r.confidence,
                    boundingBox = r.boundingBox,
                    pestInfo = pestInfo
                )
            }
            .sortedByDescending { it.confidence }
    }

    /**
     * 查询百科信息
     *
     * 优先用 [PestLabelEntry.enName] 精确匹配；
     * 失败则用 [PestLabelEntry.cnName] 模糊匹配
     */
    private fun lookupEncyclopedia(entry: PestLabelEntry?): PestInfo? {
        if (entry == null) return null
        return try {
            encyclopediaDataSource.findByEnglishName(entry.enName)
                ?: encyclopediaDataSource.findByChineseName(entry.cnName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 序列化 Top-K 为 JSON 存到 [HistoryEntity.allResultsJson]
     */
    private fun serializeTopK(results: List<DetectionResult>): String {
        val items = results.take(3).joinToString(",") { r ->
            """{"label":"${r.label}","cnName":"${r.cnName}","confidence":${r.confidence}}"""
        }
        return "[$items]"
    }

    fun getModelInfo(): Map<String, String> = if (routerReady) {
        modelRouter.getModelInfo()
    } else mapOf(
        "model" to if (onnxReady) "MobileNetV3-Small (ONNX)" else if (tfliteReady) "YOLO11n (TFLite)" else "未加载",
        "version" to if (onnxReady || tfliteReady) "1.0.0" else "unknown"
    )

    companion object {
        const val DISPLAY_THRESHOLD = 0.35f
    }
}
