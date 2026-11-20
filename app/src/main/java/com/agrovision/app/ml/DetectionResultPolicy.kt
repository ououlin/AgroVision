package com.agrovision.app.ml

/** Shared filtering rules for classification results shown by the app. */
internal object DetectionResultPolicy {

    fun normalizeCropType(cropType: String?): String? = when (cropType?.trim()) {
        "苹果" -> "apple"
        "蓝莓" -> "blueberry"
        "樱桃" -> "cherry"
        "玉米" -> "corn"
        "葡萄" -> "grape"
        "柑橘" -> "orange"
        "桃" -> "peach"
        "辣椒" -> "pepper"
        "马铃薯" -> "potato"
        "树莓" -> "raspberry"
        "大豆" -> "soybean"
        "南瓜" -> "squash"
        "草莓" -> "strawberry"
        "番茄" -> "tomato"
        else -> null
    }

    fun isBackground(label: String): Boolean {
        val normalized = label.trim().lowercase()
        return normalized == "background_without_leaves" || normalized == "background"
    }
}
