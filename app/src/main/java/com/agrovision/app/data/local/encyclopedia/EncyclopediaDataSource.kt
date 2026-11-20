package com.agrovision.app.data.local.encyclopedia

import android.content.Context
import com.agrovision.app.domain.model.EncyclopediaImageCatalog
import com.agrovision.app.domain.model.PestInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Singleton

/**
 * 病虫百科数据源
 * 从 assets/encyclopedia.json 加载数据
 */
@Singleton
class EncyclopediaDataSource(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private var pestCache: List<PestInfo>? = null

    /**
     * 加载所有病虫百科数据
     * 使用 @Synchronized 保证多协程并发时的线程安全，避免重复加载
     */
    @Synchronized
    fun loadAll(): List<PestInfo> {
        pestCache?.let { return it }

        return try {
            val json = context.assets
                .open("encyclopedia.json")
                .bufferedReader()
                .use { it.readText() }

            val type = object : TypeToken<List<PestInfo>>() {}.type
            val parsed: List<PestInfo> = gson.fromJson(json, type)
            // Keep the source knowledge base complete, but expose only entries with a
            // real image in the app until the remaining image licenses are archived.
            val list = parsed.mapNotNull { pest ->
                val imageUrl = if (pest.imageUrl.isNotBlank()) {
                    pest.imageUrl
                } else {
                    EncyclopediaImageCatalog.assetUri(pest.enName).orEmpty()
                }
                if (imageUrl.isBlank()) null else pest.copy(imageUrl = imageUrl)
            }
            pestCache = list
            Timber.d("Loaded ${list.size} image-backed encyclopedia entries from ${parsed.size} source entries")
            list
        } catch (e: IOException) {
            Timber.e(e, "Failed to load encyclopedia.json")
            emptyList()
        }
    }

    /**
     * 按英文名查找
     *
     * 优先精确匹配；失败时解析 PlantVillage 格式标签（如 `Apple___Apple_scab`）
     * 做模糊搜索兜底；知识目录覆盖范围独立于当前模型输出类别。
     */
    fun findByEnglishName(name: String): PestInfo? {
        val all = loadAll()
        // 1. 精确匹配
        all.find { it.enName.equals(name, ignoreCase = true) }?.let { return it }

        // 2. PlantVillage 格式模糊匹配：解析 `Crop___Disease` 提取关键词搜索
        if (name.contains("___")) {
            val parts = name.split("___", limit = 2)
            val crop = parts[0].lowercase().replace(",", "").trim()
            val disease = parts[1].lowercase().replace("_", " ").trim()
            // 优先匹配病害关键词（更精准），如 "apple scab" 匹配 enName 含 "apple_scab" 的条目
            val diseaseKey = parts[1].lowercase()
            all.find { pest ->
                pest.enName.lowercase().contains(diseaseKey) ||
                pest.cnName.contains(parts[1].split("_").firstOrNull() ?: "", ignoreCase = true)
            }?.let { return it }
            // 再按作物 + 病害关键词组合匹配
            all.find { pest ->
                val pestEn = pest.enName.lowercase()
                pestEn.contains(crop) && pestEn.contains(disease)
            }?.let { return it }
        }

        return null
    }

    /**
     * 按中文名精确查找
     */
    fun findByChineseName(name: String): PestInfo? {
        return loadAll().find {
            it.cnName.equals(name, ignoreCase = true)
        }
    }

    /**
     * 按中文名搜索（模糊匹配）
     */
    fun searchByChineseName(keyword: String): List<PestInfo> {
        return loadAll().filter {
            it.cnName.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * 按英文名搜索（模糊匹配）
     */
    fun searchByEnglishName(keyword: String): List<PestInfo> {
        return loadAll().filter {
            it.enName.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * 全文搜索
     */
    fun search(keyword: String): List<PestInfo> {
        val lowerKeyword = keyword.lowercase()
        return loadAll().filter {
            it.cnName.lowercase().contains(lowerKeyword) ||
                    it.enName.lowercase().contains(lowerKeyword) ||
                    it.description.lowercase().contains(lowerKeyword) ||
                    it.symptoms.any { s -> s.lowercase().contains(lowerKeyword) }
        }
    }

    /**
     * 获取所有病害类别
     */
    fun getCategories(): List<String> {
        return loadAll()
            .map { it.category }
            .distinct()
            .sorted()
    }
}
