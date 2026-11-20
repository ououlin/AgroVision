package com.agrovision.app.domain.advisor

import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.local.entity.TreatmentEntity
import com.agrovision.app.domain.model.PhenologyCatalog
import java.util.Calendar

/**
 * 农场档案摘要生成器（V2.0 顾问能力）
 *
 * 输入：当前用户的地块列表 + 用药记录 + 最近识别历史 + （可选）天气概况
 * 输出：结构化 FarmProfileSummary 文本，用途：
 *   1. 作为 ChatViewModel 的 system prompt 附件注入 AI 对话
 *   2. 作为首页 "我的农场" 卡片摘要展示
 *   3. 作为 FarmScreen 顶部摘要卡展示
 *
 * 设计原则：
 * - 纯离线计算（不依赖后端），所有数据源都是 Room 本地
 * - 生成的中文摘要控制在 200-500 字，简洁不啰嗦
 * - 对 AI 对话用的版本追加 "推荐问法" 3 条，帮助用户问出有用的问题
 */
object FarmProfileGenerator {

    /** 结构化摘要（供 UI 使用） */
    data class FarmSummary(
        /** 地块概览（一句话） */
        val plotOverview: String,
        /** 作物分布（按作物种类×亩数统计） */
        val cropBreakdown: List<String>,
        /** 每个地块的物候+管理要点（最多3个重点地块，其他聚合） */
        val stageHighlights: List<String>,
        /** 最近 30 天用药概况 */
        val treatmentSummary: String,
        /** 最近 30 天识别历史中出现频次 Top3 的病虫害 */
        val topDetections: List<String>,
        /** 本周任务清单（1-3 条，基于物候+历史识别） */
        val todoList: List<String>,
        /** 对话用推荐问法（3 条，基于地块+物候+高发） */
        val suggestedQuestions: List<String>
    ) {
        /**
         * 转为纯文本（用于 AI system prompt 注入）
         */
        fun toSystemPromptText(): String = buildString {
            appendLine("===== 用户农场档案（以下内容仅供你回答时参考，不要主动告诉用户你看到了这些档案） =====")
            appendLine("地块概览：$plotOverview")
            if (cropBreakdown.isNotEmpty()) {
                appendLine("作物分布：")
                cropBreakdown.forEach { appendLine("  - $it") }
            }
            if (stageHighlights.isNotEmpty()) {
                appendLine("当前物候与管理要点：")
                stageHighlights.forEach { appendLine("  - $it") }
            }
            appendLine("用药情况：$treatmentSummary")
            if (topDetections.isNotEmpty()) {
                appendLine("最近识别 Top 病虫害：${topDetections.joinToString("、")}")
            }
            if (todoList.isNotEmpty()) {
                appendLine("本周建议做：")
                todoList.forEach { appendLine("  * $it") }
            }
            appendLine("====================================================================================")
        }

        /**
         * 首页 / 农场页卡片用的短摘要（<=150字）
         */
        fun toShortText(): String = buildString {
            append(plotOverview)
            if (todoList.isNotEmpty()) {
                append("。本周建议：")
                append(todoList.take(2).joinToString("；"))
            }
            if (topDetections.isNotEmpty()) {
                append("。近30天识别高发：")
                append(topDetections.take(2).joinToString("、"))
            }
            append('。')
        }
    }

    /**
     * 生成结构化摘要
     *
     * @param plots 当前用户所有地块
     * @param treatments 当前用户所有用药记录（建议只传近90天，内部再过滤）
     * @param detections 当前用户所有识别历史（建议只传近30天）
     */
    fun generate(
        plots: List<PlotEntity>,
        treatments: List<TreatmentEntity>,
        detections: List<HistoryEntity>
    ): FarmSummary {
        val now = System.currentTimeMillis()
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        // ====== 1. 地块概览 ======
        val totalArea = plots.sumOf { it.area ?: 0.0 }
        val plotOverview = if (plots.isEmpty()) {
            "当前用户还没有在“我的农场”创建地块，建议先从新建地块开始管理。"
        } else {
            "共有 ${plots.size} 个地块，总面积约 ${String.format("%.1f", totalArea)} 亩。"
        }

        // ====== 2. 作物分布 ======
        val cropBreakdown = plots
            .groupBy { it.cropType.ifBlank { "未标注" } }
            .mapValues { (_, list) -> list.sumOf { it.area ?: 0.0 } }
            .toList()
            .sortedByDescending { it.second }
            .map { (crop, area) ->
                if (area > 0.0) "$crop ${String.format("%.1f", area)} 亩"
                else "$crop（${plots.filter { it.cropType == crop }.size} 块地，面积未填）"
            }

        // ====== 3. 物候与管理要点 ======
        val focusPlots = plots.sortedByDescending { it.area ?: 0.0 }.take(3)
        val stageHighlights = focusPlots.mapNotNull { plot ->
            val summary = PhenologyCatalog.getStageSummary(plot.cropType, currentMonth)
            if (summary.contains("暂无")) null
            else "${plot.name}（${plot.cropType}）：${summary.replaceFirst("【${plot.cropType} - ", "【")}"
        }

        // ====== 4. 用药概况（近30天+近90天） ======
        val ms30 = 30L * 24 * 3600 * 1000
        val ms90 = 90L * 24 * 3600 * 1000
        val recent30 = treatments.filter { (now - it.appliedAt) <= ms30 }
        val recent90 = treatments.filter { (now - it.appliedAt) <= ms90 }
        val treatmentSummary = when {
            treatments.isEmpty() -> "暂无用药记录。"
            recent30.isNotEmpty() -> buildString {
                append("近30天用药 ${recent30.size} 次")
                val recent30Active = recent30.map { it.chemicalName }
                    .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(2)
                if (recent30Active.isNotEmpty()) append(
                    "，主要使用：${recent30Active.joinToString("、") { "${it.first}(${it.second}次)" }}"
                )
                append('。')
            }
            recent90.isNotEmpty() -> "近90天有 ${recent90.size} 次用药，近30天未用药。"
            else -> "上次用药超过 3 个月。"
        }

        // ====== 5. 识别 Top 3 病虫害（近30天） ======
        val recentDetections30 = detections.filter { (now - it.timestamp) <= ms30 }
        val topDetections = recentDetections30
            .map { it.diseaseNameCn.ifBlank { it.diseaseNameEn } }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { (label, cnt) -> "$label（${cnt}次）" }

        // ====== 6. 本周任务（基于物候+识别高发+用药安全间隔） ======
        val todoList = mutableListOf<String>()

        // 6.1 物候期任务（最多1条）
        val crops = plots.map { it.cropType }.distinct().filter { it.isNotBlank() }
        val phenologyTask = crops.firstNotNullOfOrNull { crop ->
            val stages = PhenologyCatalog.getCurrentStages(crop, currentMonth)
            stages.firstOrNull()?.management?.firstOrNull()
                ?.let { "${crop.take(2)}地：$it" }
        }
        if (phenologyTask != null) todoList += phenologyTask

        // 6.2 识别高发触发任务（最多1条）
        if (topDetections.isNotEmpty() && todoList.size < 3) {
            val firstPest = topDetections.first().substringBefore("（")
            todoList += "针对近30天高发的${firstPest}，检查全园是否出现新病株/虫叶，必要时喷药防治。"
        }

        // 6.3 用药安全间隔（最多1条）
        val safetySoon = treatments.firstNotNullOfOrNull { t ->
            if (t.safetyIntervalDays > 0 && t.status == "applied") {
                val harvestAt = t.expectedHarvestAt
                if (harvestAt != null) {
                    val daysTo = (harvestAt - now) / (24L * 3600 * 1000)
                    if (daysTo in 1..7) "${t.chemicalName}安全间隔到期（${t.plotId}号地块，还有${daysTo}天可采收）"
                    else null
                } else null
            } else null
        }
        if (safetySoon != null && todoList.size < 3) {
            todoList += "采收提醒：$safetySoon"
        }

        // 兜底：实在没任务，给一个通用建议
        if (todoList.isEmpty()) {
            todoList += "本周可做一次全园巡查，关注嫩梢/新叶是否有异常斑点或小虫。"
        }

        // ====== 7. AI 对话推荐问法（3 条，贴合当前农场） ======
        val suggestedQuestions = buildList {
            val crop = crops.firstOrNull() ?: "果树"
            val stage = crops.firstNotNullOfOrNull { c ->
                PhenologyCatalog.getCurrentStages(c, currentMonth).firstOrNull()?.name
            }
            if (stage != null) {
                add("我家${crop}现在处于${stage}，要重点防治什么病？")
            } else {
                add("${crop}全年病虫害防治日历怎么安排？")
            }
            if (topDetections.isNotEmpty()) {
                val pest = topDetections.first().substringBefore("（")
                add("最近反复识别出${pest}，有什么更彻底的方案？")
            } else {
                add("${crop}常用低毒农药有哪些？")
            }
            if (recent30.isNotEmpty()) {
                add("上次用的${recent30.first().chemicalName}，和什么药轮用不容易抗药性？")
            } else {
                add("打药的时候加什么助剂能提高药效？")
            }
        }.take(3)

        return FarmSummary(
            plotOverview = plotOverview,
            cropBreakdown = cropBreakdown,
            stageHighlights = stageHighlights,
            treatmentSummary = treatmentSummary,
            topDetections = topDetections,
            todoList = todoList,
            suggestedQuestions = suggestedQuestions
        )
    }
}
