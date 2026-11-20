package com.agrovision.app.data.repository

import com.agrovision.app.data.llm.DiagnosisReportData
import com.agrovision.app.data.llm.LlmManager
import javax.inject.Singleton

/**
 * AI 诊断仓库 — 协调 LLM 诊断 + 百科数据匹配
 */
@Singleton
class DiagnosisRepository(
    private val llmManager: LlmManager,
    private val encyclopediaRepository: EncyclopediaRepository
) {

    /**
     * 生成完整的 AI 诊断报告
     */
    suspend fun generateReport(
        diseaseName: String,
        confidence: Float,
        imagePath: String = ""
    ): Result<DiagnosisReportData> {
        // 补充百科数据作为上下文（兼容中文名和英文名调用）
        val pestInfo = encyclopediaRepository.getByEnglishName(diseaseName)
            ?: encyclopediaRepository.getByChineseName(diseaseName)
        val context = pestInfo?.let {
            buildString {
                append("类别：${it.category} | 危险等级：${it.dangerLevel}")
                if (it.symptoms.isNotEmpty()) append(" | 症状：${it.symptoms.joinToString("；")}")
                if (it.causes.isNotEmpty()) append(" | 病因：${it.causes}")
                if (it.prevention.isNotEmpty()) append(" | 防治：${it.prevention}")
                if (it.pesticides.isNotEmpty()) append(" | 推荐药剂：${it.pesticides.joinToString("、")}")
            }
        } ?: ""

        return llmManager.generateDiagnosis(diseaseName, confidence, context)
    }

    /**
     * 检查是否在线模式
     */
    fun isOnline(): Boolean = llmManager.isOnlineMode()
}
