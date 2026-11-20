package com.agrovision.app.data.agent

import com.agrovision.app.data.repository.DetectionRepository
import com.agrovision.app.data.repository.EncyclopediaRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent 1: 病虫害识别 Agent
 * 负责 YOLO 推理 + 百科匹配
 */
@Singleton
class DiseaseRecognitionAgent @Inject constructor(
    private val detectionRepository: DetectionRepository
) : Agent {

    override val name = "DiseaseRecognition"
    override val type = AgentType.DISEASE_RECOGNITION
    override var status = AgentStatus.IDLE

    override suspend fun initialize(): Boolean {
        status = AgentStatus.IDLE
        return true
    }

    override suspend fun execute(input: AgentInput): AgentOutput {
        status = AgentStatus.BUSY
        val start = System.currentTimeMillis()
        return try {
            val bitmap = input.data["bitmap"] as? android.graphics.Bitmap
            if (bitmap != null) {
                val results = detectionRepository.detect(bitmap)
                AgentOutput(
                    success = true,
                    data = mapOf("results" to results, "count" to results.size),
                    agentName = name,
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } else {
                AgentOutput(success = false, errors = listOf("No bitmap provided"), agentName = name)
            }
        } catch (e: Exception) {
            status = AgentStatus.ERROR
            AgentOutput(success = false, errors = listOf(e.message ?: "Unknown"), agentName = name)
        } finally {
            status = AgentStatus.IDLE
        }
    }

    override fun capabilities() = listOf("YOLO推理", "BoundingBox检测", "病虫百科匹配", "置信度评估")
}

/**
 * Agent 2: 农业诊断 Agent
 * 负责病害分析 + 防治建议生成
 */
@Singleton
class AgricultureDiagnosisAgent @Inject constructor(
    private val encyclopediaRepository: EncyclopediaRepository
) : Agent {

    override val name = "Diagnosis"
    override val type = AgentType.AGRICULTURE_DIAGNOSIS
    override var status = AgentStatus.IDLE

    override suspend fun initialize(): Boolean {
        status = AgentStatus.IDLE
        return true
    }

    override suspend fun execute(input: AgentInput): AgentOutput {
        status = AgentStatus.BUSY
        val start = System.currentTimeMillis()
        try {
            val diseaseName = input.data["diseaseName"] as? String ?: ""
            val confidence = (input.data["confidence"] as? Number)?.toFloat() ?: 0f

            val pestInfo = encyclopediaRepository.getByEnglishName(diseaseName)
            val diagnosis = generateDiagnosis(diseaseName, confidence, pestInfo?.description ?: "")

            status = AgentStatus.IDLE
            return AgentOutput(
                success = true,
                data = mapOf("diagnosis" to diagnosis),
                agentName = name,
                executionTimeMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            status = AgentStatus.ERROR
            return AgentOutput(success = false, errors = listOf(e.message ?: ""), agentName = name)
        }
    }


    private fun generateDiagnosis(disease: String, conf: Float, desc: String): Map<String, String> {
        return mapOf(
            "disease" to disease,
            "confidence" to "${(conf * 100).toInt()}%",
            "dangerLevel" to when {
                disease.contains("黄龙") || disease.contains("晚疫") || disease.contains("稻瘟") -> "高"
                disease.contains("蚜虫") || disease.contains("霜霉") -> "中"
                else -> "低"
            },
            "suggestion" to "请查看防控方案获取详细防治建议",
            "pesticides" to "根据病害类型推荐对应药剂"
        )
    }

    override fun capabilities() = listOf("病害分析", "防治建议", "农药推荐", "恢复周期预估")
}

/**
 * Agent 5: 报告生成 Agent
 * 自动生成巡检报告
 */
@Singleton
class ReportGeneratorAgent : Agent {

    override val name = "ReportGenerator"
    override val type = AgentType.REPORT_GENERATOR
    override var status = AgentStatus.IDLE

    override suspend fun initialize(): Boolean = true

    override suspend fun execute(input: AgentInput): AgentOutput {
        status = AgentStatus.BUSY
        val start = System.currentTimeMillis()

        val taskTitle = input.data["taskTitle"] as? String ?: "巡检任务"
        val detections = input.data["detections"] as? List<String> ?: emptyList()
        val images = input.data["images"] as? List<String> ?: emptyList()
        val area = input.data["area"] as? String ?: ""

        val report = buildString {
            appendLine("# 智慧农业巡检报告")
            appendLine()
            appendLine("**任务名称：** $taskTitle")
            appendLine("**巡检区域：** ${area.ifEmpty { "未指定" }}")
            appendLine("**巡检时间：** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine("**检测数量：** ${detections.size} 处")
            appendLine()
            appendLine("## 检测结果")
            detections.forEachIndexed { i, d -> appendLine("${i + 1}. $d") }
            appendLine()
            appendLine("## 图片记录")
            appendLine("共采集 ${images.size} 张现场图片")
            appendLine()
            appendLine("## 结论与建议")
            appendLine("根据巡检数据分析，建议加强该区域的病虫害监控和防治措施。")
            appendLine()
            appendLine("---")
            appendLine("*报告由 AgroVision V3.0 自动生成*")
        }

        status = AgentStatus.IDLE
        return AgentOutput(
            success = true,
            data = mapOf("report" to report, "format" to "markdown"),
            agentName = name,
            executionTimeMs = System.currentTimeMillis() - start
        )
    }

    override fun capabilities() = listOf("巡检报告生成", "数据分析总结", "图表生成", "PDF导出")
}
