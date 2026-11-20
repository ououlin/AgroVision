package com.agrovision.app.data.local.dao

import androidx.room.*
import com.agrovision.app.data.local.entity.PlotEntity
import kotlinx.coroutines.flow.Flow

/**
 * 地块 DAO（V2.0 新增）
 *
 * 所有查询默认过滤 [isDeleted]=0 的软删除记录。
 */
@Dao
interface PlotDao {

    /** 按 user_id 获取全部未删除地块 */
    @Query("SELECT * FROM plots WHERE user_id = :userId AND is_deleted = 0 ORDER BY updated_at DESC")
    fun getByUserId(userId: String): Flow<List<PlotEntity>>

    /** 按 ID 获取单条地块 */
    @Query("SELECT * FROM plots WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: Long): PlotEntity?

    /** 插入或替换（remoteId 冲突时由同步逻辑处理） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlotEntity): Long

    /** 更新 */
    @Update
    suspend fun update(entity: PlotEntity)

    /** 软删除 */
    @Query("UPDATE plots SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    /** 获取未同步地块（增量同步扫描） */
    @Query("SELECT * FROM plots WHERE user_id = :userId AND sync_status = 0 AND is_deleted = 0")
    suspend fun getUnsynced(userId: String): List<PlotEntity>

    /** 标记已同步 */
    @Query("UPDATE plots SET sync_status = 1, remote_id = :remoteId WHERE id = :id")
    suspend fun markSynced(id: Long, remoteId: String)

    /** 按 crop_type 统计（农场档案摘要用） */
    @Query("SELECT crop_type, COUNT(*) as cnt FROM plots WHERE user_id = :userId AND is_deleted = 0 GROUP BY crop_type")
    suspend fun countByCropType(userId: String): List<CropCount>

    /**
     * 获取全部未删除地块（备份专用：不需要指定userId，全量导出当前DB中所有地块）
     */
    @Query("SELECT * FROM plots WHERE is_deleted = 0 ORDER BY created_at ASC")
    suspend fun getAllSuspend(): List<PlotEntity>
}

/** 按作物分组统计结果 */
data class CropCount(
    @ColumnInfo(name = "crop_type") val cropType: String,
    val cnt: Int
)
