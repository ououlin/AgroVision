package com.agrovision.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存管理工具类
 *
 * 提供缓存大小计算、缓存清理、服务器连接测试功能。
 * 替代 SettingsViewModel 中的硬编码假数据。
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    /** 需要统计/清理的缓存目录名 → 显示名映射 */
    private val cacheDirs = mapOf(
        "image_cache" to "图片缓存",
        "ai_cache" to "AI缓存",
        "ocr_cache" to "OCR缓存",
        "logs" to "日志缓存",
        "model" to "模型缓存"
    )

    /**
     * 计算各缓存目录大小
     * @return 显示名 → 可读大小字符串（如 "12.3 MB"）
     */
    suspend fun calculateCacheSizes(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        for ((dirName, displayName) in cacheDirs) {
            val dir = File(context.cacheDir, dirName)
            val size = if (dir.exists()) dirSize(dir) else 0L
            result[displayName] = formatSize(size)
        }
        result
    }

    /**
     * 清空所有缓存目录
     * @return 清理后的各目录大小
     */
    suspend fun clearCache(): Map<String, String> = withContext(Dispatchers.IO) {
        for ((dirName, _) in cacheDirs) {
            val dir = File(context.cacheDir, dirName)
            if (dir.exists()) {
                deleteDir(dir)
            }
        }
        calculateCacheSizes()
    }

    /**
     * 测试服务器连接
     * @param url 服务器基础 URL
     * @return 延迟毫秒数，失败返回错误信息
     */
    suspend fun testServerConnection(url: String): ConnectionResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext ConnectionResult.Failed("请先配置服务器地址")
        }

        val healthUrl = if (url.endsWith("/")) "${url}api/health" else "$url/api/health"
        val request = Request.Builder().url(healthUrl).get().build()

        try {
            val start = System.currentTimeMillis()
            okHttpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    ConnectionResult.Success(elapsed)
                } else {
                    ConnectionResult.Failed("服务器返回 ${response.code}")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Server connection test failed")
            ConnectionResult.Failed("连接失败: ${e.message}")
        }
    }

    // ===== 内部工具 =====

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        var size = 0L
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) dirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDir(dir: File): Boolean {
        if (!dir.exists()) return true
        val files = dir.listFiles() ?: return dir.delete()
        var allDeleted = true
        for (file in files) {
            if (file.isDirectory) {
                allDeleted = deleteDir(file) && allDeleted
            } else {
                allDeleted = file.delete() && allDeleted
            }
        }
        return dir.delete() && allDeleted
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}

sealed class ConnectionResult {
    data class Success(val latencyMs: Long) : ConnectionResult()
    data class Failed(val message: String) : ConnectionResult()
}
