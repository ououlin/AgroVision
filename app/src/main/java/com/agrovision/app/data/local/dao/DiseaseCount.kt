package com.agrovision.app.data.local.dao

import androidx.room.ColumnInfo

/**
 * 按病害名称分组统计结果（V2.0 农场档案摘要用）
 *
 * 用于 [HistoryDao.countDiseaseByPlot] 返回值。
 */
data class DiseaseCount(
    @ColumnInfo(name = "disease_name_cn") val diseaseNameCn: String,
    val cnt: Int
)
