package com.agrovision.app.data.repository

import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/**
 * 历史记录仓库
 */
@Singleton
class HistoryRepository(
    private val historyDao: HistoryDao
) {

    fun getAllHistory(): Flow<List<HistoryEntity>> {
        return historyDao.getAllHistory()
    }

    fun searchHistory(keyword: String): Flow<List<HistoryEntity>> {
        return historyDao.searchHistory(keyword)
    }

    suspend fun getById(id: Long): HistoryEntity? {
        return historyDao.getHistoryById(id)
    }

    suspend fun insert(entity: HistoryEntity): Long {
        return historyDao.insert(entity)
    }

    suspend fun update(entity: HistoryEntity) {
        historyDao.update(entity)
    }

    suspend fun delete(entity: HistoryEntity) {
        historyDao.delete(entity)
    }

    suspend fun deleteById(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun deleteAll() {
        historyDao.deleteAll()
    }

    suspend fun getCount(): Int {
        return historyDao.getCount()
    }

    suspend fun getTodayCount(startOfDay: Long): Int {
        return historyDao.getTodayCount(startOfDay)
    }

    suspend fun toggleFavorite(id: Long) {
        historyDao.toggleFavorite(id)
    }

    suspend fun getRecentHistory(limit: Int): List<com.agrovision.app.data.local.entity.HistoryEntity> {
        return historyDao.getRecentHistory(limit)
    }
}
