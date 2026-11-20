package com.agrovision.app.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.dao.FeedbackDao
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.util.CacheManager
import com.agrovision.app.util.LogCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DevCenterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val feedbackDao: FeedbackDao,
    private val cacheManager: CacheManager,
    private val logCollector: LogCollector,
    private val store: SettingsDataStore,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    // ===== API 测试 =====
    data class ApiTestState(
        val testing: Boolean = false,
        val result: String = "",
        val success: Boolean? = null
    )

    private val _apiTest = MutableStateFlow(ApiTestState())
    val apiTest: StateFlow<ApiTestState> = _apiTest.asStateFlow()

    /**
     * 真实测试 AI API 连通性。
     * 向指定 baseUrl 发送一个最小的 chat completions 请求，返回状态码与延迟。
     */
    fun testApiConnection(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            _apiTest.value = ApiTestState(testing = true)
            val start = System.currentTimeMillis()
            try {
                val url = baseUrl.trimEnd('/') + "/chat/completions"
                val json = """{"model":"$model","messages":[{"role":"user","content":"ping"}],"max_tokens":1}"""
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                val result = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute().use { resp ->
                        val body = resp.body?.string()?.take(500) ?: ""
                        val latency = System.currentTimeMillis() - start
                        Triple(resp.code, body, latency)
                    }
                }
                val (code, body, latency) = result
                val success = code in 200..299
                _apiTest.value = ApiTestState(
                    testing = false,
                    success = success,
                    result = buildString {
                        appendLine("HTTP $code · ${latency}ms")
                        appendLine("URL: $url")
                        appendLine("Model: $model")
                        appendLine("Body: $body")
                    }
                )
                Timber.i("API 测试完成: code=$code latency=${latency}ms")
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                _apiTest.value = ApiTestState(
                    testing = false,
                    success = false,
                    result = "连接失败 (${latency}ms): ${e.javaClass.simpleName}: ${e.message}"
                )
                Timber.w(e, "API 测试失败")
            }
        }
    }

    // ===== 日志 =====
    private val _logs = MutableStateFlow<List<LogCollector.LogEntry>>(emptyList())
    val logs: StateFlow<List<LogCollector.LogEntry>> = _logs.asStateFlow()

    fun refreshLogs() {
        _logs.value = logCollector.entries
    }

    fun clearLogs() {
        logCollector.clear()
        refreshLogs()
    }

    /** 导出日志到缓存文件，返回文件路径 */
    suspend fun exportLogs(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            val file = File(dir, "agrovision_log_${System.currentTimeMillis()}.txt")
            file.writeText(logCollector.export())
            Timber.i("日志已导出: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Timber.e(e, "日志导出失败")
            null
        }
    }

    // ===== 数据库统计 =====
    data class DbStats(
        val historyCount: Int = 0,
        val feedbackCount: Int = 0,
        val feedbackAccuracy: Float = -1f
    )

    private val _dbStats = MutableStateFlow(DbStats())
    val dbStats: StateFlow<DbStats> = _dbStats.asStateFlow()

    fun refreshDbStats() {
        viewModelScope.launch {
            _dbStats.value = DbStats(
                historyCount = historyDao.getCount(),
                feedbackCount = feedbackDao.getCount(),
                feedbackAccuracy = feedbackDao.getAccuracy()
            )
        }
    }

    // ===== 模型信息 =====
    data class ModelInfo(
        val name: String,
        val exists: Boolean,
        val sizeText: String,
        val path: String
    )

    private val _modelInfo = MutableStateFlow<List<ModelInfo>>(emptyList())
    val modelInfo: StateFlow<List<ModelInfo>> = _modelInfo.asStateFlow()

    fun refreshModelInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                val files = mutableListOf<ModelInfo>()
                // assets 模型
                listOf("mobilenetv3_plant_disease.onnx", "yolo11n.tflite", "labels.txt").forEach { name ->
                    val exists = try { context.assets.open(name).use { it.available() } > 0 } catch (_: Exception) { false }
                    files.add(ModelInfo(name = name, exists = exists, sizeText = if (exists) "assets" else "未找到", path = "assets://$name"))
                }
                // 缓存目录模型
                val modelDir = File(context.cacheDir, "model")
                if (modelDir.exists()) {
                    modelDir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            files.add(ModelInfo(f.name, true, formatSize(f.length()), f.absolutePath))
                        }
                    }
                }
                files
            }
            _modelInfo.value = info
        }
    }

    // ===== 诊断报告 =====
    private val _diagnosticReport = MutableStateFlow<String?>(null)
    val diagnosticReport: StateFlow<String?> = _diagnosticReport.asStateFlow()

    /** 生成诊断报告并导出为文件 */
    suspend fun exportDiagnosticReport(): File? = withContext(Dispatchers.IO) {
        val report = buildString {
            appendLine("===== AgroVision 诊断报告 =====")
            appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine()
            appendLine("== 数据库统计 ==")
            appendLine("历史记录: ${dbStats.value.historyCount} 条")
            appendLine("反馈: ${dbStats.value.feedbackCount} 条")
            appendLine("准确率: ${if (dbStats.value.feedbackAccuracy < 0) "暂无数据" else "%.1f%%".format(dbStats.value.feedbackAccuracy * 100)}")
            appendLine()
            appendLine("== 缓存大小 ==")
            cacheManager.calculateCacheSizes().forEach { (k, v) -> appendLine("$k: $v") }
            appendLine()
            appendLine("== 模型文件 ==")
            modelInfo.value.forEach { appendLine("${it.name} (${it.sizeText}) - ${if (it.exists) "存在" else "缺失"}") }
            appendLine()
            appendLine("== 最近日志 (${logCollector.size()} 条) ==")
            appendLine(logCollector.export())
        }
        try {
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            val file = File(dir, "agrovision_diagnostic_${System.currentTimeMillis()}.txt")
            file.writeText(report)
            _diagnosticReport.value = report
            Timber.i("诊断报告已导出: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Timber.e(e, "诊断报告导出失败")
            null
        }
    }

    // ===== Mock 模式 =====
    private val _mockMode = MutableStateFlow(false)
    val mockMode: StateFlow<Boolean> = _mockMode.asStateFlow()

    fun toggleMockMode() {
        _mockMode.value = !_mockMode.value
        Timber.i("Mock 模式已${if (_mockMode.value) "开启" else "关闭"}")
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    }
}
