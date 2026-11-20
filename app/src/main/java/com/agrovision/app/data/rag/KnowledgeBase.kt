package com.agrovision.app.data.rag

import com.agrovision.app.data.repository.EncyclopediaRepository
import javax.inject.Singleton

/**
 * 本地知识库 — 基于病虫百科的 RAG 检索（增强版）
 *
 * 检索能力升级：
 * - 农业同义词扩展（200+ 条目）
 * - 拼音首字母模糊搜索
 * - 多字段差异化加权（标题 > 症状 > 描述 > 防治）
 * - 用户意图分类前置
 */
@Singleton
class KnowledgeBase(
    private val encyclopediaRepository: EncyclopediaRepository
) {
    /**
     * 病虫缓存 — 首次访问时加载，后续复用
     */
    private val pestCache by lazy {
        encyclopediaRepository.getAllPests()
    }

    /** 同义词映射表 — 覆盖常见农业口语表达 */
    private val synonymMap = mapOf(
        // === 症状类 ===
        "黄叶" to listOf("黄化", "叶片发黄", "叶色变黄", "失绿"),
        "黄化" to listOf("黄叶", "叶片发黄", "叶色变黄"),
        "落叶" to listOf("掉叶", "叶子脱落", "早期落叶"),
        "卷叶" to listOf("叶片卷曲", "叶子卷起来", "皱缩"),
        "斑点" to listOf("病斑", "斑点病", "叶斑", "黑点", "褐斑"),
        "腐烂" to listOf("烂果", "烂根", "果实腐烂", "软腐", "霉烂"),
        "枯萎" to listOf("萎蔫", "枯死", "死亡", "倒伏"),
        "白粉" to listOf("白色粉末", "粉层", "霜状物"),
        "锈病" to listOf("铁锈色", "锈粉", "橙黄色孢子"),
        "穿孔" to listOf("孔洞", "破洞", "叶片破损"),
        "花叶" to listOf("斑驳", "镶嵌", "马赛克", "花叶病毒"),
        "畸形" to listOf("变形", "扭曲", "异常生长", "缩果"),
        "流胶" to listOf("树胶", "胶质物", "树脂溢出"),
        "裂果" to listOf("开裂", "裂口", "果皮破裂"),

        // === 作物类 ===
        "苹果" to listOf("苹果树", "红富士", "嘎拉", "国光"),
        "樱桃" to listOf("大樱桃", "车厘子", "美早", "红灯"),
        "蓝莓" to listOf("越橘", "蓝莓树"),
        "葡萄" to listOf("提子", "巨峰", "夏黑"),
        "桃" to listOf("桃树", "水蜜桃", "油桃", "蟠桃"),
        "梨" to listOf("梨树", "雪梨", "鸭梨", "皇冠梨"),
        "杏" to listOf("杏树", "杏子", "甜杏"),
        "水稻" to listOf("稻谷", "稻田", "秧苗", "稻苗"),
        "玉米" to listOf("苞米", "棒子", "玉米苗"),
        "小麦" to listOf("麦子", "麦苗", "麦田"),
        "番茄" to listOf("西红柿", "番茄苗"),
        "黄瓜" to listOf("青瓜", "黄瓜苗"),
        "辣椒" to listOf("椒类", "辣椒苗"),

        // === 操作/动作类 ===
        "防治" to listOf("治疗", "怎么治", "如何治", "治理", "解决", "处理"),
        "打药" to listOf("喷药", "施药", "用药", "喷洒农药", "喷施", "喷雾"),
        "施肥" to listOf("追肥", "用肥", "营养", "肥料", "补肥", "上肥"),
        "浇水" to listOf("灌溉", "浇水管理", "水分", "供水", "灌水"),
        "修剪" to listOf("剪枝", "整枝", "疏剪", "短截", "摘心"),
        "选种" to listOf("品种选择", "种苗", "苗木", "嫁接"),
        "种植" to listOf("栽培", "栽植", "定植", "移栽", "播种"),
        "采摘" to listOf("收获", "采收", "成熟期", "上市"),
        "安全间隔期" to listOf("采收间隔", "打药后几天", "多久能采", "农残"),
        "轮换用药" to listOf("抗药性", "抗性管理", "轮用", "交替用药"),
        "助剂" to listOf("展着剂", "增效剂", "有机硅", "喷雾助剂"),
        "绿色防控" to listOf("低毒农药", "生物农药", "物理防治", "生态防控"),

        // === 害虫类 ===
        "蚜虫" to listOf("腻虫", "蜜虫", "蚜"),
        "红蜘蛛" to listOf("螨虫", "叶螨", "朱砂叶螨", "山楂叶螨"),
        "食心虫" to listOf("蛀果蛾", "果蠹", "钻心虫"),
        "介壳虫" to listOf("蚧壳虫", "球坚蚧", "桑白蚧"),
        "天牛" to listOf("天牛幼虫", "蛀干害虫"),
        "金龟子" to listOf("铜绿丽金龟", "苹毛丽金龟", "小青花金龟"),

        // === 病害通用词 ===
        "病害" to listOf("疾病", "生病", "感染", "侵染"),
        "真菌" to listOf("霉菌", "真菌性病害", "真菌感染"),
        "细菌" to listOf("细菌性病害", "细菌感染", "溃疡"),
        "病毒" to listOf("病毒病", "病毒感染", "花叶病"),

        // === 其他高频词 ===
        "落果" to listOf("掉果", "坐果差", "生理落果", "采前落果"),
        "大小年" to listOf("隔年结果", "产量波动"),
        "裂果" to listOf("果裂", "裂口", "炸裂"),
        "日灼" to listOf("日烧", "晒伤", "高温伤害"),
        "冻害" to listOf("霜冻", "寒害", "低温伤害", "冷害"),
        "药害" to listOf("药物中毒", "残留", "浓度过高"),
        "缺素" to listOf("元素缺乏", "营养失衡", "微量元素缺失")
    )

    /** 拼音首字母映射表 — 支持输入 hxb 匹配 "黑星病" 等 */
    private val pinyinMap = mapOf(
        // 常见病害
        "hxb" to "黑星病", "hxs" to "黑星病",
        "bfb" to "白粉病", "bfs" to "白粉病",
        "smb" to "霜霉病", "sms" to "霜霉病",
        "xjb" to "锈病", "xls" to "锈病",
        "ybj" to "叶病", "ybs" to "叶病",
        "gfj" to "根腐病", "gfs" to "根腐病",
        "hlb" to "黄龙病", "hls" to "黄龙病",
        "dwb" to "蒂腐病", "dws" to "蒂腐病",
        "hmj" to "灰霉病", "hms" to "灰霉病",
        // 常见害虫
        "yc" to "蚜虫", "ych" to "蚜虫",
        "hzz" to "红蜘蛛", "hzzz" to "红蜘蛛",
        "szc" to "食心虫", "jsch" to "食心虫",
        "jqch" to "介壳虫", "jkch" to "介壳虫",
        "tn" to "天牛",
        "jqz" to "金龟子", "jlqz" to "金龟子",
        // 作物
        "pg" to "苹果", "pgs" to "苹果",
        "yt" to "樱桃", "yts" to "樱桃",
        "ll" to "蓝莓", "lll" to "蓝莓",
        "pt" to "葡萄", "pts" to "葡萄",
        "tz" to "桃树"
    )

    /**
     * 用户意图分类
     */
    enum class QueryIntent(val keywords: List<String>) {
        IDENTIFY(listOf("识别", "诊断", "这是什么", "什么病", "什么虫", "拍照")),
        TREATMENT(listOf("怎么治", "如何防治", "用什么药", "治疗方法", "怎么处理", "怎么办")),
        PREVENTION(listOf("预防", "防止", "避免", "怎么预防", "预防措施")),
        SYMPTOM(listOf("症状", "表现", "什么样", "怎么看出来", "特征")),
        CAUSE(listOf("原因", "为什么", "怎么会", "发病条件", "引起")),
        PESTICIDE(listOf("药剂", "农药", "用什么药", "打什么", "推荐药", "哪种药好")),
        FERTILIZER(listOf("施肥", "肥料", "用什么肥", "追肥", "基肥", "营养")),
        WATERING(listOf("浇水", "灌溉", "水", "湿度", "干旱", "水多")),
        PRUNING(listOf("修剪", "剪枝", "整枝", "什么时候剪", "怎么剪")),
        VARIETY(listOf("品种", "哪个品种", "选什么种", "推荐品种", "苗")),
        GENERAL(listOf())
    }

    companion object {
        /** 检索相关性最低阈值，低于此分数的结果不返回 */
        const val MIN_RELEVANCE_SCORE = 0.3
        /** 默认返回条数 */
        const val DEFAULT_TOP_K = 3
        private val AGRICULTURE_TERMS = listOf(
            "农业", "农田", "果园", "作物", "果树", "种植", "栽培",
            "叶片", "叶子", "根系", "枝条", "花期", "幼果", "土壤",
            "黄叶", "黄化", "落叶", "卷叶", "病斑", "斑点", "腐烂",
            "枯萎", "萎蔫", "裂果", "落果", "缺素", "冻害", "药害",
            "病害", "虫害", "农药", "药剂", "施肥", "肥料", "灌溉",
            "浇水", "修剪", "采收", "亩", "温室", "大棚", "苗木",
            "苹果", "樱桃", "蓝莓", "葡萄", "桃树", "梨树", "杏树",
            "水稻", "稻田", "玉米", "小麦", "番茄", "西红柿", "黄瓜", "辣椒",
            "安全间隔", "采收间隔", "农残", "抗药性", "轮换用药", "助剂",
            "展着剂", "增效剂", "低毒", "绿色防控", "生物农药", "物候",
            "生育期", "全年管理", "防治日历", "天气", "下雨", "高温", "低温", "大风"
        )
    }

    /**
     * 搜索知识库，返回相关文档（带评分）
     *
     * 增强策略：
     * 1. 先做意图分类，调整检索权重
     * 2. 同义词扩展查询词
     * 3. 拼音首字母兜底匹配
     * 4. 多字段差异化加权计算
     * 5. 过滤低分结果
     */
    fun search(query: String, topK: Int = DEFAULT_TOP_K): List<String> {
        if (query.isBlank()) return emptyList()

        val pests = pestCache
        if (pests.isEmpty()) return emptyList()

        // 1. 意图分类
        val intent = classifyIntent(query)

        // 2. 构建扩展查询词（原始 + 同义词）
        val expandedTerms = expandQueryTerms(query)

        // 3. 计算每个病虫的相关性得分
        val scored = pests.map { pest ->
            calculateScore(pest, expandedTerms, intent)
        }.filter { it.second >= MIN_RELEVANCE_SCORE }
         .sortedByDescending { it.second }

        return scored.take(topK).map { (pest, score) ->
            formatPestDoc(pest, score)
        }
    }

    /**
     * 分类用户查询意图
     */
    fun classifyIntent(query: String): QueryIntent {
        val lowerQuery = query.lowercase()
        for (intent in QueryIntent.entries) {
            if (intent.keywords.any { lowerQuery.contains(it) }) {
                return intent
            }
        }
        return QueryIntent.GENERAL
    }

    /** 判断问题是否需要农业知识库，避免日常对话被无关 RAG 内容污染。 */
    fun isAgriculturalQuery(query: String): Boolean {
        if (query.isBlank()) return false
        val lower = query.lowercase()
        if (pestCache.any { lower.contains(it.cnName.lowercase()) || lower.contains(it.enName.lowercase()) }) {
            return true
        }
        return AGRICULTURE_TERMS.any { lower.contains(it) }
    }

    /**
     * 扩展查询词：原始词 + 同义词 + 拼音转换
     */
    private fun expandQueryTerms(query: String): List<ExpandedTerm> {
        val terms = mutableListOf<ExpandedTerm>()

        // 原始 N-gram 分词
        val ngramTokens = tokenize(query)
        ngramTokens.forEach { terms.add(ExpandedTerm(it, 1.0f, Source.NGRAM)) }

        // 同义词扩展
        val lowerQuery = query.lowercase()
        for ((key, synonyms) in synonymMap) {
            if (lowerQuery.contains(key)) {
                synonyms.forEach { syn ->
                    terms.add(ExpandedTerm(syn, 0.85f, Source.SYNONYM))
                }
            }
        }

        // 拼音首字母匹配 → 还原为中文
        val pinyinKey = extractPinyinPrefix(lowerQuery)
        if (pinyinKey != null && pinyinMap.containsKey(pinyinKey)) {
            val chineseTerm = pinyinMap[pinyinKey]!!
            terms.add(ExpandedTerm(chineseTerm, 0.9f, Source.PINYIN))
        }

        return terms.distinctBy { it.term.lowercase() }
    }

    /**
     * 提取查询中的拼音前缀（连续小写英文字母）
     */
    private fun extractPinyinPrefix(text: String): String? {
        val match = Regex("[a-z]{2,}").find(text)
        return match?.value?.lowercase()
    }

    /**
     * 计算单个病虫害文档与查询的相关性得分
     *
     * 字段权重（根据意图动态调整）：
     * - 标题(中文名)：基础权重 3.0
     * - 标题(英文名)：基础权重 2.5
     * - 类别：基础权重 1.0
     * - 症状：基础权重 2.0（识别/症状意图时提升至 3.0）
     * - 防治方法：基础权重 1.5（治疗/预防意图时提升至 2.5）
     * - 描述：基础权重 1.0
     */
    private fun calculateScore(
        pest: com.agrovision.app.domain.model.PestInfo,
        expandedTerms: List<ExpandedTerm>,
        intent: QueryIntent
    ): Pair<com.agrovision.app.domain.model.PestInfo, Double> {

        val docLower = buildString {
            append(pest.cnName.lowercase()).append(" ")
            append(pest.enName.lowercase()).append(" ")
            append(pest.category.lowercase()).append(" ")
            append(pest.description.lowercase()).append(" ")
            append(pest.symptoms.joinToString(" ").lowercase()).append(" ")
            append(pest.causes.lowercase()).append(" ")
            append(pest.prevention.lowercase()).append(" ")
            append(pest.pesticides.joinToString(" ").lowercase())
        }

        var totalScore = 0.0
        var matchedTerms = 0

        for (term in expandedTerms) {
            val termLower = term.term.lowercase()
            if (!docLower.contains(termLower)) continue

            matchedTerms++
            val baseWeight = term.confidence

            // 差异化加权
            val fieldBonus = when {
                pest.cnName.lowercase().contains(termLower) -> 3.0 * baseWeight   // 中文名精确匹配
                pest.enName.lowercase().contains(termLower) -> 2.5 * baseWeight  // 英文名匹配
                pest.symptoms.any { it.lowercase().contains(termLower) } ->
                    if (intent == QueryIntent.IDENTIFY || intent == QueryIntent.SYMPTOM) 3.0 else 2.0
                pest.prevention.lowercase().contains(termLower) ->
                    if (intent == QueryIntent.TREATMENT || intent == QueryIntent.PREVENTION) 2.5 else 1.5
                pest.causes.lowercase().contains(termLower) ->
                    if (intent == QueryIntent.CAUSE) 2.5 else 1.5
                pest.pesticides.any { it.lowercase().contains(termLower) } ->
                    if (intent == QueryIntent.PESTICIDE) 3.0 else 2.0
                pest.category.lowercase().contains(termLower) -> 1.5 * baseWeight
                pest.description.lowercase().contains(termLower) -> 1.0 * baseWeight
                else -> 0.5 * baseWeight
            }

            totalScore += fieldBonus
        }

        // 长度惩罚：匹配词占总词数的比例
        val coverageBonus = if (expandedTerms.isNotEmpty()) {
            matchedTerms.toDouble() / expandedTerms.size
        } else 0.0

        totalScore *= (1.0 + coverageBonus * 0.3)

        return pest to totalScore
    }

    /**
     * 中文 N-gram 分词（保留原有逻辑）
     */
    internal fun tokenize(query: String): List<String> {
        val phrases = query.lowercase()
            .split(" ", "，", "。", "？", "！", ",", ".", "?", "!", "\n", "、", "；")
            .map { it.trim() }
            .filter { it.length >= 2 }

        val tokens = mutableSetOf<String>()
        phrases.forEach { phrase ->
            if (phrase.length >= 2) tokens.add(phrase)
            if (phrase.length > 2) {
                for (i in 0..phrase.length - 2) {
                    tokens.add(phrase.substring(i, i + 2))
                }
            }
            if (phrase.length > 3) {
                for (i in 0..phrase.length - 3) {
                    tokens.add(phrase.substring(i, i + 3))
                }
            }
        }
        return tokens.toList()
    }

    /**
     * 格式化病虫害文档输出（含相关性评分）
     */
    internal fun formatPestDoc(
        pest: com.agrovision.app.domain.model.PestInfo,
        relevanceScore: Double = 0.0
    ): String = buildString {
        appendLine("### ${pest.cnName} (${pest.enName})")
        appendLine("**类别：** ${pest.category}")
        appendLine("**危险等级：** ${pest.dangerLevel}")
        appendLine()
        appendLine("**简介：** ${pest.description}")
        if (pest.symptoms.isNotEmpty()) {
            appendLine()
            appendLine("**症状：** ${pest.symptoms.joinToString("；")}")
        }
        if (pest.causes.isNotEmpty()) {
            appendLine()
            appendLine("**原因：** ${pest.causes}")
        }
        if (pest.prevention.isNotEmpty()) {
            appendLine()
            appendLine("**防治：** ${pest.prevention}")
        }
        if (pest.pesticides.isNotEmpty()) {
            appendLine()
            appendLine("**药剂：** ${pest.pesticides.joinToString("、")}")
        }
    }

    /** 扩展查询项 */
    private data class ExpandedTerm(
        val term: String,
        val confidence: Float,
        val source: Source
    )

    private enum class Source { NGRAM, SYNONYM, PINYIN }

    /** 获取知识库总条目数 */
    fun size(): Int = pestCache.size

    /** 获取所有病虫害名称（用于快捷问题生成） */
    fun getAllPestNames(): List<Pair<String, String>> =
        pestCache.map { it.cnName to it.enName }
}
