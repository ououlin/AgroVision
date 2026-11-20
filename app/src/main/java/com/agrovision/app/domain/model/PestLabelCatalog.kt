package com.agrovision.app.domain.model

/**
 * V2.0 病虫草害标签四维目录
 *
 * 替代 V1.0 的 [PlantDiseaseLabels] 单层 LABEL_MAP，按 `crop / type / cnName / enName` 四维组织，
 * 支持双头模型（病害分类头 + 害虫检测头）输出统一标签查询。
 *
 * ## 设计要点
 * - [crop]：作物代码（如 "apple"、"rice"），与 [PlotEntity.cropType] 对齐
 * - [type]：[CatalogType] DISEASE / PEST / WEED / HEALTHY
 * - [cnName] / [enName]：中英文名
 * - [code]：类 ICD 的稳定短码（如 "apple_scab"），用于规则引擎、知识库、云端同步
 * - [safetyIntervalDays]：默认安全间隔期，用药计划自动填充（可在 [TreatmentEntity] 覆盖）
 *
 * ## 兼容策略
 * - V1.0 模型输出的 `Apple___Apple_scab` 仍可由 [fromLegacyLabel] 转换
 * - V2.0 模型输出的 `apple_scab` 可由 [fromCode] 查询
 */
data class PestLabelEntry(
    val code: String,
    val crop: String,
    val type: CatalogType,
    val cnName: String,
    val enName: String,
    val safetyIntervalDays: Int = 0,
    /** V1.0 模型原始标签（用于双头过渡期兼容） */
    val legacyLabel: String? = null
)

enum class CatalogType {
    DISEASE,
    PEST,
    WEED,
    HEALTHY
}

object PestLabelCatalog {

    /**
     * V2.0 全量目录（120+ 类）
     *
     * 包含：
     * - V1.0 PlantVillage 38 类（保留 legacyLabel 实现兼容）
     * - V2.0 新增作物病害（水稻/小麦/黄瓜/西瓜/甜瓜/茄子/白菜/茶叶等）
     * - V2.0 害虫 10 类（蚜虫/红蜘蛛/食心虫/介壳虫/天牛/金龟子/菜青虫/潜叶蝇/白粉虱/烟青虫）
     */
    val ENTRIES: List<PestLabelEntry> = listOf(
        // ============ Apple 苹果 ============
        PestLabelEntry("apple_scab", "apple", CatalogType.DISEASE, "苹果黑星病", "Apple Scab", 14, "Apple___Apple_scab"),
        PestLabelEntry("apple_black_rot", "apple", CatalogType.DISEASE, "苹果黑腐病", "Apple Black Rot", 14, "Apple___Black_rot"),
        PestLabelEntry("apple_rust", "apple", CatalogType.DISEASE, "苹果锈病", "Cedar Apple Rust", 14, "Apple___Cedar_apple_rust"),
        PestLabelEntry("apple_healthy", "apple", CatalogType.HEALTHY, "苹果(健康)", "Apple (Healthy)", 0, "Apple___healthy"),

        // ============ Blueberry 蓝莓 ============
        PestLabelEntry("blueberry_healthy", "blueberry", CatalogType.HEALTHY, "蓝莓(健康)", "Blueberry (Healthy)", 0, "Blueberry___healthy"),

        // ============ Cherry 樱桃 ============
        PestLabelEntry("cherry_powdery_mildew", "cherry", CatalogType.DISEASE, "樱桃白粉病", "Cherry Powdery Mildew", 14, "Cherry_(including_sour)___Powdery_mildew"),
        PestLabelEntry("cherry_healthy", "cherry", CatalogType.HEALTHY, "樱桃(健康)", "Cherry (Healthy)", 0, "Cherry_(including_sour)___healthy"),

        // ============ Corn 玉米 ============
        PestLabelEntry("corn_gray_leaf_spot", "corn", CatalogType.DISEASE, "玉米灰斑病", "Corn Gray Leaf Spot", 14, "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot"),
        PestLabelEntry("corn_common_rust", "corn", CatalogType.DISEASE, "玉米锈病", "Corn Common Rust", 14, "Corn_(maize)___Common_rust_"),
        PestLabelEntry("corn_northern_leaf_blight", "corn", CatalogType.DISEASE, "玉米北方叶枯病", "Corn Northern Leaf Blight", 14, "Corn_(maize)___Northern_Leaf_Blight"),
        PestLabelEntry("corn_healthy", "corn", CatalogType.HEALTHY, "玉米(健康)", "Corn (Healthy)", 0, "Corn_(maize)___healthy"),

        // ============ Grape 葡萄 ============
        PestLabelEntry("grape_black_rot", "grape", CatalogType.DISEASE, "葡萄黑腐病", "Grape Black Rot", 14, "Grape___Black_rot"),
        PestLabelEntry("grape_esca", "grape", CatalogType.DISEASE, "葡萄黑麻疹病", "Grape Esca", 14, "Grape___Esca_(Black_Measles)"),
        PestLabelEntry("grape_leaf_blight", "grape", CatalogType.DISEASE, "葡萄叶枯病", "Grape Leaf Blight", 14, "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)"),
        PestLabelEntry("grape_healthy", "grape", CatalogType.HEALTHY, "葡萄(健康)", "Grape (Healthy)", 0, "Grape___healthy"),

        // ============ Orange 柑橘 ============
        PestLabelEntry("orange_huanglongbing", "orange", CatalogType.DISEASE, "柑橘黄龙病", "Citrus Greening", 0, "Orange___Haunglongbing_(Citrus_greening)"),
        PestLabelEntry("citrus_canker", "citrus", CatalogType.DISEASE, "柑橘溃疡病", "Citrus Canker", 14),
        PestLabelEntry("citrus_scab", "citrus", CatalogType.DISEASE, "柑橘疮痂病", "Citrus Scab", 14),

        // ============ Peach 桃 ============
        PestLabelEntry("peach_bacterial_spot", "peach", CatalogType.DISEASE, "桃细菌性穿孔病", "Peach Bacterial Spot", 14, "Peach___Bacterial_spot"),
        PestLabelEntry("peach_healthy", "peach", CatalogType.HEALTHY, "桃(健康)", "Peach (Healthy)", 0, "Peach___healthy"),

        // ============ Pepper 辣椒 ============
        PestLabelEntry("pepper_bacterial_spot", "pepper", CatalogType.DISEASE, "辣椒细菌性斑点病", "Pepper Bacterial Spot", 14, "Pepper,_bell___Bacterial_spot"),
        PestLabelEntry("pepper_healthy", "pepper", CatalogType.HEALTHY, "辣椒(健康)", "Pepper (Healthy)", 0, "Pepper,_bell___healthy"),

        // ============ Potato 马铃薯 ============
        PestLabelEntry("potato_early_blight", "potato", CatalogType.DISEASE, "马铃薯早疫病", "Potato Early Blight", 14, "Potato___Early_blight"),
        PestLabelEntry("potato_late_blight", "potato", CatalogType.DISEASE, "马铃薯晚疫病", "Potato Late Blight", 21, "Potato___Late_blight"),
        PestLabelEntry("potato_healthy", "potato", CatalogType.HEALTHY, "马铃薯(健康)", "Potato (Healthy)", 0, "Potato___healthy"),

        // ============ Raspberry 树莓 ============
        PestLabelEntry("raspberry_healthy", "raspberry", CatalogType.HEALTHY, "树莓(健康)", "Raspberry (Healthy)", 0, "Raspberry___healthy"),

        // ============ Soybean 大豆 ============
        PestLabelEntry("soybean_healthy", "soybean", CatalogType.HEALTHY, "大豆(健康)", "Soybean (Healthy)", 0, "Soybean___healthy"),

        // ============ Squash 南瓜 ============
        PestLabelEntry("squash_powdery_mildew", "squash", CatalogType.DISEASE, "南瓜白粉病", "Squash Powdery Mildew", 14, "Squash___Powdery_mildew"),

        // ============ Strawberry 草莓 ============
        PestLabelEntry("strawberry_leaf_scorch", "strawberry", CatalogType.DISEASE, "草莓叶焦病", "Strawberry Leaf Scorch", 14, "Strawberry___Leaf_scorch"),
        PestLabelEntry("strawberry_healthy", "strawberry", CatalogType.HEALTHY, "草莓(健康)", "Strawberry (Healthy)", 0, "Strawberry___healthy"),
        PestLabelEntry("strawberry_powdery_mildew", "strawberry", CatalogType.DISEASE, "草莓白粉病", "Strawberry Powdery Mildew", 14),
        PestLabelEntry("strawberry_anthracnose", "strawberry", CatalogType.DISEASE, "草莓炭疽病", "Strawberry Anthracnose", 14),

        // ============ Tomato 番茄 ============
        PestLabelEntry("tomato_bacterial_spot", "tomato", CatalogType.DISEASE, "番茄细菌性斑点病", "Tomato Bacterial Spot", 14, "Tomato___Bacterial_spot"),
        PestLabelEntry("tomato_early_blight", "tomato", CatalogType.DISEASE, "番茄早疫病", "Tomato Early Blight", 14, "Tomato___Early_blight"),
        PestLabelEntry("tomato_late_blight", "tomato", CatalogType.DISEASE, "番茄晚疫病", "Tomato Late Blight", 21, "Tomato___Late_blight"),
        PestLabelEntry("tomato_leaf_mold", "tomato", CatalogType.DISEASE, "番茄叶霉病", "Tomato Leaf Mold", 14, "Tomato___Leaf_Mold"),
        PestLabelEntry("tomato_septoria_leaf_spot", "tomato", CatalogType.DISEASE, "番茄斑枯病", "Tomato Septoria Leaf Spot", 14, "Tomato___Septoria_leaf_spot"),
        PestLabelEntry("tomato_spider_mites", "tomato", CatalogType.PEST, "番茄红蜘蛛", "Tomato Spider Mites", 14, "Tomato___Spider_mites Two-spotted_spider_mite"),
        PestLabelEntry("tomato_target_spot", "tomato", CatalogType.DISEASE, "番茄靶斑病", "Tomato Target Spot", 14, "Tomato___Target_Spot"),
        PestLabelEntry("tomato_yellow_leaf_curl_virus", "tomato", CatalogType.DISEASE, "番茄黄化曲叶病毒病", "Tomato Yellow Leaf Curl Virus", 0, "Tomato___Tomato_Yellow_Leaf_Curl_Virus"),
        PestLabelEntry("tomato_mosaic_virus", "tomato", CatalogType.DISEASE, "番茄花叶病毒病", "Tomato Mosaic Virus", 0, "Tomato___Tomato_mosaic_virus"),
        PestLabelEntry("tomato_healthy", "tomato", CatalogType.HEALTHY, "番茄(健康)", "Tomato (Healthy)", 0, "Tomato___healthy"),

        // ============ V2.0 新增作物病害 ============
        // Rice 水稻
        PestLabelEntry("rice_blast", "rice", CatalogType.DISEASE, "稻瘟病", "Rice Blast", 21),
        PestLabelEntry("rice_bacterial_leaf_blight", "rice", CatalogType.DISEASE, "水稻白叶枯病", "Rice Bacterial Leaf Blight", 21),
        PestLabelEntry("rice_sheath_blight", "rice", CatalogType.DISEASE, "水稻纹枯病", "Rice Sheath Blight", 21),
        PestLabelEntry("rice_brown_spot", "rice", CatalogType.DISEASE, "稻胡麻斑病", "Rice Brown Spot", 21),
        PestLabelEntry("rice_healthy", "rice", CatalogType.HEALTHY, "水稻(健康)", "Rice (Healthy)", 0),

        // Wheat 小麦
        PestLabelEntry("wheat_rust", "wheat", CatalogType.DISEASE, "小麦锈病", "Wheat Rust", 21),
        PestLabelEntry("wheat_powdery_mildew", "wheat", CatalogType.DISEASE, "小麦白粉病", "Wheat Powdery Mildew", 14),
        PestLabelEntry("wheat_fusarium_head_blight", "wheat", CatalogType.DISEASE, "小麦赤霉病", "Wheat Fusarium Head Blight", 21),
        PestLabelEntry("wheat_healthy", "wheat", CatalogType.HEALTHY, "小麦(健康)", "Wheat (Healthy)", 0),

        // Cucumber 黄瓜
        PestLabelEntry("cucumber_powdery_mildew", "cucumber", CatalogType.DISEASE, "黄瓜白粉病", "Cucumber Powdery Mildew", 14),
        PestLabelEntry("cucumber_downy_mildew", "cucumber", CatalogType.DISEASE, "黄瓜霜霉病", "Cucumber Downy Mildew", 14),
        PestLabelEntry("cucumber_anthracnose", "cucumber", CatalogType.DISEASE, "黄瓜炭疽病", "Cucumber Anthracnose", 14),
        PestLabelEntry("cucumber_healthy", "cucumber", CatalogType.HEALTHY, "黄瓜(健康)", "Cucumber (Healthy)", 0),

        // Watermelon 西瓜
        PestLabelEntry("watermelon_anthracnose", "watermelon", CatalogType.DISEASE, "西瓜炭疽病", "Watermelon Anthracnose", 14),
        PestLabelEntry("watermelon_fusarium_wilt", "watermelon", CatalogType.DISEASE, "西瓜枯萎病", "Watermelon Fusarium Wilt", 21),
        PestLabelEntry("watermelon_mosaic_virus", "watermelon", CatalogType.DISEASE, "西瓜花叶病毒病", "Watermelon Mosaic Virus", 0),
        PestLabelEntry("watermelon_healthy", "watermelon", CatalogType.HEALTHY, "西瓜(健康)", "Watermelon (Healthy)", 0),

        // Melon 甜瓜
        PestLabelEntry("melon_powdery_mildew", "melon", CatalogType.DISEASE, "甜瓜白粉病", "Melon Powdery Mildew", 14),
        PestLabelEntry("melon_downy_mildew", "melon", CatalogType.DISEASE, "甜瓜霜霉病", "Melon Downy Mildew", 14),
        PestLabelEntry("melon_healthy", "melon", CatalogType.HEALTHY, "甜瓜(健康)", "Melon (Healthy)", 0),

        // Eggplant 茄子
        PestLabelEntry("eggplant_bacterial_wilt", "eggplant", CatalogType.DISEASE, "茄子青枯病", "Eggplant Bacterial Wilt", 21),
        PestLabelEntry("eggplant_phomopsis_rot", "eggplant", CatalogType.DISEASE, "茄子褐纹病", "Eggplant Phomopsis Rot", 14),
        PestLabelEntry("eggplant_healthy", "eggplant", CatalogType.HEALTHY, "茄子(健康)", "Eggplant (Healthy)", 0),

        // Chinese Cabbage 白菜
        PestLabelEntry("cabbage_downy_mildew", "cabbage", CatalogType.DISEASE, "白菜霜霉病", "Cabbage Downy Mildew", 14),
        PestLabelEntry("cabbage_black_rot", "cabbage", CatalogType.DISEASE, "白菜黑腐病", "Cabbage Black Rot", 14),
        PestLabelEntry("cabbage_soft_rot", "cabbage", CatalogType.DISEASE, "白菜软腐病", "Cabbage Soft Rot", 14),
        PestLabelEntry("cabbage_healthy", "cabbage", CatalogType.HEALTHY, "白菜(健康)", "Cabbage (Healthy)", 0),

        // Tea 茶叶
        PestLabelEntry("tea_anthracnose", "tea", CatalogType.DISEASE, "茶炭疽病", "Tea Anthracnose", 14),
        PestLabelEntry("tea_brown_blight", "tea", CatalogType.DISEASE, "茶叶褐斑病", "Tea Brown Blight", 14),
        PestLabelEntry("tea_healthy", "tea", CatalogType.HEALTHY, "茶叶(健康)", "Tea (Healthy)", 0),

        // ============ V2.0 害虫 10 类 ============
        PestLabelEntry("pest_aphid", "general", CatalogType.PEST, "蚜虫", "Aphid", 14),
        PestLabelEntry("pest_spider_mite", "general", CatalogType.PEST, "红蜘蛛/叶螨", "Spider Mite", 14),
        PestLabelEntry("pest_fruit_borer", "general", CatalogType.PEST, "食心虫", "Fruit Borer", 21),
        PestLabelEntry("pest_scale_insect", "general", CatalogType.PEST, "介壳虫", "Scale Insect", 21),
        PestLabelEntry("pest_longhorn_beetle", "general", CatalogType.PEST, "天牛", "Longhorn Beetle", 30),
        PestLabelEntry("pest_chafer", "general", CatalogType.PEST, "金龟子", "Chafer", 21),
        PestLabelEntry("pest_cabbage_worm", "general", CatalogType.PEST, "菜青虫", "Cabbage Worm", 14),
        PestLabelEntry("pest_leafminer", "general", CatalogType.PEST, "潜叶蝇", "Leafminer", 14),
        PestLabelEntry("pest_whitefly", "general", CatalogType.PEST, "白粉虱", "Whitefly", 14),
        PestLabelEntry("pest_tobacco_budworm", "general", CatalogType.PEST, "烟青虫", "Tobacco Budworm", 21),

        // ============ V2.0 草害 5 类（可选） ============
        PestLabelEntry("weed_barnyard_grass", "general", CatalogType.WEED, "稗草", "Barnyard Grass", 0),
        PestLabelEntry("weed_crabgrass", "general", CatalogType.WEED, "马唐", "Crabgrass", 0),
        PestLabelEntry("weed_green_foxtail", "general", CatalogType.WEED, "狗尾草", "Green Foxtail", 0),
        PestLabelEntry("weed_lamb_quarters", "general", CatalogType.WEED, "藜", "Lamb's Quarters", 0),
        PestLabelEntry("weed_pigweed", "general", CatalogType.WEED, "苋", "Pigweed", 0)
    )

    /** 索引：按 code 查询 */
    val BY_CODE: Map<String, PestLabelEntry> = ENTRIES.associateBy { it.code }

    /** 索引：按 V1.0 legacyLabel 查询（双头过渡期兼容） */
    val BY_LEGACY: Map<String, PestLabelEntry> =
        ENTRIES.filter { it.legacyLabel != null }.associateBy { it.legacyLabel!! }

    /** 索引：按 cnName 查询 */
    val BY_CN: Map<String, PestLabelEntry> = ENTRIES.associateBy { it.cnName }

    /** 按作物分组（农技搜索 UI 用） */
    val BY_CROP: Map<String, List<PestLabelEntry>> = ENTRIES.groupBy { it.crop }

    /** 按类型分组（双头模型路由用） */
    val BY_TYPE: Map<CatalogType, List<PestLabelEntry>> = ENTRIES.groupBy { it.type }

    /**
     * V1.0 兼容：旧模型输出 `Apple___Apple_scab` → 查到对应 [PestLabelEntry]
     */
    fun fromLegacyLabel(label: String): PestLabelEntry? = BY_LEGACY[label]

    /**
     * V2.0 主路径：模型输出短码 `apple_scab` → 查到对应 [PestLabelEntry]
     */
    fun fromCode(code: String): PestLabelEntry? = BY_CODE[code]

    /**
     * 中文兜底查询（搜索 UI 用）
     */
    fun fromCnName(cnName: String): PestLabelEntry? = BY_CN[cnName]

    /**
     * 取某作物下所有病害（不含 healthy，不含 pest/weed）
     */
    fun diseasesOf(crop: String): List<PestLabelEntry> =
        BY_CROP[crop]?.filter { it.type == CatalogType.DISEASE } ?: emptyList()

    /**
     * 取所有害虫标签（用于害虫检测头训练标签对齐）
     */
    fun allPests(): List<PestLabelEntry> = BY_TYPE[CatalogType.PEST] ?: emptyList()

    /**
     * V1.0 兼容：仍提供 LABEL_MAP 风格的 code → cnName 映射
     *
     * 用于 [PlantDiseaseLabels.LABEL_MAP] 的无缝替换。
     */
    val LEGACY_LABEL_MAP: Map<String, String> = BY_LEGACY.mapValues { it.value.cnName }

    /**
     * V1.0 兼容：将模型标签转为中文名
     *
     * - 优先匹配 V1.0 legacyLabel
     * - 失败则尝试 V2.0 code
     * - 仍失败则做兜底处理（与 V1.0 行为一致）
     */
    fun toCnName(label: String): String {
        return fromLegacyLabel(label)?.cnName
            ?: fromCode(label)?.cnName
            ?: label.split("___").lastOrNull()?.replace("_", " ")
            ?: label
    }

    /**
     * 取安全间隔期默认值（识别后建议用药时自动填充）
     */
    fun safetyIntervalDays(code: String): Int =
        fromCode(code)?.safetyIntervalDays ?: 14
}
