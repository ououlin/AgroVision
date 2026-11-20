package com.agrovision.app.config

/**
 * 功能开关
 *
 * V2.0 起按版本分组管理：V2.0 开关默认开启核心 Epic，V3.0 项保持关闭。
 * 灰度发布时可在 BuildConfig 里覆盖（详见 [isV2GradualRollout]）。
 *
 * 核心闭环（V2.0）：拍照识别 → AI诊断 → 病虫百科 → 历史记录
 *                  ↘ 地块管理 → 用药计划 → 农情顾问提醒
 */
object FeatureFlag {

    // ============ V2.0 开关（默认开启） ============

    /**
     * V2.0 账号体系：手机号 + 短信验证码登录；游客模式保留但 14 天后提示绑定。
     * 关闭时回落到 V1.0 的 [AuthRepository.loginAsGuest] 硬编码游客模式。
     */
    val isV2AccountEnabled: Boolean = false

    /**
     * V2.0 个人农场模型：地块、用药记录、识别记录结构化绑定。
     * 关闭时 PlotEntity/TreatmentEntity 仍存在于 schema，但 UI 入口隐藏。
     */
    val isV2FarmEnabled: Boolean = true

    /**
     * V2.0 云同步：仅同步元数据（不含原图，缩略图 ≤ 200×200）。
     * 依赖 [isV2AccountEnabled]；关闭时 SyncService 仍可被调用但不自动触发。
     */
    val isV2CloudSyncEnabled: Boolean = false

    /**
     * V2.0 农情顾问：物候提醒 + 病害预警 + 用药安全间隔期倒计时。
     * 使用 WorkManager + 本地通知（不上 FCM）。
     */
    val isV2AdvisorEnabled: Boolean = true

    /**
     * V2.0 农技搜索入口：把 KnowledgeBase 同义词 + 拼音 + 意图分类产品化为独立入口。
     * 与 AI 助手区分：搜索=检索式（RAG only，不调用 LLM，离线可用）。
     */
    val isV2AgriSearchEnabled: Boolean = true

    /**
     * V2.0 双头模型路由：PestDetector + DiseaseDetector + MobileNetV3 路由。
     * 关闭时回落到当前随包的 8 类试验分类模型。
     */
    val isV2DualModelEnabled: Boolean = true

    /**
     * V2.0 灰度发布：通过 BuildConfig.GRADUAL_ROLLOUT_PERCENT 控制，0=全关，100=全量。
     * 灰度项独立于功能开关，用于在已开启的功能内进一步控制曝光比例。
     */
    val isV2GradualRollout: Boolean = false

    // ============ V3.0 / 其他开关（保持关闭） ============

    /** V3.0 智慧平台模块（GIS / 巡检 / 告警 / 监控 / 巡检中心）— 空壳，关闭 */
    val isV3Enabled: Boolean = false

    /**
     * OCR 识别模块
     *
     * V2.0 已引入 ML Kit Text Recognition 依赖，但产品定位上「农技搜索」走
     * KnowledgeBase 检索而非 OCR 提取，所以 OCR 入口仍关闭，代码保留备查。
     */
    val isOcrEnabled: Boolean = false
}
