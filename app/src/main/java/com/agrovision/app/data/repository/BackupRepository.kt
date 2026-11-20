package com.agrovision.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.agrovision.app.data.local.dao.FeedbackDao
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.local.dao.PlotDao
import com.agrovision.app.data.local.dao.TreatmentDao
import com.agrovision.app.data.local.entity.DetectionFeedback
import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.local.entity.TreatmentEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Singleton

/**
 * 设备级数据备份/恢复仓库（V2.0 全量版）
 *
 * 导出：将 地块(plots) + 用药记录(treatments) + 识别历史(detection_history) + 反馈(detection_feedback)
 *       4 张表全量序列化为 JSON，通过 FileProvider + Intent.ACTION_SEND 分享备份文件。
 * 导入：从用户选择的 JSON 文件反序列化写入数据库（按依赖顺序：plots → treatments → history → feedback）。
 *
 * ## 版本策略
 * - version=1：V1.x 仅 history+feedback（兼容，V2 导入时 plots/treatments 为空列表）
 * - version=2：V2.0 全量 4 表（当前）
 */
@Singleton
class BackupRepository(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val feedbackDao: FeedbackDao,
    private val plotDao: PlotDao,
    private val treatmentDao: TreatmentDao,
    private val gson: Gson
) {
    companion object {
        private const val CURRENT_BACKUP_VERSION = 2
    }

    private data class BackupData(
        val version: Int = CURRENT_BACKUP_VERSION,
        val exportTime: String,
        val plots: List<PlotEntity> = emptyList(),
        val treatments: List<TreatmentEntity> = emptyList(),
        val history: List<HistoryEntity>,
        val feedback: List<DetectionFeedback>
    )

    /**
     * 导出数据为 JSON 文件并返回分享 Intent
     * @return 分享 Intent，或 null 表示导出失败
     */
    suspend fun exportData(): Intent? = withContext(Dispatchers.IO) {
        try {
            val allPlots = plotDao.getAllSuspend()
            val allTreatments = treatmentDao.getAllSuspend()
            val allHistory = historyDao.getRecentHistory(Int.MAX_VALUE)
            val allFeedback = feedbackDao.getAll()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val backup = BackupData(
                version = CURRENT_BACKUP_VERSION,
                exportTime = dateFormat.format(Date()),
                plots = allPlots,
                treatments = allTreatments,
                history = allHistory,
                feedback = allFeedback
            )
            val json = gson.toJson(backup)

            val backupDir = File(context.cacheDir, "backups")
            backupDir.mkdirs()
            val fileName = "agrovision_backup_v2_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val backupFile = File(backupDir, fileName)
            backupFile.writeText(json, Charsets.UTF_8)

            Timber.d("V2.0备份已导出: ${backupFile.absolutePath}")
            Timber.d("  - 地块: ${allPlots.size} 条")
            Timber.d("  - 用药记录: ${allTreatments.size} 条")
            Timber.d("  - 识别历史: ${allHistory.size} 条")
            Timber.d("  - 用户反馈: ${allFeedback.size} 条")

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "农智眼 V2.0 数据备份")
                putExtra(Intent.EXTRA_TEXT, buildString {
                    append("农智眼 V2.0 全量数据备份\n")
                    append("导出时间: ${backup.exportTime}\n")
                    append("地块: ${allPlots.size} 条\n")
                    append("用药记录: ${allTreatments.size} 条\n")
                    append("识别历史: ${allHistory.size} 条\n")
                    append("用户反馈: ${allFeedback.size} 条")
                })
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Timber.e(e, "V2.0导出备份失败")
            null
        }
    }

    /**
     * 从 JSON 文件导入数据
     *
     * 依赖顺序导入：plots(先) → treatments(plotId需已存在) → history → feedback
     * 导入时 id=0 会被 Room 自动分配新的本地 ID，同时记录 本地旧ID → 新ID 的映射
     * 用于修正 treatment.plotId / treatment.historyId / feedback.historyId 的外键引用。
     *
     * @param uri 用户选择的备份文件 URI
     * @return 四元组 (plots, treatments, history, feedback) 导入记录数，null 表示导入失败
     */
    suspend fun importData(uri: Uri): Tuple4? = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext null

            val type = object : TypeToken<BackupData>() {}.type
            val backup: BackupData = gson.fromJson(json, type)

            Timber.d("开始导入备份（version=${backup.version}, exportTime=${backup.exportTime}）")

            var importedPlots = 0
            var importedTreatments = 0
            var importedHistory = 0
            var importedFeedback = 0

            // 1. 导入 plots，记录 旧plotId → 新plotId 映射
            val plotIdMapping = mutableMapOf<Long, Long>()
            backup.plots.forEach { oldPlot ->
                val newId = plotDao.insert(oldPlot.copy(id = 0))
                plotIdMapping[oldPlot.id] = newId
                importedPlots++
            }

            // 2. 导入 treatments，映射 plotId（historyId 先沿用旧值，稍后修正）
            val treatmentIdMapping = mutableMapOf<Long, Long>()
            backup.treatments.forEach { oldTreatment ->
                val newPlotId = plotIdMapping[oldTreatment.plotId] ?: oldTreatment.plotId
                val newId = treatmentDao.insert(
                    oldTreatment.copy(id = 0, plotId = newPlotId)
                )
                treatmentIdMapping[oldTreatment.id] = newId
                importedTreatments++
            }

            // 3. 导入 history，记录 旧historyId → 新historyId 映射
            val historyIdMapping = mutableMapOf<Long, Long>()
            backup.history.forEach { oldHistory ->
                val newPlotId = oldHistory.plotId?.let { plotIdMapping[it] }
                val newId = historyDao.insert(
                    oldHistory.copy(id = 0, plotId = newPlotId)
                )
                historyIdMapping[oldHistory.id] = newId
                importedHistory++
            }

            // 3.5 批量修正 treatments.historyId（刚才是临时沿用的旧值）
            if (treatmentIdMapping.isNotEmpty() && historyIdMapping.isNotEmpty()) {
                backup.treatments.forEach { oldTreatment ->
                    val newTreatmentId = treatmentIdMapping[oldTreatment.id]
                    val oldHistoryId = oldTreatment.historyId
                    if (newTreatmentId != null && oldHistoryId != null) {
                        val newHistoryId = historyIdMapping[oldHistoryId]
                        if (newHistoryId != null) {
                            treatmentDao.getById(newTreatmentId)?.let {
                                treatmentDao.update(it.copy(historyId = newHistoryId))
                            }
                        }
                    }
                }
            }

            // 4. 导入 feedback，映射 historyId
            backup.feedback.forEach { oldFeedback ->
                val newHistoryId = historyIdMapping[oldFeedback.historyId] ?: oldFeedback.historyId
                feedbackDao.insert(
                    oldFeedback.copy(id = 0, historyId = newHistoryId)
                )
                importedFeedback++
            }

            Timber.d("V2.0备份已导入完成:")
            Timber.d("  - 地块: ${importedPlots}/${backup.plots.size} 条")
            Timber.d("  - 用药记录: ${importedTreatments}/${backup.treatments.size} 条")
            Timber.d("  - 识别历史: ${importedHistory}/${backup.history.size} 条")
            Timber.d("  - 用户反馈: ${importedFeedback}/${backup.feedback.size} 条")

            Tuple4(importedPlots, importedTreatments, importedHistory, importedFeedback)
        } catch (e: Exception) {
            Timber.e(e, "V2.0导入备份失败")
            null
        }
    }
}

/**
 * 四元组：(地块数, 用药记录数, 历史记录数, 反馈数)
 */
data class Tuple4(
    val plots: Int,
    val treatments: Int,
    val history: Int,
    val feedback: Int
)
