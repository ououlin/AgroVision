package com.agrovision.app.domain.advisor

import com.agrovision.app.data.repository.WeatherRepository
import com.agrovision.app.domain.model.PhenologyCatalog

/**
 * 病害气象预警规则引擎（V2.0 顾问能力 — 轻量规则）
 *
 * 基于 "气象条件 + 当前物候期" 触发 6 条经典病害预警规则。
 * 每条规则返回一条 Alert，供 AdvisorSchedulerWorker 周期调度写入告警中心，
 * 也供 ChatViewModel 作为 system prompt 附件注入让 AI 回答更贴地气。
 *
 * 规则说明（全部可以离线计算，不依赖后端）：
 * R1 稻瘟病-穗颈瘟：水稻 抽穗扬花期 + 连阴雨≥3天 或 日均湿度>90% 连续3天
 * R2 小麦赤霉病：小麦 抽穗扬花期 + 连阴雨≥2天 或 雾露≥2天
 * R3 马铃薯晚疫病 / 番茄晚疫病：温度 10-25℃ + 日均湿度>90% 连续2天（10:00规则经典"两高"）
 * R4 玉米大斑病：玉米 抽雄吐丝期 + 多雨 + 温度 20-25℃
 * R5 苹果早期落叶病（褐斑病）：苹果 幼果膨大~成熟期 + 连阴雨≥5天 或 旬降雨>80mm
 * R6 葡萄霜霉病：葡萄 膨大~转色期 + 温度 22-28℃ + 夜间结露重（日均湿度>85%连续3天）
 *
 * 注：当前实现直接采用输入 WeatherInsight（最近3-7天的窗口统计），
 * 如果没有真实天气数据，则 isReliable=false 仅作为弱提示不推送通知。
 */
object DiseaseWeatherRuleEngine {

    /** 单条病害气象预警 */
    data class Alert(
        val ruleCode: String,
        val diseaseCnName: String,
        val cropType: String,
        val severity: Severity,
        val description: String,
        /** 建议措施（2-3 条可执行） */
        val suggestions: List<String>,
        /** 真实天气命中 = true，数据缺失的经验性提示 = false */
        val isReliable: Boolean
    )

    enum class Severity { LOW, MEDIUM, HIGH }

    /**
     * 外部传入的最近天气摘要（可由 WeatherRepository 构造，或由 Worker 传本地缓存）
     * 全部字段可 null，null 表示该维度数据缺失，规则将以 isReliable=false 弱触发
     */
    data class WeatherInsight(
        /** 最近 3 天平均气温 ℃ */
        val avgTempC: Float? = null,
        /** 最近 N 天连续降雨天数（0~N） */
        val consecutiveRainyDays: Int? = null,
        /** 最近 3 天平均相对湿度 % */
        val avgHumidity: Float? = null,
        /** 近 10 天累计降雨量 mm */
        val precipitation10dMm: Float? = null,
        /** 是否有连续 2 天大雾/重露（人工观测或湿度>95%近似） */
        val hasHeavyDew2d: Boolean? = null
    )

    /**
     * 执行规则评估：输入作物 + 当前月份物候 + 天气摘要，输出 0-N 条预警
     */
    fun evaluate(
        cropType: String,
        currentMonth: Int,
        weather: WeatherInsight = WeatherInsight()
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val phenology = PhenologyCatalog.forCrop(cropType) ?: return emptyList()
        val currentStageNames = PhenologyCatalog.getCurrentStages(cropType, currentMonth)
            .map { it.name }.toSet()

        // ===== R1 稻瘟病-穗颈瘟 =====
        if (cropType in setOf("水稻", "稻子") &&
            currentStageNames.intersect(setOf("抽穗扬花期", "灌浆成熟期")).isNotEmpty()) {
            val trigger = (weather.consecutiveRainyDays ?: 0) >= 3 ||
                    (weather.avgHumidity ?: 0f) >= 90f
            if (trigger || weather.consecutiveRainyDays == null) {
                alerts += Alert(
                    ruleCode = "R1",
                    diseaseCnName = "稻瘟病-穗颈瘟",
                    cropType = cropType,
                    severity = if (trigger) Severity.HIGH else Severity.LOW,
                    description = "水稻抽穗扬花至灌浆期是穗颈瘟高发期，破口前后5天为防控关键窗",
                    suggestions = listOf(
                        "破口前5-7天第一次施药（三环唑/稻瘟灵/肟菌·戊唑醇）",
                        "齐穗期第二次用药，阴雨抢停雨间隙",
                        "氮肥过量地块重点预防"
                    ),
                    isReliable = weather.consecutiveRainyDays != null
                )
            }
        }

        // ===== R2 小麦赤霉病 =====
        if (cropType in setOf("小麦", "麦子") && "抽穗扬花期" in currentStageNames) {
            val trigger = (weather.consecutiveRainyDays ?: 0) >= 2 ||
                    (weather.hasHeavyDew2d == true)
            if (trigger || weather.consecutiveRainyDays == null) {
                alerts += Alert(
                    ruleCode = "R2",
                    diseaseCnName = "赤霉病",
                    cropType = cropType,
                    severity = if (trigger) Severity.HIGH else Severity.LOW,
                    description = "小麦抽穗扬花期遇连阴雨/大雾，赤霉病爆发风险极高，'见花打药、雨后补防'",
                    suggestions = listOf(
                        "扬花5%-10%第一次用药（氰烯·戊唑醇/丙硫菌唑·戊唑醇）",
                        "药后6小时遇雨必须补防",
                        "一喷三防结合杀虫剂+叶面肥"
                    ),
                    isReliable = weather.consecutiveRainyDays != null
                )
            }
        }

        // ===== R3 马铃薯/番茄晚疫病（通用两高规则） =====
        run {
            val temp = weather.avgTempC
            val humid = weather.avgHumidity
            val tempOk = temp == null || (temp in 10f..25f)
            val humidOk = humid == null || humid >= 90f
            if (tempOk && humidOk) {
                // 该规则对非特定作物也给出弱提示（广谱预防），severity 低
                alerts += Alert(
                    ruleCode = "R3",
                    diseaseCnName = "晚疫病（马铃薯/番茄/茄科类）",
                    cropType = cropType,
                    severity = if (temp != null && humid != null) Severity.MEDIUM else Severity.LOW,
                    description = "低温高湿（10-25℃+湿度>90%）是晚疫病经典发病条件，中心病株出现后3-5天全园流行",
                    suggestions = listOf(
                        "巡查田间低洼处查中心病株，发现即拔除带出田外",
                        "保护性+治疗性复配（代森锰锌+精甲霜·锰锌/烯酰吗啉）",
                        "整枝打杈改善通风，避免大水漫灌"
                    ),
                    isReliable = temp != null && humid != null
                )
            }
        }

        // ===== R4 玉米大斑病 =====
        if (cropType in setOf("玉米", "苞米") &&
            currentStageNames.intersect(setOf("拔节期", "抽雄吐丝期")).isNotEmpty()) {
            val temp = weather.avgTempC
            val rainy = weather.consecutiveRainyDays
            val trigger = (temp == null || temp in 20f..25f) && (rainy == null || rainy >= 2)
            if (trigger) {
                alerts += Alert(
                    ruleCode = "R4",
                    diseaseCnName = "玉米大斑病/小斑病",
                    cropType = cropType,
                    severity = Severity.MEDIUM,
                    description = "玉米拔节后高温多雨年份，大小斑病易从下部叶片快速向上扩展",
                    suggestions = listOf(
                        "发病初期（下部叶出现梭形大斑）及时喷药：苯甲·丙环唑/肟菌·戊唑醇",
                        "打掉病叶带出田外",
                        "追施叶面肥防早衰"
                    ),
                    isReliable = temp != null && rainy != null
                )
            }
        }

        // ===== R5 苹果早期落叶病（褐斑病） =====
        if (cropType in setOf("苹果", "苹果树") &&
            currentStageNames.intersect(setOf("幼果膨大期", "果实膨大期", "成熟期采收")).isNotEmpty()) {
            val trigger = (weather.consecutiveRainyDays ?: 0) >= 5 ||
                    (weather.precipitation10dMm ?: 0f) >= 80f
            if (trigger || weather.precipitation10dMm == null) {
                alerts += Alert(
                    ruleCode = "R5",
                    diseaseCnName = "苹果褐斑病（早期落叶病）",
                    cropType = cropType,
                    severity = if (trigger) Severity.HIGH else Severity.LOW,
                    description = "苹果套袋后雨季是褐斑病侵染高峰，5-8月降雨集中年份9月可全园落叶",
                    suggestions = listOf(
                        "雨季前开始保护性用药（波尔多液/代森锰锌，15-20天1次）",
                        "发病初期立即用治疗剂：戊唑醇/苯醚甲环唑/吡唑醚菌酯",
                        "注意叶背面均匀着药"
                    ),
                    isReliable = weather.consecutiveRainyDays != null || weather.precipitation10dMm != null
                )
            }
        }

        // ===== R6 葡萄霜霉病 =====
        if (cropType in setOf("葡萄", "葡萄树", "提子") &&
            currentStageNames.intersect(setOf("果实膨大期", "转色成熟期")).isNotEmpty()) {
            val temp = weather.avgTempC
            val humid = weather.avgHumidity
            val trigger = (temp == null || temp in 22f..28f) &&
                    (humid == null || humid >= 85f) || (weather.consecutiveRainyDays ?: 0) >= 2
            if (trigger) {
                alerts += Alert(
                    ruleCode = "R6",
                    diseaseCnName = "葡萄霜霉病",
                    cropType = cropType,
                    severity = if (humid != null && temp != null) Severity.HIGH else Severity.MEDIUM,
                    description = "葡萄果实膨大至转色期，夜间结露重/多雨条件下霜霉病可在7-10天全园爆发落叶落果",
                    suggestions = listOf(
                        "雨季前保护性：波尔多液/代森锰锌/氰霜唑",
                        "发病后治疗：烯酰吗啉/霜脲·锰锌/氟噻唑吡乙酮，叶背面着药",
                        "摘副梢控新梢，保证架面通风透光"
                    ),
                    isReliable = humid != null && temp != null
                )
            }
        }

        // severity 降序返回（HIGH→MEDIUM→LOW），isReliable=true 优先
        return alerts.sortedWith(
            compareByDescending<Alert> { it.severity }
                .thenByDescending { it.isReliable }
        )
    }

    // ================ 无 WeatherRepository 依赖的工厂便捷方法 ================

    /**
     * 离线模式评估（无真实天气数据，全部 isReliable=false）
     * 用于 Chat 首次进入或首页展示时的"本阶段高发病"提醒。
     */
    fun evaluateOffline(cropType: String, currentMonth: Int): List<Alert> =
        evaluate(cropType, currentMonth, WeatherInsight())
}
