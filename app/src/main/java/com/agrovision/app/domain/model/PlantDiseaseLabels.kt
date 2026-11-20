package com.agrovision.app.domain.model

/**
 * PlantVillage 标签 → 中文名映射
 *
 * 分类模型输出的标签格式: `Apple___Apple_scab`，此处统一转换为中文病害名。
 * 从 [com.agrovision.app.data.repository.DetectionRepository] 抽离，集中管理。
 */
object PlantDiseaseLabels {

    val LABEL_MAP: Map<String, String> = mapOf(
        // Apple 苹果
        "Apple___Apple_scab" to "苹果黑星病",
        "Apple___Black_rot" to "苹果黑腐病",
        "Apple___Cedar_apple_rust" to "苹果锈病",
        "Apple___healthy" to "苹果(健康)",
        // Background
        "Background_without_leaves" to "无叶片背景",
        // Blueberry 蓝莓
        "Blueberry___healthy" to "蓝莓(健康)",
        // Cherry 樱桃
        "Cherry_(including_sour)___Powdery_mildew" to "樱桃白粉病",
        "Cherry_(including_sour)___healthy" to "樱桃(健康)",
        // Corn 玉米
        "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot" to "玉米灰斑病",
        "Corn_(maize)___Common_rust_" to "玉米锈病",
        "Corn_(maize)___Northern_Leaf_Blight" to "玉米北方叶枯病",
        "Corn_(maize)___healthy" to "玉米(健康)",
        // Grape 葡萄
        "Grape___Black_rot" to "葡萄黑腐病",
        "Grape___Esca_(Black_Measles)" to "葡萄黑麻疹病",
        "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)" to "葡萄叶枯病",
        "Grape___healthy" to "葡萄(健康)",
        // Orange 柑橘
        "Orange___Haunglongbing_(Citrus_greening)" to "柑橘黄龙病",
        // Peach 桃
        "Peach___Bacterial_spot" to "桃细菌性穿孔病",
        "Peach___healthy" to "桃(健康)",
        // Pepper 辣椒
        "Pepper,_bell___Bacterial_spot" to "辣椒细菌性斑点病",
        "Pepper,_bell___healthy" to "辣椒(健康)",
        // Potato 马铃薯
        "Potato___Early_blight" to "马铃薯早疫病",
        "Potato___Late_blight" to "马铃薯晚疫病",
        "Potato___healthy" to "马铃薯(健康)",
        // Raspberry 树莓
        "Raspberry___healthy" to "树莓(健康)",
        // Soybean 大豆
        "Soybean___healthy" to "大豆(健康)",
        // Squash 南瓜
        "Squash___Powdery_mildew" to "南瓜白粉病",
        // Strawberry 草莓
        "Strawberry___Leaf_scorch" to "草莓叶焦病",
        "Strawberry___healthy" to "草莓(健康)",
        // Tomato 番茄
        "Tomato___Bacterial_spot" to "番茄细菌性斑点病",
        "Tomato___Early_blight" to "番茄早疫病",
        "Tomato___Late_blight" to "番茄晚疫病",
        "Tomato___Leaf_Mold" to "番茄叶霉病",
        "Tomato___Septoria_leaf_spot" to "番茄斑枯病",
        "Tomato___Spider_mites Two-spotted_spider_mite" to "番茄红蜘蛛",
        "Tomato___Target_Spot" to "番茄靶斑病",
        "Tomato___Tomato_Yellow_Leaf_Curl_Virus" to "番茄黄化曲叶病毒病",
        "Tomato___Tomato_mosaic_virus" to "番茄花叶病毒病",
        "Tomato___healthy" to "番茄(健康)"
    )

    /** 将模型标签转为中文名，无映射时做兜底处理 */
    fun toCnName(label: String): String =
        LABEL_MAP[label] ?: label.split("___").lastOrNull()?.replace("_", " ") ?: label
}
