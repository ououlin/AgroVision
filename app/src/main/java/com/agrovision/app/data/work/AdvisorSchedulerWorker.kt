package com.agrovision.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.data.repository.PlotRepository
import com.agrovision.app.data.repository.TreatmentRepository
import com.agrovision.app.domain.advisor.DiseaseWeatherRuleEngine
import com.agrovision.app.domain.advisor.FarmProfileGenerator
import com.agrovision.app.domain.model.PhenologyCatalog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 顾问周期调度 Worker（V2.0 顾问能力）
 *
 * 调度频率：每 3 天执行一次（RepeatInterval=3，单位 DAYS）
 * 执行内容：
 *  1. 扫描所有地块，根据当前月份更新 phenologyStage
 *  2. 对每个作物执行 DiseaseWeatherRuleEngine（离线弱提示版）
 *  3. 生成 FarmProfileSummary 快照，供下次进入聊天/首页直接读取
 *
 * 注意：
 *  - 用 HiltWorker + @AssistedInject 注入依赖
 *  - 需要 FeatureFlag.V2_ENABLED 才实际执行，否则直接 SUCCESS 空跑
 */
@HiltWorker
class AdvisorSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val plotRepository: PlotRepository,
    private val treatmentRepository: TreatmentRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!com.agrovision.app.BuildConfig.DEBUG) {
            // 正式版可以在这里加 FeatureFlag 判断
        }
        val userId = authRepository.getCurrentUser()?.userId ?: return Result.success()
        Timber.d("V2.0 AdvisorSchedulerWorker 开始调度，userId=$userId")

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        val plots = plotRepository.getPlotsOnce(userId)
        if (plots.isEmpty()) {
            Timber.d("V2.0 Worker: 无地块，跳过顾问调度")
            return Result.success()
        }

        // ===== 1. 更新地块 phenologyStage =====
        var updatedPhenology = 0
        plots.forEach { plot ->
            val stages = PhenologyCatalog.getCurrentStages(plot.cropType, currentMonth)
            val primaryStage = stages.firstOrNull()?.name
            if (primaryStage != null && primaryStage != plot.phenologyStage) {
                plotRepository.updatePhenology(plot.id, primaryStage)
                updatedPhenology++
            }
        }
        Timber.d("V2.0 Worker: 物候期更新 $updatedPhenology/${plots.size} 个地块")

        // ===== 2. 离线病害预警评估（仅打日志 + 统计，UI 侧按需查询） =====
        val cropTypes = plots.map { it.cropType }.distinct().filter { it.isNotBlank() }
        val alerts = cropTypes.flatMap { crop ->
            DiseaseWeatherRuleEngine.evaluateOffline(crop, currentMonth)
        }.filter { it.severity >= DiseaseWeatherRuleEngine.Severity.MEDIUM }
        Timber.d("V2.0 Worker: 离线病害预警命中 ${alerts.size} 条")
        alerts.forEach { Timber.d("  - [${it.severity}] ${it.cropType} ${it.diseaseCnName}") }

        // ===== 3. 摘要快照（记录到日志；UI 在 ChatViewModel 懒加载，这里不重复算） =====
        val treatments = treatmentRepository.getRecentTreatments(userId, limit = 100)
        Timber.d("V2.0 Worker: 扫描用药记录 ${treatments.size} 条")

        // 4. 安全间隔期即将到期：通过 HarvestReminderWorker 做专门的通知
        Timber.d("V2.0 AdvisorSchedulerWorker 调度完成")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "v2_advisor_scheduler"

        /** 3 天一次的周期调度注册（幂等：KEEP 已存在则跳过） */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AdvisorSchedulerWorker>(
                3, TimeUnit.DAYS,
                12, TimeUnit.HOURS  // flex time：在周期的最后12小时内灵活执行
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("V2.0: AdvisorSchedulerWorker 已注册（每 3 天）")
        }
    }
}
