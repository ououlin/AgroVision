package com.agrovision.app.di

import android.content.Context
import com.agrovision.app.data.local.encyclopedia.EncyclopediaDataSource
import com.agrovision.app.data.ocr.OcrProcessor
import com.agrovision.app.ml.YoloDetector
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 核心 DI 模块 — Gson、Context 相关数据源等基础依赖。
 *
 * 网络    → [NetworkModule]
 * 仓库    → [RepositoryModule]
 * LLM    → [LlmModule]
 * 智能体  → [AgentModule]
 * 数据库  → [DatabaseModule]
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides @Singleton
    fun provideEncyclopediaDataSource(
        @ApplicationContext context: Context
    ): EncyclopediaDataSource = EncyclopediaDataSource(context)

    @Provides @Singleton
    fun provideYoloDetector(
        @ApplicationContext context: Context
    ): YoloDetector = YoloDetector(context)

    @Provides @Singleton
    fun provideOcrProcessor(): OcrProcessor = OcrProcessor()
}
