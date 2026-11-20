package com.agrovision.app.domain.model

import java.util.Locale

object EncyclopediaImageCatalog {
    private const val ASSET_URI_PREFIX = "file:///android_asset/encyclopedia/"

    fun assetUri(label: String): String? {
        val normalizedLabel = normalizeLegacyLabel(label)
        if (PestLabelCatalog.fromLegacyLabel(normalizedLabel) == null) return null

        val fileName = normalizedLabel
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        return "$ASSET_URI_PREFIX$fileName.jpg"
    }

    private fun normalizeLegacyLabel(label: String): String = when (label) {
        "Cherry___Powdery_mildew" -> "Cherry_(including_sour)___Powdery_mildew"
        "Corn___Cercospora_leaf_spot Gray_leaf_spot" ->
            "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot"
        "Corn___Common_rust" -> "Corn_(maize)___Common_rust_"
        "Corn___Northern_Leaf_Blight" -> "Corn_(maize)___Northern_Leaf_Blight"
        else -> label
    }
}
