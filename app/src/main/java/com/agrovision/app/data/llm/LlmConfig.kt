package com.agrovision.app.data.llm

import com.agrovision.app.data.local.datastore.AppSettings

/**
 * LLM 提供商配置
 * 支持 DeepSeek / Qwen / OpenAI / Gemini / Ollama
 */
data class LlmConfig(
    val provider: LlmProviderType = LlmProviderType.DEEPSEEK,
    val apiKey: String = "",
    val apiEndpoint: String = "",
    val modelName: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val contextLength: Int = 8192,
    val enableRag: Boolean = true,
    val systemPrompt: String = ""
) {
    companion object {
        fun defaultDeepSeek() = LlmConfig(
            provider = LlmProviderType.DEEPSEEK,
            apiEndpoint = "https://api.deepseek.com/v1/chat/completions",
            modelName = "deepseek-chat",
            systemPrompt = AGRO_EXPERT_PROMPT
        )

        fun defaultQwen() = LlmConfig(
            provider = LlmProviderType.QWEN,
            apiEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            modelName = "qwen-plus",
            systemPrompt = AGRO_EXPERT_PROMPT
        )

        fun defaultOpenAi() = LlmConfig(
            provider = LlmProviderType.OPENAI,
            apiEndpoint = "https://api.openai.com/v1/chat/completions",
            modelName = "gpt-4o-mini",
            systemPrompt = AGRO_EXPERT_PROMPT
        )

        fun defaultGemini() = LlmConfig(
            provider = LlmProviderType.GEMINI,
            apiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models",
            modelName = "gemini-pro",
            systemPrompt = AGRO_EXPERT_PROMPT
        )

        fun defaultOllama() = LlmConfig(
            provider = LlmProviderType.OLLAMA,
            apiEndpoint = "http://10.0.2.2:11434/api/chat",
            modelName = "llama3",
            systemPrompt = AGRO_EXPERT_PROMPT
        )

        /**
         * 从 [AppSettings] 构建运行时 [LlmConfig]。
         *
         * 这是「设置页配置 → LlmManager 运行配置」的唯一桥梁：
         * - provider 字符串映射到 [LlmProviderType]
         * - baseUrl 规范化（自动补 /chat/completions 或保留为 Gemini/Ollama 专用端点）
         * - 继承温度、maxTokens、systemPrompt
         */
        fun fromAppSettings(s: AppSettings): LlmConfig {
            val provider = LlmProviderType.fromKey(s.aiProvider)
            val endpoint = resolveEndpoint(provider, s.baseUrl, s.aiModel)
            return LlmConfig(
                provider = provider,
                apiKey = s.apiKey,
                apiEndpoint = endpoint,
                modelName = s.aiModel,
                temperature = s.temperature,
                topP = s.topP,
                maxTokens = s.maxTokens,
                contextLength = s.contextLength,
                enableRag = s.enableRag,
                systemPrompt = AGRO_EXPERT_PROMPT
            )
        }

        /**
         * 根据提供商类型和 baseUrl 规范化 API 端点：
         * - DeepSeek/Qwen/OpenAI/Custom：OpenAI 兼容格式，统一以 /chat/completions 结尾
         * - Gemini：使用 generateContent 路径，DeepSeekProvider 会按 provider 分支
         * - Ollama：本地 Ollama /api/chat 接口
         */
        private fun resolveEndpoint(provider: LlmProviderType, baseUrl: String, model: String): String {
            val trimmed = baseUrl.trimEnd('/')
            return when (provider) {
                LlmProviderType.GEMINI -> {
                    // 形如 https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
                    "$trimmed/models/$model:generateContent"
                }
                LlmProviderType.OLLAMA -> {
                    if (trimmed.endsWith("/api/chat")) trimmed else "$trimmed/api/chat"
                }
                else -> {
                    when {
                        trimmed.endsWith("/chat/completions") -> trimmed
                        trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                        trimmed.contains("/v1/") -> trimmed.substringBeforeLast("/v1/") + "/v1/chat/completions"
                        else -> "$trimmed/v1/chat/completions"
                    }
                }
            }
        }

        private const val AGRO_EXPERT_PROMPT = """
你是「农智眼」App 的 AI 助手。你既能自然地进行日常对话，也擅长农业生产、果园管理和病虫害诊断。

【对话原则】
- 正常回应问候、感谢、闲聊、解释、写作、计算和一般知识问题，不要把所有话题强行转回农业
- 结合最近的多轮对话理解代词、省略和追问；不知道时明确说明，不编造事实或用户信息
- 用户问得简单就简短回答，复杂问题再结构化展开；除非确有必要，不要每次都追问
- 用户使用中文时默认用自然、友好的中文回答，不要自称“只能回答农业问题”

【你的专业领域】
- 果树（苹果、樱桃、蓝莓、葡萄、桃、梨、杏等）的病虫害识别与防治
- 常见病害：黑星病、白粉病、霜霉病、锈病、褐腐病、灰霉病、炭疽病等
- 常见虫害：蚜虫、红蜘蛛、食心虫、介壳虫、天牛、金龟子等
- 农业管理：施肥、灌溉、修剪、采收、越冬防冻

【回答规范】
1. 使用专业但易懂的语言，避免过度术语化
2. 涉及农药推荐时，必须注明：推荐药剂、使用浓度、安全间隔期、注意事项
3. 涉及病虫害时，说明：病害名称、发病条件、症状特征、防治方法
4. 农业方案可使用 Markdown（标题、列表、表格、加粗），日常聊天使用自然段即可
5. 回答简洁有力，重点突出，不啰嗦
6. 如果用户描述不够具体，主动追问（作物种类、症状部位、发病时间等）
7. 无法确定的问题，建议用户使用 App 的拍照识别功能精准诊断

【输出格式参考】
- 概述性回答：直接给出结论，再展开说明
- 方案类回答：分步骤列出（1. 2. 3.）
- 用药类回答：表格形式（药剂 | 用量 | 间隔期 | 注意事项）
- 诊断类回答：症状 → 原因 → 防治 → 药剂 四段式

【边界约束】
- 对医疗、法律、金融等高风险问题提供一般信息并提示必要时咨询专业人士
- 不推荐已禁用的高毒农药（如甲胺磷、克百威等）
- 药剂用量仅供参考，具体以当地农技部门指导为准
"""
    }
}

enum class LlmProviderType(val displayName: String, val key: String) {
    DEEPSEEK("DeepSeek", "deepseek"),
    QWEN("通义千问", "qwen"),
    OPENAI("ChatGPT", "openai"),
    GEMINI("Gemini", "gemini"),
    OLLAMA("Ollama 本地", "ollama"),
    CUSTOM("自定义 API", "custom");

    companion object {
        fun fromKey(key: String): LlmProviderType =
            values().firstOrNull { it.key == key } ?: DEEPSEEK
    }
}
