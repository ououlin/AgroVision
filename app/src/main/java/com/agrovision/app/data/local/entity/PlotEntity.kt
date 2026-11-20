package com.agrovision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 地块 Room 实体（V2.0 新增）
 *
 * 一个「地块」= 用户农场中的一个管理单元，可绑定识别记录与用药记录。
 * 不存 GIS 多边形（留给 V3.0），只存经纬度中心点（可选）。
 */
@Entity(
    tableName = "plots",
    indices = [Index("user_id"), Index("remote_id")]
)
data class PlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 云端地块 ID（同步后回填，未同步前为空字符串） */
    @ColumnInfo(name = "remote_id")
    val remoteId: String = "",

    /** 用户 ID（游客时为 guest_xxx，登录后为后端 userId） */
    @ColumnInfo(name = "user_id")
    val userId: String,

    /** 地块名称（用户自定义，如「东边苹果园」、「3号大棚番茄」） */
    val name: String,

    /** 作物类型（取自 PestLabelCatalog.crop 字段，如 "apple"、"rice"） */
    @ColumnInfo(name = "crop_type")
    val cropType: String,

    /** 面积（亩，可空：老张型用户常不愿填） */
    val area: Double? = null,

    /** 经度（中心点，可空：不强制要求定位） */
    val longitude: Double? = null,

    /** 纬度（中心点，可空：不强制要求定位） */
    val latitude: Double? = null,

    /** 物候期（取自 PhenologyCatalog，如 "flowering"、"harvest"） */
    @ColumnInfo(name = "phenology_stage")
    val phenologyStage: String = "",

    /** 备注 */
    val note: String = "",

    /** 创建时间戳 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 更新时间戳 */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /** 云同步状态：0=未同步，1=已同步 */
    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0,

    /** 是否软删除（不真删，保留以便云同步反查） */
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
