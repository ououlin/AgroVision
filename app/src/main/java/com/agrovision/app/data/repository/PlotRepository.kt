package com.agrovision.app.data.repository

import com.agrovision.app.data.local.dao.PlotDao
import com.agrovision.app.data.local.dao.CropCount
import com.agrovision.app.data.local.entity.PlotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Singleton

/**
 * 地块仓库（V2.0）
 *
 * 业务规则：
 * - userId 从 [AuthRepository.getCurrentUser] 取，本仓库不做强校验，由调用方传入
 * - 软删除：[delete] 只置 is_deleted=1，不真删，以便云同步反查
 * - 物候期更新由顾问模块（WorkManager）触发，本仓库只提供持久化
 */
@Singleton
class PlotRepository(
    private val plotDao: PlotDao
) {

    /** 按 userId 获取地块列表（自动过滤软删除） */
    fun getPlots(userId: String): Flow<List<PlotEntity>> =
        plotDao.getByUserId(userId)

    /** 一次性快照（不订阅变化），农场档案摘要生成用 */
    suspend fun getPlotsOnce(userId: String): List<PlotEntity> =
        plotDao.getByUserId(userId).first()

    /** 按 ID 获取单条地块 */
    suspend fun getPlot(id: Long): PlotEntity? = plotDao.getById(id)

    /**
     * 创建地块
     *
     * @return 新建地块的本地 ID
     */
    suspend fun createPlot(
        userId: String,
        name: String,
        cropType: String,
        area: Double? = null,
        longitude: Double? = null,
        latitude: Double? = null,
        phenologyStage: String = "",
        note: String = ""
    ): Long {
        val entity = PlotEntity(
            userId = userId,
            name = name,
            cropType = cropType,
            area = area,
            longitude = longitude,
            latitude = latitude,
            phenologyStage = phenologyStage,
            note = note
        )
        val id = plotDao.insert(entity)
        Timber.d("createPlot: id=$id, name=$name, cropType=$cropType")
        return id
    }

    /**
     * 更新地块
     */
    suspend fun updatePlot(entity: PlotEntity) {
        val updated = entity.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = 0  // 改动后重新进入未同步队列
        )
        plotDao.update(updated)
    }

    /**
     * 软删除地块
     */
    suspend fun deletePlot(id: Long) {
        plotDao.softDelete(id)
        Timber.d("deletePlot (soft): id=$id")
    }

    /**
     * 更新物候期（由顾问模块触发）
     */
    suspend fun updatePhenology(plotId: Long, stage: String) {
        val plot = plotDao.getById(plotId) ?: return
        plotDao.update(plot.copy(
            phenologyStage = stage,
            updatedAt = System.currentTimeMillis(),
            syncStatus = 0
        ))
    }

    /**
     * 按作物类型统计（农场档案摘要用）
     */
    suspend fun countByCropType(userId: String): List<CropCount> =
        plotDao.countByCropType(userId)
}
