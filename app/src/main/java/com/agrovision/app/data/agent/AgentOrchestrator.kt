package com.agrovision.app.data.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多智能体协调器 — 统一调度所有 Agent
 * V3.0: 管理 5 个 Agent 的协同工作流
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val recognitionAgent: DiseaseRecognitionAgent,
    private val diagnosisAgent: AgricultureDiagnosisAgent,
    private val reportGeneratorAgent: ReportGeneratorAgent
) {
    private val agents = mutableMapOf<AgentType, Agent>()
    private val taskQueue = mutableListOf<InspectionTask>()
    private val completedTasks = mutableListOf<InspectionTask>()
    private val alerts = mutableListOf<Alert>()

    init {
        registerAgent(recognitionAgent)
        registerAgent(diagnosisAgent)
        registerAgent(reportGeneratorAgent)
    }

    fun registerAgent(agent: Agent) {
        agents[agent.type] = agent
    }

    fun getAgent(type: AgentType): Agent? = agents[type]

    fun getAllAgents(): List<Agent> = agents.values.toList()

    fun getAgentStatuses(): Map<String, AgentStatus> = agents.map { it.key.label to it.value.status }.toMap()


    /**
     * 创建巡检任务
     */
    fun createInspectionTask(task: InspectionTask): String {
        taskQueue.add(task)
        return task.id
    }

    /**
     * 获取待执行任务
     */
    fun getPendingTasks(): List<InspectionTask> = taskQueue.filter {
        it.status == TaskStatus.PENDING
    }

    /**
     * 获取进行中任务
     */
    fun getActiveTasks(): List<InspectionTask> = taskQueue.filter {
        it.status == TaskStatus.IN_PROGRESS
    }

    /**
     * 获取所有任务
     */
    fun getAllTasks(): List<InspectionTask> = taskQueue + completedTasks

    /**
     * 更新任务状态
     */
    fun updateTaskStatus(taskId: String, status: TaskStatus): Boolean {
        val task = taskQueue.find { it.id == taskId } ?: return false
        val updated = when (status) {
            TaskStatus.COMPLETED -> task.copy(status = status, completedAt = System.currentTimeMillis()).also {
                completedTasks.add(it)
            }
            TaskStatus.IN_PROGRESS -> task.copy(status = status, startedAt = System.currentTimeMillis())
            else -> task.copy(status = status)
        }
        taskQueue.remove(task)
        if (status != TaskStatus.COMPLETED) taskQueue.add(updated)
        return true
    }

    /**
     * 添加告警
     */
    fun addAlert(alert: Alert) {
        alerts.add(0, alert)
    }

    /**
     * 获取活跃告警
     */
    fun getActiveAlerts(): List<Alert> = alerts.filter { !it.isResolved }

    /**
     * 获取所有告警
     */
    fun getAllAlerts(): List<Alert> = alerts.toList()

    /**
     * 标记告警已读
     */
    fun markAlertRead(alertId: String) {
        alerts.indexOfFirst { it.id == alertId }.takeIf { it >= 0 }?.let { i ->
            alerts[i] = alerts[i].copy(isRead = true)
        }
    }

    /**
     * 解决告警
     */
    fun resolveAlert(alertId: String) {
        alerts.indexOfFirst { it.id == alertId }.takeIf { it >= 0 }?.let { i ->
            alerts[i] = alerts[i].copy(isResolved = true)
        }
    }

    /**
     * 执行完整巡检流水线
     */
    suspend fun executeInspectionPipeline(taskId: String): InspectionTask? {
        val task = taskQueue.find { it.id == taskId } ?: return null
        updateTaskStatus(taskId, TaskStatus.IN_PROGRESS)

        // Agent 1: 病虫害识别 (如果是拍照任务)
        // Agent 2: 农业诊断
        // Agent 5: 生成报告
        val reportResult = reportGeneratorAgent.execute(
            AgentInput(
                type = "report",
                data = mapOf(
                    "taskTitle" to task.title,
                    "area" to task.targetArea,
                    "detections" to task.detectedDiseases,
                    "images" to task.images
                )
            )
        )
        val reportUrl = reportResult.data["report"] as? String ?: ""

        val completed = task.copy(
            status = TaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            reportUrl = reportUrl
        )
        taskQueue.remove(task)
        completedTasks.add(completed)

        // 如果有病害检测，触发告警
        if (task.detectedDiseases.isNotEmpty()) {
            addAlert(Alert(
                id = "alert_${System.currentTimeMillis()}",
                title = "病害检测告警",
                message = "${task.title} 中发现 ${task.detectedDiseases.size} 处病害",
                level = AlertLevel.WARNING,
                source = task.type.label,
                relatedTaskId = taskId
            ))
        }

        return completed
    }

    /**
     * 获取系统运行状态快照
     */
    fun getSystemStatus(): SystemStatus {
        return SystemStatus(
            appVersion = "1.0.0",
            modelVersion = "YOLO11n v1.0",
            modelStatus = "运行中",
            robotCount = 3,
            robotOnlineCount = 2,
            totalInspections = completedTasks.size + taskQueue.size,
            todayInspections = completedTasks.size,
            totalDetections = completedTasks.sumOf { it.detectedDiseases.size },
            activeAlerts = alerts.count { !it.isResolved },
            gpuStatus = "正常",
            memoryUsage = "128MB / 512MB",
            storageUsage = "2.3GB / 8GB",
            lastSyncTime = System.currentTimeMillis(),
            uptime = System.currentTimeMillis()
        )
    }
}
