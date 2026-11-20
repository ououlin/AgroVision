package com.agrovision.app.di

import com.agrovision.app.data.diagnosis.LocalDiagnosisEngine
import com.agrovision.app.data.llm.DeepSeekProvider
import com.agrovision.app.data.llm.LlmConfig
import com.agrovision.app.data.llm.LlmManager
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.rag.KnowledgeBase
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Singleton

/**
 * LLM DI 模块 — 大模型配置、Provider、知识库与调度管理器。
 *
 * [provideDefaultLlmConfig] 启动时从 [SettingsDataStore] 读取持久化配置，
 * 保证 DeepSeekProvider 与 LlmManager 拿到同一份初始配置；
 * 运行时配置变更由 [LlmManager.updateConfig] 统一处理。
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideDefaultLlmConfig(settingsDataStore: SettingsDataStore): LlmConfig = try {
        runBlocking {
            val settings = settingsDataStore.settings.first()
            LlmConfig.fromAppSettings(settings)
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to load LLM config at startup, fallback to default DeepSeek")
        LlmConfig.defaultDeepSeek()
    }

    @Provides
    @Singleton
    fun provideDeepSeekProvider(
        gson: Gson,
        okHttpClient: OkHttpClient,
        localDiagnosisEngine: LocalDiagnosisEngine,
        llmConfig: LlmConfig
    ): DeepSeekProvider = DeepSeekProvider(gson, okHttpClient, localDiagnosisEngine, llmConfig)

    @Provides
    @Singleton
    fun provideKnowledgeBase(
        encyclopediaRepository: EncyclopediaRepository
    ): KnowledgeBase = KnowledgeBase(encyclopediaRepository)

    @Provides
    @Singleton
    fun provideLlmManager(
        deepSeekProvider: DeepSeekProvider,
        knowledgeBase: KnowledgeBase,
        settingsDataStore: SettingsDataStore
    ): LlmManager = LlmManager(deepSeekProvider, knowledgeBase, settingsDataStore)
}
