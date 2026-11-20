package com.agrovision.app.data.repository

import com.agrovision.app.data.local.dao.TreatmentDao
import com.agrovision.app.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Singleton

/**
 * 用药记录仓库（V2.0）
 *
 * 业务规则：
 * - 必须关联 plotId，无地块则不能创建用药记录
 * - 安全间隔期倒计时由 WorkManager 在顾问模块计算，本仓库只持久化
 * - 软删除：与地块一致策略
 */
@Singleton
class TreatmentRepository(
    private val treatmentDao: TreatmentDao,
    private val plotRepository: PlotRepository  // 用于按 userId 反查 plotIds
) {

    /** 按 plotId 获取用药记录列表 */
    fun getTreatmentsByPlot(plotId: Long): Flow<List<TreatmentEntity>> =
        treatmentDao.getByPlotId(plotId)

    /**
     * 按 userId 获取最近 N 条用药记录（农场档案摘要用）。
     * 通过 plotRepository 先查该用户所有地块，再过滤 treatments。
     */
    suspend fun getRecentTreatments(userId: String, limit: Int = 50): List<TreatmentEntity> {
        val plotIds = plotRepository.getPlotsOnce(userId).map { it.id }.toSet()
        if (plotIds.isEmpty()) return emptyList()
        // 从 getAllSuspend() 过滤，按 appliedAt 降序，取 limit
        return treatmentDao.getAllSuspend()
            .filter { it.plotId in plotIds }
            .sortedByDescending { it.appliedAt }
            .take(limit)
    }

    /** 按 ID 获取单条 */
    suspend fun getTreatment(id: Long): TreatmentEntity? = treatmentDao.getById(id)

    /**
     * 添加用药记录（识别后或手动）
     *
     * @param plotId 关联地块 ID
     * @param chemicalName 药剂名称
     * @param dosage 用量
     * @param appliedAt 施用日期时间戳
     * @param safetyIntervalDays 安全间隔期天数
     * @param expectedHarvestAt 预计采收日期（可空）
     * @param historyId 关联识别记录 ID（可空）
     * @param note 备注
     */
    suspend fun addTreatment(
        plotId: Long,
        chemicalName: String,
        dosage: String,
        appliedAt: Long,
        safetyIntervalDays: Int = 0,
        expectedHarvestAt: Long? = null,
        historyId: Long? = null,
        note: String = ""
    ): Long {
        val entity = TreatmentEntity(
            plotId = plotId,
            historyId = historyId,
            chemicalName = chemicalName,
            dosage = dosage,
            appliedAt = appliedAt,
            safetyIntervalDays = safetyIntervalDays,
            expectedHarvestAt = expectedHarvestAt,
            status = "applied",
            note = note
        )
        val id = treatmentDao.insert(entity)
        Timber.d("addTreatment: id=$id, plotId=$plotId, chemical=$chemicalName")
        return id
    }

    /** 更新 */
    suspend fun updateTreatment(entity: TreatmentEntity) {
        treatmentDao.update(entity.copy(syncStatus = 0))
    }

    /** 软删除 */
    suspend fun deleteTreatment(id: Long) {
        treatmentDao.softDelete(id)
    }

    /** 标记已采收 */
    suspend fun markHarvested(id: Long) {
        val t = treatmentDao.getById(id) ?: return
        treatmentDao.update(t.copy(status = "done", syncStatus = 0))
    }

    /**
     * 查询即将到安全间隔期截止的用药记录（顾问告警）
     *
     * @param warningMs 距采收前多久开始告警（默认 7 天）
     */
    suspend fun getUpcomingHarvests(warningMs: Long = 7L * 24 * 60 * 60 * 1000): List<TreatmentEntity> =
        treatmentDao.getUpcomingHarvests(System.currentTimeMillis(), warningMs)

    /** 按地块取最近 N 条（农场档案摘要用） */
    suspend fun getRecentByPlot(plotId: Long, limit: Int = 5): List<TreatmentEntity> =
        treatmentDao.getRecentByPlot(plotId, limit)
}
