package com.agrovision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 检测历史记录 Room 实体
 */
@Entity(tableName = "detection_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 图片本地存储路径 */
    @ColumnInfo(name = "image_path")
    val imagePath: String,

    /** 检测到的病害英文名（Top-1） */
    @ColumnInfo(name = "disease_name_en")
    val diseaseNameEn: String,

    /** 检测到的病害中文名（Top-1） */
    @ColumnInfo(name = "disease_name_cn")
    val diseaseNameCn: String,

    /** 置信度 0.0~1.0（Top-1） */
    val confidence: Float,

    /** 检测时间戳 */
    val timestamp: Long = System.currentTimeMillis(),

    /** 是否已收藏 */
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    /** 用户备注（预留） */
    val note: String = "",

    /**
     * Top-3 检测结果 JSON，格式：
     * [{"label":"...","cnName":"...","confidence":0.9}, ...]
     *
     * 用于完整回溯模型输出，Top-1 字段保留用于列表展示与搜索。
     */
    @ColumnInfo(name = "all_results_json")
    val allResultsJson: String = "",

    /**
     * V2.0 关联地块 ID（可空，老数据未绑定地块时为 null）。
     *
     * 迁移策略：v4 → v5 时新增此列并默认 NULL；UI 在用户未创建地块前
     * 仍可正常使用识别，地块管理上线后引导补绑。
     */
    @ColumnInfo(name = "plot_id")
    val plotId: Long? = null,

    /**
     * V2.0 云同步状态：0=未同步，1=已同步。
     * 增量同步时扫描 `sync_status = 0` 的记录上传，成功后置 1。
     */
    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0
)
