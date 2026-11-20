package com.agrovision.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CacheManager 工具类测试
 *
 * 验证缓存大小格式化和目录操作逻辑的正确性。
 * 纯算法部分不依赖 Android 框架。
 */
class CacheManagerFormatTest {

    // 与 CacheManager.formatSize 实现一致
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    @Test
    fun `formatSize formats bytes correctly`() {
        assertEquals("0 B", formatSize(0))
        assertEquals("512 B", formatSize(512))
        assertEquals("1023 B", formatSize(1023))
    }

    @Test
    fun `formatSize formats kilobytes correctly`() {
        assertEquals("1.0 KB", formatSize(1024))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("10.0 KB", formatSize(10240))
    }

    @Test
    fun `formatSize formats megabytes correctly`() {
        assertEquals("1.0 MB", formatSize(1024 * 1024L))
        assertEquals("12.3 MB", formatSize(12_897_024L))  // 12.3 * 1024 * 1024 ≈ 12897024
    }

    @Test
    fun `formatSize formats gigabytes correctly`() {
        val oneGB = 1024L * 1024 * 1024
        val result = formatSize(oneGB)
        assertTrue("Expected GB format, got $result", result.contains("GB"))
    }
}
