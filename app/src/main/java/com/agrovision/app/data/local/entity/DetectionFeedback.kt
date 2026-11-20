package com.agrovision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户检测反馈 Room 实体
 * 用于采集用户对识别结果的反馈（对/不对），计算真实准确率
 */
@Entity(
    tableName = "detection_feedback",
    foreignKeys = [
        ForeignKey(
            entity = HistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["history_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("history_id")]
)
data class DetectionFeedback(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 关联的检测历史记录 ID */
    @ColumnInfo(name = "history_id")
    val historyId: Long,

    /** 识别结果是否正确：true=正确，false=不正确 */
    @ColumnInfo(name = "is_correct")
    val isCorrect: Boolean,

    /** 反馈时间戳 */
    val timestamp: Long = System.currentTimeMillis()
)
