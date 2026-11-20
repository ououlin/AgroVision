package com.agrovision.app.ui.screen.manual

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrovision.app.BuildConfig
import com.agrovision.app.ui.theme.*

/**
 * 用户使用手册（上架合规 + 帮助用户快速上手）
 * 入口：设置 → 关于 → 用户使用手册
 *
 * 内容涵盖：
 * 1. 快速开始（欢迎屏/首次进入流程）
 * 2. 拍照问诊（CameraX + 本地推理）
 * 3. 相册问诊（选择照片分析）
 * 4. 病虫百科与当前模型范围
 * 5. 问农技（对话 + 快速问题 + 农场上下文）
 * 6. 设置指南（字体/深色模式/定位/权限/清除缓存）
 * 7. 数据与隐私（本地推理 / 云端 AI / 历史记录 / 崩溃日志）
 * 8. 常见问题 FAQ（识别不准 / 离线模式 / 权限被永久拒绝 / 崩溃排查）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(onNavigateBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户使用手册", fontWeight = FontWeight.SemiBold) },
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
            Spacer(Modifier.height(Spacing.LG.dp))

            // 顶部说明卡
            Surface(
                shape = ShapeLG,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                tonalElevation = Elevation.CARD.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XL.dp)
            ) {
                Column(Modifier.padding(Spacing.LG.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocalFlorist, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(Spacing.SM.dp))
                        Text(
                            "农智眼 · 操作指南",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(Spacing.SM.dp))
                    Text(
                        "本手册帮助你快速了解并使用所有功能。识别和问答仅供农技参考，实际生产决策请结合现场情况与当地植保部门专业指导。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 1. 快速开始
            ManualGroup(title = "1. 快速开始", icon = Icons.Filled.PlayArrow) {
                ManualStep("首次启动 · 欢迎屏") {
                    "① 阅读并勾选《用户协议》《隐私政策》（必须勾选才能进入应用）\n" +
                        "② 点击「账号登录」或「体验一下（游客模式）」均可进入首页\n" +
                        "③ 可直接使用拍照问诊 / 相册问诊 / 病虫百科 / 本地历史记录\n" +
                        "④ 创建地块后，可继续记录用药和查看农事提醒"
                }
                ManualStep("底部导航 · 四大入口") {
                    "首页：拍照问诊 · 今日农事 · 常用工具\n" +
                        "问诊：拍摄或从相册选择作物异常照片\n" +
                        "农事：地块、用药记录和农情提醒\n" +
                        "我的（Profile）：历史记录、设置、关于、用户手册"
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 2. 拍照问诊
            ManualGroup(title = "2. 拍照问诊", icon = Icons.Filled.CameraAlt) {
                ManualStep("权限申请") {
                    "首次进入问诊页会弹出「相机权限用途说明」，说明照片如何使用。\n" +
                        "点击「查看权限说明并授予」→ 系统权限弹窗选「仅在使用该应用时允许」。\n" +
                        "若误选「不允许」，会引导你到系统设置页开启。"
                }
                ManualStep("对准拍摄要点") {
                    "① 确保叶片 / 病斑 完整出现在取景框内，背景尽量干净\n" +
                        "② 保持光线充足（避免逆光、强阴影），可点击屏幕手动对焦\n" +
                        "③ 点击底部快门开始分析\n" +
                        "④ 查看可能问题、严重程度和处理建议"
                }
                ManualStep("离线模式可用") {
                    "拍照问诊可在设备本地完成基础识别，山区或果园无信号时也可使用。"
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 3. 图片识别
            ManualGroup(title = "3. 相册问诊", icon = Icons.Filled.PhotoLibrary) {
                ManualStep("相册选图") {
                    "首页点击「相册问诊」→ 选择系统相册中的作物图片。\n" +
                        "支持 JPEG / PNG / WebP 格式，建议图片 ≥ 640×640 像素以获得准确结果。"
                }
                ManualStep("结果解读") {
                    "识别完成后进入「问诊结果」：\n" +
                        "可能问题 + 判断程度 + 严重程度 + 今天先做 + 安全提醒。\n" +
                        "判断依据不足时，应用会提示换角度补拍。"
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 4. 病虫百科
            ManualGroup(title = "4. 病虫百科", icon = Icons.Filled.MenuBook) {
                ManualStep("当前离线模型范围") {
                    "当前随包模型为 8 类试验模型，覆盖苹果、蓝莓、樱桃健康/病害及无叶背景。百科中可检索更多作物知识，但不代表模型能够识别这些类别。\n" +
                        "首页 → 「病虫百科」卡片 或 我的 → 病虫百科 可进入。"
                }
                ManualStep("词条结构") {
                    "每个词条包含：病害别名 · 典型症状图 · 发病规律 · 农业防治 · 化学防治 · 生物防治 · 推荐药剂（含有效成分和稀释倍数，仅供参考）。"
                }
                ManualStep("搜索") {
                    "顶部搜索框支持按「作物名 / 病害名 / 症状关键词」模糊检索，支持中文拼音首字母。"
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 5. 问农技
            ManualGroup(title = "5. 问农技", icon = Icons.Filled.Forum) {
                ManualStep("快速问答") {
                    "问农技支持日常对话、一般问题和农业专业问答，例：\n" +
                        "「你好，你能帮我做什么？」\n" +
                        "「番茄叶片上有黄色小斑点是什么病？」\n" +
                        "「苹果套袋前打什么药？」\n" +
                        "也可点击输入框上方的快捷问题继续对话。"
                }
                ManualStep("离线也能问") {
                    "本地助手支持问候等基础交流和农业知识问答。网络能力不可用时，页面会自动提示当前使用本地助手。"
                }
                ManualStep("上下文引用") {
                    "AI 助手会保留最近的多轮对话。创建地块、识别记录或用药记录后，还会结合本机农场摘要回答相关管理问题。"
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 6. 设置指南
            ManualGroup(title = "6. 设置指南", icon = Icons.Filled.Tune) {
                ManualStep("显示") {
                    "深色模式：跟随系统 / 浅色 / 深色 三选一\n" +
                        "字体大小：小 / 标准 / 大 / 特大 四档（老人友好）\n" +
                        "字号调整后所有页面文字实时放大/缩小。"
                }
                ManualStep("定位与天气") {
                    "首页右上角天气需城市级粗定位（不收集 GPS 轨迹）。\n" +
                        "若显示「定位不可用」→ 点击该文字会弹出「定位权限用途说明」弹窗，引导到系统设置开启定位权限。\n" +
                        "也可手动在 设置 → 数据源 → 手动选择城市。"
                }
                ManualStep("存储与缓存") {
                    "我的 → 设置 → 存储：\n" +
                        "可查看识别缓存 / AI 对话缓存 / 模型数据包大小\n" +
                        "一键清除：识别缓存（不删历史）、对话缓存、全部缓存。\n" +
                        "模型数据包不建议清除（~30MB），否则下次打开识别页会重新下载。"
                }
                ManualStep("权限管理") {
                    "我的 → 设置 → 权限：可查看相机 / 定位 / 通知 / 相册 授权状态，点击跳转到系统设置页修改。\n" +
                        "应用每次使用权限前都会弹用途说明弹窗，符合国内应用市场合规要求。"
                }
                ManualStep("高级连接设置") {
                    "高级用户可在设置中管理可选的在线 AI 连接。普通用户无需配置即可使用拍照问诊、本地知识和基础交流。"
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 7. 数据与隐私
            ManualGroup(title = "7. 数据与隐私", icon = Icons.Filled.PrivacyTip) {
                ManualStep("本地推理优先") {
                    "病虫害识别（实时 / 图片）、OCR 识别、病虫百科查询默认 100% 在本地设备运行，原始图片、识别过程、识别结果默认不联网上传。"
                }
                ManualStep("历史记录存储") {
                    "识别历史、反馈、对话记录使用加密 Room 数据库存储在应用私有目录：\n" +
                        "/data/data/com.agrovision.app/databases/\n" +
                        "第三方应用无法读取。Root 设备请谨慎使用。"
                }
                ManualStep("云端 AI 数据流") {
                    "仅当用户主动配置并开启云端 AI 问答时，AI 助手的对话文本才会发送到用户配置的第三方 API 服务商。\n" +
                        "本应用本身不向任何自有服务器发送用户数据。"
                }
                ManualStep("崩溃日志") {
                    "release 版本若发生崩溃，日志会加密写入：\n" +
                        "Android/data/com.agrovision.app/files/crash/\n" +
                        "不自动上传，仅当用户主动联系客服时可手动导出提供排查。"
                }
                ManualStep("数据导出与注销") {
                    "我的 → 设置 → 数据中心：\n" +
                        "一键导出识别历史 / 反馈 / API 配置\n" +
                        "一键注销账号（会清空所有本地 + 云端用户数据，不可恢复）。"
                }
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            // 8. 常见问题 FAQ
            ManualGroup(title = "8. 常见问题 FAQ", icon = Icons.Filled.HelpOutline) {
                ManualFaq(
                    q = "识别准确率不高 / 识别错了怎么办？",
                    a = "① 检查图片清晰度：叶片是否对焦、病斑是否完整\n" +
                        "② 避免背景杂物过多，建议单叶片近距离拍摄\n" +
                        "③ 光线不足会影响识别，可在晴天自然光下重拍\n" +
                        "④ 识别完成后点「反馈识别结果」帮助我们改进模型"
                )
                ManualFaq(
                    q = "相机 / 定位 权限被永久拒绝了怎么恢复？",
                    a = "触发权限时应用会自动弹出「权限被永久拒绝」的提示，点击「去设置页开启」会直接跳到系统设置 → 权限页。\n" +
                        "也可手动：长按应用图标 → 应用信息 → 权限 → 相机 / 位置 → 选择「允许」。"
                )
                ManualFaq(
                    q = "没有网络能用吗？",
                    a = "能。拍照问诊、相册问诊、病虫百科、本地历史记录、基础规则问答都可离线使用。\n" +
                        "城市天气和可选的在线 AI 问答需要网络；账号登录同步当前版本默认关闭。"
                )
                ManualFaq(
                    q = "应用崩溃 / 闪退怎么办？",
                    a = "① 先重启应用看是否复现\n" +
                        "② 我的 → 设置 → 开发者中心 → 查看崩溃日志（release 版本路径：Android/data/com.agrovision.app/files/crash/）\n" +
                        "③ 我的 → 设置 → 清除缓存 清理一次\n" +
                        "④ 若仍存在，可通过关于页联系方式把日志发给我们排查。"
                )
                ManualFaq(
                    q = "换手机了历史记录能迁移吗？",
                    a = "① 老手机：我的 → 设置 → 数据中心 → 导出数据包（加密 zip）\n" +
                        "② 新手机：登录同一账号 → 数据中心 → 导入 zip\n" +
                        "③ 历史记录 / API Key / 配置 都会恢复（加密 Key 与设备绑定，导入后需重新验证）。"
                )
                ManualFaq(
                    q = "AI 问答给出的用药方案可以直接使用吗？",
                    a = "不可以。AI 问答、识别结果、防治方案均仅供农技参考，不构成任何植保 / 用药建议。\n" +
                        "实际用药请严格按照农药标签说明书，或咨询当地农技推广部门、持证植保员。"
                )
            }

            Spacer(Modifier.height(Spacing.XXL.dp))

            // 底部：联系 / 版本
            Text(
                "农智眼 v${BuildConfig.VERSION_NAME}  ·  最后更新：2026 年 8 月 4 日",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "如遇问题：设置 → 关于 → 联系我们",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.XXL.dp))
        }
    }
}

// ================================================================
// 手册组件（与 AboutScreen 风格一致，避免循环依赖，本地定义）
// ================================================================

@Composable
private fun ManualGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = ShapeLG,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.CARD.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XL.dp)
    ) {
        Column(Modifier.padding(Spacing.LG.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = ShapeXS,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        icon, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.MD.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(Spacing.MD.dp))
            ManualDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.MD.dp))
            content()
        }
    }
}

@Composable
private fun ColumnScope.ManualStep(title: String, body: () -> String) {
    Column(Modifier.padding(vertical = Spacing.XS.dp)) {
        Text(
            "▸ $title",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.XS.dp))
        Text(
            body(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ColumnScope.ManualFaq(q: String, a: String) {
    Column(Modifier.padding(vertical = Spacing.SM.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.QuestionAnswer, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(Modifier.width(Spacing.XS.dp))
            Text(
                q,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(Spacing.XS.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.CheckCircle, null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(top = 2.dp, start = 1.dp)
                    .size(16.dp)
            )
            Spacer(Modifier.width(Spacing.SM.dp))
            Text(
                a,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ManualDivider(color: Color = MaterialTheme.colorScheme.outlineVariant) {
    androidx.compose.material3.HorizontalDivider(
        thickness = 1.dp,
        color = color,
        modifier = Modifier.fillMaxWidth()
    )
}
