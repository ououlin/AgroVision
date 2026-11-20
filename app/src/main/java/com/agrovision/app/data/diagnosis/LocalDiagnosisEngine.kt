package com.agrovision.app.data.diagnosis

import com.agrovision.app.data.llm.DiagnosisReportData
import com.agrovision.app.data.llm.OfflineConversation
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.agrovision.app.domain.model.PlantDiseaseLabels
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地诊断规则引擎 — 在无 LLM API Key 时提供离线降级诊断。
 *
 * 基于 [EncyclopediaRepository] 百科数据生成结构化诊断报告；百科覆盖范围不等于模型识别范围。
 * 从 [com.agrovision.app.data.llm.DeepSeekProvider] 抽离，保持单一职责。
 */
@Singleton
class LocalDiagnosisEngine @Inject constructor(
    private val encyclopediaRepository: EncyclopediaRepository
) {

    /** 生成结构化诊断报告 */
    fun generateDiagnosis(
        name: String, conf: Float, context: String
    ): DiagnosisReportData {
        // 优先从百科数据获取完整信息（兼容中文名和 PlantVillage 英文标签）
        val pestInfo = encyclopediaRepository.getByChineseName(name)
            ?: encyclopediaRepository.getByEnglishName(name)
            ?: run {
                // 百科查不到时，尝试用 PlantDiseaseLabels 映射中文名再查一次
                val cnName = PlantDiseaseLabels.toCnName(name)
                if (cnName != name) encyclopediaRepository.getByChineseName(cnName) else null
            }

        val displayName = pestInfo?.cnName ?: PlantDiseaseLabels.toCnName(name)
        val dangerLevel = pestInfo?.dangerLevel ?: inferDangerLevel(displayName)

        // 病害分析：优先用百科描述，否则用关键词推断
        val analysis = if (pestInfo != null && pestInfo.description.isNotEmpty()) {
            buildString {
                append(pestInfo.description)
                if (pestInfo.symptoms.isNotEmpty()) {
                    append("\n\n典型症状：")
                    append(pestInfo.symptoms.joinToString("；"))
                }
            }
        } else {
            buildString {
                append(displayName)
                append("是农业生产中的常见病虫害。")
                append(inferAnalysisByKeyword(displayName))
                append("在适宜条件下传播较快，需要及时采取综合防治措施。")
            }
        }

        // 发病原因：优先用百科，否则用关键词推断
        val causes = if (pestInfo != null && pestInfo.causes.isNotEmpty()) {
            pestInfo.causes
        } else {
            inferCausesByKeyword(displayName)
        }

        // 防治方法：优先用百科，否则用通用建议
        val prevention = if (pestInfo != null && pestInfo.prevention.isNotEmpty()) {
            pestInfo.prevention
        } else {
            "1. 选用抗病品种\n2. 合理密植，加强通风透光\n3. 科学施肥，增施有机肥\n4. 及时排水降低湿度\n5. 清除病残体减少侵染源\n6. 发病初期及时喷药防治"
        }

        // 推荐药剂：优先用百科，否则用通用建议
        val pesticides = if (pestInfo != null && pestInfo.pesticides.isNotEmpty()) {
            pestInfo.pesticides
        } else {
            listOf("代森锰锌", "苯醚甲环唑", "吡唑醚菌酯", "甲基硫菌灵")
        }

        val recovery = when (dangerLevel) {
            "高" -> "初期治疗约5-7天可见效果，需持续防控2-3周。重度感染建议拔除销毁、重新定植。"
            "中" -> "治疗约3-5天症状得到控制，7-10天基本恢复。需注意后续预防复发。"
            else -> "治疗约2-3天见效，5-7天恢复。加强日常管理可有效预防。"
        }

        val markdown = buildString {
            appendLine("## ${displayName} — 问诊结果")
            appendLine()
            appendLine("**危险等级：$dangerLevel** | 识别置信度：${(conf * 100).toInt()}%")
            appendLine()
            appendLine("### 病害分析")
            appendLine(analysis)
            appendLine()
            appendLine("### 发病原因")
            appendLine(causes)
            appendLine()
            appendLine("### 防治方法")
            appendLine(prevention)
            appendLine()
            appendLine("### 推荐药剂")
            pesticides.forEach { appendLine("- $it") }
            appendLine()
            appendLine("### 预计恢复周期")
            appendLine(recovery)
            appendLine()
            appendLine("> 注：药剂使用请遵循当地农技部门指导，注意安全间隔期。")
        }

        return DiagnosisReportData(
            diseaseName = displayName,
            diseaseNameEn = pestInfo?.enName ?: name,
            dangerLevel = dangerLevel,
            analysis = analysis,
            causes = causes,
            prevention = prevention,
            pesticides = pesticides,
            recoveryPeriod = recovery,
            rawMarkdown = markdown
        )
    }

    /** 根据病名关键词推断危险等级 */
    private fun inferDangerLevel(name: String): String = when {
        name.contains("黄龙") || name.contains("晚疫") || name.contains("稻瘟") ||
        name.contains("病毒") || name.contains("花叶") || name.contains("火疫") ||
        name.contains("蠹蛾") || name.contains("果蝇") || name.contains("根腐") -> "高"
        name.contains("蚜虫") || name.contains("霜霉") || name.contains("白粉") ||
        name.contains("锈") || name.contains("黑腐") || name.contains("叶枯") ||
        name.contains("叶霉") || name.contains("红蜘蛛") || name.contains("斑枯") -> "中"
        else -> "中"
    }

    /** 根据病名关键词推断病害分析 */
    private fun inferAnalysisByKeyword(name: String): String = when {
        name.contains("晚疫") -> "由疫霉菌引起，低温高湿环境爆发快，叶片出现水渍状病斑，湿度大时产生白色霉层。"
        name.contains("早疫") -> "由链格孢菌引起，叶片出现同心轮纹病斑，高温高湿时发病重。"
        name.contains("白粉") -> "由白粉菌引起，叶面覆盖白色粉状霉层，温暖干燥环境发病快。"
        name.contains("锈") -> "由锈菌引起，叶片出现锈粉状病斑，通过气流传播。"
        name.contains("细菌") || name.contains("穿孔") -> "由细菌引起，通过风雨传播，多雨年份发病重。"
        name.contains("病毒") -> "由病毒引起，通过昆虫或接触传播，叶片出现花叶卷曲症状。"
        name.contains("蚜虫") || name.contains("红蜘蛛") -> "刺吸式害虫，群集叶背为害，繁殖快，干旱时爆发。"
        name.contains("黑腐") -> "由真菌引起，果实和叶片出现腐烂病斑，高温多雨发病重。"
        else -> ""
    }

    /** 根据病名关键词推断发病原因 */
    private fun inferCausesByKeyword(name: String): String = when {
        name.contains("晚疫") -> "1. 致疫霉通过气流和雨水传播\n2. 温度18-22°C、相对湿度>90%\n3. 连作导致病原菌积累\n4. 偏施氮肥、密植通风不良\n5. 排水不畅、田间湿度过高"
        name.contains("早疫") -> "1. 链格孢菌在病残体上越冬\n2. 高温（24-29°C）高湿\n3. 植株衰弱、营养不足\n4. 连作田发病重"
        name.contains("白粉") -> "1. 白粉菌在病残体越冬\n2. 温暖干燥（20-25°C）环境\n3. 密植通风不良\n4. 偏施氮肥植株幼嫩"
        name.contains("锈") -> "1. 锈菌随气流传播\n2. 转主寄主存在\n3. 春季多雨有利于侵染\n4. 品种抗性差"
        name.contains("细菌") || name.contains("穿孔") -> "1. 细菌在枝条溃疡斑越冬\n2. 风雨传播\n3. 多雨年份发病重\n4. 伤口有利于侵入"
        name.contains("病毒") -> "1. 病毒通过昆虫（蚜虫、粉虱）传播\n2. 接触传播（农事操作）\n3. 种苗带毒\n4. 传毒昆虫发生严重时发病重"
        name.contains("蚜虫") -> "1. 20-25°C、干旱少雨\n2. 天敌减少\n3. 不合理用药导致抗药性\n4. 氮肥过量植株幼嫩"
        name.contains("红蜘蛛") -> "1. 高温干旱\n2. 天敌减少\n3. 连作积累虫源\n4. 不合理用药杀死天敌"
        else -> "1. 品种抗病性不足\n2. 气候条件适宜病害发生\n3. 栽培管理不当\n4. 田间卫生条件差"
    }

    /**
     * 生成离线问答回复（RAG 降级，增强版）
     *
     * 增强策略：
     * 1. 先做意图分类，根据意图选择不同的检索策略和回复结构
     * 2. 百科命中时，根据意图生成针对性结构化回复（治疗意图突出防治+药剂；症状意图突出症状描述）
     * 3. 未命中百科时，扩展到 15+ 常见农业场景的专业回复模板
     * 4. 回复末尾自动追加追问引导，提升对话延续性
     */
    fun generateReply(query: String, docs: List<String>): String {
        OfflineConversation.replyTo(query)?.let { return it }

        // 1. 意图分类
        val intent = classifyIntent(query)

        // 2. 百科检索 — 先识别整句中出现的病害名，再做普通全文检索。
        // 例如“苹果黑星病怎么防治”不应拿整句话去做精确字段匹配。
        val allPests = encyclopediaRepository.getAllPests()
        val pests = LocalQuestionMatcher.findMentionedPest(query, allPests)
            ?.let(::listOf)
            ?: encyclopediaRepository.search(query)
        if (pests.isNotEmpty()) {
            return buildPestReplyByIntent(pests.first(), intent)
        }

        // 3. 关键词推断 — 扩展为 15+ 场景
        return buildKeywordReply(query, intent, docs)
    }

    /**
     * 意图分类（本地实现，避免依赖 KnowledgeBase 形成循环依赖）
     */
    private fun classifyIntent(query: String): String {
        val lower = query.lowercase()
        return when {
            listOf("症状", "表现", "什么样", "怎么看", "特征").any { lower.contains(it) } -> "SYMPTOM"
            listOf("怎么治", "如何治", "怎么防治", "如何防治", "治疗方法", "怎么处理", "怎么办", "如何解决").any { lower.contains(it) } -> "TREATMENT"
            listOf("预防", "防止", "避免", "预防措施").any { lower.contains(it) } -> "PREVENTION"
            listOf("原因", "为什么", "怎么会", "发病条件", "引起").any { lower.contains(it) } -> "CAUSE"
            listOf("药剂", "农药", "用什么药", "打什么", "推荐药", "哪种药").any { lower.contains(it) } -> "PESTICIDE"
            listOf("施肥", "肥料", "追肥", "基肥", "营养", "用什么肥").any { lower.contains(it) } -> "FERTILIZER"
            listOf("浇水", "灌溉", "湿度", "干旱", "水多", "排水").any { lower.contains(it) } -> "WATERING"
            listOf("修剪", "剪枝", "整枝", "摘心", "疏果").any { lower.contains(it) } -> "PRUNING"
            listOf("品种", "选种", "推荐品种", "嫁接", "苗木", "种苗").any { lower.contains(it) } -> "VARIETY"
            listOf("识别", "诊断", "这是什么", "什么病", "什么虫", "拍照").any { lower.contains(it) } -> "IDENTIFY"
            else -> "GENERAL"
        }
    }

    /**
     * 根据意图生成针对性百科回复
     *
     * 不同意图突出不同字段：
     * - TREATMENT / PESTICIDE：突出防治方法+药剂
     * - SYMPTOM / IDENTIFY：突出症状描述
     * - CAUSE：突出发病原因
     * - PREVENTION：突出预防措施
     * - GENERAL：全量输出
     */
    private fun buildPestReplyByIntent(
        pest: com.agrovision.app.domain.model.PestInfo,
        intent: String
    ): String = buildString {
        appendLine("## ${pest.cnName}（${pest.enName}）")
        appendLine()
        appendLine("**类别：** ${pest.category} | **危险等级：** ${pest.dangerLevel}")
        appendLine()

        when (intent) {
            "TREATMENT", "PESTICIDE" -> {
                // 治疗意图：优先展示防治+药剂
                if (pest.prevention.isNotEmpty()) {
                    appendLine("### 防治方法")
                    appendLine(pest.prevention)
                    appendLine()
                }
                if (pest.pesticides.isNotEmpty()) {
                    appendLine("### 推荐药剂")
                    pest.pesticides.forEachIndexed { idx, drug ->
                        appendLine("${idx + 1}. $drug")
                    }
                    appendLine()
                    appendLine("> **安全提示**：使用药剂时请遵循安全间隔期，注意轮换用药避免抗性。")
                }
                if (pest.symptoms.isNotEmpty()) {
                    appendLine("### 识别要点")
                    appendLine(pest.symptoms.joinToString("；"))
                }
                appendLine()
                appendReplyGuide(intent, pest.cnName)
            }

            "SYMPTOM", "IDENTIFY" -> {
                // 症状意图：优先展示症状+描述
                if (pest.symptoms.isNotEmpty()) {
                    appendLine("### 典型症状")
                    pest.symptoms.forEach { appendLine("- $it") }
                    appendLine()
                }
                if (pest.description.isNotEmpty()) {
                    appendLine("### 简介")
                    appendLine(pest.description)
                    appendLine()
                }
                if (pest.causes.isNotEmpty()) {
                    appendLine("### 发病条件")
                    appendLine(pest.causes)
                }
                appendLine()
                appendReplyGuide(intent, pest.cnName)
            }

            "CAUSE" -> {
                // 原因意图：优先展示发病原因+条件
                if (pest.causes.isNotEmpty()) {
                    appendLine("### 发病原因")
                    appendLine(pest.causes)
                    appendLine()
                }
                if (pest.description.isNotEmpty()) {
                    appendLine("### 补充说明")
                    appendLine(pest.description)
                }
                appendLine()
                appendReplyGuide(intent, pest.cnName)
            }

            "PREVENTION" -> {
                // 预防意图：突出预防措施
                if (pest.prevention.isNotEmpty()) {
                    appendLine("### 预防措施")
                    appendLine(pest.prevention)
                    appendLine()
                }
                if (pest.causes.isNotEmpty()) {
                    appendLine("### 避免这些发病条件")
                    appendLine(pest.causes)
                }
                appendLine()
                appendReplyGuide(intent, pest.cnName)
            }

            else -> {
                // GENERAL：全量结构化输出
                if (pest.description.isNotEmpty()) {
                    appendLine("### 简介")
                    appendLine(pest.description)
                    appendLine()
                }
                if (pest.symptoms.isNotEmpty()) {
                    appendLine("### 典型症状")
                    pest.symptoms.forEach { appendLine("- $it") }
                    appendLine()
                }
                if (pest.causes.isNotEmpty()) {
                    appendLine("### 发病原因")
                    appendLine(pest.causes)
                    appendLine()
                }
                if (pest.prevention.isNotEmpty()) {
                    appendLine("### 防治方法")
                    appendLine(pest.prevention)
                    appendLine()
                }
                if (pest.pesticides.isNotEmpty()) {
                    appendLine("### 推荐药剂")
                    appendLine(pest.pesticides.joinToString("、"))
                }
                appendLine()
                appendReplyGuide(intent, pest.cnName)
            }
        }
    }

    /**
     * 关键词场景回复 — 扩展到 15+ 农业场景
     */
    private fun buildKeywordReply(query: String, intent: String, docs: List<String>): String {
        return when (LocalQuestionMatcher.classify(query)) {
            LocalQuestionTopic.YELLOW_LEAF -> buildYellowLeafReply()
            LocalQuestionTopic.LEAF_DROP -> buildLeafDropReply()
            LocalQuestionTopic.CURL_LEAF -> buildCurlLeafReply()
            LocalQuestionTopic.LEAF_SPOT -> buildSpotReply()
            LocalQuestionTopic.FRUIT_ROT -> buildRotReply()
            LocalQuestionTopic.WILT -> buildWiltReply()
            LocalQuestionTopic.FRUIT_CRACK -> buildCrackReply()
            LocalQuestionTopic.FRUIT_DROP -> buildFruitDropReply()
            LocalQuestionTopic.ANNUAL_CALENDAR -> buildAnnualCalendarReply(query)
            LocalQuestionTopic.PHENOLOGY -> buildPhenologyReply(query)
            LocalQuestionTopic.LOW_TOXIC_PESTICIDE -> buildLowToxicPesticideReply()
            LocalQuestionTopic.PESTICIDE_ROTATION -> buildPesticideRotationReply()
            LocalQuestionTopic.SPRAY_ADJUVANT -> buildSprayAdjuvantReply()
            LocalQuestionTopic.SAFETY_INTERVAL -> buildSafetyIntervalReply()
            LocalQuestionTopic.FERTILIZER -> buildFertilizerReply()
            LocalQuestionTopic.PESTICIDE -> buildPesticideReply()
            LocalQuestionTopic.WATERING -> buildWateringReply()
            LocalQuestionTopic.PRUNING -> buildPruningReply()
            LocalQuestionTopic.VARIETY -> buildVarietyReply()
            LocalQuestionTopic.HARVEST -> buildHarvestReply()
            LocalQuestionTopic.WINTER -> buildWinterReply()
            LocalQuestionTopic.WEEDING -> buildWeedingReply()
            LocalQuestionTopic.SOIL -> buildSoilReply()
            LocalQuestionTopic.WEATHER -> buildWeatherReply()
            LocalQuestionTopic.GENERAL_PEST -> buildPestGeneralReply(query)
            LocalQuestionTopic.FALLBACK -> buildFallbackReply(query, docs)
        }
    }

    // ===== 症状类回复 =====

    private fun buildYellowLeafReply(): String = """
**叶片黄化原因分析**

**1. 病害因素**
- 黄萎病：叶脉间变黄，逐步蔓延至全叶
- 叶枯病：叶尖开始发黄干枯
- 根腐病：全株萎蔫黄化，根系腐烂

**2. 营养缺乏**
- 缺氮：老叶先黄，整体淡绿
- 缺铁：新叶先黄，叶脉绿色（叶脉间失绿）
- 缺镁：老叶叶脉间变黄
- 缺锌：新叶小而簇生，黄化

**3. 环境因素**
- 水涝：根系缺氧导致黄叶
- 干旱：缺水引起叶尖黄化
- 光照不足：下部叶片黄化脱落
- 土壤pH不适：影响养分吸收

**建议**：
1. 检查土壤湿度和排水情况
2. 观察黄叶位置（老叶/新叶）判断缺素类型
3. 补充对应肥料：缺氮用尿素，缺铁用硫酸亚铁
4. 使用拍照识别精准诊断是否为病害

**追问**：您种植的是什么果树？黄叶主要集中在哪个部位？
""".trimIndent()

    private fun buildLeafDropReply(): String = """
**早期落叶原因分析**

**1. 病害导致**
- 褐斑病/斑点落叶病：叶斑后大量脱落
- 霉污病：叶面煤烟状物，光合受阻
- 根腐病：根系受损导致落叶

**2. 虫害导致**
- 红蜘蛛/叶螨：刺吸汁液，叶片枯黄脱落
- 食叶害虫：直接啃食叶片

**3. 环境胁迫**
- 涝害：根系缺氧
- 干旱：水分不足
- 药害：喷药浓度过高
- 营养不良：缺素或树势衰弱

**建议**：
1. 检查落叶是否有病斑或虫迹
2. 挖根检查根系是否腐烂
3. 改善排水，合理施肥增强树势
4. 病害严重时喷施苯醚甲环唑+代森锰锌

**追问**：落叶上有斑点吗？斑点什么颜色？
""".trimIndent()

    private fun buildCurlLeafReply(): String = """
**叶片卷曲原因分析**

**1. 虫害**
- 蚜虫：群集嫩叶背面，叶片向下卷曲
- 蚧壳虫：刺吸导致叶片卷皱
- 潜叶蛾：叶肉被蛀食，叶片皱缩

**2. 病害**
- 缩叶病：叶片肥厚卷曲变形（桃树常见）
- 病毒病：叶片花叶卷曲

**3. 生理性**
- 干旱缺水：叶片自我保护卷曲
- 药害：除草剂漂移或浓度过高
- 缺素：缺硼导致叶片畸形

**建议**：
1. 翻看卷叶背面检查是否有蚜虫
2. 蚜虫用吡虫啉或啶虫脒防治
3. 缩叶病用石硫合剂预防
4. 改善水分管理

**追问**：卷叶背面有没有看到小虫子？
""".trimIndent()

    private fun buildSpotReply(): String = """
**叶片斑点诊断指南**

**常见斑点病害对照**：
| 斑点特征 | 可能病害 | 防治药剂 |
|---------|---------|---------|
| 黑色天鹅绒状 | 黑星病 | 苯醚甲环唑 |
| 同心轮纹褐色 | 早疫病 | 代森锰锌 |
| 不规则褐色 | 褐斑病 | 嘧菌酯 |
| 白粉状物 | 白粉病 | 醚菌酯 |
| 铁锈色粉状 | 锈病 | 三唑酮 |
| 角状透明斑 | 霜霉病 | 烯酰吗啉 |
| 圆形穿孔 | 穿孔病 | 铜制剂 |

**建议**：
1. 观察斑点颜色、形状、大小
2. 检查叶片正面和背面
3. 使用拍照识别获取精准诊断
4. 发病初期及时喷药

**追问**：斑点是什么颜色？有多大？分布在叶片正面还是背面？
""".trimIndent()

    private fun buildRotReply(): String = """
**果实/根茎腐烂诊断**

**1. 果实腐烂**
- 炭疽病：褐色凹陷病斑，后期有粉色孢子
- 软腐病：水渍状软腐，有臭味
- 灰霉病：果实上灰色霉层
- 褐腐病：果实变褐干缩

**2. 根部腐烂**
- 根腐病：根系褐变腐烂，有霉味
- 白绢病：根部白色菌丝
- 疫病：根颈水渍状腐烂

**防治方法**：
1. 及时清除病果，减少侵染源
2. 改善通风透光，降低湿度
3. 果实套袋预防
4. 药剂：炭疽病用咪鲜胺，灰霉病用嘧霉胺

**安全提示**：腐烂果实不可食用，避免传播

**追问**：腐烂部位有没有霉层？什么颜色？
""".trimIndent()

    private fun buildWiltReply(): String = """
**植株枯萎萎蔫诊断**

**可能原因**：
1. **根腐病**：根系腐烂，无法吸收水分
2. **青枯病**：细菌性，迅速全株萎蔫
3. **枯萎病**：真菌性，半边或全株枯萎
4. **干旱**：土壤缺水
5. **涝害**：根系窒息
6. **茎部虫害**：天牛蛀干

**诊断要点**：
- 挖根检查：褐色腐烂→根腐病
- 切开茎基：有菌脓→青枯病
- 维管束变色：褐色→枯萎病
- 土壤干硬：缺水

**防治方法**：
1. 改善排水，避免积水
2. 根腐病用恶霉灵灌根
3. 枯萎病用多菌灵+恶霉灵
4. 增施有机肥，改良土壤

**追问**：萎蔫是突然发生还是逐渐加重？土壤是干还是湿？
""".trimIndent()

    private fun buildCrackReply(): String = """
**裂果原因与防治**

**主要原因**：
1. **水分不均**：干旱后突然大水/暴雨
2. **缺钙**：果实细胞壁脆弱
3. **品种特性**：薄皮品种易裂
4. **成熟过度**：果皮老化失去弹性

**防治方法**：
1. **均衡供水**：避免干旱后猛浇，保持土壤湿度稳定
2. **补钙**：花期和幼果期叶面喷施钙肥（氯化钙/氨基酸钙）
3. **覆盖地膜**：稳定土壤水分
4. **适时采收**：避免过熟
5. **选抗裂品种**

**追问**：裂果是纵向裂还是横向裂？近期有没有下大雨？
""".trimIndent()

    private fun buildFruitDropReply(): String = """
**落果原因分析**

**1. 生理落果**
- 花后1-2周：授粉不良
- 6月落果：营养竞争
- 采前落果：成熟过度

**2. 病害导致**
- 炭疽病：果实病斑后脱落
- 轮纹病：果实腐烂后落果

**3. 虫害导致**
- 食心虫：蛀果后果实脱落
- 果蝇：产卵后果实软化

**4. 环境因素**
- 干旱、大风、药害

**防治方法**：
1. 加强花期授粉（放蜂/人工）
2. 合理疏果，减少竞争
3. 病害用苯醚甲环唑预防
4. 虫害用氯虫苯甲酰胺
5. 适时采收

**追问**：落果上有虫眼吗？还是完好的？
""".trimIndent()

    // ===== 操作类回复 =====

    private fun buildFertilizerReply(): String = """
**科学施肥指南**

**1. 基肥（秋季施入）**
- 有机肥：2000-3000kg/亩（腐熟农家肥或商品有机肥）
- 复合肥（15-15-15）：40-50kg/亩
- 微肥：硼砂1-2kg/亩，硫酸锌2-3kg/亩

**2. 追肥**
| 时期 | 肥料 | 用量 | 作用 |
|-----|------|-----|------|
| 萌芽前 | 氮肥 | 10-15kg/亩 | 促芽促花 |
| 花期 | 磷钾肥 | 5-8kg/亩 | 保花保果 |
| 幼果期 | 复合肥 | 15-20kg/亩 | 促果膨大 |
| 着色期 | 钾肥 | 8-10kg/亩 | 促着色增糖 |

**3. 叶面肥**
- 花期：硼肥（0.2%硼砂）保花保果
- 幼果期：钙肥（0.3%氯化钙）防裂果
- 着色期：磷酸二氢钾（0.3%）增糖着色

**注意事项**：
- 避免高温施肥，防止烧根
- 施肥后及时浇水
- 薄肥勤施，少量多次
- 有机无机配合，改良土壤

**追问**：您种植的是什么作物？处于什么生长阶段？
""".trimIndent()

    private fun buildPesticideReply(): String = """
**农药使用规范**

**1. 选药原则**
- 对症下药：先确诊再选药
- 高效低毒：优先选择生物农药和低毒化学农药
- 轮换用药：避免单一药剂产生抗性

**2. 常见病害用药**
| 病害 | 推荐药剂 | 安全间隔期 |
|-----|---------|-----------|
| 黑星病 | 苯醚甲环唑 | 21天 |
| 白粉病 | 醚菌酯/腈菌唑 | 14天 |
| 霜霉病 | 烯酰吗啉/霜脲氰 | 7天 |
| 炭疽病 | 咪鲜胺/苯醚甲环唑 | 21天 |
| 蚜虫 | 吡虫啉/啶虫脒 | 7天 |
| 红蜘蛛 | 阿维菌素/螺螨酯 | 14天 |

**3. 施药要点**
- 时间：上午10点前或下午4点后
- 天气：风力≤3级，无雨
- 浓度：严格按说明，不随意加量
- 防护：佩戴口罩、手套、长袖

**4. 注意事项**
- 现配现用，不隔夜
- 采前遵守安全间隔期
- 不混用碱性农药和酸性农药
- 蜜蜂活动期避免施药

**追问**：您要防治的是什么病虫害？
""".trimIndent()

    private fun buildSafetyIntervalReply(): String = """
## 农药安全间隔期怎么判断

安全间隔期是**最后一次施药到允许采收之间的最短天数**，不能只按药剂通用名称估算。

1. 查看本次使用产品标签上的作物、有效成分、剂型和安全间隔期。
2. 多种药剂混用或连续施药时，按要求最严格、到期最晚的一项计算。
3. 从最后一次实际施药日期开始计时；补喷后需要重新计算。
4. 到期不等于农残一定合格，订单农业或高风险作物应按要求抽检。

> 不同厂家、浓度和登记作物的间隔期可能不同，App 中的默认天数只能用于提醒，最终以产品登记标签和当地植保指导为准。

**继续告诉我**：作物、药剂完整名称和最后施药日期，我可以帮你整理计算步骤。
""".trimIndent()

    private fun buildLowToxicPesticideReply(): String = """
## 低风险用药与绿色防控

先明确病虫害，再在该作物已登记产品中选择低毒、低残留和作用靶标明确的方案。

- 病害预防可优先考虑枯草芽孢杆菌、木霉菌等生物制剂，以及农业防治和清园措施。
- 鳞翅目幼虫可关注苏云金杆菌等生物制剂；刺吸式害虫优先保护天敌并配合黄板、诱捕。
- 化学药剂不要只看“低毒”字样，还要核对登记作物、防治对象、稀释倍数和安全间隔期。
- 同一作用机制不要连续使用，避免抗药性快速上升。

> “低毒”不等于无毒，也不代表可以在未登记作物上使用。

**继续告诉我**：具体作物和病虫害，我再按防治对象缩小选择范围。
""".trimIndent()

    private fun buildPesticideRotationReply(): String = """
## 轮换用药与抗性管理

轮换看的是**作用机制**，不是商品名。两个不同品牌如果有效成分或 FRAC/IRAC 作用组相同，仍属于同类连续使用。

1. 记录每次使用的有效成分和作用机制分组。
2. 下一次优先换用不同作用机制，避免同组连续施用。
3. 严格按标签剂量和次数使用，不通过随意加量解决效果下降。
4. 同时结合清园、通风、诱捕和抗病品种，降低单纯依赖药剂的压力。

**继续告诉我**：上次药剂的有效成分、目标病虫害和作物，我可以帮你判断是否属于同一作用组。
""".trimIndent()

    private fun buildSprayAdjuvantReply(): String = """
## 喷药助剂使用原则

助剂不是每次都要加。先看农药标签是否允许，再根据叶面蜡质、药液附着和水质决定。

- 展着剂适合改善蜡质叶面的铺展，但浓度过高可能增加药害。
- 油类助剂在高温、嫩叶期或与部分药剂混用时风险较高。
- 水质偏硬或偏碱时，应先做水质调整和小范围兼容性试验。
- 多种药剂和叶面肥混配时，先做小杯试验，出现沉淀、絮凝、发热或分层就不要使用。

> 不要为了“增强药效”自行提高助剂浓度。幼果、花期和高温天气尤其需要谨慎。

**继续告诉我**：药剂名称、作物阶段和当天温度，我可以帮你排查主要混配风险。
""".trimIndent()

    private fun buildAnnualCalendarReply(query: String): String = """
## 全年病虫害管理框架

你问的是：$query

| 时段 | 重点工作 |
|---|---|
| 休眠期/采后 | 清除病残枝叶、刮除病斑、修剪通风、记录上季高发问题 |
| 萌芽至花前 | 检查越冬虫源，保护新梢，避免花期不当用药伤害授粉昆虫 |
| 幼果至膨大期 | 每周巡查叶背和果面，重点关注斑点、霉层、虫卵和落果 |
| 转色至采收期 | 控制临近采收用药，严格计算安全间隔期，雨后复查病害 |

日历必须结合地区、作物和物候调整，不能用一张表覆盖所有果园。

**继续告诉我**：作物、所在地区和当前月份，我可以把框架缩小到当前阶段。
""".trimIndent()

    private fun buildPhenologyReply(query: String): String = """
## 当前生长阶段管理思路

你问的是：$query

1. 先确认实际物候，不只按月份判断；同一地区不同品种可能相差数周。
2. 巡查新叶、叶背、花果和枝干，记录异常比例与扩散速度。
3. 花期优先保护授粉昆虫；幼果期关注水肥波动、病斑和虫口；成熟期重点控制采前用药。
4. 只有确认病虫害并达到防治需要时再选药，同时核对登记作物和安全间隔期。

**继续告诉我**：具体作物、物候名称、当地近期天气和看到的症状，我可以给出更针对性的巡查清单。
""".trimIndent()

    private fun buildWeatherReply(): String = """
## 天气变化下的田间管理

- 连续阴雨：优先排水、降低冠层湿度，雨停后检查叶斑、霉层和果实腐烂。
- 高温干旱：早晚补水并覆盖保墒，关注红蜘蛛等喜高温干燥害虫。
- 大风天气：暂停喷药，检查枝条和支架，避免药液漂移。
- 降温霜冻：提前灌水保墒、树干防护，冻后不要立即重剪或猛施肥。

喷药前应确认未来数小时无明显降雨、风力符合产品标签要求。

**继续告诉我**：作物、天气情况和生长阶段，我可以把注意事项进一步收窄。
""".trimIndent()

    private fun buildWateringReply(): String = """
**灌溉管理指南**

**1. 浇水原则**
- 见干见湿：不旱不浇，浇则浇透
- 早晨浇水最佳，避免傍晚（防病害）
- 根部浇灌优于叶面喷淋

**2. 不同生育期需水**
| 时期 | 需水特点 | 管理 |
|-----|---------|------|
| 萌芽期 | 需水促萌 | 浇透 |
| 花期 | 适当控水 | 少浇促花 |
| 幼果期 | 需水膨大 | 充足 |
| 着色期 | 适当控水 | 减少浇水 |
| 采收后 | 恢复树势 | 适量 |

**3. 注意事项**
- 避免大水漫灌，防止根系缺氧
- 雨后及时排水，防涝害
- 高温天气增加频率，少量多次
- 冬季浇足封冻水

**追问**：您的果园是什么灌溉方式（滴灌/漫灌）？
""".trimIndent()

    private fun buildPruningReply(): String = """
**果树修剪指南**

**1. 修剪时期**
- **冬季修剪**：落叶后至萌芽前（12月-2月）
- **夏季修剪**：生长期抹芽、摘心、疏枝（5-8月）

**2. 修剪方法**
| 方法 | 作用 | 适用 |
|-----|------|-----|
| 短截 | 促发新枝 | 幼树扩冠 |
| 疏剪 | 改善光照 | 密挤枝 |
| 回缩 | 更新复壮 | 衰老枝 |
| 摘心 | 控长促花 | 新梢 |
| 环剥 | 促花芽 | 营养枝 |

**3. 修剪原则**
- 去强留弱、去直留斜
- 疏密适当、通风透光
- 幼树轻剪、老树重剪
- 因树修剪，随枝造型

**4. 注意事项**
- 剪口平滑、不留桩
- 大剪口（>2cm）涂保护剂
- 工具消毒防传播病害
- 剪下的病虫枝集中烧毁

**追问**：您种植的是什么果树？树龄几年了？
""".trimIndent()

    private fun buildVarietyReply(): String = """
**品种选择指南**

**1. 选择原则**
- 适应当地气候土壤
- 抗病虫害能力强
- 市场需求大、经济效益好
- 考虑授粉搭配（多数果树异花授粉）

**2. 常见果树推荐**
| 果树 | 推荐品种 | 特点 |
|-----|---------|------|
| 苹果 | 红富士、嘎拉、瑞阳 | 丰产耐储 |
| 樱桃 | 美早、红灯、布鲁克斯 | 早熟高价 |
| 蓝莓 | 蓝丰、都克、奥尼尔 | 保健价值高 |
| 葡萄 | 阳光玫瑰、巨峰、夏黑 | 鲜食酿酒 |
| 桃 | 油桃、水蜜桃、蟠桃 | 上市期长 |
| 梨 | 雪梨、皇冠梨、鸭梨 | 适应性强 |

**3. 注意事项**
- 选择正规苗木供应商，索要检疫证
- 注意授粉品种搭配（3:1或4:1）
- 考虑成熟期错峰上市
- 南方北方品种适应性不同

**追问**：您在哪个地区？想种什么果树？
""".trimIndent()

    private fun buildHarvestReply(): String = """
**采收管理指南**

**1. 采收时机判断**
- **颜色**：果皮底色由绿转黄/红
- **硬度**：果实适当变软
- **糖度**：手持测糖仪，达标采收
- **香气**：散发品种特有香气
- **种子**：种子变褐色

**2. 采收方法**
- 晴天上午露水干后采收
- 轻拿轻放，避免机械伤
- 保留果梗（部分品种）
- 分期采收：先采外层大果

**3. 采后处理**
- 预冷：采后2小时内降温
- 分级：按大小、色泽、糖度分级
- 包装：通风纸箱或塑料筐
- 储藏：0-4°C冷库，湿度85-90%

**追问**：您要采收什么水果？准备鲜卖还是储藏？
""".trimIndent()

    private fun buildWinterReply(): String = """
**果树越冬防冻指南**

**1. 入冬前准备**
- **涂白**：树干涂白剂（石灰+硫磺），防冻防晒防虫
- **灌封冻水**：土壤封冻前浇透
- **培土**：根颈培土20-30cm
- **缠干**：幼树主干缠草绳或无纺布

**2. 防冻措施**
| 措施 | 适用 | 方法 |
|-----|------|-----|
| 熏烟 | 霜冻夜 | 上风口堆湿草熏烟 |
| 喷防冻剂 | 低温前 | 喷施防冻液 |
| 覆盖 | 幼树 | 地膜+草帘覆盖 |
| 培土 | 根颈 | 培土防根颈受冻 |

**3. 冻后管理**
- 轻冻：喷施叶面肥恢复
- 重冻：剪除冻死枝，促发新枝
- 根颈冻：刮除坏死皮，涂药保护

**追问**：您果园在哪个地区？往年有冻害吗？
""".trimIndent()

    private fun buildWeedingReply(): String = """
**果园除草指南**

**1. 除草方式**
- **人工除草**：环保无残留，适合小面积
- **机械除草**：效率高，中耕松土
- **化学除草**：省工，注意安全

**2. 化学除草要点**
- 行间用草甘膦或百草枯（已禁用）
- 推荐用草铵膦替代
- 避免药液接触果树
- 幼树园慎用除草剂

**3. 生草栽培（推荐）**
- 行间种草（三叶草、鼠茅草）
- 改良土壤，增加有机质
- 调节地温，保持水土
- 定期刈割，覆盖树盘

**建议**：推荐生草栽培，减少除草剂使用

**追问**：您果园杂草严重吗？面积多大？
""".trimIndent()

    private fun buildSoilReply(): String = """
**土壤改良指南**

**1. 常见土壤问题**
| 问题 | 表现 | 改良方法 |
|-----|------|---------|
| 板结 | 透水性差 | 增施有机肥+深翻 |
| 盐碱 | pH偏高 | 施石膏+灌水洗盐 |
| 酸化 | pH偏低 | 施石灰+有机肥 |
| 贫瘠 | 有机质低 | 大量有机肥 |
| 粘重 | 透气差 | 掺沙+有机肥 |

**2. 改良措施**
- **增施有机肥**：每年2000-3000kg/亩
- **种植绿肥**：苜蓿、三叶草翻压
- **深翻改土**：秋季深翻30-40cm
- **秸秆覆盖**：树盘覆盖10cm
- **测土配方**：根据检测结果精准施肥

**3. 理想土壤指标**
- 有机质：>1.5%
- pH：6.0-7.0
- 容重：1.1-1.3g/cm³
- 孔隙度：50-60%

**追问**：您的土壤是什么类型？做过检测吗？
""".trimIndent()

    // ===== 病虫害通用回复 =====

    private fun buildPestGeneralReply(query: String): String {
        val relatedPests = encyclopediaRepository.search(query)
        val hint = if (relatedPests.isNotEmpty()) {
            relatedPests.take(5).joinToString("\n") { pest ->
                "- **${pest.cnName}**（${pest.category}，危险等级：${pest.dangerLevel}）"
            }
        } else {
            listOf("苹果黑星病", "樱桃白粉病", "葡萄黑腐病", "桃细菌性穿孔病", "番茄晚疫病")
                .joinToString("\n") { "- $it" }
        }

        return """
**病虫害查询结果**

根据您的问题，百科库中有以下相关条目：

$hint

**建议**：
1. 在病虫百科中搜索具体病害名称，获取详细防治方案
2. 使用拍照识别功能精准诊断
3. 提供更具体的症状描述（如叶片斑点颜色、形状等）

**追问**：您能描述一下具体的症状吗？什么作物？
""".trimIndent()
    }

    // ===== 兜底回复 =====

    private fun buildFallbackReply(query: String, docs: List<String>): String {
        val docContext = if (docs.isNotEmpty()) {
            "\n\n**知识库参考：**\n" + docs.take(2).joinToString("\n") { "- ${it.take(200)}" }
        } else ""

        return """
根据您的问题，建议：

1. 如果涉及病虫害识别，使用拍照识别功能精准诊断
2. 在病虫百科中搜索相关病虫害信息
3. 提供更具体的信息：
   - 作物种类
   - 症状描述
   - 发病部位
   - 环境条件
$docContext

**追问**：您能更具体地描述问题吗？涉及什么作物或病害？
""".trimIndent()
    }

    /**
     * 追问引导 — 根据意图生成不同的追问，直接追加到当前 StringBuilder
     */
    private fun StringBuilder.appendReplyGuide(intent: String, pestName: String = "") {
        appendLine("---")
        when (intent) {
            "TREATMENT", "PESTICIDE" -> {
                appendLine("**想了解更多？**")
                appendLine("- 发病条件：发送「$pestName 怎么引起的」")
                appendLine("- 预防措施：发送「$pestName 怎么预防」")
                appendLine("- 精准诊断：使用拍照识别功能")
            }
            "SYMPTOM", "IDENTIFY" -> {
                appendLine("**确认是这种病害？**")
                appendLine("- 查看防治方法：发送「$pestName 怎么治」")
                appendLine("- 查看推荐药剂：发送「$pestName 用什么药」")
                appendLine("- 拍照识别：获取AI精准诊断")
            }
            "CAUSE" -> {
                appendLine("**想进一步了解？**")
                appendLine("- 防治方法：发送「$pestName 怎么治」")
                appendLine("- 预防措施：发送「$pestName 怎么预防」")
            }
            "PREVENTION" -> {
                appendLine("**想进一步了解？**")
                appendLine("- 发病症状：发送「$pestName 症状」")
                appendLine("- 治疗方法：发送「$pestName 怎么治」")
            }
            else -> {
                appendLine("**想了解更多？**")
                appendLine("- 治疗方案：发送「$pestName 怎么治」")
                appendLine("- 预防方法：发送「$pestName 怎么预防」")
                appendLine("- 拍照识别：获取AI精准诊断")
            }
        }
    }
}
