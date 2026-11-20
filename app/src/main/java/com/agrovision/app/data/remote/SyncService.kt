package com.agrovision.app.data.remote

import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.local.dao.PlotDao
import com.agrovision.app.data.local.dao.TreatmentDao
import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.local.entity.TreatmentEntity
import timber.log.Timber
import javax.inject.Singleton

/**
 * V2.0 云同步服务（重构版）
 *
 * ## 设计原则
 * - **本地优先**：Room 仍是主数据源，云端只做增量备份
 * - **不存原图**：检测记录只同步 `imagePath` 之外的元数据 + 缩略图（≤200×200）
 * - **增量同步**：扫描 `sync_status = 0` 的记录上传，成功后置 1
 * - **换机恢复**：用户登录后从云端拉取地块 + 用药 + 历史元数据，回填本地
 *
 * ## 同步策略
 * 1. WorkManager 周期触发（默认 6 小时）+ 登录后立即触发一次
 * 2. 单条失败不影响其他记录（最多 100 条/批）
 * 3. 网络异常时直接返回 Result.success()，下次调度再试（避免 WorkManager 退避雪崩）
 */
@Singleton
class SyncService(
    private val api: AgroVisionApi,
    private val historyDao: HistoryDao,
    private val plotDao: PlotDao,
    private val treatmentDao: TreatmentDao
) {

    /**
     * 全量增量同步入口（由 WorkManager 调用）
     *
     * 顺序：地块 → 用药 → 历史记录
     * 之所以地块在前：用药的 history_remote_id 依赖地块 remote_id 已回填
     *
     * @param userId 当前用户 ID（游客时仍可同步元数据到 guest_xxx 命名空间）
     * @return true=全部成功或部分成功但无致命错误；false=需要立即重试
     */
    suspend fun syncAll(userId: String): Boolean {
        if (!FeatureFlag.isV2CloudSyncEnabled) {
            Timber.d("V2.0 云同步未启用，跳过")
            return true
        }

        var allOk = true
        allOk = syncPlots(userId) && allOk
        allOk = syncTreatments() && allOk
        allOk = syncDetections() && allOk
        return allOk
    }

    // ============ 地块同步 ============

    /**
     * 增量上传未同步地块
     */
    suspend fun syncPlots(userId: String): Boolean {
        val unsynced = try {
            plotDao.getUnsynced(userId)
        } catch (e: Exception) {
            Timber.w(e, "syncPlots 查询未同步地块失败")
            return false
        }
        if (unsynced.isEmpty()) {
            Timber.d("syncPlots: 无未同步地块")
            return true
        }

        val requests = unsynced.map { it.toSyncRequest() }
        return try {
            val response = api.upsertPlots(requests)
            if (response.code == 200 && response.data != null) {
                response.data.ids.forEach { mapping ->
                    plotDao.markSynced(mapping.localId, mapping.remoteId)
                }
                Timber.d("syncPlots: 成功 ${response.data.synced} 条")
                true
            } else {
                Timber.w("syncPlots 业务失败 code=${response.code}, msg=${response.message}")
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "syncPlots 网络异常")
            false
        }
    }

    /**
     * 从云端拉取地块（换机登录后恢复）
     */
    suspend fun pullPlots(userId: String): Boolean {
        if (!FeatureFlag.isV2CloudSyncEnabled) return true

        var page = 1
        var total = Int.MAX_VALUE
        while (page * 50 < total + 50) {
            val response = try {
                api.getPlots(page = page, size = 50)
            } catch (e: Exception) {
                Timber.w(e, "pullPlots 网络异常 page=$page")
                return false
            }
            if (response.code != 200 || response.data == null) {
                Timber.w("pullPlots 业务失败 code=${response.code}")
                return false
            }
            val data = response.data
            total = data.total

            data.items.forEach { item ->
                // 已存在则跳过（按 remote_id 判定）；不存在则插入
                val local = PlotEntity(
                    remoteId = item.remoteId,
                    userId = userId,
                    name = item.name,
                    cropType = item.cropType,
                    area = item.area,
                    longitude = item.longitude,
                    latitude = item.latitude,
                    phenologyStage = item.phenologyStage,
                    note = item.note,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt,
                    syncStatus = 1
                )
                plotDao.insert(local)
            }
            page++
            if (data.items.isEmpty()) break
        }
        Timber.d("pullPlots: 完成，共 $total 条")
        return true
    }

    // ============ 用药记录同步 ============

    suspend fun syncTreatments(): Boolean {
        val unsynced = try {
            treatmentDao.getUnsynced()
        } catch (e: Exception) {
            Timber.w(e, "syncTreatments 查询失败")
            return false
        }
        if (unsynced.isEmpty()) {
            Timber.d("syncTreatments: 无未同步记录")
            return true
        }

        // 需要 plot 的 remoteId 已回填；如果未回填则跳过该条
        val requests = unsynced.mapNotNull { treatment ->
            val plot = plotDao.getById(treatment.plotId)
            if (plot?.remoteId.isNullOrEmpty()) {
                Timber.w("syncTreatments: 地块 remoteId 未回填，跳过 treatment id=${treatment.id}")
                return@mapNotNull null
            }
            treatment.toSyncRequest(plot!!.remoteId)
        }

        if (requests.isEmpty()) {
            Timber.w("syncTreatments: 所有用药记录的地块 remoteId 未回填，跳过")
            return false
        }

        return try {
            val response = api.upsertTreatments(requests)
            if (response.code == 200 && response.data != null) {
                response.data.ids.forEach { mapping ->
                    treatmentDao.markSynced(mapping.localId, mapping.remoteId)
                }
                Timber.d("syncTreatments: 成功 ${response.data.synced} 条")
                true
            } else {
                Timber.w("syncTreatments 业务失败 code=${response.code}")
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "syncTreatments 网络异常")
            false
        }
    }

    // ============ 检测历史同步 ============

    /**
     * 上传单条检测记录（V1.0 兼容接口，V2.0 包装为增量同步）
     */
    suspend fun uploadDetection(entity: HistoryEntity): Result<SyncResult> {
        return try {
            val request = DetectionSyncRequest(
                imagePath = "",  // V2.0 不传原图路径，云端不存原图
                diseaseNameCn = entity.diseaseNameCn,
                diseaseNameEn = entity.diseaseNameEn,
                confidence = entity.confidence,
                timestamp = entity.timestamp
            )
            val response = api.uploadDetection(request)
            historyDao.markSynced(entity.id)
            Result.success(response.data ?: SyncResult())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 批量上传（V1.0 兼容接口）
     */
    suspend fun uploadDetections(entities: List<HistoryEntity>): Result<SyncResult> {
        val requests = entities.map {
            DetectionSyncRequest(
                imagePath = "",
                diseaseNameCn = it.diseaseNameCn,
                diseaseNameEn = it.diseaseNameEn,
                confidence = it.confidence,
                timestamp = it.timestamp
            )
        }
        return try {
            val response = api.uploadDetections(requests)
            entities.forEach { historyDao.markSynced(it.id) }
            Result.success(response.data ?: SyncResult())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * V2.0 增量同步未上传的检测历史
     */
    suspend fun syncDetections(): Boolean {
        val unsynced = try {
            historyDao.getUnsynced(limit = 100)
        } catch (e: Exception) {
            Timber.w(e, "syncDetections 查询失败")
            return false
        }
        if (unsynced.isEmpty()) {
            Timber.d("syncDetections: 无未同步记录")
            return true
        }

        val result = uploadDetections(unsynced)
        return result.isSuccess
    }

    /**
     * 获取云端统计（V1.0 兼容）
     */
    suspend fun getStatsOverview(): Result<StatsResponse> {
        return try {
            val response = api.getStatsOverview()
            Result.success(response.data ?: StatsResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检查模型更新
     */
    suspend fun checkModelUpdate(currentVersion: String): Result<ModelUpdateResponse> {
        return try {
            val response = api.checkModelUpdate(currentVersion)
            Result.success(response.data ?: ModelUpdateResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ Entity → DTO 转换 ============

    private fun PlotEntity.toSyncRequest() = PlotSyncRequest(
        remoteId = remoteId,
        name = name,
        cropType = cropType,
        area = area,
        longitude = longitude,
        latitude = latitude,
        phenologyStage = phenologyStage,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun TreatmentEntity.toSyncRequest(plotRemoteId: String) = TreatmentSyncRequest(
        remoteId = remoteId,
        plotRemoteId = plotRemoteId,
        historyRemoteId = null,  // 历史记录云端 ID 暂不回溯关联
        chemicalName = chemicalName,
        dosage = dosage,
        appliedAt = appliedAt,
        safetyIntervalDays = safetyIntervalDays,
        expectedHarvestAt = expectedHarvestAt,
        status = status,
        note = note,
        createdAt = createdAt,
        isDeleted = isDeleted
    )
}
