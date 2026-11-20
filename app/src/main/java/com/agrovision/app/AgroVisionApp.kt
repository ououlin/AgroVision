package com.agrovision.app

import android.app.Application
import android.os.Build
import android.os.Process
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.work.AdvisorSchedulerWorker
import com.agrovision.app.data.work.HarvestReminderWorker
import com.agrovision.app.util.LogCollector
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * AgroVision Application 入口
 * 使用 Hilt 进行依赖注入
 */
@HiltAndroidApp
class AgroVisionApp : Application(), Configuration.Provider {

    @Inject lateinit var logCollector: LogCollector
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initLogging()
        installCrashLogger()
        // ===== V2.0 周期调度：顾问物候+采收提醒 =====
        scheduleBackgroundWorkAfterPrivacyConsent()
    }

    private fun scheduleBackgroundWorkAfterPrivacyConsent() {
        applicationScope.launch {
            settingsDataStore.settings
                .filter { it.privacyAgreed }
                .first()
            AdvisorSchedulerWorker.schedule(this@AgroVisionApp)
            HarvestReminderWorker.schedule(this@AgroVisionApp)
        }
    }

    private fun initLogging() {
        // 始终 plant LogCollector 用于开发者中心日志查看
        Timber.plant(logCollector)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * 全局崩溃日志落盘。
     * 路径：/sdcard/Android/data/com.agrovision.app/files/crash/crash_yyyyMMdd_HHmmss.log
     * 上架排查 / 用户反馈时可通过「开发者中心 → 导出日志」或 adb pull 读取。
     */
    private fun installCrashLogger() {
        val dir = try {
            File(getExternalFilesDir(null), "crash").also { it.mkdirs() }
        } catch (t: Throwable) {
            Timber.w(t, "创建 crash 目录失败，退回 fileDir")
            File(filesDir, "crash").also { it.mkdirs() }
        }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val out = File(dir, "crash_$stamp.log")
                PrintWriter(FileWriter(out, true)).use { pw ->
                    pw.println("=== AgroVision Crash Report ===")
                    pw.println("time:       ${Date()}")
                    pw.println("version:    ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
                    pw.println("flavor:     ${BuildConfig.BUILD_TYPE}")
                    pw.println("device:     ${Build.MANUFACTURER} ${Build.MODEL}")
                    pw.println("android:    ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    pw.println("abi:        ${Build.SUPPORTED_ABIS.joinToString()}")
                    pw.println("package:    $packageName")
                    pw.println("pid:        ${Process.myPid()}")
                    pw.println("thread:     ${t.name} (id=${t.id}, prio=${t.priority})")
                    pw.println()
                    pw.println("--- stacktrace ---")
                    e.printStackTrace(pw)
                    var cause = e.cause
                    var depth = 0
                    while (cause != null && depth < 5) {
                        pw.println()
                        pw.println("--- cause $depth ---")
                        cause.printStackTrace(pw)
                        cause = cause.cause
                        depth++
                    }
                    pw.println()
                    pw.println("--- all threads ---")
                    for ((id, st) in Thread.getAllStackTraces()) {
                        pw.println("\nThread ${id.name} (id=${id.id}, prio=${id.priority}, state=${id.state})")
                        for (s in st) pw.println("\t$s")
                    }
                    pw.flush()
                }
                Timber.e(e, "检测到崩溃，日志已写入 %s", out.absolutePath)
            }.onFailure { Timber.wtf(it, "崩溃日志写入失败") }
            // 交给系统原 Handler 弹「应用停止」对话框 / 统计
            prev?.uncaughtException(t, e)
        }
    }
}
