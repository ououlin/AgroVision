package com.agrovision.app.data.local.datastore

import com.agrovision.app.data.llm.LlmConfig
import com.agrovision.app.data.llm.LlmProviderType
import java.util.UUID

/**
 * AI Provider 配置
 *
 * 持久化于 DataStore（JSON 序列化）。新增/删除/修改后会被写入磁盘，重启 App 不丢失。
 */
data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    val isDefault: Boolean = false
) {
    /**
     * 将设置页 Provider 配置转换为运行时 [LlmConfig]。
     *
     * - provider 类型由 [id] 推断（deepseek/qwen/openai/gemini/ollama/custom）
     * - apiKey、baseUrl 由用户在设置页填写
     * - 默认模型取 [models] 首项
     * - 端点规范化（自动补 /chat/completions 或映射到 Gemini/Ollama 专用接口）由 [LlmConfig.fromAppSettings] 统一处理
     */
    fun toLlmConfig(selectedModel: String = models.firstOrNull() ?: ""): LlmConfig {
        val provider = LlmProviderType.fromKey(id)
        // 复用 LlmConfig 的端点规范化逻辑：构造一个临时 AppSettings 走 fromAppSettings
        val settings = AppSettings(
            aiProvider = id,
            apiKey = apiKey,
            baseUrl = baseUrl,
            aiModel = selectedModel
        )
        return LlmConfig.fromAppSettings(settings)
    }
}

/** 内置默认 Provider 列表（首次启动 / 重置时使用） */
fun defaultProviders(): List<AiProviderConfig> = listOf(
    AiProviderConfig(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
        isDefault = true
    ),
    AiProviderConfig(
        id = "qwen",
        name = "Qwen (通义千问)",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        models = listOf("qwen-max", "qwen-plus", "qwen-turbo")
    ),
    AiProviderConfig(
        id = "openai",
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4o", "gpt-4.1", "gpt-4-turbo")
    ),
    AiProviderConfig(
        id = "gemini",
        name = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        models = listOf("gemini-2.0-flash", "gemini-2.0-pro")
    ),
    AiProviderConfig(
        id = "ollama",
        name = "Ollama (本地)",
        baseUrl = "http://192.168.1.100:11434",
        models = listOf("llama3", "qwen2.5", "mistral")
    )
)
