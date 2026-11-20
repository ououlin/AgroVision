package com.agrovision.app.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.llm.LlmConfig
import com.agrovision.app.data.llm.LlmManager
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.data.repository.HistoryRepository
import com.agrovision.app.data.repository.PlotRepository
import com.agrovision.app.data.repository.TreatmentRepository
import com.agrovision.app.domain.advisor.FarmProfileGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class ChatMessage(
    val id: Int = nextId(),
    val content: String,
    val isUser: Boolean = true,
    val isMarkdown: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    /** 该消息是否可重试（仅用户消息标记） */
    val retryable: Boolean = false
) {
    companion object {
        private val idCounter = AtomicInteger(1)
        fun nextId(): Int = idCounter.getAndIncrement()
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isAiTyping: Boolean = false,
    val isModelReady: Boolean = false,
    val modeLabel: String = "离线助手",
    val modelName: String = "离线模式",
    /** 最近一次错误信息（用于显示重试按钮），null 表示无错误 */
    val lastError: ChatError? = null,
    /** 动态推荐的快捷问题 — 根据对话上下文 + 农场档案生成 */
    val suggestedQuestions: List<String> = defaultQuestions()
) {
    companion object {
        /** 默认快捷问题 — 无对话历史 + 无农场档案时显示 */
        fun defaultQuestions(): List<String> = listOf(
            "苹果黑星病怎么防治？",
            "果树什么时候施肥最好？",
            "叶子发黄是什么原因？",
            "农药安全间隔期怎么算？"
        )
    }
}

/** 聊天错误类型，用于区分网络错误与 API Key 错误，提供精准提示 */
sealed class ChatError {
    /** 网络异常（无连接、超时、DNS 失败等） */
    data class Network(val message: String) : ChatError()
    /** API Key 未配置或无效（401/403） */
    data class AuthKey(val message: String) : ChatError()
    /** 其他服务端错误 */
    data class Server(val message: String) : ChatError()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmManager: LlmManager,
    private val settingsDataStore: SettingsDataStore,
    private val knowledgeBase: com.agrovision.app.data.rag.KnowledgeBase,
    // ===== V2.0 农场档案摘要注入 =====
    private val plotRepository: PlotRepository,
    private val treatmentRepository: TreatmentRepository,
    private val historyRepository: HistoryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 历史记录（用于多轮对话，限 20 条）
    private val chatHistory = mutableListOf<Pair<String, Boolean>>()
    private val maxHistory = 20

    // 最近一条待重试的用户消息
    private var lastFailedUserMessage: String? = null
    private var lastInitialQuestion: String? = null

    // V2.0: 农场档案摘要（懒加载：首次进入聊天异步计算一次，后续每10轮刷新）
    private var farmSummaryCache: FarmProfileGenerator.FarmSummary? = null
    private var farmSummaryDirty = true  // 首次需要计算

    init {
        refreshStatus()
        // V2.0: 用 async 启动摘要计算，拿到 Deferred，供 loadHistory 结构化并发 await
        val summaryDeferred = viewModelScope.async(kotlinx.coroutines.Dispatchers.Default) {
            refreshFarmSummaryIfNeeded(force = true)
        }
        loadHistory(summaryDeferred)
    }

    /**
     * （懒）刷新农场档案摘要。
     * V2.0 重构：不再写副作用到 cache 再让调用端轮询，而是**返回结果**供 async/await 结构化并发使用；
     * 同时仍写入 cache 作为后续懒加载用（dirty 机制保留）。
     */
    private suspend fun refreshFarmSummaryIfNeeded(force: Boolean = false): FarmProfileGenerator.FarmSummary? {
        if (!force && !farmSummaryDirty && farmSummaryCache != null) return farmSummaryCache
        return try {
            val userId = authRepository.getCurrentUser()?.userId ?: "guest_local"
            val plots = plotRepository.getPlotsOnce(userId)
            val treatments = treatmentRepository.getRecentTreatments(userId, limit = 200)
            val detections = historyRepository.getRecentHistory(limit = 200)
            val summary = FarmProfileGenerator.generate(plots, treatments, detections)
            farmSummaryCache = summary
            farmSummaryDirty = false
            Timber.d("农场档案摘要已刷新，plots=${plots.size} treatments=${treatments.size} detections=${detections.size}")
            summary
        } catch (e: Exception) {
            Timber.w(e, "刷新农场档案摘要失败")
            null
        }
    }

    /** 从 LlmManager 刷新在线状态与模型名 */
    private fun refreshStatus() {
        val modelReady = llmManager.isModelAvailable()
        val modeLabel = when {
            llmManager.isLocalModelMode() -> "本地模型"
            llmManager.isOnlineMode() -> "在线模型"
            else -> "离线助手"
        }
        val modelLabel = if (modelReady) {
            "${llmManager.getProviderName()} · ${llmManager.getConfig().modelName}"
        } else "本地知识库"

        _uiState.value = _uiState.value.copy(
            isModelReady = modelReady,
            modeLabel = modeLabel,
            modelName = modelLabel
        )
    }

    /**
     * 从持久化加载历史对话 — 同时根据农场档案个性化欢迎语与默认推荐问题
     * V2.0 重构：通过 [summaryDeferred] 结构化并发等待摘要结果，取代 while+delay 忙等轮询；
     * 最多等待 250ms，超时直接使用默认欢迎语。
     */
    private fun loadHistory(summaryDeferred: kotlinx.coroutines.Deferred<FarmProfileGenerator.FarmSummary?>) {
        viewModelScope.launch {
            // 结构化并发：最多等 250ms，拿不到就算（用默认欢迎语）
            val summary: FarmProfileGenerator.FarmSummary? = kotlinx.coroutines.withTimeoutOrNull(250) {
                summaryDeferred.await()
            }
            // 如果 250ms 内没等到，后续 cache 中会有，sendMessage 会再次读取

            val saved = settingsDataStore.loadChatHistory()
            if (saved.isEmpty()) {
                val welcome = buildWelcomeMessage(summary)
                val defaultSuggestions = summary?.suggestedQuestions
                    ?: ChatUiState.defaultQuestions()
                _uiState.value = _uiState.value.copy(
                    messages = listOf(
                        ChatMessage(
                            id = 0,
                            content = welcome,
                            isUser = false,
                            isMarkdown = true
                        )
                    ),
                    suggestedQuestions = defaultSuggestions
                )
            } else {
                chatHistory.clear()
                chatHistory.addAll(saved)
                val defaultSuggestions = summary?.suggestedQuestions
                    ?: ChatUiState.defaultQuestions()
                _uiState.value = _uiState.value.copy(
                    messages = saved.mapIndexed { idx, (content, isUser) ->
                        ChatMessage(
                            id = idx,
                            content = content,
                            isUser = isUser,
                            isMarkdown = !isUser
                        )
                    },
                    suggestedQuestions = defaultSuggestions
                )
            }
        }
    }

    /** 基于农场摘要生成个性化欢迎语 */
    private fun buildWelcomeMessage(summary: FarmProfileGenerator.FarmSummary?): String {
        val baseWelcome = if (summary == null || summary.plotOverview.contains("还没有")) {
            buildString {
                appendLine("你好，我是农智眼农技助手。")
                appendLine("")
                appendLine("我可以帮你：")
                appendLine("• 日常交流、解释问题、整理思路")
                appendLine("• 识别果园病虫害，提供防治方案")
                appendLine("• 解答果树种植、水肥、修剪问题")
                appendLine("• 解释常见防治思路和用药安全注意事项")
                appendLine("• 推荐果园季节性管理要点")
                appendLine("")
                append("新建地块并绑定识别/用药记录后，我将给出更贴合你农场的个性化建议。")
            }
        } else {
            buildString {
                appendLine("你好，我是你的农场农技助手。")
                appendLine("")
                appendLine("**农场档案**：")
                appendLine("• ${summary.plotOverview}")
                if (summary.cropBreakdown.isNotEmpty()) {
                    appendLine("• 作物分布：${summary.cropBreakdown.joinToString("，")}")
                }
                appendLine("• ${summary.treatmentSummary}")
                appendLine("")
                if (summary.todoList.isNotEmpty()) {
                    appendLine("**本周建议**：")
                    summary.todoList.take(3).forEach { appendLine("  * $it") }
                    appendLine("")
                }
                append("可以随时问我病虫害防治、水肥管理、用药方案、物候期操作等问题。")
            }
        }
        val modeNote = if (llmManager.isModelAvailable()) {
            "\n\n我会结合你的农场档案回答；涉及用药时，请同时核对产品标签和当地农技指导。"
        } else {
            "\n\n当前使用本地助手，支持基础交流和本地农业知识问答。"
        }
        return baseWelcome + modeNote
    }

    /** 持久化当前 chatHistory */
    private fun persistHistory() {
        viewModelScope.launch {
            settingsDataStore.saveChatHistory(chatHistory.toList())
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    /**
     * 发送消息 — V2.0 自动注入农场档案作为 AI 的 system 上下文
     */
    fun sendMessage() {
        if (_uiState.value.isAiTyping) return
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        doSend(text)
    }

    /**
     * 快捷问题直发 — 不修改输入框
     */
    fun sendQuickQuestion(question: String) {
        if (_uiState.value.isAiTyping) return
        doSend(question)
    }

    /** 诊断页等业务入口带入的问题，同一页面生命周期内只自动发送一次。 */
    fun startContextQuestion(question: String) {
        val normalized = question.trim()
        if (normalized.isEmpty() || normalized == lastInitialQuestion || _uiState.value.isAiTyping) return
        lastInitialQuestion = normalized
        doSend(normalized)
    }

    /** 实际发送逻辑 — V2.0 注入农场摘要 */
    private fun doSend(text: String) {
        lastFailedUserMessage = null
        _uiState.value = _uiState.value.copy(lastError = null)

        val userMessage = ChatMessage(content = text, isUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            isAiTyping = true
        )

        viewModelScope.launch {
            val historyForRequest = chatHistory.toList()
            refreshFarmSummaryIfNeeded()
            val summaryText = farmSummaryCache?.toSystemPromptText()
            val systemContext = summaryText?.let {
                listOf("以下是用户在本机维护的农场档案。仅在问题相关时使用，不要主动暴露无关信息：\n$it")
            }.orEmpty()
            val result = llmManager.chat(text, historyForRequest, systemContext)

            result.onSuccess { reply ->
                chatHistory.add(text to true)
                chatHistory.add(reply to false)
                trimHistory()
                val newSuggestions = generateSuggestions(text, reply)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatMessage(
                        content = reply,
                        isUser = false,
                        isMarkdown = true
                    ),
                    isAiTyping = false,
                    suggestedQuestions = newSuggestions
                )
                persistHistory()
                val completedRounds = chatHistory.count { (_, isUser) -> isUser }
                if (completedRounds > 0 && completedRounds % 10 == 0) {
                    farmSummaryDirty = true
                }
            }.onFailure { e ->
                val error = classifyError(e)
                lastFailedUserMessage = text
                _uiState.value = _uiState.value.copy(
                    isAiTyping = false,
                    lastError = error
                )
                Timber.w(e, "Chat failed: ${error::class.simpleName}")
            }
        }
    }

    /** 重试最近一条失败的消息 */
    fun retryLastMessage() {
        val failedText = lastFailedUserMessage ?: return
        if (_uiState.value.isAiTyping) return
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.dropLastWhile { it.content == failedText && it.isUser },
            lastError = null
        )
        doSend(failedText)
    }

    /** 清除错误状态 */
    fun clearError() {
        _uiState.value = _uiState.value.copy(lastError = null)
    }

    /** 历史记录上限管理 */
    private fun trimHistory() {
        while (chatHistory.size > maxHistory) {
            chatHistory.removeAt(0)
        }
    }

    /**
     * 根据当前对话上下文 + 农场档案 生成动态快捷问题
     */
    private fun generateSuggestions(userQuestion: String, aiReply: String): List<String> {
        if (!knowledgeBase.isAgriculturalQuery(userQuestion)) {
            return listOf(
                "叶子发黄是什么原因？",
                "果树什么时候施肥最好？",
                "打药时怎么避免产生抗药性？",
                "最近天气变化要注意什么？"
            )
        }
        val intent = knowledgeBase.classifyIntent(userQuestion)
        val pestNames = knowledgeBase.getAllPestNames().map { it.first }
        val mentionedPest = pestNames.firstOrNull { pest ->
            aiReply.contains(pest) || userQuestion.contains(pest)
        }

        val suggestions = mutableListOf<String>()

        if (mentionedPest != null) {
            when (intent) {
                com.agrovision.app.data.rag.KnowledgeBase.QueryIntent.TREATMENT,
                com.agrovision.app.data.rag.KnowledgeBase.QueryIntent.PESTICIDE -> {
                    suggestions.add("$mentionedPest 怎么预防？")
                    suggestions.add("$mentionedPest 是什么原因引起的？")
                    suggestions.add("$mentionedPest 用什么药效果最好？")
                }
                com.agrovision.app.data.rag.KnowledgeBase.QueryIntent.SYMPTOM,
                com.agrovision.app.data.rag.KnowledgeBase.QueryIntent.IDENTIFY -> {
                    suggestions.add("$mentionedPest 怎么治？")
                    suggestions.add("$mentionedPest 怎么预防？")
                    suggestions.add("$mentionedPest 用什么药？")
                }
                else -> {
                    suggestions.add("$mentionedPest 怎么治？")
                    suggestions.add("$mentionedPest 怎么预防？")
                    suggestions.add("$mentionedPest 用什么药？")
                }
            }
        } else {
            when (intent) {
                com.agrovision.app.data.rag.KnowledgeBase.QueryIntent.FERTILIZER -> {
                    suggestions.add("果树什么时候施基肥？")
                    suggestions.add("叶面肥怎么用？")
                    suggestions.add("缺素症怎么判断？")
                }
                com.agrovision.app.data.rag.KnowledgeBase.QueryIntent.WATERING -> {
                    suggestions.add("果树浇水的最佳时间？")
                    suggestions.add("雨季怎么排水防涝？")
                    suggestions.add("干旱时怎么灌溉？")
                }
                com.agrovision.app.data.rag.KnowledgeBase.QueryIntent.PRUNING -> {
                    suggestions.add("果树冬季修剪注意事项？")
                    suggestions.add("夏季修剪怎么做？")
                    suggestions.add("幼树怎么整形？")
                }
                else -> {
                    farmSummaryCache?.suggestedQuestions?.let {
                        return (suggestions + it).distinct().take(4)
                    }
                    return ChatUiState.defaultQuestions().shuffled().take(4)
                }
            }
        }

        farmSummaryCache?.suggestedQuestions?.firstOrNull()
            ?.takeIf { it !in suggestions }
            ?.let { suggestions.add(it) }

        if (suggestions.size < 4) {
            suggestions += ChatUiState.defaultQuestions().take(4 - suggestions.size)
        }

        return suggestions.take(4)
    }

    /**
     * 错误分类
     */
    private fun classifyError(e: Throwable): ChatError {
        return when (e) {
            is UnknownHostException, is IOException -> {
                ChatError.Network("网络连接异常，请检查网络后重试")
            }
            else -> {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized", ignoreCase = true) ->
                        ChatError.AuthKey("在线问答暂时不可用，请稍后重试")
                    msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ->
                        ChatError.Network("请求超时，请重试")
                    else -> ChatError.Server("AI 服务暂时不可用：$msg")
                }
            }
        }
    }

    /**
     * 切换 LLM 提供商（保留兼容）
     */
    fun switchProvider(config: LlmConfig) {
        llmManager.updateConfig(config)
        refreshStatus()
    }

    override fun onCleared() {
        super.onCleared()
        persistHistory()
    }
}
