package com.agrovision.app.data.llm

/**
 * LLM 统一接口 — 所有大模型提供商必须实现
 */
interface LlmProvider {

    /** 发送对话消息，返回 AI 回复文本 */
    suspend fun chat(
        messages: List<LlmMessage>,
        config: LlmConfig
    ): Result<String>

    /** 生成农业诊断报告 */
    suspend fun generateDiagnosis(
        diseaseName: String,
        confidence: Float,
        context: String = ""
    ): Result<DiagnosisReportData>

    /** 检索知识库并回答问题（RAG模式） */
    suspend fun ragQuery(
        query: String,
        relevantDocs: List<String>
    ): Result<String>
}

/**
 * 统一的 LLM 消息格式
 */
data class LlmMessage(
    val role: Role,
    val content: String
) {
    enum class Role(val value: String) {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant")
    }
}

/**
 * AI 诊断报告数据
 */
data class DiagnosisReportData(
    val diseaseName: String,
    val diseaseNameEn: String,
    val dangerLevel: String,
    val analysis: String,
    val causes: String,
    val prevention: String,
    val pesticides: List<String>,
    val recoveryPeriod: String,
    val rawMarkdown: String
)
