package com.agrovision.app.data.robot

/**
 * 机器人统一巡检接口
 * 支持: ROS2小车 / Go2-W / 乐聚机器人 / 无人机
 */
interface RobotController {

    val robotId: String
    val robotType: com.agrovision.app.data.agent.RobotType

    /** 连接机器人 */
    suspend fun connect(): Boolean

    /** 断开连接 */
    suspend fun disconnect()

    /** 是否已连接 */
    fun isConnected(): Boolean

    /** 发送巡航指令 */
    suspend fun cruiseTo(point: com.agrovision.app.data.agent.GeoPoint): Boolean

    /** 获取当前位置 */
    suspend fun getCurrentPosition(): com.agrovision.app.data.agent.GeoPoint?

    /** 拍照 */
    suspend fun captureImage(): ByteArray?

    /** 获取电池电量 */
    suspend fun getBatteryLevel(): Int

    /** 获取机器人状态 */
    suspend fun getStatus(): RobotStatus
}

data class RobotStatus(
    val isOnline: Boolean = false,
    val batteryLevel: Int = 0,
    val currentTask: String = "",
    val position: com.agrovision.app.data.agent.GeoPoint? = null,
    val speed: Float = 0f,
    val temperature: Float = 0f
)

/**
 * ROS2 小车模拟实现
 */
class Ros2CarController(
    override val robotId: String = "ROS2-001",
    override val robotType: com.agrovision.app.data.agent.RobotType = com.agrovision.app.data.agent.RobotType.ROS2_CAR
) : RobotController {

    private var connected = false
    private var currentPos = com.agrovision.app.data.agent.GeoPoint(39.9042, 116.4074, 0.0, "北京")

    override suspend fun connect(): Boolean { connected = true; return true }
    override suspend fun disconnect() { connected = false }
    override fun isConnected() = connected
    override suspend fun cruiseTo(point: com.agrovision.app.data.agent.GeoPoint): Boolean {
        currentPos = point; return true
    }
    override suspend fun getCurrentPosition() = currentPos
    override suspend fun captureImage(): ByteArray? = null
    override suspend fun getBatteryLevel() = 85
    override suspend fun getStatus() = RobotStatus(
        isOnline = connected, batteryLevel = 85, position = currentPos, speed = 0.5f, temperature = 35f
    )
}

/**
 * Go2-W 四足机器人模拟实现
 */
class Go2WController(
    override val robotId: String = "GO2W-001",
    override val robotType: com.agrovision.app.data.agent.RobotType = com.agrovision.app.data.agent.RobotType.GO2_W
) : RobotController {

    private var connected = false
    private var currentPos = com.agrovision.app.data.agent.GeoPoint(39.9042, 116.4074, 0.0, "北京")

    override suspend fun connect(): Boolean { connected = true; return true }
    override suspend fun disconnect() { connected = false }
    override fun isConnected() = connected
    override suspend fun cruiseTo(point: com.agrovision.app.data.agent.GeoPoint): Boolean {
        currentPos = point; return true
    }
    override suspend fun getCurrentPosition() = currentPos
    override suspend fun captureImage(): ByteArray? = null
    override suspend fun getBatteryLevel() = 72
    override suspend fun getStatus() = RobotStatus(
        isOnline = connected, batteryLevel = 72, position = currentPos, speed = 1.2f, temperature = 42f
    )
}

/**
 * 机器人管理器 — 统一管理所有机器人
 */
class RobotManager {

    private val robots = mutableMapOf<String, RobotController>()

    fun register(robot: RobotController) {
        robots[robot.robotId] = robot
    }

    fun getRobot(id: String): RobotController? = robots[id]

    fun getAllRobots(): List<RobotController> = robots.values.toList()

    fun getOnlineCount(): Int = robots.values.count { it.isConnected() }

    fun getTypes(): Map<com.agrovision.app.data.agent.RobotType, Int> =
        robots.values.groupBy { it.robotType }.mapValues { it.value.size }
}
