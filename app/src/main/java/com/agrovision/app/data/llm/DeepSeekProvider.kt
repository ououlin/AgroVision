package com.agrovision.app.data.llm

import com.agrovision.app.data.diagnosis.LocalDiagnosisEngine
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Singleton

/**
 * DeepSeek Provider — 兼容 OpenAI Chat Completions API
 *
 * 使用注入的 OkHttpClient 发送请求，复用 DI 统一配置的超时、日志、拦截器。
 * 无 API Key 时自动降级到 [LocalDiagnosisEngine] 提供离线诊断。
 */
@Singleton
class DeepSeekProvider(
    private val gson: Gson,
    private val okHttpClient: OkHttpClient,
    private val localDiagnosisEngine: LocalDiagnosisEngine,
    private val defaultConfig: LlmConfig
) : LlmProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun localReply(query: String, relevantDocs: List<String>): String =
        localDiagnosisEngine.generateReply(query, relevantDocs)

    override suspend fun chat(
        messages: List<LlmMessage>,
        config: LlmConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (config.provider) {
                LlmProviderType.GEMINI -> chatGemini(messages, config)
                LlmProviderType.OLLAMA -> chatOllama(messages, config)
                else -> chatOpenAiCompatible(messages, config)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * OpenAI Chat Completions 兼容格式 — 适用于 DeepSeek / Qwen / OpenAI / Custom
     */
    private fun chatOpenAiCompatible(
        messages: List<LlmMessage>,
        config: LlmConfig
    ): Result<String> {
        val requestBody = mapOf(
            "model" to config.modelName,
            "messages" to messages.map {
                mapOf("role" to it.role.value, "content" to it.content)
            },
            "temperature" to config.temperature,
            "top_p" to config.topP,
            "max_tokens" to config.maxTokens
        )

        val jsonBody = gson.toJson(requestBody).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(config.apiEndpoint)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@use Result.failure(
                    Exception("API error: ${response.code} ${response.message}")
                )
            }

            val body = response.body?.string()
                ?: return@use Result.failure(Exception("Empty response body"))

            val result = gson.fromJson(body, ChatResponse::class.java)
            val content = result.choices.firstOrNull()?.message?.content.orEmpty().trim()
            if (content.isEmpty()) Result.failure(Exception("AI returned an empty response"))
            else Result.success(content)
        }
    }

    /**
     * Gemini generateContent 格式
     * - 端点：{baseUrl}/models/{model}:generateContent?key={apiKey}
     * - system 消息合并到 systemInstruction
     * - user/assistant 映射到 contents[].role = user/model
     */
    private fun chatGemini(
        messages: List<LlmMessage>,
        config: LlmConfig
    ): Result<String> {
        val systemMsg = messages
            .filter { it.role == LlmMessage.Role.SYSTEM }
            .joinToString("\n\n") { it.content }
        val dialogMsgs = messages.filter { it.role != LlmMessage.Role.SYSTEM }

        val contents = dialogMsgs.map { msg ->
            mapOf(
                "role" to if (msg.role == LlmMessage.Role.USER) "user" else "model",
                "parts" to listOf(mapOf("text" to msg.content))
            )
        }

        val requestBody = buildMap {
            if (systemMsg.isNotBlank()) {
                put("systemInstruction", mapOf(
                    "parts" to listOf(mapOf("text" to systemMsg))
                ))
            }
            put("contents", contents)
            put("generationConfig", mapOf(
                "temperature" to config.temperature,
                "topP" to config.topP,
                "maxOutputTokens" to config.maxTokens
            ))
        }

        val jsonBody = gson.toJson(requestBody).toRequestBody(jsonMediaType)
        val url = "${config.apiEndpoint}?key=${config.apiKey}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@use Result.failure(
                    Exception("Gemini API error: ${response.code} ${response.message}")
                )
            }

            val body = response.body?.string()
                ?: return@use Result.failure(Exception("Empty response body"))

            val geminiResp = gson.fromJson(body, GeminiResponse::class.java)
            val content = geminiResp.candidates
                ?.firstOrNull()
                ?.content?.parts?.joinToString("") { it.text ?: "" }.orEmpty().trim()
            if (content.isEmpty()) Result.failure(Exception("Gemini returned an empty response"))
            else Result.success(content)
        }
    }

    /**
     * Ollama /api/chat 格式 — OpenAI 兼容，但无需 Authorization 头
     */
    private fun chatOllama(
        messages: List<LlmMessage>,
        config: LlmConfig
    ): Result<String> {
        val requestBody = mapOf(
            "model" to config.modelName,
            "messages" to messages.map {
                mapOf("role" to it.role.value, "content" to it.content)
            },
            "stream" to false,
            "options" to mapOf(
                "temperature" to config.temperature,
                "top_p" to config.topP,
                "num_predict" to config.maxTokens
            )
        )

        val jsonBody = gson.toJson(requestBody).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(config.apiEndpoint)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@use Result.failure(
                    Exception("Ollama API error: ${response.code} ${response.message}")
                )
            }

            val body = response.body?.string()
                ?: return@use Result.failure(Exception("Empty response body"))

            val result = gson.fromJson(body, ChatResponse::class.java)
            val content = result.choices.firstOrNull()?.message?.content
                ?: gson.fromJson(body, OllamaResponse::class.java)?.message?.content
                ?: ""
            if (content.isBlank()) Result.failure(Exception("Ollama returned an empty response"))
            else Result.success(content.trim())
        }
    }

    override suspend fun generateDiagnosis(
        diseaseName: String,
        confidence: Float,
        context: String
    ): Result<DiagnosisReportData> = generateDiagnosis(diseaseName, confidence, context, defaultConfig)

    suspend fun generateDiagnosis(
        diseaseName: String,
        confidence: Float,
        context: String,
        config: LlmConfig
    ): Result<DiagnosisReportData> = withContext(Dispatchers.IO) {
        // 无可用大模型时降级到本地规则引擎；Ollama 不需要 API Key。
        if (!canCallModel(config)) {
            return@withContext Result.success(
                localDiagnosisEngine.generateDiagnosis(diseaseName, confidence, context)
            )
        }

        // 有 API Key 时调用 LLM 生成诊断，失败则降级到本地规则引擎
        val messages = listOf(
            LlmMessage(LlmMessage.Role.SYSTEM, config.systemPrompt),
            LlmMessage(LlmMessage.Role.USER, buildDiagnosisPrompt(diseaseName, confidence, context))
        )
        chat(messages, config).fold(
            onSuccess = { content ->
                Result.success(parseDiagnosisResponse(content, diseaseName, confidence))
            },
            onFailure = {
                Result.success(localDiagnosisEngine.generateDiagnosis(diseaseName, confidence, context))
            }
        )
    }

    override suspend fun ragQuery(
        query: String,
        relevantDocs: List<String>
    ): Result<String> = ragQuery(query, relevantDocs, defaultConfig)

    suspend fun ragQuery(
        query: String,
        relevantDocs: List<String>,
        config: LlmConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        // 无可用大模型时降级到本地规则引擎；Ollama 不需要 API Key。
        if (!canCallModel(config)) {
            return@withContext Result.success(localDiagnosisEngine.generateReply(query, relevantDocs))
        }

        // 有 API Key 时调用 LLM，失败则降级到本地规则引擎
        val context = relevantDocs.joinToString("\n\n---\n\n")
        val prompt = """
基于以下农业知识库内容，回答用户问题。

知识库内容：
$context

用户问题：$query

请基于知识库内容给出专业回答，如果知识库中没有相关信息，请根据你的农业知识补充。
        """.trimIndent()

        val messages = listOf(
            LlmMessage(LlmMessage.Role.SYSTEM, config.systemPrompt),
            LlmMessage(LlmMessage.Role.USER, prompt)
        )
        chat(messages, config).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.success(localDiagnosisEngine.generateReply(query, relevantDocs)) }
        )
    }

    // ===== Helpers =====

    private fun canCallModel(config: LlmConfig): Boolean =
        config.apiEndpoint.isNotBlank() &&
            config.modelName.isNotBlank() &&
            (config.provider == LlmProviderType.OLLAMA || config.apiKey.isNotBlank())

    private fun buildDiagnosisPrompt(diseaseName: String, confidence: Float, context: String): String =
        """请根据以下病虫害检测结果，生成一份专业的农业诊断报告：

病害名称：$diseaseName
识别置信度：${(confidence * 100).toInt()}%
${if (context.isNotEmpty()) "补充信息：$context" else ""}

请按照以下格式输出（用Markdown格式）：
## 病害分析
[详细分析]

## 发病原因
[列出主要原因]

## 防治方法
[具体的防治步骤]

## 推荐药剂
[列出2-4种药剂及使用说明]

## 预计恢复周期
[恢复时间预估]

## 农业建议
[日常管理建议]"""

    /** 将 LLM 返回的 Markdown 解析为结构化诊断报告 */
    private fun parseDiagnosisResponse(
        content: String, diseaseName: String, confidence: Float
    ): DiagnosisReportData {
        fun section(start: String, end: String): String =
            content.substringAfter(start, "")
                .substringBefore(end, "")
                .trim()
                .removePrefix("\n")

        val pesticidesRaw = section("## 推荐药剂", "## 预计恢复周期")
        val pesticides = pesticidesRaw.lines()
            .map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("•") }
            .map { it.removePrefix("-").removePrefix("•").trim() }
            .ifEmpty { listOf("详见报告") }

        return DiagnosisReportData(
            diseaseName = diseaseName,
            diseaseNameEn = "",
            dangerLevel = "详见报告",
            analysis = section("## 病害分析", "## 发病原因").ifEmpty { content },
            causes = section("## 发病原因", "## 防治方法"),
            prevention = section("## 防治方法", "## 推荐药剂"),
            pesticides = pesticides,
            recoveryPeriod = section("## 预计恢复周期", "## 农业建议"),
            rawMarkdown = content
        )
    }

    // ===== HTTP Response DTO =====
    data class ChatResponse(
        @SerializedName("choices") val choices: List<Choice> = emptyList()
    )
    data class Choice(
        @SerializedName("message") val message: MessageContent? = null
    )
    data class MessageContent(
        @SerializedName("content") val content: String = ""
    )

    /** Gemini generateContent 响应 */
    data class GeminiResponse(
        @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null
    )
    data class GeminiCandidate(
        @SerializedName("content") val content: GeminiContent? = null
    )
    data class GeminiContent(
        @SerializedName("parts") val parts: List<GeminiPart> = emptyList()
    )
    data class GeminiPart(
        @SerializedName("text") val text: String? = null
    )

    /** Ollama /api/chat 响应（非流式） */
    data class OllamaResponse(
        @SerializedName("message") val message: OllamaMessage? = null
    )
    data class OllamaMessage(
        @SerializedName("content") val content: String = ""
    )
}
