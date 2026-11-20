package com.agrovision.app.data.repository

import com.agrovision.app.data.local.dao.FeedbackDao
import com.agrovision.app.data.local.entity.DetectionFeedback
import timber.log.Timber
import javax.inject.Singleton

/**
 * 用户反馈仓库
 * 提供保存反馈和查询真实识别准确率的能力
 */
@Singleton
class FeedbackRepository(
    private val feedbackDao: FeedbackDao
) {

    /** 保存用户反馈 */
    suspend fun saveFeedback(historyId: Long, isCorrect: Boolean) {
        Timber.d("保存反馈: historyId=$historyId, isCorrect=$isCorrect")
        feedbackDao.insert(DetectionFeedback(historyId = historyId, isCorrect = isCorrect))
    }

    /**
     * 获取真实识别准确率
     * @return 0.0~1.0 的准确率；若无反馈数据返回 null
     */
    suspend fun getAccuracy(): Float? {
        val accuracy = feedbackDao.getAccuracy()
        return if (accuracy < 0) null else accuracy
    }

    /** 获取总反馈数 */
    suspend fun getFeedbackCount(): Int = feedbackDao.getCount()

    /** 查询某条历史的反馈记录 */
    suspend fun getFeedbackByHistoryId(historyId: Long): DetectionFeedback? {
        return feedbackDao.getByHistoryId(historyId)
    }
}
