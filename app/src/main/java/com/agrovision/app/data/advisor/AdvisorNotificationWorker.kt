package com.agrovision.app.data.advisor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agrovision.app.MainActivity
import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.data.local.dao.PlotDao
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.data.repository.TreatmentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar

/**
 * V2.0 农情顾问通知 Worker
 *
 * 调度：WorkManager 周期触发（默认 6 小时）+ 登录后立即触发一次
 *
 * 任务：
 * 1. 物候提醒：按用户的每个地块作物 + 当前物候期，查 [PhenologyRuleEngine] 推送管理要点
 * 2. 病害预警：按用户地块作物 + 当前天气，查 [WeatherAlertEngine] 推送预警
 * 3. 用药安全间隔期倒计时：[TreatmentRepository.getUpcomingHarvests] 采收前 N 天告警
 *
 * 不上 FCM，避免引入 Google 依赖（国产机兼容性差）。
 */
@HiltWorker
class AdvisorNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val plotDao: PlotDao,
    private val treatmentRepository: TreatmentRepository,
    private val phenologyRuleEngine: PhenologyRuleEngine,
    private val weatherAlertEngine: WeatherAlertEngine,
    private val authRepository: AuthRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID_ADVISOR = "advisor_alerts"
        const val CHANNEL_NAME = "农情顾问提醒"
        const val NOTIFICATION_ID_BASE = 2000  // 避免 HistoryDetail 等通知 ID 冲突

        /** 每个 Worker 周期最多发 3 条通知，避免骚扰 */
        const val MAX_NOTIFICATIONS_PER_RUN = 3
    }

    override suspend fun doWork(): Result {
        if (!FeatureFlag.isV2AdvisorEnabled) {
            Timber.d("AdvisorNotificationWorker: V2.0 顾问开关关闭，跳过")
            return Result.success()
        }

        val user = authRepository.getCurrentUser()
        val userId = user?.userId ?: "guest_local"

        var sentCount = 0

        // 1. 物候提醒 + 病害预警
        val plots = try {
            plotDao.getByUserId(userId)
        } catch (e: Exception) {
            Timber.w(e, "AdvisorNotificationWorker: 查询地块失败")
            return Result.success()  // 不重试，下次周期再试
        }

        // 这里 plots 是 Flow，Worker 内同步取快照
        val plotList = plots.first()

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        for (plot in plotList) {
            if (sentCount >= MAX_NOTIFICATIONS_PER_RUN) break

            // 物候提醒
            val stage = phenologyRuleEngine.parseStage(plot.phenologyStage)
                ?: phenologyRuleEngine.inferStageByMonth(currentMonth)
            val adviceItems = phenologyRuleEngine.getAdvice(plot.cropType, stage)
            val highPriorityAdvice = adviceItems.filter { it.priority >= 4 }
            if (highPriorityAdvice.isNotEmpty()) {
                val first = highPriorityAdvice.first()
                sendNotification(
                    title = "【${plot.name}】${first.title}",
                    content = first.detail
                )
                sentCount++
                continue
            }

            // 病害预警
            val alerts = weatherAlertEngine.check(plot.cropType)
            val criticalAlerts = alerts.filter { it.riskLevel == WeatherAlertEngine.RiskLevel.HIGH || it.riskLevel == WeatherAlertEngine.RiskLevel.CRITICAL }
            if (criticalAlerts.isNotEmpty() && sentCount < MAX_NOTIFICATIONS_PER_RUN) {
                val first = criticalAlerts.first()
                sendNotification(
                    title = "【${plot.name}】${first.diseaseCnName}预警",
                    content = "${first.summary}\n建议：${first.advice}"
                )
                sentCount++
            }
        }

        // 3. 用药安全间隔期倒计时（独立扫描）
        if (sentCount < MAX_NOTIFICATIONS_PER_RUN) {
            val upcoming = treatmentRepository.getUpcomingHarvests(warningMs = 3L * 24 * 60 * 60 * 1000)
            for (t in upcoming) {
                if (sentCount >= MAX_NOTIFICATIONS_PER_RUN) break
                val daysLeft = t.expectedHarvestAt?.let {
                    ((it - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                } ?: continue

                sendNotification(
                    title = "采收倒计时：${t.chemicalName} 安全间隔期还剩 $daysLeft 天",
                    content = "请在安全间隔期结束后采收，否则农药残留可能超标。"
                )
                sentCount++
            }
        }

        Timber.d("AdvisorNotificationWorker: sent $sentCount notifications")
        return Result.success()
    }

    private fun sendNotification(title: String, content: String) {
        val context = applicationContext

        // Android 13+ 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("通知权限未授予，跳过：$title")
            return
        }

        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ADVISOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: Exception) {
            Timber.w(e, "sendNotification 失败：$title")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ADVISOR,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "物候提醒、病害预警、用药安全间隔期倒计时"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
