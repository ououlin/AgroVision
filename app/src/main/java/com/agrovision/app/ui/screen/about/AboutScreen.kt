package com.agrovision.app.ui.screen.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrovision.app.BuildConfig
import com.agrovision.app.ui.theme.*

/**
 * 关于页：Logo + 版本 + 技术规格 + 开源许可 + 《用户协议》+《隐私政策》
 *
 * @param initialTab 可选锚点：
 *   - "terms"   → 进入页面后自动滚动到「用户协议」段落
 *   - "privacy" → 进入页面后自动滚动到「隐私政策」段落
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    initialTab: String? = null
) {
    val scrollState = rememberScrollState()

    // 两个协议卡片的锚点：记录位置（px）后滚动
    val termsOffset = remember { mutableStateOf(0) }
    val privacyOffset = remember { mutableStateOf(0) }

    LaunchedEffect(initialTab) {
        // 等首帧布局结束后再滚
        kotlinx.coroutines.delay(100)
        when (initialTab) {
            "terms"   -> { if (termsOffset.value > 0) scrollState.animateScrollTo(termsOffset.value) }
            "privacy" -> { if (privacyOffset.value > 0) scrollState.animateScrollTo(privacyOffset.value) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Spacing.XXL.dp))

            // Logo
            Surface(
                modifier = Modifier.size(72.dp),
                shape = ShapeLG,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                tonalElevation = Elevation.CARD.dp
            ) {
                Icon(Icons.Filled.LocalFlorist, null,
                    modifier = Modifier.padding(Spacing.MD.dp + 2.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(Spacing.LG.dp + 2.dp))
            Text("农智眼",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("AgroVision",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(Spacing.XS.dp))
            Text("果园病虫害智能识别 · AI 农技助手",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(Spacing.XXL2.dp))

            // 版本卡片
            InfoGroup("版本信息") {
                InfoRow("应用版本", BuildConfig.VERSION_NAME)
                InfoRow("模型版本", "MobileNetV3 v2.0 · YOLO11n")
                InfoRow("推理引擎", "ONNX Runtime 1.17 · TFLite 2.13")
                InfoRow("构建版本", "2026.8")
            }

            Spacer(modifier = Modifier.height(Spacing.XL.dp))

            // 技术规格
            InfoGroup("技术规格") {
                InfoRow("模型架构", "MobileNetV3-Small + YOLO11n")
                InfoRow("输入尺寸", "640 × 640")
                InfoRow("推理方式", "CPU / GPU 自适应 (ONNX / TFLite)")
                InfoRow("框架", "Kotlin / Jetpack Compose / Material3")
                InfoRow("最低 SDK", "Android 8.0 (API 26)")
                InfoRow("当前离线模型", "8 类试验模型（苹果 / 蓝莓 / 樱桃）")
            }

            Spacer(modifier = Modifier.height(Spacing.XL.dp))

            // SDK / 第三方清单（上架要求列第三方 SDK 说明）
            InfoGroup("第三方 SDK 说明") {
                InfoRow("TensorFlow Lite", "本地 AI 推理 · 不联网上传图片")
                InfoRow("ONNX Runtime", "本地 AI 推理 · 不联网上传图片")
                InfoRow("Google ML Kit", "中文 OCR 识别 · 离线可运行")
                InfoRow("Google Play Services Location", "城市级粗定位 · 获取天气")
                InfoRow("CameraX", "相机预览 · 本地推理")
                InfoRow("OkHttp / Retrofit", "用户配置的 AI 服务网络请求")
                InfoRow("Hilt / Room / DataStore", "Google Jetpack 架构组件")
            }

            Spacer(modifier = Modifier.height(Spacing.XL.dp))

            // 开源许可
            InfoGroup("开源许可") {
                Text(
                    "本项目使用以下开源组件，全部遵循 Apache 2.0 许可证：\n" +
                    "ONNX Runtime · TensorFlow Lite · CameraX\n" +
                    "Room · Hilt (Dagger) · Navigation Compose\n" +
                    "Coil · Timber · Retrofit · OkHttp · Gson",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.SM.dp),
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(Spacing.XXL.dp))

            // ===== 《用户协议》 =====
            LegalGroup(
                title = "《用户协议》",
                icon = Icons.Filled.Description,
                captureOffset = { termsOffset.value = it }
            ) {
                LegalParagraph("1. 服务内容") {
                    "农智眼 App（下简称「本应用」）提供基于人工智能的农作物病虫害识别、农业知识问答、病虫百科查询、离线识别等农业辅助服务。服务形式包括本地模型推理和用户自行配置的云端 AI 问答两种。"
                }
                LegalParagraph("2. 用户注册与账号") {
                    "用户可以使用游客模式体验本应用基本功能。需要使用云端同步、历史记录漫游等高级功能时须注册账号。用户须保证注册信息真实准确，对账号下的所有活动承担全部责任。"
                }
                LegalParagraph("3. 用户行为规范") {
                    "用户不得利用本应用从事任何违反国家法律法规、侵犯他人合法权益的行为。不得上传、传播包含违法、色情、暴力、诽谤、侵犯知识产权等内容的图片和文字。"
                }
                LegalParagraph("4. 隐私与数据保护") {
                    "本应用高度重视用户隐私。病虫害识别、OCR 等模型在设备本地运行，默认不将原始图片上传云端。云端 AI 问答功能仅在用户主动勾选并填写 API Key 后启用，问答内容由用户自行配置的第三方服务商处理。详见《隐私政策》。"
                }
                LegalParagraph("5. 知识产权") {
                    "本应用自有源代码、UI 设计、模型权重、数据集商标等均受知识产权法保护。用户仅获得本应用的非独占、不可转让的使用权。PlantVillage 数据集遵循 CC BY-SA 3.0 协议。"
                }
                LegalParagraph("6. 免责声明") {
                    "病虫害识别结果和 AI 问答仅供农技参考，不构成任何农学、植保、用药建议。实际农业生产决策应结合现场情况和当地农技部门专业指导，由此产生的损失本应用不承担责任。"
                }
                LegalParagraph("7. 服务变更与终止") {
                    "由于技术迭代、合规要求等原因，本应用保留随时变更、中断、终止部分或全部服务的权利。变更将通过应用内公告或消息推送方式告知。"
                }
                LegalParagraph("8. 协议修订") {
                    "本协议如有修订，新版本将在应用内公告并重新获得用户同意。用户继续使用服务视为接受修订后的协议。"
                }
                LegalParagraph("9. 法律适用与争议解决") {
                    "本协议签订、履行、解释均适用中华人民共和国法律。因本协议产生的争议双方协商不成时，任何一方可向本应用运营方所在地人民法院提起诉讼。"
                }
                Text("最后更新日期：2026 年 8 月 1 日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.MD.dp))
            }

            Spacer(modifier = Modifier.height(Spacing.XXL.dp))

            // ===== 《隐私政策》 =====
            LegalGroup(
                title = "《隐私政策》",
                icon = Icons.Filled.PrivacyTip,
                captureOffset = { privacyOffset.value = it }
            ) {
                LegalParagraph("引言") {
                    "本隐私政策旨在帮助您了解农智眼 App 会收集哪些信息、为什么收集、如何使用、是否共享、您的权利与选择。我们将严格遵守《个人信息保护法》《网络安全法》等相关法律法规，采取业界公认的安全措施保护您的信息。"
                }
                LegalParagraph("1. 我们收集的信息") {
                    "（1）您主动提供的信息：本地昵称，以及 AI 问答时在设置页填写的第三方 API Key（经 Keystore 加密存储在本地）。\n（2）设备信息：设备型号和系统版本，仅写入本地崩溃日志用于问题排查。\n（3）位置信息：仅在您主动授权后读取城市级粗略位置，用于加载当前城市天气，不记录 GPS 轨迹。\n（4）图片：病虫害识别图片通过系统文件选择器或相机获取，默认只在本地进行 AI 推理，历史数据保存在应用私有目录。\n（5）日志：崩溃日志仅保存在本地，不自动上传。"
                }
                LegalParagraph("2. 信息的使用目的") {
                    "（1）提供、维护、改进本应用的核心服务；\n（2）病虫害识别、AI 农技问答、历史记录查询、天气展示等业务功能；\n（3）账户安全、风控与反欺诈；\n（4）遵守法律法规与监管要求。"
                }
                LegalParagraph("3. 信息共享与转让") {
                    "我们不会向任何第三方出售您的个人信息。以下场景除外：\n（1）获得您的明确同意；\n（2）法律法规或司法/行政机关依法要求；\n（3）您在设置页主动配置了第三方 AI 服务提供商（如 DeepSeek/阿里云通义千问），此时 AI 问答内容将发送至您配置的服务商处理，建议您仔细阅读对应服务商的隐私政策。"
                }
                LegalParagraph("4. 信息存储与安全") {
                    "（1）Token（access/refresh）使用 AndroidX EncryptedSharedPreferences + AES256_GCM 加密；\n（2）AI API Key 使用 AndroidX Security EncryptedFile + MasterKey 加密；\n（3）用户设置、偏好配置通过 DataStore 保存；\n（4）识别历史记录使用 Room 数据库保存在应用私有目录；\n（5）正式版本的网络接口强制 HTTPS，默认禁止明文 HTTP。"
                }
                LegalParagraph("5. 您的权利") {
                    "您有权：（1）查看、更正、删除您的个人信息；（2）撤回权限授权（相机、定位、存储、通知）；（3）注销账号并清除全部数据；（4）随时撤回同意。以上操作可通过「设置」页面执行，问题请通过关于页联系开发者。"
                }
                LegalParagraph("6. 儿童隐私") {
                    "本应用不面向未满 14 周岁的儿童。若您是儿童监护人，发现儿童未经同意使用本应用或提交了个人信息，请立即联系我们删除。"
                }
                LegalParagraph("7. 政策更新") {
                    "本政策如有重大变更，将在应用内重新弹窗取得您的明示同意；非重大变更将在应用内公告并更新最后更新日期。"
                }
                LegalParagraph("8. 联系我们") {
                    "如您对本政策有任何疑问、投诉、建议，或希望行使您的权利，可发送邮件至：privacy@agrovision.cn。"
                }
                Text("最后更新日期：2026 年 8 月 1 日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.MD.dp))
            }

            Spacer(Modifier.height(Spacing.XXL2.dp))
            Text("© 2026 AgroVision Team. All rights reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(Spacing.XL.dp))
        }
    }
}

/* ===================== UI 子组件 ===================== */

@Composable
private fun InfoGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.XL.dp)) {
        Text(title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.SM.dp, start = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeMD,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = ContentCardElevation
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.LG.dp, vertical = Spacing.XS.dp),
                content = content
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.SM.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(Spacing.MD.dp))
        Text(value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/**
 * 协议 section 大卡：记录滚动偏移 → 便于锚点跳转。
 */
@Composable
private fun LegalGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    captureOffset: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.XL.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // 头部小卡
        Surface(
            shape = ShapeMD,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(Spacing.LG.dp)
                    .onGloballyPositioned { layout -> captureOffset(layout.positionInRoot().y.toInt()) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = ShapeXS,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(icon, null,
                        modifier = Modifier.padding(Spacing.SM.dp + 2.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(Spacing.MD.dp))
                Text(title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        Spacer(Modifier.height(Spacing.MD.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeMD,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = ContentCardElevation
        ) {
            Column(
                modifier = Modifier.padding(Spacing.LG.dp),
                content = content
            )
        }
    }
}

@Composable
private fun LegalParagraph(heading: String, body: () -> String) {
    Spacer(Modifier.height(Spacing.MD.dp))
    Text(heading,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(Spacing.XS.dp))
    androidx.compose.material3.Text(
        text = androidx.compose.ui.text.AnnotatedString(body()),
        style = MaterialTheme.typography.bodySmall.copy(
            lineHeight = 20.sp,
            textIndent = TextIndent(firstLine = 20.sp)
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
