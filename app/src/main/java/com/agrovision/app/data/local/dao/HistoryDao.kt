package com.agrovision.app.data.local.dao

import androidx.room.*
import com.agrovision.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    /** 获取所有历史记录，按时间倒序 */
    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    /** 按病害名称搜索 */
    @Query("SELECT * FROM detection_history WHERE disease_name_cn LIKE '%' || :keyword || '%' OR disease_name_en LIKE '%' || :keyword || '%' ORDER BY timestamp DESC")
    fun searchHistory(keyword: String): Flow<List<HistoryEntity>>

    /** 按 ID 获取单条记录 */
    @Query("SELECT * FROM detection_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): HistoryEntity?

    /** 插入记录，返回生成的 ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    /** 更新记录 */
    @Update
    suspend fun update(entity: HistoryEntity)

    /** 删除记录 */
    @Delete
    suspend fun delete(entity: HistoryEntity)

    /** 按 ID 删除 */
    @Query("DELETE FROM detection_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清空所有历史 */
    @Query("DELETE FROM detection_history")
    suspend fun deleteAll()

    /** 获取记录总数 */
    @Query("SELECT COUNT(*) FROM detection_history")
    suspend fun getCount(): Int

    /** 获取今日记录数（从当天0点起） */
    @Query("SELECT COUNT(*) FROM detection_history WHERE timestamp >= :startOfDay")
    suspend fun getTodayCount(startOfDay: Long): Int

    /** 切换收藏状态 */
    @Query("UPDATE detection_history SET is_favorite = NOT is_favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    /** 获取最近N条记录 */
    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<HistoryEntity>

    // ============ V2.0 地块相关查询 ============

    /** 按地块 ID 获取历史记录 */
    @Query("SELECT * FROM detection_history WHERE plot_id = :plotId ORDER BY timestamp DESC")
    fun getByPlotId(plotId: Long): Flow<List<HistoryEntity>>

    /** 获取未同步的历史记录（增量同步扫描） */
    @Query("SELECT * FROM detection_history WHERE sync_status = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 100): List<HistoryEntity>

    /** 标记已同步 */
    @Query("UPDATE detection_history SET sync_status = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    /** 绑定地块（V1 老数据补绑） */
    @Query("UPDATE detection_history SET plot_id = :plotId WHERE id = :id")
    suspend fun bindPlot(id: Long, plotId: Long)

    /** 按病害名称统计（农场档案摘要用） */
    @Query("""
        SELECT disease_name_cn, COUNT(*) as cnt
        FROM detection_history
        WHERE plot_id = :plotId AND disease_name_cn != ''
        GROUP BY disease_name_cn
        ORDER BY cnt DESC
        LIMIT :limit
    """)
    suspend fun countDiseaseByPlot(plotId: Long, limit: Int = 5): List<com.agrovision.app.data.local.dao.DiseaseCount>
}
