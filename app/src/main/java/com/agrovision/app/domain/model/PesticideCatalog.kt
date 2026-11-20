package com.agrovision.app.domain.model

enum class PesticideCategory(val label: String) {
    FUNGICIDE("杀菌剂"),
    INSECTICIDE("杀虫剂"),
    BIOLOGICAL("生物制剂"),
    ADJUVANT("助剂")
}

data class PesticideProduct(
    val id: String,
    val name: String,
    val activeIngredient: String,
    val formulation: String,
    val category: PesticideCategory,
    val specification: String,
    val referencePrice: Double,
    val toxicity: String,
    val registeredTargets: List<String>,
    val badge: String = ""
)

object PesticideCatalog {
    val products: List<PesticideProduct> = listOf(
        PesticideProduct("difenoconazole", "10%苯醚甲环唑水分散粒剂", "苯醚甲环唑", "水分散粒剂", PesticideCategory.FUNGICIDE, "100克", 18.80, "低毒", listOf("黑星病", "炭疽病", "叶斑病"), "常用"),
        PesticideProduct("mancozeb", "80%代森锰锌可湿性粉剂", "代森锰锌", "可湿性粉剂", PesticideCategory.FUNGICIDE, "500克", 26.90, "低毒", listOf("早疫病", "晚疫病", "霜霉病", "黑腐病"), "保护性"),
        PesticideProduct("pyraclostrobin", "25%吡唑醚菌酯悬浮剂", "吡唑醚菌酯", "悬浮剂", PesticideCategory.FUNGICIDE, "100毫升", 31.50, "低毒", listOf("白粉病", "炭疽病", "叶枯病"), "广谱"),
        PesticideProduct("tebuconazole", "43%戊唑醇悬浮剂", "戊唑醇", "悬浮剂", PesticideCategory.FUNGICIDE, "100毫升", 22.80, "低毒", listOf("锈病", "白粉病", "黑星病")),
        PesticideProduct("azoxystrobin", "25%嘧菌酯悬浮剂", "嘧菌酯", "悬浮剂", PesticideCategory.FUNGICIDE, "100毫升", 28.60, "低毒", listOf("霜霉病", "晚疫病", "叶斑病")),
        PesticideProduct("dimethomorph", "50%烯酰吗啉可湿性粉剂", "烯酰吗啉", "可湿性粉剂", PesticideCategory.FUNGICIDE, "100克", 24.90, "低毒", listOf("霜霉病", "晚疫病"), "卵菌"),
        PesticideProduct("zhongshengmycin", "3%中生菌素可湿性粉剂", "中生菌素", "可湿性粉剂", PesticideCategory.BIOLOGICAL, "100克", 16.80, "低毒", listOf("细菌性斑点病", "溃疡病", "软腐病"), "生物源"),
        PesticideProduct("kasugamycin", "6%春雷霉素可湿性粉剂", "春雷霉素", "可湿性粉剂", PesticideCategory.BIOLOGICAL, "100克", 19.90, "低毒", listOf("稻瘟病", "细菌性病害"), "生物源"),
        PesticideProduct("imidacloprid", "10%吡虫啉可湿性粉剂", "吡虫啉", "可湿性粉剂", PesticideCategory.INSECTICIDE, "100克", 12.80, "低毒", listOf("蚜虫", "白粉虱", "蓟马"), "刺吸害虫"),
        PesticideProduct("acetamiprid", "20%啶虫脒可溶粉剂", "啶虫脒", "可溶粉剂", PesticideCategory.INSECTICIDE, "100克", 15.60, "低毒", listOf("蚜虫", "白粉虱", "介壳虫")),
        PesticideProduct("abamectin", "5%阿维菌素乳油", "阿维菌素", "乳油", PesticideCategory.INSECTICIDE, "100毫升", 13.90, "中等毒", listOf("红蜘蛛", "潜叶蝇", "小菜蛾"), "杀虫杀螨"),
        PesticideProduct("spirodiclofen", "24%螺螨酯悬浮剂", "螺螨酯", "悬浮剂", PesticideCategory.INSECTICIDE, "100毫升", 36.80, "低毒", listOf("红蜘蛛", "叶螨"), "杀螨剂"),
        PesticideProduct("emamectin", "5%甲维盐水分散粒剂", "甲维盐", "水分散粒剂", PesticideCategory.INSECTICIDE, "50克", 17.50, "低毒", listOf("食心虫", "菜青虫", "烟青虫")),
        PesticideProduct("bt", "32000IU/毫克苏云金杆菌", "苏云金杆菌", "可湿性粉剂", PesticideCategory.BIOLOGICAL, "100克", 21.80, "低毒", listOf("菜青虫", "鳞翅目幼虫"), "绿色防控"),
        PesticideProduct("silicone", "有机硅喷雾助剂", "聚醚改性硅氧烷", "水剂", PesticideCategory.ADJUVANT, "100毫升", 9.90, "低风险", listOf("改善展着", "提高附着"), "助剂")
    )

    fun search(
        query: String = "",
        category: PesticideCategory? = null,
        recommendedIngredients: List<String> = emptyList()
    ): List<PesticideProduct> {
        val keyword = query.trim().lowercase()
        return products.filter { product ->
            val categoryMatches = category == null || product.category == category
            val queryMatches = keyword.isEmpty() || listOf(
                product.name,
                product.activeIngredient,
                product.category.label,
                product.registeredTargets.joinToString(" ")
            ).any { it.lowercase().contains(keyword) }
            categoryMatches && queryMatches
        }.sortedByDescending { product ->
            recommendedIngredients.any { ingredient -> product.matchesIngredient(ingredient) }
        }
    }
}

fun PesticideProduct.matchesIngredient(ingredient: String): Boolean {
    val normalizedProduct = normalizeIngredient(activeIngredient)
    val normalizedQuery = normalizeIngredient(ingredient)
    return normalizedQuery.isNotEmpty() &&
        (normalizedProduct.contains(normalizedQuery) || normalizedQuery.contains(normalizedProduct))
}

private fun normalizeIngredient(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9\\p{L}]+"), "")
