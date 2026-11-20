package com.agrovision.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agrovision.app.data.local.entity.DetectionFeedback

/**
 * 用户反馈数据访问对象
 */
@Dao
interface FeedbackDao {

    /** 插入反馈（替换同 ID 记录） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: DetectionFeedback): Long

    /** 获取全部反馈数量 */
    @Query("SELECT COUNT(*) FROM detection_feedback")
    suspend fun getCount(): Int

    /** 获取正确反馈数量 */
    @Query("SELECT COUNT(*) FROM detection_feedback WHERE is_correct = 1")
    suspend fun getCorrectCount(): Int

    /**
     * 计算准确率 = 正确反馈数 / 总反馈数
     * 无反馈时返回 -1 表示暂无数据
     */
    @Query("""
        SELECT CASE
            WHEN COUNT(*) = 0 THEN -1.0
            ELSE SUM(CASE WHEN is_correct THEN 1.0 ELSE 0.0 END) / COUNT(*)
        END
        FROM detection_feedback
    """)
    suspend fun getAccuracy(): Float

    /** 按历史记录 ID 查询反馈 */
    @Query("SELECT * FROM detection_feedback WHERE history_id = :historyId LIMIT 1")
    suspend fun getByHistoryId(historyId: Long): DetectionFeedback?

    /** 获取全部反馈（用于数据备份导出） */
    @Query("SELECT * FROM detection_feedback")
    suspend fun getAll(): List<DetectionFeedback>

    /** 清空全部反馈 */
    @Query("DELETE FROM detection_feedback")
    suspend fun deleteAll()
}
