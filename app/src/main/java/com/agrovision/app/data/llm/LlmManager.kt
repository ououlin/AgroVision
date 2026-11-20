package com.agrovision.app.data.llm

import com.agrovision.app.data.local.datastore.AppSettings
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.rag.KnowledgeBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Singleton

/**
 * LLM 管理器 — 统一调度多个 LLM Provider
 * 支持：DeepSeek / Qwen / OpenAI / Gemini / Ollama
 * 带离线降级能力
 *
 * 配置链路：
 * - 启动时 [init] 同步读取一次 [SettingsDataStore] 持久化配置，初始化 [activeConfig]
 * - 设置页通过 [updateConfig] / [updateApiKey] / [updateFromSettings] 实时同步
 * - [isModelAvailable] 统一判断云端模型或 Ollama 本地模型是否可用
 */
@Singleton
class LlmManager(
    private val deepSeekProvider: DeepSeekProvider,
    private val localKnowledgeBase: KnowledgeBase,
    private val settingsDataStore: SettingsDataStore
) {
    @Volatile
    private var activeConfig: LlmConfig = loadInitialConfig()

    private fun loadInitialConfig(): LlmConfig = try {
        runBlocking {
            val settings = settingsDataStore.settings.first()
            LlmConfig.fromAppSettings(settings)
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to load LLM config from DataStore, fallback to default")
        LlmConfig.defaultDeepSeek()
    }

    /**
     * 整体替换配置（设置页 provider/model/baseUrl/apiKey 全部变更时调用）
     */
    fun updateConfig(config: LlmConfig) {
        activeConfig = config
        Timber.i("LlmManager config updated: provider=${config.provider}, model=${config.modelName}, hasKey=${config.apiKey.isNotBlank()}")
    }

    /**
     * 从 [AppSettings] 同步配置（设置页单项修改后调用）
     */
    fun updateFromSettings(settings: AppSettings) {
        updateConfig(LlmConfig.fromAppSettings(settings))
    }

    /**
     * 仅更新 API Key
     */
    fun updateApiKey(key: String) {
        activeConfig = activeConfig.copy(apiKey = key)
    }

    /**
     * 获取当前配置（不可变副本）
     */
    fun getConfig(): LlmConfig = activeConfig

    /**
     * AI 聊天 — 带 RAG 检索和离线降级
     *
     * RAG 注入优化：
     * 1. 检索结果做相关性过滤，丢弃低分文档
     * 2. 长文档智能截断（保留标题+症状+防治，省略冗余描述）
     * 3. 动态控制上下文长度，避免超出模型 token 限制
     */
    suspend fun chat(
        userMessage: String,
        history: List<Pair<String, Boolean>>,
        additionalSystemContext: List<String> = emptyList()
    ): Result<String> {
        val config = activeConfig
        val modelAvailable = isModelAvailable(config)
        val ragQuery = buildRagQuery(userMessage, history)

        // 仅对农业问题检索知识库，避免日常聊天被无关病虫害资料污染。
        val relevantDocs = if (config.enableRag && ragQuery != null) {
            val rawDocs = localKnowledgeBase.search(ragQuery, topK = 5)
            // 智能截断长文档，控制总上下文长度
            rawDocs.map { doc -> summarizeDoc(doc) }
                .take(3)  // 最终注入不超过3条
        } else emptyList()

        // 2. 构建消息列表
        val messages = buildList {
            add(LlmMessage(LlmMessage.Role.SYSTEM, config.systemPrompt))

            additionalSystemContext
                .filter { it.isNotBlank() }
                .forEach { context ->
                    add(LlmMessage(LlmMessage.Role.SYSTEM, context))
                }

            // 注入知识库内容（精简格式）
            if (relevantDocs.isNotEmpty()) {
                val context = relevantDocs.joinToString("\n\n---\n\n") { it }
                add(LlmMessage(LlmMessage.Role.SYSTEM,
                    "以下是农智眼知识库中的相关病虫害资料，回答时优先参考：\n\n$context\n\n" +
                    "回答要求：\n" +
                    "1. 如果用户问题与上述资料相关，基于资料内容给出专业回答\n" +
                    "2. 资料中未涉及的内容，可结合你的农业知识补充，但需注明\n" +
                    "3. 涉及用药推荐时，必须注明安全间隔期"))
            }

            // 历史对话（动态长度：根据是否有知识库上下文调整）
            val contextBudget = (config.contextLength * 3).coerceIn(6_000, 48_000)
            takeHistoryWithinBudget(history, contextBudget).forEach { (msg, isUser) ->
                add(LlmMessage(
                    if (isUser) LlmMessage.Role.USER else LlmMessage.Role.ASSISTANT,
                    msg
                ))
            }

            // 当前问题
            add(LlmMessage(LlmMessage.Role.USER, userMessage))
        }

        // 3. 调用已配置的大模型，或降级到本地引擎
        return if (modelAvailable) {
            deepSeekProvider.chat(messages, config).fold(
                onSuccess = { Result.success(it) },
                onFailure = { error ->
                    Timber.w(error, "Online chat failed, falling back to local agricultural assistant")
                    Result.success(deepSeekProvider.localReply(userMessage, relevantDocs))
                }
            )
        } else {
            Result.success(deepSeekProvider.localReply(userMessage, relevantDocs))
        }
    }

    private fun buildRagQuery(
        userMessage: String,
        history: List<Pair<String, Boolean>>
    ): String? {
        if (localKnowledgeBase.isAgriculturalQuery(userMessage)) return userMessage

        val previousAgriculturalQuestion = history.asReversed()
            .firstOrNull { (message, isUser) ->
                isUser && localKnowledgeBase.isAgriculturalQuery(message)
            }
            ?.first
        val isFollowUp = listOf(
            "怎么治", "怎么办", "为什么", "怎么用", "用什么药",
            "如何预防", "还有呢", "具体点", "详细说", "它", "这个"
        ).any { userMessage.contains(it) }

        return if (isFollowUp && previousAgriculturalQuestion != null) {
            "$previousAgriculturalQuestion $userMessage"
        } else {
            null
        }
    }

    private fun takeHistoryWithinBudget(
        history: List<Pair<String, Boolean>>,
        characterBudget: Int
    ): List<Pair<String, Boolean>> {
        var used = 0
        val selected = ArrayDeque<Pair<String, Boolean>>()
        for (message in history.asReversed()) {
            if (selected.size >= 20 || used + message.first.length > characterBudget) break
            selected.addFirst(message)
            used += message.first.length
        }
        return selected.toList()
    }

    /**
     * 文档智能截断 — 保留核心信息，省略冗余描述
     *
     * 策略：保留标题、类别、危险等级、症状、防治方法、药剂
     * 截断：过长的描述和原因说明（超过 500 字符时截断）
     */
    private fun summarizeDoc(doc: String): String {
        val maxLen = 600
        if (doc.length <= maxLen) return doc

        // 保留 Markdown 结构化段落，截断过长的连续文本
        val lines = doc.lines()
        val result = StringBuilder()
        for (line in lines) {
            if (result.length + line.length > maxLen) {
                result.appendLine("...")
                break
            }
            result.appendLine(line)
        }
        return result.toString().trim()
    }

    /**
     * 生成 AI 诊断报告
     */
    suspend fun generateDiagnosis(
        diseaseName: String,
        confidence: Float,
        context: String = ""
    ): Result<DiagnosisReportData> {
        return deepSeekProvider.generateDiagnosis(diseaseName, confidence, context, activeConfig)
    }

    /** 检查当前云端或本地大模型配置是否可调用。 */
    fun isModelAvailable(): Boolean = isModelAvailable(activeConfig)

    fun isOnlineMode(): Boolean =
        isModelAvailable() && activeConfig.provider != LlmProviderType.OLLAMA

    fun isLocalModelMode(): Boolean =
        isModelAvailable() && activeConfig.provider == LlmProviderType.OLLAMA

    private fun isModelAvailable(config: LlmConfig): Boolean =
        config.apiEndpoint.isNotBlank() &&
            config.modelName.isNotBlank() &&
            (config.provider == LlmProviderType.OLLAMA || config.apiKey.isNotBlank())

    /**
     * 获取当前提供商名称
     */
    fun getProviderName(): String = activeConfig.provider.displayName
}
