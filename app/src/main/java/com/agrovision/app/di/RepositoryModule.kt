package com.agrovision.app.di

import android.content.Context
import com.agrovision.app.data.local.dao.FeedbackDao
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.local.dao.PlotDao
import com.agrovision.app.data.local.dao.TreatmentDao
import com.agrovision.app.data.local.encyclopedia.EncyclopediaDataSource
import com.agrovision.app.data.llm.LlmManager
import com.agrovision.app.data.repository.BackupRepository
import com.agrovision.app.data.repository.DetectionRepository
import com.agrovision.app.data.repository.DiagnosisRepository
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.agrovision.app.data.repository.FeedbackRepository
import com.agrovision.app.data.repository.HistoryRepository
import com.agrovision.app.data.repository.PlotRepository
import com.agrovision.app.data.repository.TreatmentRepository
import com.agrovision.app.data.repository.WeatherRepository
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.ml.ModelRouter
import com.agrovision.app.ml.OnnxDetector
import com.agrovision.app.ml.YoloDetector
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * 仓库 DI 模块 — 统一提供 10 个 Repository 的构造依赖。
 *
 * V1.0 7 个：Detection / Encyclopedia / History / Feedback / Weather / Backup / Diagnosis
 * V2.0 +3 个：Plot（地块）、Treatment（用药记录）
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideDetectionRepository(
        modelRouter: ModelRouter,
        onnxDetector: OnnxDetector,
        yoloDetector: YoloDetector,
        encyclopediaDataSource: EncyclopediaDataSource,
        historyDao: HistoryDao
    ): DetectionRepository = DetectionRepository(
        modelRouter,
        onnxDetector,
        yoloDetector,
        encyclopediaDataSource,
        historyDao
    )

    @Provides @Singleton
    fun provideEncyclopediaRepository(
        encyclopediaDataSource: EncyclopediaDataSource
    ): EncyclopediaRepository = EncyclopediaRepository(encyclopediaDataSource)

    @Provides @Singleton
    fun provideHistoryRepository(
        historyDao: HistoryDao
    ): HistoryRepository = HistoryRepository(historyDao)

    @Provides @Singleton
    fun provideFeedbackRepository(
        feedbackDao: FeedbackDao
    ): FeedbackRepository = FeedbackRepository(feedbackDao)

    @Provides @Singleton
    fun provideWeatherRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        gson: Gson,
        settingsDataStore: SettingsDataStore
    ): WeatherRepository = WeatherRepository(context, okHttpClient, gson, settingsDataStore)

    @Provides @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        historyDao: HistoryDao,
        feedbackDao: FeedbackDao,
        plotDao: PlotDao,
        treatmentDao: TreatmentDao,
        gson: Gson
    ): BackupRepository = BackupRepository(context, historyDao, feedbackDao, plotDao, treatmentDao, gson)

    @Provides @Singleton
    fun provideDiagnosisRepository(
        llmManager: LlmManager,
        encyclopediaRepository: EncyclopediaRepository
    ): DiagnosisRepository = DiagnosisRepository(llmManager, encyclopediaRepository)

    // ============ V2.0 新增仓库 ============

    @Provides @Singleton
    fun providePlotRepository(
        plotDao: PlotDao
    ): PlotRepository = PlotRepository(plotDao)

    @Provides @Singleton
    fun provideTreatmentRepository(
        treatmentDao: TreatmentDao,
        plotRepository: PlotRepository
    ): TreatmentRepository = TreatmentRepository(treatmentDao, plotRepository)
}
