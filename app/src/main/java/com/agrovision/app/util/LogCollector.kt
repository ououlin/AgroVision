package com.agrovision.app.util

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志采集器 — 自定义 Timber Tree，将日志缓存到内存环形缓冲区。
 *
 * 用于开发者中心"日志查看/导出/清除"功能，替代硬编码假日志。
 * 最多保留 [MAX_LOGS] 条，超出后丢弃最旧。
 */
@Singleton
class LogCollector @Inject constructor() : Timber.Tree() {

    private val deque = ConcurrentLinkedDeque<LogEntry>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    val entries: List<LogEntry> get() = deque.toList()

    @Synchronized
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            android.util.Log.ASSERT -> "A"
            else -> "?"
        }
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            time = dateFormat.format(Date()),
            level = level,
            tag = tag ?: "App",
            message = message + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        )
        deque.addLast(entry)
        while (deque.size > MAX_LOGS) deque.pollFirst()
    }

    /** 清空缓存的日志 */
    @Synchronized
    fun clear() = deque.clear()

    /** 导出全部日志为可读文本 */
    fun export(): String = buildString {
        deque.forEach { e ->
            append("${e.time} ${e.level}/${e.tag}: ${e.message}\n")
        }
    }

    /** 当前日志条数 */
    fun size(): Int = deque.size

    data class LogEntry(
        val timestamp: Long,
        val time: String,
        val level: String,
        val tag: String,
        val message: String
    )

    companion object {
        const val MAX_LOGS = 500
    }
}
