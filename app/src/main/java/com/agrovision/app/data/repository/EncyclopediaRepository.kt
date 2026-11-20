package com.agrovision.app.data.repository

import com.agrovision.app.data.local.encyclopedia.EncyclopediaDataSource
import com.agrovision.app.domain.model.PestInfo
import javax.inject.Singleton

/**
 * 百科仓库
 */
@Singleton
class EncyclopediaRepository(
    private val encyclopediaDataSource: EncyclopediaDataSource
) {

    fun getAllPests(): List<PestInfo> {
        return encyclopediaDataSource.loadAll()
    }

    fun search(keyword: String): List<PestInfo> {
        if (keyword.isBlank()) return getAllPests()
        return encyclopediaDataSource.search(keyword)
    }

    fun getByEnglishName(name: String): PestInfo? {
        return encyclopediaDataSource.findByEnglishName(name)
    }

    fun getByChineseName(name: String): PestInfo? {
        return encyclopediaDataSource.findByChineseName(name)
    }

    fun getCategories(): List<String> {
        return encyclopediaDataSource.getCategories()
    }

    fun getByCategory(category: String): List<PestInfo> {
        return getAllPests().filter { it.category == category }
    }
}
