package com.agrovision.app.data.advisor

import com.agrovision.app.data.local.entity.PlotEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2.0 物候规则引擎（冷启动规则表）
 *
 * 按作物 × 物候期 × 管理动作三张表组织，冷启动用规则表 + 专家校对。
 * 不用 LLM，保持可解释、可审计、离线可用。
 *
 * ## 物候期定义
 * [PhenologyStage]：萌芽 → 开花 → 座果 → 膨大 → 着色 → 采收
 *
 * ## 入参
 * [getAdvice] 接收作物类型 + 当前物候期，返回管理要点列表
 */
@Singleton
class PhenologyRuleEngine @Inject constructor() {

    /**
     * 物候期（取值固定）
     */
    enum class PhenologyStage(val cnName: String) {
        BUDDING("萌芽"),
        FLOWERING("开花"),
        FRUIT_SET("座果"),
        ENLARGEMENT("膨大"),
        COLORING("着色"),
        HARVEST("采收"),
        DORMANCY("休眠")
    }

    /**
     * 管理要点（一个物候期可对应多个要点）
     */
    data class AdviceItem(
        val stage: PhenologyStage,
        val crop: String,
        val title: String,
        val detail: String,
        /** 优先级 1-5，5=最高（采收前禁药类） */
        val priority: Int = 3
    )

    /**
     * 规则表：crop + stage → 管理要点列表
     *
     * 冷启动覆盖 6 种主流作物（苹果、樱桃、葡萄、桃、番茄、马铃薯），其余延后。
     */
    private val ruleTable: Map<Pair<String, PhenologyStage>, List<AdviceItem>> = mapOf(
        // ========== 苹果 ==========
        "apple" to PhenologyStage.BUDDING to listOf(
            AdviceItem(PhenologyStage.BUDDING, "apple", "萌芽期清园", "刮除老翘皮、清扫落叶，减少越冬病虫源（蚜虫卵、红蜘蛛、腐烂病菌）。", 4),
            AdviceItem(PhenologyStage.BUDDING, "apple", "萌芽前喷药", "喷 3-5°Be 石硫合剂，预防腐烂病、白粉病、红蜘蛛。", 4)
        ),
        "apple" to PhenologyStage.FLOWERING to listOf(
            AdviceItem(PhenologyStage.FLOWERING, "apple", "花期授粉", "盛花期放蜂或人工授粉，提高座果率；避免盛花当天喷药伤蜂。", 5),
            AdviceItem(PhenologyStage.FLOWERING, "apple", "霉心病预防", "初花+盛花各喷一次 10% 多抗霉素，预防霉心病。", 4)
        ),
        "apple" to PhenologyStage.FRUIT_SET to listOf(
            AdviceItem(PhenologyStage.FRUIT_SET, "apple", "疏果定果", "盛花后 25-30 天定果，大型果 25cm 留 1 果，小型果 20cm 留 1 果。", 3),
            AdviceItem(PhenologyStage.FRUIT_SET, "apple", "套袋前防虫", "套袋前喷 1 次 25% 灭幼脲+70% 甲基硫菌灵，预防食心虫和轮纹病。", 5)
        ),
        "apple" to PhenologyStage.ENLARGEMENT to listOf(
            AdviceItem(PhenologyStage.ENLARGEMENT, "apple", "追肥灌水", "7-8 月追施磷钾肥，促进果实膨大；高温干旱期及时灌水。", 3),
            AdviceItem(PhenologyStage.ENLARGEMENT, "apple", "早期落叶病预防", "雨季来临前喷 1:2:200 波尔多液或 80% 代森锰锌，预防褐斑病。", 4)
        ),
        "apple" to PhenologyStage.COLORING to listOf(
            AdviceItem(PhenologyStage.COLORING, "apple", "摘叶转果", "采前 25-30 天摘除遮光叶、转动果实促进全面着色。", 3),
            AdviceItem(PhenologyStage.COLORING, "apple", "禁药期提醒", "距采收 < 30 天禁用高毒农药，建议使用低毒生物农药。", 5)
        ),
        "apple" to PhenologyStage.HARVEST to listOf(
            AdviceItem(PhenologyStage.HARVEST, "apple", "适期采收", "按品种固有色泽、糖度、淀粉指数判断成熟度；勿早采影响品质。", 4),
            AdviceItem(PhenologyStage.HARVEST, "apple", "采后处理", "采后 24h 内入库预冷，0-4℃ 贮藏，减少腐烂。", 4)
        ),

        // ========== 樱桃 ==========
        "cherry" to PhenologyStage.BUDDING to listOf(
            AdviceItem(PhenologyStage.BUDDING, "cherry", "清园防虫", "刮老皮、清落叶，预防红蜘蛛、蚧壳虫越冬代。", 4)
        ),
        "cherry" to PhenologyStage.FLOWERING to listOf(
            AdviceItem(PhenologyStage.FLOWERING, "cherry", "花期放蜂", "大樱桃自花不实，必须配授粉树 + 花期放蜂。", 5)
        ),
        "cherry" to PhenologyStage.HARVEST to listOf(
            AdviceItem(PhenologyStage.HARVEST, "cherry", "防裂果", "采前 15 天控制灌水量，遇雨及时覆盖防裂；成熟前 7 天禁药。", 5)
        ),

        // ========== 葡萄 ==========
        "grape" to PhenologyStage.BUDDING to listOf(
            AdviceItem(PhenologyStage.BUDDING, "grape", "出土上架", "杏树花期出土，出土后喷 3-5°Be 石硫合剂，预防黑痘病、毛毡病。", 4)
        ),
        "grape" to PhenologyStage.FLOWERING to listOf(
            AdviceItem(PhenologyStage.FLOWERING, "grape", "花前摘心", "开花前 3-5 天结果枝摘心，提高座果率。", 4),
            AdviceItem(PhenologyStage.FLOWERING, "grape", "灰霉病预防", "花期喷 50% 腐霉利或 40% 嘧霉胺，预防灰霉病。", 5)
        ),
        "grape" to PhenologyStage.ENLARGEMENT to listOf(
            AdviceItem(PhenologyStage.ENLARGEMENT, "grape", "套袋防鸟", "转色前套袋，预防炭疽病 + 鸟害。", 4),
            AdviceItem(PhenologyStage.ENLARGEMENT, "grape", "霜霉病预防", "雨季前喷 1:0.7:200 波尔多液，预防霜霉病。", 5)
        ),

        // ========== 桃 ==========
        "peach" to PhenologyStage.BUDDING to listOf(
            AdviceItem(PhenologyStage.BUDDING, "peach", "萌芽前清园", "喷 3-5°Be 石硫合剂，预防蚧壳虫、缩叶病。", 4)
        ),
        "peach" to PhenologyStage.FLOWERING to listOf(
            AdviceItem(PhenologyStage.FLOWERING, "peach", "疏花疏果", "疏去 1/3 花蕾，提高单果重；长果枝留 2 果，中果枝留 1 果。", 3)
        ),
        "peach" to PhenologyStage.HARVEST to listOf(
            AdviceItem(PhenologyStage.HARVEST, "peach", "食心虫防治", "采前 20 天诱杀成虫 + 喷 25% 灭幼脲，预防食心虫蛀果。", 5)
        ),

        // ========== 番茄 ==========
        "tomato" to PhenologyStage.FLOWERING to listOf(
            AdviceItem(PhenologyStage.FLOWERING, "tomato", "保花保果", "用 10-20ppm 2,4-D 沾花或番茄灵喷花，提高座果率。", 4),
            AdviceItem(PhenologyStage.FLOWERING, "tomato", "灰霉病预防", "花期喷 50% 腐霉利，预防灰霉病侵染花器。", 5)
        ),
        "tomato" to PhenologyStage.ENLARGEMENT to listOf(
            AdviceItem(PhenologyStage.ENLARGEMENT, "tomato", "晚疫病预防", "低温高湿期喷 72% 霜脲·锰锌，预防晚疫病。", 5),
            AdviceItem(PhenologyStage.ENLARGEMENT, "tomato", "控水防裂", "果实膨大后期控制灌水量，预防裂果。", 3)
        ),
        "tomato" to PhenologyStage.HARVEST to listOf(
            AdviceItem(PhenologyStage.HARVEST, "tomato", "采前禁药", "采前 7 天禁用任何化学农药，可使用生物农药。", 5)
        ),

        // ========== 马铃薯 ==========
        "potato" to PhenologyStage.ENLARGEMENT to listOf(
            AdviceItem(PhenologyStage.ENLARGEMENT, "potato", "晚疫病预警", "15-20℃ + 高湿 48h 是晚疫病爆发条件，立即喷 68% 精甲霜灵锰锌。", 5)
        ),
        "potato" to PhenologyStage.HARVEST to listOf(
            AdviceItem(PhenologyStage.HARVEST, "potato", "杀秧促薯", "收获前 7-10 天割秧，促进薯皮木栓化，减少破皮。", 4)
        )
    )

    /**
     * 按作物 + 物候期查询管理要点
     *
     * @return 管理要点列表，无匹配返回空列表
     */
    fun getAdvice(cropType: String, stage: PhenologyStage): List<AdviceItem> {
        return ruleTable[cropType to stage] ?: emptyList()
    }

    /**
     * 由字符串物候期（存储在 [PlotEntity.phenologyStage]）转为枚举
     */
    fun parseStage(stageStr: String): PhenologyStage? {
        if (stageStr.isBlank()) return null
        return try {
            PhenologyStage.valueOf(stageStr.uppercase())
        } catch (_: Exception) {
            // 兼容中文物候期字符串
            PhenologyStage.entries.find { it.cnName == stageStr }
        }
    }

    /**
     * 默认物候期推荐：基于月份（冷启动无用户标记时的兜底）
     *
     * 简化规则：北半球温带气候，可被用户实际物候标记覆盖
     */
    fun inferStageByMonth(month: Int): PhenologyStage = when (month) {
        in 1..2, in 11..12 -> PhenologyStage.DORMANCY
        3 -> PhenologyStage.BUDDING
        4 -> PhenologyStage.FLOWERING
        5 -> PhenologyStage.FRUIT_SET
        in 6..7 -> PhenologyStage.ENLARGEMENT
        in 8..9 -> PhenologyStage.COLORING
        10 -> PhenologyStage.HARVEST
        else -> PhenologyStage.DORMANCY
    }
}
