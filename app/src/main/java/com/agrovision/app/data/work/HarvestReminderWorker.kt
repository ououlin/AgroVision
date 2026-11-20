package com.agrovision.app.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agrovision.app.data.repository.TreatmentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 采收/安全间隔期提醒 Worker（V2.0 顾问能力）
 *
 * 调度频率：每天一次（RepeatInterval=1 DAY）
 * 触发条件：用药记录中 status=APPLIED + 距 expectedHarvestAt <= 7 天
 * 动作：发送系统 Notification 提醒用户"距离[药剂名]安全间隔期采收还有 N 天"，
 *       点击 Notification 跳转到对应的 PlotDetail 页。
 *
 * 注意：需要声明 POST_NOTIFICATIONS 运行时权限（Android 13+），
 * 没权限时 Notification 不会弹出但 Worker 仍正常执行。
 */
@HiltWorker
class HarvestReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val treatmentRepository: TreatmentRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ensureChannel()

        val upcoming = try {
            treatmentRepository.getUpcomingHarvests()
        } catch (e: Exception) {
            Timber.w(e, "V2.0 HarvestReminderWorker 查询数据失败")
            return Result.retry()
        }

        if (upcoming.isEmpty()) {
            Timber.d("V2.0 HarvestReminderWorker: 无即将到期的安全间隔期提醒")
            return Result.success()
        }

        Timber.d("V2.0 HarvestReminderWorker: ${upcoming.size} 条采收提醒")
        val now = System.currentTimeMillis()
        val dayMs = 24L * 3600 * 1000

        upcoming.forEachIndexed { i, t ->
            val harvestAt = t.expectedHarvestAt ?: return@forEachIndexed
            val daysLeft = ((harvestAt - now) + dayMs - 1) / dayMs
            val title = when {
                daysLeft <= 0 -> "✅ 安全间隔期已到，可安排采收"
                daysLeft == 1L -> "⏰ 明天就到采收期，请留意采样检测农残"
                else -> "🌾 ${t.chemicalName}安全间隔期还有 ${daysLeft} 天"
            }
            val body = "地块 ID #${t.plotId}，农药：${t.chemicalName}"
            fireNotification(id = (t.plotId * 31 + i).toInt(), title, body)
        }
        return Result.success()
    }

    // ==================== Notification 辅助 ====================

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "农智眼 V2.0 用药安全间隔期与采收提醒"
                    setShowBadge(true)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun fireNotification(id: Int, title: String, body: String) {
        val ctx = applicationContext
        // 点击通知跳转到启动页；具体跳转 PlotDetail 可通过 Intent extra 扩展
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi: PendingIntent? = launchIntent?.let {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
            PendingIntent.getActivity(ctx, id, it, flags)
        }

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(TAG, id, notification)
        } catch (se: SecurityException) {
            Timber.w("V2.0 HarvestReminder: 缺少 POST_NOTIFICATIONS 权限，跳过弹出通知")
        }
    }

    companion object {
        private const val WORK_NAME = "v2_harvest_reminder"
        private const val CHANNEL_ID = "v2_advisor_channel"
        private const val CHANNEL_NAME = "农场顾问提醒"
        private const val TAG = "V2Harvest"

        /** 每天一次注册（幂等） */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HarvestReminderWorker>(
                1, TimeUnit.DAYS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("V2.0: HarvestReminderWorker 已注册（每天 1 次）")
        }
    }
}
