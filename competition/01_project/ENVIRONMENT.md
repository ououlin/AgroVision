# 运行环境与依赖清单

> 状态：根据 2026-08-05 本机与版本目录整理。正式提交前需在干净电脑复现并补齐精确 SDK 组件。

## 1. Android 工程

| 项目 | 版本/配置 |
|---|---|
| 操作系统 | Windows 11 10.0 amd64 |
| 当前 Gradle Launcher JDK | Oracle JDK 21.0.1 |
| 推荐提交构建 JDK | JDK 17，具体发行版和补丁版本待锁定 |
| Gradle Wrapper | 8.9 |
| Android Gradle Plugin | 8.7.0 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 26 |
| Java/Kotlin 字节码目标 | 17 |
| applicationId | `com.agrovision.app` |
| versionName / versionCode | `2.0.0` / `20000` |

本机 Android SDK 路径来自 `local.properties`：

```text
C:\Users\24875\AppData\Local\Android\Sdk
```

该路径不能写入正式复现脚本，应由复现者自己的 `local.properties` 提供。

## 2. 主要依赖

| 组件 | 版本 |
|---|---|
| Jetpack Compose BOM | 2024.09.03 |
| Hilt | 2.52 |
| Room | 2.6.1 |
| Navigation Compose | 2.7.6 |
| CameraX | 1.4.1 |
| ONNX Runtime Android | 1.17.1 |
| TensorFlow Lite | 2.13.0 |
| WorkManager | 2.9.1 |
| Retrofit | 2.9.0 |
| OkHttp | 4.12.0 |
| Gson | 2.10.1 |
| Coil | 2.5.0 |
| Timber | 5.0.1 |
| JUnit | 4.13.2 |

完整版本源为 `gradle/libs.versions.toml`。

## 3. 模型训练环境

训练脚本依赖 Python、PyTorch、torchvision、ONNX、ONNX Runtime 等，但仓库当前没有锁定的 `requirements.txt`，历史训练环境也未完整保存。

正式比赛模型必须新增：

- `requirements-lock.txt` 或 Conda environment 文件。
- Python、CUDA、cuDNN、GPU、驱动的精确版本。
- 训练命令、随机种子、数据 manifest 和日志。
- 导出 ONNX 的 PyTorch、ONNX 和 opset 版本。

当前随包 ONNX 元信息：PyTorch 2.6.0 导出，ONNX opset 11。

## 4. 已知构建与测试状态

- `:app:compileDebugKotlin` 曾在 2026-08-05 通过。
- 历史测试报告显示 `OfflineConversationTest` 3 项通过、0 失败。
- 全量 `testDebugUnitTest` 曾受 KSP `EOFException` 阻断，尚无本次整理后的完整测试结果。
- 当前目录中的 APK 元数据为 1.0.0 旧包，不可提交。

## 5. 正式复现命令

```powershell
.\gradlew.bat clean :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

正式执行后将控制台日志、测试 XML/HTML、Lint 报告和 APK 哈希归档到 `competition/04_testing/evidence/`。
