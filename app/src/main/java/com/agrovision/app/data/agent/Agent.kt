package com.agrovision.app.data.agent

/**
 * 多智能体系统 — Agent 统一接口
 * V3.0: 5个Agent协同工作
 */
interface Agent {

    /** Agent 唯一标识 */
    val name: String

    /** Agent 类型 */
    val type: AgentType

    /** Agent 状态 */
    val status: AgentStatus

    /** 执行任务，返回结果 */
    suspend fun execute(input: AgentInput): AgentOutput

    /** 初始化 Agent */
    suspend fun initialize(): Boolean

    /** 获取 Agent 能力描述 */
    fun capabilities(): List<String>
}

enum class AgentType(val label: String) {
    DISEASE_RECOGNITION("病虫害识别"),
    AGRICULTURE_DIAGNOSIS("农业诊断"),
    KNOWLEDGE_RETRIEVAL("知识检索"),
    INSPECTION_SCHEDULER("巡检调度"),
    REPORT_GENERATOR("报告生成")
}

enum class AgentStatus(val label: String) {
    IDLE("空闲"),
    BUSY("工作中"),
    ERROR("异常"),
    OFFLINE("离线")
}

data class AgentInput(
    val type: String = "",           // 输入类型: image / text / task
    val data: Map<String, Any?> = emptyMap(),  // 输入数据
    val metadata: Map<String, String> = emptyMap()
)

data class AgentOutput(
    val success: Boolean = true,
    val data: Map<String, Any?> = emptyMap(),
    val errors: List<String> = emptyList(),
    val agentName: String = "",
    val executionTimeMs: Long = 0L
)

/**
 * 巡检任务 — 机器人/无人机统一模型
 */
data class InspectionTask(
    val id: String = "",
    val title: String = "",
    val type: InspectionType = InspectionType.ROBOT_CRUISE,
    val status: TaskStatus = TaskStatus.PENDING,
    val assignee: RobotType = RobotType.GO2_W,
    val route: List<GeoPoint> = emptyList(),
    val targetArea: String = "",
    val cropType: String = "",
    val scheduledAt: Long = 0L,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val detectedDiseases: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val reportUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class InspectionType(val label: String) {
    ROBOT_CRUISE("机器人巡航"),
    DRONE_PATROL("无人机巡检"),
    MANUAL_INSPECTION("人工巡检")
}

enum class TaskStatus(val label: String) {
    PENDING("待执行"),
    IN_PROGRESS("执行中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    CANCELLED("已取消")
}

enum class RobotType(val label: String) {
    ROS2_CAR("ROS2小车"),
    GO2_W("Go2-W四足"),
    LEJU("乐聚机器人"),
    DRONE("无人机")
}

data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val label: String = ""
)

/**
 * 告警模型
 */
data class Alert(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val level: AlertLevel = AlertLevel.WARNING,
    val source: String = "",
    val isRead: Boolean = false,
    val isResolved: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val geoPoint: GeoPoint? = null,
    val relatedTaskId: String = ""
)

enum class AlertLevel(val label: String) {
    INFO("信息"),
    WARNING("警告"),
    CRITICAL("严重")
}

/**
 * 系统运行状态
 */
data class SystemStatus(
    val appVersion: String = "3.0.0",
    val modelVersion: String = "YOLO11n v1.0",
    val modelStatus: String = "运行中",
    val robotCount: Int = 0,
    val robotOnlineCount: Int = 0,
    val totalInspections: Int = 0,
    val todayInspections: Int = 0,
    val totalDetections: Int = 0,
    val activeAlerts: Int = 0,
    val gpuStatus: String = "正常",
    val memoryUsage: String = "--",
    val storageUsage: String = "--",
    val lastSyncTime: Long = 0L,
    val uptime: Long = 0L
)
