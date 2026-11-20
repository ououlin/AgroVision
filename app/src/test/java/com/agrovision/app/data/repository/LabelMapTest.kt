package com.agrovision.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DetectionRepository 标签映射逻辑的单元测试
 *
 * 验证知识目录中的 39 项 PlantVillage/背景标签到中文名的映射正确性。
 * 测试与 DetectionRepository.toCnName 的逻辑保持一致。
 */
class LabelMapTest {

    // 与 DetectionRepository.labelMap 保持一致
    private val labelMap = mapOf(
        "Apple___Apple_scab" to "苹果黑星病",
        "Apple___Black_rot" to "苹果黑腐病",
        "Apple___Cedar_apple_rust" to "苹果锈病",
        "Apple___healthy" to "苹果(健康)",
        "Background_without_leaves" to "无叶片背景",
        "Blueberry___healthy" to "蓝莓(健康)",
        "Cherry___Powdery_mildew" to "樱桃白粉病",
        "Cherry___healthy" to "樱桃(健康)",
        "Corn___Cercospora_leaf_spot Gray_leaf_spot" to "玉米灰斑病",
        "Corn___Common_rust" to "玉米锈病",
        "Corn___Northern_Leaf_Blight" to "玉米北方叶枯病",
        "Corn___healthy" to "玉米(健康)",
        "Grape___Black_rot" to "葡萄黑腐病",
        "Grape___Esca_(Black_Measles)" to "葡萄黑麻疹病",
        "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)" to "葡萄叶枯病",
        "Grape___healthy" to "葡萄(健康)",
        "Orange___Haunglongbing_(Citrus_greening)" to "柑橘黄龙病",
        "Peach___Bacterial_spot" to "桃细菌性穿孔病",
        "Peach___healthy" to "桃(健康)",
        "Pepper,_bell___Bacterial_spot" to "辣椒细菌性斑点病",
        "Pepper,_bell___healthy" to "辣椒(健康)",
        "Potato___Early_blight" to "马铃薯早疫病",
        "Potato___Late_blight" to "马铃薯晚疫病",
        "Potato___healthy" to "马铃薯(健康)",
        "Raspberry___healthy" to "树莓(健康)",
        "Soybean___healthy" to "大豆(健康)",
        "Squash___Powdery_mildew" to "南瓜白粉病",
        "Strawberry___Leaf_scorch" to "草莓叶焦病",
        "Strawberry___healthy" to "草莓(健康)",
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

    // 与 DetectionRepository.toCnName 逻辑一致
    private fun toCnName(label: String): String =
        labelMap[label] ?: label.split("___").lastOrNull()?.replace("_", " ") ?: label

    @Test
    fun `label map contains 38 PlantVillage classes plus background`() {
        assertEquals(39, labelMap.size)
    }

    @Test
    fun `known labels map to Chinese names`() {
        assertEquals("苹果黑星病", toCnName("Apple___Apple_scab"))
        assertEquals("马铃薯晚疫病", toCnName("Potato___Late_blight"))
        assertEquals("番茄晚疫病", toCnName("Tomato___Late_blight"))
        assertEquals("柑橘黄龙病", toCnName("Orange___Haunglongbing_(Citrus_greening)"))
        assertEquals("葡萄黑腐病", toCnName("Grape___Black_rot"))
        assertEquals("玉米锈病", toCnName("Corn___Common_rust"))
    }

    @Test
    fun `unknown label falls back to formatted English`() {
        // 未知标签格式化：取 ___ 后部分，_ 替换为空格
        val result = toCnName("SomeCrop___Some_Disease")
        assertEquals("Some Disease", result)
    }

    @Test
    fun `unknown label without separator returns as-is`() {
        val result = toCnName("UnknownLabel")
        assertEquals("UnknownLabel", result)
    }

    @Test
    fun `healthy labels contain parenthetical notation`() {
        val healthyLabels = labelMap.filter { it.key.endsWith("healthy") }
        assertTrue(healthyLabels.isNotEmpty())
        assertTrue(healthyLabels.values.all { it.contains("(健康)") })
    }

    @Test
    fun `all tomato diseases are mapped`() {
        val tomatoLabels = labelMap.filter { it.key.startsWith("Tomato___") }
        // 番茄有 10 个类别（9 种病害 + 1 个健康）
        assertEquals(10, tomatoLabels.size)
    }
}
