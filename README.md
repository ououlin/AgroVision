# AgroVision 农智眼

基于 Android + ONNX Runtime 的智能农业病害识别与农技决策应用，集成 AI 大模型问诊、本地规则引擎兜底、RAG 知识库检索等能力。

> **当前版本：2.0.0**（个人农技顾问 / 2026 比赛构建）  
> **文档更新：2026-08-11**
> V2.0 已落地隐私门闩、本地用户模式、地块与用药记录、农技搜索、农情顾问规则和手动数据备份。账号与云同步仅保留客户端骨架，因生产后端尚未上线，2.0.0 首发通过 `FeatureFlag` 关闭。V3.0 智慧平台能力同样不在 2.0 版本中开放。

## 功能特性

### 已交付（2.0）

- **AI 病害识别** — 随包 MobileNetV3-Small ONNX 支持 PlantVillage 38 类、14 种作物，输入 224×224，离线输出 Top-3 分类结果
- **行动式问诊结果** — 按“今天先做、原因、用药参考、复查时间”组织建议；在线能力不可用时降级到本地规则引擎
- **问农技** — 支持日常对话与多轮上下文；可从问诊结果携带上下文继续追问，离线保留基础交流和农业知识问答
- **病虫百科** — 本地知识库支持中英文检索；原始知识库 74 个条目，当前 App 先展示其中 26 个有真实 PlantVillage 示例图的条目
- **历史记录** — Room 持久化，支持回溯与复诊
- **个人农场模型** — 农事总览、地块管理、地块详情、用药记录、安全间隔期与采收提醒
- **农技搜索** — KnowledgeBase 同义词、拼音首字母、意图分类检索，离线可用，不调用 LLM
- **植保用药** — 按病虫百科推荐有效成分，依据产品登记标签计算总药量、总用水和分桶配比；内置农资展示目录、采购清单，并可保存到地块用药记录
- **农情顾问** — 物候规则、天气病害预警、用药安全间隔期提醒，使用 WorkManager 本地调度
- **数据备份** — Room 四表 JSON 手动备份/恢复；系统自动云备份已关闭
- **设置中心** — AI Provider/API Key 管理、主题切换、缓存和隐私合规设置
- **隐私门闩** — 用户同意后才初始化认证流程、注册后台提醒及进入业务页面

### V2.0 当前限制

- 当前随包 `model/yolo11n.onnx` 的真实输出维度为 `[1, 38]`。文件名是历史兼容名称，实际架构为 MobileNetV3-Small 分类模型，不提供目标框。`PestDetector` 代码已接入，但独立害虫检测资产 `model/pest_detector.onnx` 尚未随包发布。
- 38 类模型在固定随机种子划分的 PlantVillage color 测试集上取得 Top-1 `99.1808%`、Top-3 `99.9511%`。该结果属于同分布受控叶片图像测试，不能替代真实田间外部验证。
- 账号和云同步在 2.0.0 首发关闭。客户端代码按“不传原图、只传元数据”保留；必须完成生产 API、短信服务、服务端账号删除和安全验收后才能开放。
- OCR、GIS、巡检、告警、机器人、多智能体大屏属于 V3.0/实验模块，默认不可见，不作为 2.0 可用功能承诺。

### 规划中（V3.0 智慧平台，当前关闭）

以下模块代码已保留但通过 `config/FeatureFlag.kt` 关闭，UI/导航不可见，留作未来迭代基础：

- **多智能体编排** — 病害识别 → 诊断 → 报告生成流水线
- **巡检中心** — GIS 地图、巡检任务、告警中心、系统监控
- **OCR 文字识别** — ML Kit 中英文文字识别
- **云端协同** — 多角色、多设备、GIS 级数据协作
- **巡检机器人** — 机器人控制与调度

> ⚠️ 这些模块当前含未落地的占位实现（如 `AgentOrchestrator.getSystemStatus()` 的部分状态字段为示例数据），不应对外承诺功能可用性。

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| 架构 | Clean Architecture（data/domain/ui 分层）+ MVVM |
| DI | Hilt 2.52 |
| 导航 | Navigation Compose |
| 数据库 | Room 2.6 |
| 网络 | Retrofit 2.9 + OkHttp 4.12 |
| 推理 | ONNX Runtime 1.17 / TensorFlow Lite 2.13 |
| LLM | DeepSeek 兼容 OpenAI Chat Completions API |
| 日志 | Timber |

## 项目结构

```
AgroVision/
├── app/                          # Android 主模块
│   └── src/main/java/com/agrovision/app/
│       ├── config/               # 功能开关 (FeatureFlag)
│       ├── data/                 # 数据层
│       │   ├── agent/            # 多智能体（V3.0，关闭）
│       │   ├── diagnosis/        # 本地诊断规则引擎
│       │   ├── llm/              # LLM Provider 与管理器
│       │   ├── local/            # Room DAO、DataStore、本地数据源
│       │   ├── advisor/          # V2.0 物候/天气/提醒规则
│       │   ├── ocr/              # OCR 处理（实验入口关闭）
│       │   ├── rag/              # 知识库检索
│       │   ├── remote/           # V2.0 API 接口、元数据云同步骨架
│       │   ├── repository/       # 仓库实现
│       │   └── robot/            # 巡检机器人（V3.0，关闭）
│       ├── di/                   # Hilt 依赖注入模块
│       ├── domain/               # 领域模型
│       ├── ml/                   # ONNX/TFLite 检测器
│       ├── ui/                   # Compose UI（按 screen 分包）
│       └── util/                 # 工具类
├── gradle/libs.versions.toml     # Version Catalog 统一版本管理
├── tools/                        # 模型训练与转换脚本（Python）
│   ├── agrovision_cls/           # 分类模型权重
│   ├── plant_dataset/            # 训练数据集
│   ├── train_*.py                # 训练脚本
│   └── convert_*.py / export_*.py# 模型转换与导出
├── docs/                         # 文档
│   ├── PROJECT_MANUAL.md         # 项目说明书（当前项目事实总入口）
│   ├── USER_MANUAL.md            # 用户手册（唯一源）
│   ├── MODEL_TRAINING_GUIDE.md   # 模型训练指南
│   ├── COMPETITION_PLAN_2026.md  # 2026 数据要素大赛方案与排期
│   ├── DATA_PROVENANCE.md        # 数据来源、许可与血缘台账
│   └── assets/                   # 文档图片资源
└── output/                       # 构建产物（不入库，见 .gitignore）
    └── doc/                      # 用户手册 docx 输出
```

## 快速开始

### 环境要求

- Android Studio Ladybug+
- JDK 17
- Android SDK 35（minSdk 26）
- Python 3.10+（仅训练/转换模型时需要）

### 构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（需配置签名，见下方「Release 签名」）
./gradlew assembleRelease
```

### Release 签名（上架前必做）

未配置正式 jks 时，`assembleRelease` 会回落到 debug 签名，**产生的 APK 不可上架**，仅用于本地混淆/崩溃自查。上架前需在 `gradle.properties` 或环境变量中配置：

```properties
KEYSTORE_FILE=/path/to/release.jks
KEYSTORE_PASSWORD=****
KEY_ALIAS=agrovision
KEY_PASSWORD=****
```

### 模型训练

训练与模型导出脚本位于 `tools/`，详见 `docs/MODEL_TRAINING_GUIDE.md`。

### 2026 数据要素大赛

- 项目现状、系统架构、功能、数据、模型、部署和限制见 `docs/PROJECT_MANUAL.md`。
- 比赛定位、评分证据、改造优先级与提交排期见 `docs/COMPETITION_PLAN_2026.md`。
- 数据集、公共数据、用户数据和模型资产的来源及许可状态见 `docs/DATA_PROVENANCE.md`。
- 未在测试产物中留存原始记录的指标不得用于报名材料、测试报告或答辩。

## 配置

- **API Key** — 在应用「设置 → API 管理」中配置 LLM API Key，启用在线 AI 诊断与问答
- **功能开关** — `config/FeatureFlag.kt` 控制 V2.0 账号、农场、云同步、农情顾问、农技搜索与 V3.0 巡检/OCR 等功能可见性

## 产品路线图

| 版本 | 状态 | 说明 |
|------|------|------|
| 1.0.0 | ✅ 已完成 | 核心闭环：拍照识别 → AI 诊断 → 病虫百科 → 历史记录 |
| 2.0.0 | ✅ 当前版本 | 本地个人农技顾问：我的农场、用药记录、农技搜索、农情提醒、手动备份；账号/云同步暂不开放 |
| 3.0.0 | 🔬 规划中 | 智慧平台：多智能体编排、巡检中心、GIS、告警、监控、OCR、巡检机器人 |

> 版本号说明：对外版本叙事以 `app/build.gradle.kts` 的 `versionName` 为准。V3.0 相关代码因历史命名保留在仓库中，但其功能未闭环，不构成对外承诺。

## License

私有项目，保留所有权利。
