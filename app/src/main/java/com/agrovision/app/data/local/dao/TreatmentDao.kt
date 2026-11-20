package com.agrovision.app.data.local.dao

import androidx.room.*
import com.agrovision.app.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用药记录 DAO（V2.0 新增）
 */
@Dao
interface TreatmentDao {

    /** 按 plot_id 获取用药记录（按施药日期倒序） */
    @Query("SELECT * FROM treatments WHERE plot_id = :plotId AND is_deleted = 0 ORDER BY applied_at DESC")
    fun getByPlotId(plotId: Long): Flow<List<TreatmentEntity>>

    /** 按 ID 获取单条 */
    @Query("SELECT * FROM treatments WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: Long): TreatmentEntity?

    /** 插入或替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TreatmentEntity): Long

    /** 更新 */
    @Update
    suspend fun update(entity: TreatmentEntity)

    /** 软删除 */
    @Query("UPDATE treatments SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    /** 获取未同步用药记录 */
    @Query("SELECT * FROM treatments WHERE sync_status = 0 AND is_deleted = 0")
    suspend fun getUnsynced(): List<TreatmentEntity>

    /** 标记已同步 */
    @Query("UPDATE treatments SET sync_status = 1, remote_id = :remoteId WHERE id = :id")
    suspend fun markSynced(id: Long, remoteId: String)

    /** 获取某地块最近 N 条用药记录（农场档案摘要用） */
    @Query("SELECT * FROM treatments WHERE plot_id = :plotId AND is_deleted = 0 ORDER BY applied_at DESC LIMIT :limit")
    suspend fun getRecentByPlot(plotId: Long, limit: Int): List<TreatmentEntity>

    /** 获取即将到安全间隔期截止的用药记录（采收前 N 天告警） */
    @Query("""
        SELECT * FROM treatments
        WHERE is_deleted = 0
          AND status = 'applied'
          AND expected_harvest_at IS NOT NULL
          AND expected_harvest_at - :now <= :warningMs
        ORDER BY expected_harvest_at ASC
    """)
    suspend fun getUpcomingHarvests(now: Long, warningMs: Long): List<TreatmentEntity>

    /**
     * 获取全部未删除用药记录（备份专用：全量导出）
     */
    @Query("SELECT * FROM treatments WHERE is_deleted = 0 ORDER BY created_at ASC")
    suspend fun getAllSuspend(): List<TreatmentEntity>
}
