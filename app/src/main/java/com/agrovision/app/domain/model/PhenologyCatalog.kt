package com.agrovision.app.domain.model

/**
 * 作物物候期 Catalog（V2.0 顾问能力）
 *
 * 覆盖 6 种主粮 + 果树：水稻、小麦、玉米、苹果、柑橘、葡萄
 * 每种作物按 5-8 个物候期 × { 时间窗、管理要点、常发病虫害、水肥建议 } 组织数据。
 *
 * 用途：
 * 1. PlotDetailScreen 根据 plot.cropType + 当前日期自动高亮当前物候期卡片
 * 2. AdvisorSchedulerWorker 每 3 天根据物候期自动生成待办
 * 3. FarmProfileGenerator 生成"本周该做什么"摘要
 * 4. ChatViewModel 作为 system prompt 附件注入，让 AI 回答更贴合作物生长阶段
 */
object PhenologyCatalog {

    /** 单个物候期 */
    data class Stage(
        /** 物候期名称，例："分蘖期" */
        val name: String,
        /** 大致开始月份（1-12），基于黄淮海区域物候，其他地区做 ±1 月容错 */
        val startMonth: Int,
        val endMonth: Int,
        /** 该阶段田间管理要点（3-5 条） */
        val management: List<String>,
        /** 该阶段高发病害/虫害（名称与 PestLabelCatalog.cnName 对齐，便于 RAG 检索） */
        val commonPests: List<String>,
        /** 水肥建议（1-2 条，空表示该阶段无需特殊水肥） */
        val fertilizerWater: List<String> = emptyList()
    )

    /** 作物物候 */
    data class CropPhenology(
        val cropType: String,
        val stages: List<Stage>
    )

    // ============================ 六大作物物候数据 ============================

    private val RICE = CropPhenology(
        cropType = "水稻",
        stages = listOf(
            Stage("育秧期", 3, 4,
                management = listOf("选种晒种", "种子消毒包衣", "苗床整地", "控制温度湿度防立枯"),
                commonPests = listOf("立枯病", "稻瘟病-苗瘟", "稻蓟马"),
                fertilizerWater = listOf("苗床施足底肥，1叶1心追断奶肥")
            ),
            Stage("移栽返青期", 5, 6,
                management = listOf("适龄移栽", "浅水护苗", "查苗补苗"),
                commonPests = listOf("稻飞虱", "二化螟"),
                fertilizerWater = listOf("移栽后5-7天追分蘖肥+除草剂")
            ),
            Stage("分蘖期", 6, 7,
                management = listOf("浅水勤灌", "够苗晒田控无效分蘖", "查二化螟枯心苗"),
                commonPests = listOf("二化螟", "稻纵卷叶螟", "稻飞虱", "稻瘟病-叶瘟")
            ),
            Stage("拔节孕穗期", 7, 8,
                management = listOf("复水灌溉", "防治稻曲病破口前施药", "防倒伏"),
                commonPests = listOf("稻曲病", "稻瘟病-穗颈瘟", "纹枯病", "稻飞虱"),
                fertilizerWater = listOf("重施穗肥，氮磷钾配合")
            ),
            Stage("抽穗扬花期", 8, 9,
                management = listOf("保持水层", "破口期综合用药", "防高低温危害"),
                commonPests = listOf("稻瘟病-穗颈瘟", "稻曲病", "稻飞虱", "稻纵卷叶螟")
            ),
            Stage("灌浆成熟期", 9, 10,
                management = listOf("干湿交替管水", "防早衰", "适时收获"),
                commonPests = listOf("稻飞虱", "稻瘟病-谷粒瘟")
            )
        )
    )

    private val WHEAT = CropPhenology(
        cropType = "小麦",
        stages = listOf(
            Stage("播种苗期", 10, 11,
                management = listOf("种子包衣", "适墒播种", "查苗补种"),
                commonPests = listOf("纹枯病", "蚜虫", "金针虫")
            ),
            Stage("越冬期", 12, 2,
                management = listOf("冬灌保墒", "镇压防冻", "防野鼠"),
                commonPests = listOf("纹枯病", "全蚀病")
            ),
            Stage("返青拔节期", 2, 3,
                management = listOf("追返青肥", "防纹枯病", "化学除草"),
                commonPests = listOf("纹枯病", "麦蜘蛛", "蚜虫"),
                fertilizerWater = listOf("重施拔节肥尿素10-15kg/亩")
            ),
            Stage("抽穗扬花期", 4, 5,
                management = listOf("一喷三防关键期（杀菌剂+杀虫剂+叶面肥）", "防赤霉病阴雨抢晴施药"),
                commonPests = listOf("赤霉病", "白粉病", "条锈病", "穗蚜", "吸浆虫")
            ),
            Stage("灌浆成熟期", 5, 6,
                management = listOf("叶面喷肥防早衰", "防干热风", "蜡熟期收获"),
                commonPests = listOf("穗蚜", "白粉病", "叶锈病")
            )
        )
    )

    private val CORN = CropPhenology(
        cropType = "玉米",
        stages = listOf(
            Stage("播种苗期", 4, 5,
                management = listOf("种子包衣防丝黑穗", "及时间定苗", "化学除草"),
                commonPests = listOf("苗枯病", "地老虎", "金针虫")
            ),
            Stage("拔节期", 6, 7,
                management = listOf("追拔节肥", "化控防倒伏", "中耕培土"),
                commonPests = listOf("玉米螟", "蚜虫", "大斑病"),
                fertilizerWater = listOf("拔节前后追尿素，小喇叭口期重施穗肥")
            ),
            Stage("抽雄吐丝期", 7, 8,
                management = listOf("人工辅助授粉", "防玉米螟蛀穗", "防旱防涝"),
                commonPests = listOf("玉米螟", "蚜虫", "大斑病", "小斑病", "丝黑穗病")
            ),
            Stage("灌浆成熟期", 8, 9,
                management = listOf("防早衰", "站秆扒皮晾晒", "苞叶发黄收获"),
                commonPests = listOf("玉米螟", "蚜虫", "穗腐病")
            )
        )
    )

    private val APPLE = CropPhenology(
        cropType = "苹果",
        stages = listOf(
            Stage("休眠期", 11, 2,
                management = listOf("冬剪整形", "清园刮老皮", "涂白防冻", "喷石硫合剂清园"),
                commonPests = listOf("腐烂病", "轮纹病")
            ),
            Stage("萌芽期", 3, 3,
                management = listOf("追萌芽肥", "花前复剪", "防倒春寒"),
                commonPests = listOf("霉心病", "白粉病", "红蜘蛛", "蚜虫"),
                fertilizerWater = listOf("萌芽前追氮肥+浇水")
            ),
            Stage("花期", 4, 4,
                management = listOf("疏花疏果", "壁蜂授粉", "霉心病花序分离期施药"),
                commonPests = listOf("霉心病", "白粉病", "蚜虫", "金龟子")
            ),
            Stage("幼果膨大期", 5, 6,
                management = listOf("套袋前综合用药", "疏果定果", "果实套袋"),
                commonPests = listOf("轮纹病", "炭疽病", "早期落叶病", "食心虫", "红蜘蛛")
            ),
            Stage("果实膨大期", 7, 8,
                management = listOf("追膨大肥", "夏剪摘心", "抗旱排涝"),
                commonPests = listOf("早期落叶病", "轮纹病", "红蜘蛛", "食心虫"),
                fertilizerWater = listOf("6月底7月初追磷钾肥为主")
            ),
            Stage("成熟期采收", 9, 10,
                management = listOf("摘袋", "铺反光膜增色", "分批采收", "采后入库"),
                commonPests = listOf("轮纹病", "炭疽病", "食心虫"),
                fertilizerWater = listOf("采后早施基肥，有机肥+复合肥")
            )
        )
    )

    private val CITRUS = CropPhenology(
        cropType = "柑橘",
        stages = listOf(
            Stage("休眠期", 12, 1,
                management = listOf("清园", "冬季修剪", "防冻", "喷石硫合剂"),
                commonPests = listOf("炭疽病", "红蜘蛛", "介壳虫")
            ),
            Stage("春梢萌芽期", 2, 3,
                management = listOf("追春梢肥", "抹芽控梢", "防花蕾蛆"),
                commonPests = listOf("疮痂病", "炭疽病", "红蜘蛛", "花蕾蛆"),
                fertilizerWater = listOf("萌芽前施速效氮肥+腐熟饼肥")
            ),
            Stage("开花坐果期", 4, 5,
                management = listOf("摇花", "环割保果", "喷叶面肥保果"),
                commonPests = listOf("疮痂病", "炭疽病", "潜叶蛾", "红蜘蛛", "蚜虫")
            ),
            Stage("夏梢期", 6, 7,
                management = listOf("控夏梢防落果", "抹夏梢或药剂杀梢", "防日灼"),
                commonPests = listOf("溃疡病", "炭疽病", "潜叶蛾", "锈壁虱")
            ),
            Stage("秋梢果实膨大期", 8, 9,
                management = listOf("放秋梢", "壮果肥", "防裂果"),
                commonPests = listOf("溃疡病", "炭疽病", "潜叶蛾", "锈壁虱", "红蜘蛛"),
                fertilizerWater = listOf("放梢前15天施壮果攻梢肥，钾为主")
            ),
            Stage("着色成熟期", 10, 11,
                management = listOf("控水增糖", "转色期喷磷钾肥", "适时采收"),
                commonPests = listOf("炭疽病", "蒂腐病", "锈壁虱"),
                fertilizerWater = listOf("采果后7天内施还阳肥")
            )
        )
    )

    private val GRAPE = CropPhenology(
        cropType = "葡萄",
        stages = listOf(
            Stage("休眠期", 12, 2,
                management = listOf("冬剪", "清园", "埋土防寒(北方)", "喷石硫合剂"),
                commonPests = listOf("黑痘病", "炭疽病")
            ),
            Stage("萌芽期", 3, 3,
                management = listOf("出土上架", "追萌芽肥", "抹芽定梢"),
                commonPests = listOf("黑痘病", "灰霉病", "绿盲蝽"),
                fertilizerWater = listOf("芽膨大期追速效氮+浇水")
            ),
            Stage("花序展露期", 4, 4,
                management = listOf("绑蔓", "花序整形", "摘心"),
                commonPests = listOf("灰霉病", "黑痘病", "穗轴褐枯病")
            ),
            Stage("开花坐果期", 5, 5,
                management = listOf("花前摘心", "花期喷硼", "花后疏果"),
                commonPests = listOf("灰霉病", "穗轴褐枯病", "绿盲蝽")
            ),
            Stage("果实膨大期", 6, 7,
                management = listOf("套袋前综合杀菌", "果实套袋", "膨果肥"),
                commonPests = listOf("白腐病", "霜霉病", "炭疽病", "蓟马"),
                fertilizerWater = listOf("坐果后追氮磷钾复合肥，保证水分")
            ),
            Stage("转色成熟期", 8, 9,
                management = listOf("控水", "摘叶转果增色", "去袋增色", "分批采收"),
                commonPests = listOf("霜霉病", "炭疽病", "白腐病", "灰霉病"),
                fertilizerWater = listOf("转色前施钾肥，采后施基肥")
            )
        )
    )

    private val catalog = mapOf(
        "水稻" to RICE, "小麦" to WHEAT, "玉米" to CORN,
        "苹果" to APPLE, "柑橘" to CITRUS, "葡萄" to GRAPE,
        // 兼容常见俗名
        "稻子" to RICE, "麦子" to WHEAT, "苞米" to CORN,
        "苹果树" to APPLE, "桔子" to CITRUS, "橙子" to CITRUS, "桔子树" to CITRUS,
        "葡萄树" to GRAPE, "提子" to GRAPE
    )

    /** 全部支持的作物类型 */
    val supportedCrops: List<String> = listOf("水稻", "小麦", "玉米", "苹果", "柑橘", "葡萄")

    /** 根据作物类型获取物候，未知作物返回 null */
    fun forCrop(cropType: String): CropPhenology? = catalog[cropType]

    /**
     * 获取当前月份所处的物候期（黄淮海基准月 ± 1 月容错）。
     * 边界月份（如 5 月处于返青拔节 / 抽穗扬花交界）返回两个阶段。
     *
     * @param month 当前月份 1-12
     */
    fun getCurrentStages(cropType: String, month: Int): List<Stage> {
        val phenology = forCrop(cropType) ?: return emptyList()
        return phenology.stages.filter {
            month in (it.startMonth - 1)..(it.endMonth + 1)
        }
    }

    /**
     * 生成"本阶段该做什么"简要提示（1-2 句话），供首页 / 农场档案卡片用
     */
    fun getStageSummary(cropType: String, month: Int): String {
        val stages = getCurrentStages(cropType, month)
        if (stages.isEmpty()) return "$cropType：暂无推荐管理要点"
        val primary = stages.first()
        return buildString {
            append("【$cropType - ${primary.name}】")
            append(primary.management.take(2).joinToString("、"))
            if (primary.commonPests.isNotEmpty()) {
                append("；重点防控：")
                append(primary.commonPests.take(2).joinToString("、"))
            }
        }
    }
}
