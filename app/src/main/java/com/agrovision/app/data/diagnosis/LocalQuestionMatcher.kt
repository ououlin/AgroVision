package com.agrovision.app.data.diagnosis

import com.agrovision.app.domain.model.PestInfo

internal enum class LocalQuestionTopic {
    YELLOW_LEAF,
    LEAF_DROP,
    CURL_LEAF,
    LEAF_SPOT,
    FRUIT_ROT,
    WILT,
    FRUIT_CRACK,
    FRUIT_DROP,
    ANNUAL_CALENDAR,
    PHENOLOGY,
    LOW_TOXIC_PESTICIDE,
    PESTICIDE_ROTATION,
    SPRAY_ADJUVANT,
    SAFETY_INTERVAL,
    FERTILIZER,
    PESTICIDE,
    WATERING,
    PRUNING,
    VARIETY,
    HARVEST,
    WINTER,
    WEEDING,
    SOIL,
    WEATHER,
    GENERAL_PEST,
    FALLBACK
}

internal object LocalQuestionMatcher {
    fun classify(query: String): LocalQuestionTopic {
        val q = query.lowercase()
        return when {
            listOf("黄叶", "黄化", "发黄", "失绿").any(q::contains) -> LocalQuestionTopic.YELLOW_LEAF
            listOf("落叶", "掉叶", "叶子脱落").any(q::contains) -> LocalQuestionTopic.LEAF_DROP
            listOf("卷叶", "皱缩", "叶片卷曲").any(q::contains) -> LocalQuestionTopic.CURL_LEAF
            listOf("斑点", "病斑", "黑点", "褐斑").any(q::contains) -> LocalQuestionTopic.LEAF_SPOT
            listOf("烂果", "腐烂", "软腐", "霉烂").any(q::contains) -> LocalQuestionTopic.FRUIT_ROT
            listOf("枯萎", "萎蔫", "枯死").any(q::contains) -> LocalQuestionTopic.WILT
            listOf("裂果", "开裂", "炸裂").any(q::contains) -> LocalQuestionTopic.FRUIT_CRACK
            listOf("落果", "掉果").any(q::contains) -> LocalQuestionTopic.FRUIT_DROP
            (q.contains("全年") || q.contains("一年")) &&
                listOf("防治", "管理", "日历").any(q::contains) -> LocalQuestionTopic.ANNUAL_CALENDAR
            listOf("处于", "物候", "生育期", "生长阶段").any(q::contains) &&
                listOf("重点", "注意", "防治", "管理").any(q::contains) -> LocalQuestionTopic.PHENOLOGY
            listOf("低毒", "绿色防控", "生物农药").any(q::contains) -> LocalQuestionTopic.LOW_TOXIC_PESTICIDE
            listOf("轮用", "轮换用药", "抗药性", "抗性管理").any(q::contains) -> LocalQuestionTopic.PESTICIDE_ROTATION
            listOf("助剂", "展着剂", "增效剂").any(q::contains) -> LocalQuestionTopic.SPRAY_ADJUVANT
            listOf("安全间隔", "采收间隔", "打药后几天", "多久能采").any(q::contains) -> LocalQuestionTopic.SAFETY_INTERVAL
            listOf("施肥", "肥料", "追肥", "基肥", "叶面肥", "缺素").any(q::contains) -> LocalQuestionTopic.FERTILIZER
            listOf("打药", "农药", "施药", "喷药", "药剂").any(q::contains) -> LocalQuestionTopic.PESTICIDE
            listOf("浇水", "灌溉", "水分", "排水", "干旱").any(q::contains) -> LocalQuestionTopic.WATERING
            listOf("修剪", "剪枝", "整枝", "摘心", "疏果").any(q::contains) -> LocalQuestionTopic.PRUNING
            listOf("品种", "选种", "苗木", "嫁接").any(q::contains) -> LocalQuestionTopic.VARIETY
            listOf("采收", "采摘", "成熟").any(q::contains) -> LocalQuestionTopic.HARVEST
            listOf("越冬", "防冻", "冻害", "霜冻").any(q::contains) -> LocalQuestionTopic.WINTER
            q.contains("除草") -> LocalQuestionTopic.WEEDING
            listOf("土壤", "改良", "板结", "盐碱", "酸化").any(q::contains) -> LocalQuestionTopic.SOIL
            listOf("天气", "下雨", "高温", "低温", "大风").any(q::contains) -> LocalQuestionTopic.WEATHER
            listOf("病", "虫", "病害", "虫害").any(q::contains) -> LocalQuestionTopic.GENERAL_PEST
            else -> LocalQuestionTopic.FALLBACK
        }
    }

    fun findMentionedPest(query: String, pests: List<PestInfo>): PestInfo? {
        val compactQuery = compact(query)
        return pests.sortedByDescending { it.cnName.length }
            .firstOrNull { pest ->
                compactQuery.contains(compact(pest.cnName)) ||
                    compactQuery.contains(compact(pest.enName))
            }
    }

    private fun compact(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9\\p{L}]+"), "")
}
