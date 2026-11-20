package com.agrovision.app.domain.model

/**
 * 病虫百科信息
 */
data class PestInfo(
    /** 英文名称（与YOLO模型标签对应） */
    val enName: String,

    /** 中文名称 */
    val cnName: String,

    /** 类别 */
    val category: String = "病害",

    /** 描述 */
    val description: String = "",

    /** 症状列表 */
    val symptoms: List<String> = emptyList(),

    /** 危害等级: 低/中/高 */
    val dangerLevel: String = "中",

    /** 发病原因 */
    val causes: String = "",

    /** 防治方法 */
    val prevention: String = "",

    /** 推荐药剂 */
    val pesticides: List<String> = emptyList(),

    /** 图片资源路径（预留） */
    val imageUrl: String = ""
)
