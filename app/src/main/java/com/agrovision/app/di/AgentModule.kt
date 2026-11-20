package com.agrovision.app.di

import com.agrovision.app.data.agent.AgentOrchestrator
import com.agrovision.app.data.agent.AgricultureDiagnosisAgent
import com.agrovision.app.data.agent.DiseaseRecognitionAgent
import com.agrovision.app.data.agent.ReportGeneratorAgent
import com.agrovision.app.data.repository.DetectionRepository
import com.agrovision.app.data.repository.EncyclopediaRepository
import com.agrovision.app.data.robot.RobotManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 多智能体 DI 模块 — V3.0 病害识别/诊断/报告智能体、编排器与巡检机器人。
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides @Singleton
    fun provideDiseaseRecognitionAgent(
        detectionRepository: DetectionRepository
    ): DiseaseRecognitionAgent = DiseaseRecognitionAgent(detectionRepository)

    @Provides @Singleton
    fun provideDiagnosisAgent(
        encyclopediaRepository: EncyclopediaRepository
    ): AgricultureDiagnosisAgent = AgricultureDiagnosisAgent(encyclopediaRepository)

    @Provides @Singleton
    fun provideReportGeneratorAgent(): ReportGeneratorAgent = ReportGeneratorAgent()

    @Provides @Singleton
    fun provideAgentOrchestrator(
        recognitionAgent: DiseaseRecognitionAgent,
        diagnosisAgent: AgricultureDiagnosisAgent,
        reportAgent: ReportGeneratorAgent
    ): AgentOrchestrator = AgentOrchestrator(recognitionAgent, diagnosisAgent, reportAgent)

    @Provides @Singleton
    fun provideRobotManager(): RobotManager = RobotManager()
}
