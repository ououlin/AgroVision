package com.agrovision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用药记录 Room 实体（V2.0 新增）
 *
 * 一条用药记录 = 一次诊断后的施药行动。绑定到地块 + 可选绑定识别历史。
 * 「安全间隔期」由药剂目录维护，倒计时由 WorkManager 计算并在采收前 N 天告警。
 */
@Entity(
    tableName = "treatments",
    foreignKeys = [
        ForeignKey(
            entity = PlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["plot_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("plot_id"), Index("history_id"), Index("remote_id")]
)
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 云端用药记录 ID（同步后回填） */
    @ColumnInfo(name = "remote_id")
    val remoteId: String = "",

    /** 关联地块 ID（必填，无地块则不能创建用药记录） */
    @ColumnInfo(name = "plot_id")
    val plotId: Long,

    /** 关联识别历史 ID（可空：用户可手动添加用药不依赖识别） */
    @ColumnInfo(name = "history_id")
    val historyId: Long? = null,

    /** 药剂名称（如「阿维菌素」「代森锰锌」） */
    @ColumnInfo(name = "chemical_name")
    val chemicalName: String,

    /** 用量（自由文本，如「30ml/亩」、「1:800 喷雾」） */
    val dosage: String,

    /** 施用日期（时间戳） */
    @ColumnInfo(name = "applied_at")
    val appliedAt: Long,

    /** 安全间隔期天数（最后一次施药到采收之间的最短天数） */
    @ColumnInfo(name = "safety_interval_days")
    val safetyIntervalDays: Int = 0,

    /** 预计采收日期（时间戳，可空：用于倒计时告警） */
    @ColumnInfo(name = "expected_harvest_at")
    val expectedHarvestAt: Long? = null,

    /** 状态：pending（待施药）/ applied（已施药）/ done（已采收） */
    val status: String = "applied",

    /** 备注 */
    val note: String = "",

    /** 创建时间戳 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 云同步状态：0=未同步，1=已同步 */
    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0,

    /** 是否软删除 */
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
