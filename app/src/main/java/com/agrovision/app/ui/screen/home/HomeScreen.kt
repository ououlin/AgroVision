package com.agrovision.app.ui.screen.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import com.agrovision.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agrovision.app.ui.components.PermissionRationaleDialog
import com.agrovision.app.ui.theme.*
import java.util.Calendar

@Composable
fun HomeScreen(
    onNavigateToDetection: () -> Unit,
    onNavigateToGallery: () -> Unit = onNavigateToDetection,
    onNavigateToEncyclopedia: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToPestDetail: (String) -> Unit,
    onNavigateToDiagnosis: (String, Float, String) -> Unit = { _, _, _ -> },
    onNavigateToData: () -> Unit = {},
    // ===== V2.0 新增入口 =====
    onNavigateToFarm: () -> Unit = {},
    onNavigateToAgriSearch: () -> Unit = {},
    onNavigateToPesticideCenter: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "早上好"; in 12..17 -> "下午好"; else -> "晚上好" }

    // ===== 粗定位 Rationale：Manifest 只申请 ACCESS_COARSE_LOCATION（不申请 FINE），避免 GPS 级定位敏感 =====
    var showLocationRationale by remember { mutableStateOf(false) }
    var hasRequestedLocationOnce by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedLocationOnce = true
        if (granted) viewModel.loadData()
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** 合规入口：先弹 Rationale → 再真正请求粗定位权限 */
    fun requestLocationWithRationale() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.loadData()
            return
        }
        showLocationRationale = true
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部（支持点击定位标签 → 先 Rationale 再请求权限）
        TopGreeting(
            greeting = greeting,
            uiState = uiState,
            onRequestLocation = { requestLocationWithRationale() }
        )

        Spacer(Modifier.height(Spacing.XL.dp))

        FarmerActionPanel(
            onCamera = onNavigateToDetection,
            onGallery = onNavigateToGallery,
            onAsk = onNavigateToChat
        )

        Spacer(Modifier.height(Spacing.XXL.dp))

        TodayTaskSection(
            state = uiState,
            onFarm = onNavigateToFarm
        )

        Spacer(Modifier.height(Spacing.XXL.dp))

        FarmerToolRow(
            onAgriSearch = onNavigateToAgriSearch,
            onEncyclopedia = onNavigateToEncyclopedia,
            onHistory = onNavigateToHistory,
            onPesticideCenter = { onNavigateToPesticideCenter("") }
        )

        Spacer(Modifier.height(Spacing.XXL.dp))

        // 最近识别
        RecentSection(
            detections = uiState.recentDetections,
            isLoading = uiState.isLoading,
            onViewAll = onNavigateToHistory
        )

        Spacer(Modifier.height(Spacing.HERO.dp))
    }

    // ===== 粗定位 Rationale Dialog =====
    if (showLocationRationale) {
        val permanentlyDenied = remember(hasRequestedLocationOnce) {
            hasRequestedLocationOnce &&
                (context as? androidx.activity.ComponentActivity)
                    ?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) == false
        }
        PermissionRationaleDialog(
            icon = Icons.Filled.LocationOn,
            title = "定位权限说明",
            purpose = "仅使用城市级粗略定位，用于加载当前城市的天气与农情提醒。",
            whatHappens = "仅通过网络基站/WiFi 推断城市，不启动 GPS，不记录精确位置轨迹；定位结果只用于拼接天气 API 请求，不会上传云端用于其他用途。",
            whatIfDeny = "拒绝后仍可使用识别、聊天、百科等全部核心功能，仅首页天气展示为默认或空。",
            permanentlyDenied = permanentlyDenied,
            onConfirm = {
                showLocationRationale = false
                if (permanentlyDenied) openAppSettings()
                else locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onOpenSettings = { showLocationRationale = false; openAppSettings() },
            onDismiss = { showLocationRationale = false }
        )
    }
}

@Composable
private fun TopGreeting(
    greeting: String,
    uiState: HomeUiState,
    onRequestLocation: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.XL.dp, end = Spacing.XL.dp, top = Spacing.XL.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("农智眼", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(greeting, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = ShapeXS,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.heightIn(min = TouchTarget.MIN.dp).clickable(onClick = onRequestLocation)
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.MD.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(TouchTarget.ICON.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.SM.dp))
                    Text(uiState.location, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(Spacing.SM.dp))
        val subtitle = if (uiState.weather != "--" && uiState.weather.isNotEmpty()) {
            "今日天气：${uiState.weather}。巡田时留意叶背、嫩梢和果面。"
        } else {
            "先看作物哪里不对，再拍照问诊。"
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FarmerActionPanel(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onAsk: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XL.dp)) {
        Button(
            onClick = onCamera,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = ShapeMD
        ) {
            Icon(Icons.Filled.CameraAlt, null, Modifier.size(28.dp))
            Spacer(Modifier.width(Spacing.MD.dp))
            Text("拍照问诊", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(Spacing.MD.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)) {
            OutlinedButton(
                onClick = onGallery,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget.MIN_ALT.dp),
                shape = ShapeMD
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null)
                Spacer(Modifier.width(Spacing.SM.dp))
                Text("相册问诊")
            }
            OutlinedButton(
                onClick = onAsk,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget.MIN_ALT.dp),
                shape = ShapeMD
            ) {
                Icon(Icons.Outlined.RecordVoiceOver, null)
                Spacer(Modifier.width(Spacing.SM.dp))
                Text("问农技")
            }
        }
    }
}

@Composable
private fun TodayTaskSection(state: HomeUiState, onFarm: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XL.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("今天要做", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onFarm) { Text("进入农事") }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onFarm),
            shape = ShapeMD,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
            Row(Modifier.padding(Spacing.LG.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.MD.dp))
                Column(Modifier.weight(1f)) {
                    val title = state.recentDetections.firstOrNull()?.let { "复查最近发现的 ${it.diseaseName}" }
                        ?: "巡田检查叶背、嫩梢和果面"
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Spacing.XS.dp))
                    Text(
                        "发现斑点、虫口或萎蔫时，拍清楚异常部位再问诊。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FarmerToolRow(
    onAgriSearch: () -> Unit,
    onEncyclopedia: () -> Unit,
    onHistory: () -> Unit,
    onPesticideCenter: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XL.dp)) {
        Text("常用工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.MD.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FarmerTool(Icons.Outlined.Search, "农技搜索", onAgriSearch)
            FarmerTool(Icons.Outlined.MenuBook, "病虫百科", onEncyclopedia)
            FarmerTool(Icons.Outlined.Medication, "植保用药", onPesticideCenter)
            FarmerTool(Icons.Outlined.History, "问诊记录", onHistory)
        }
    }
}

@Composable
private fun RowScope.FarmerTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = Spacing.SM.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(Modifier.size(TouchTarget.MIN_ALT.dp), ShapeMD,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(TouchTarget.ICON_LG.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Spacing.SM.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BrandSection() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XL.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = R.drawable.nvtlogo,
                contentDescription = null,
                // FE Review: 48→TouchTarget.AVATAR
                modifier = Modifier.size(TouchTarget.AVATAR.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(Spacing.MD.dp))
            HorizontalDivider(
                // FE Review: 36→Spacing.XXL2 28? no → closest spacing scale 32=XXL3; 1dp保留为分割线宽是 Material 默认
                modifier = Modifier.height(Spacing.XXL3.dp).width(1.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Spacer(Modifier.width(Spacing.MD.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = R.drawable.beilian_logo_simple,
                    contentDescription = null,
                    // FE Review: 32→Spacing.XXL3 作为高度（最接近 32 令牌）
                    modifier = Modifier.height(Spacing.XXL3.dp)
                )
                Spacer(Modifier.width(Spacing.MD.dp))
                Column {
                    Text(
                        "BEILIAN",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        "北联机电科技",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // FE Review: MD+SM=20 → Spacing.XL 正好 20，直接简化
        Spacer(Modifier.height(Spacing.XL.dp))
        Text("农智眼", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("让每一株作物，都被看见。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("AgroVision AI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
    }
}

@Composable
private fun QuickGrid(
    onRealTime: () -> Unit, onGallery: () -> Unit,
    onEncyclopedia: () -> Unit, onDiagnosis: () -> Unit
) {
    Column(Modifier.padding(horizontal = Spacing.XL.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)) {
            QuickCard(Modifier.weight(1f), Icons.Filled.CameraAlt, "拍照问诊", "拍摄异常部位", brandGreen(), onRealTime)
            QuickCard(Modifier.weight(1f), Icons.Filled.PhotoLibrary, "相册问诊", "选择已有照片", brandBlue(), onGallery)
        }
        Spacer(Modifier.height(Spacing.MD.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)) {
            QuickCard(Modifier.weight(1f), Icons.Filled.MenuBook, "病虫百科", "农业知识库", brandGreen(), onEncyclopedia)
            QuickCard(Modifier.weight(1f), Icons.Filled.Psychology, "问农技", "农业问题和日常交流", accentOrange(), onDiagnosis)
        }
    }
}

@Composable
private fun QuickCard(mod: Modifier, icon: ImageVector, title: String, sub: String, tone: Color, onClick: () -> Unit) {
    Card(mod.clickable(onClick = onClick), shape = ShapeLG,
        elevation = ActionCardElevation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(Spacing.XL.dp)) {
            // FE Review: 44→TouchTarget.MIN_ALT；padding(10) → 用 Box 居中 Icon，避免魔法数字
            Surface(Modifier.size(TouchTarget.MIN_ALT.dp), ShapeMD, tone.copy(alpha = 0.10f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(TouchTarget.ICON_LG.dp), tint = tone)
                }
            }
            Spacer(Modifier.height(Spacing.MD.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OverviewCards(state: HomeUiState, onViewStats: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.XL.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.MD.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("今日概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onViewStats) { Text("查看统计") }
        }

        if (state.isLoading) {
            Row(Modifier.fillMaxWidth(),
                // FE Review: MD-2=10 → Spacing.SM 8，稍小但一致更重要
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM.dp)) {
                OverviewSkeletonCard(Modifier.weight(1f)); OverviewSkeletonCard(Modifier.weight(1f))
            }
            // FE Review: MD-2=10 → Spacing.SM 8
            Spacer(Modifier.height(Spacing.SM.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.SM.dp)) {
                OverviewSkeletonCard(Modifier.weight(1f)); OverviewSkeletonCard(Modifier.weight(1f))
            }
        } else {
            val animSpec = tween<Float>(600)
            val todayCount = animateFloatAsState(state.todayDetections.toFloat(), animSpec).value.toInt()
            val totalCount = animateFloatAsState(state.totalDetections.toFloat(), animSpec).value.toInt()
            val diseases = animateFloatAsState(state.todayDiseases.toFloat(), animSpec).value.toInt()

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.SM.dp)) {
                OverviewCard(Modifier.weight(1f), "$todayCount", "今日识别", brandGreen())
                OverviewCard(Modifier.weight(1f), "$totalCount", "累计识别", brandBlue())
            }
            Spacer(Modifier.height(Spacing.SM.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.SM.dp)) {
                OverviewCard(Modifier.weight(1f), "${(state.successRate * 100).toInt()}%", "识别准确率", brandGreen())
                OverviewCard(Modifier.weight(1f), "$diseases", "今日病害", accentOrange())
            }
        }
    }
}

@Composable
private fun OverviewSkeletonCard(mod: Modifier) {
    Card(mod, shape = ShapeMD, elevation = ActionCardElevation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(Spacing.XL.dp)) {
            // FE Review: 28→Spacing.XXL 24；稍小但避免魔法数字
            SkeletonBox(Modifier.fillMaxWidth(0.6f).height(Spacing.XXL.dp))
            Spacer(Modifier.height(Spacing.SM.dp))
            // FE Review: 14→Spacing.MD 12；稍小但步长合规
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(Spacing.MD.dp))
        }
    }
}

@Composable
private fun SkeletonBox(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = ShapeXS,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {}
}

@Composable
private fun OverviewCard(mod: Modifier, value: String, label: String, color: Color) {
    Card(mod, shape = ShapeMD, elevation = ActionCardElevation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(Spacing.XL.dp)) {
            // 26sp 与 headlineLarge 完全一致；用 Typography 系统承接 fontSize scale
            Text(value,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = color)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentSection(detections: List<DetectionSummary>, isLoading: Boolean, onViewAll: () -> Unit) {
    Column(Modifier.padding(start = Spacing.XL.dp)) {
        Row(Modifier.fillMaxWidth().padding(end = Spacing.XL.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("最近识别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onViewAll) { Text("全部") }
        }
        when {
            isLoading -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp),
                    contentPadding = PaddingValues(end = Spacing.XL.dp)) {
                    items(3) {
                        Card(
                            // FE Review: 160.dp → 提取为本地常量（卡片尺寸属于布局规格非通用令牌）
                            modifier = Modifier.width(RECENT_CARD_WIDTH.dp),
                            shape = ShapeMD,
                            elevation = ActionCardElevation,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column {
                                Box(Modifier.fillMaxWidth()
                                    // FE Review: 100→Spacing.XXL2*3 + Spacing.XL = 84+20=104? 直接定义 CARD_IMAGE_HEIGHT
                                    .height(RECENT_CARD_IMAGE_HEIGHT.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)))
                                Column(Modifier.padding(Spacing.MD.dp)) {
                                    SkeletonBox(Modifier.fillMaxWidth(0.7f).height(Spacing.LG.dp))
                                    // FE Review: XS+XS=8 → Spacing.SM
                                    Spacer(Modifier.height(Spacing.SM.dp))
                                    // FE Review: 12→Spacing.MD 12（正好对齐）
                                    SkeletonBox(Modifier.fillMaxWidth(0.4f).height(Spacing.MD.dp))
                                }
                            }
                        }
                    }
                }
            }
            detections.isEmpty() -> {
                Surface(Modifier.fillMaxWidth().padding(end = Spacing.XL.dp), shape = ShapeMD,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.XXL2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // FE Review: 32→Spacing.XXL2 28 稍小，或直接用 TouchTarget.AVATAR 48太大；用 Spacing.XXL3 32 正好
                        Icon(Icons.Outlined.ImageSearch, null, Modifier.size(Spacing.XXL3.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                        Spacer(Modifier.height(Spacing.SM.dp))
                        Text("还没有识别记录", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp),
                    contentPadding = PaddingValues(end = Spacing.XL.dp)) {
                    items(detections) { d ->
                        Card(Modifier.width(RECENT_CARD_WIDTH.dp), shape = ShapeMD,
                            elevation = ActionCardElevation,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column {
                                Box(Modifier.fillMaxWidth()
                                    .height(RECENT_CARD_IMAGE_HEIGHT.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center) {
                                    if (d.imagePath.isNotEmpty()) {
                                        AsyncImage(d.imagePath, null, Modifier.fillMaxSize())
                                    } else {
                                        // FE Review: 24→TouchTarget.ICON_LG
                                        Icon(Icons.Outlined.Image, null, Modifier.size(TouchTarget.ICON_LG.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                                    }
                                    val confColor = confidenceColor(d.confidence)
                                    Surface(Modifier.align(Alignment.TopEnd).padding(Spacing.SM.dp), shape = ShapeXS,
                                        color = confColor.copy(alpha = 0.20f)) {
                                        Text("${(d.confidence * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall, color = confColor,
                                            fontWeight = FontWeight.Bold,
                                            // FE Review: SM-2=6→XS=4水平；XS/2=2→XS=4垂直；视觉略松但一致性达标
                                            modifier = Modifier.padding(horizontal = Spacing.XS.dp, vertical = Spacing.XS.dp))
                                    }
                                }
                                Column(Modifier.padding(Spacing.MD.dp)) {
                                    Text(d.diseaseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(formatTimestamp(d.timestamp), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// FE Review: 最近识别卡片规格（布局级常量，非全局令牌，保留在文件顶层）
private const val RECENT_CARD_WIDTH = 160
private const val RECENT_CARD_IMAGE_HEIGHT = 100

private fun formatTimestamp(ts: Long): String {
    if (ts <= 0) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}

// ================================================================
// V2.0 新增首页入口：我的农场 + 农技搜索
// ================================================================

@Composable
private fun V2FeatureGrid(onFarm: () -> Unit, onAgriSearch: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.XL.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.MD.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("农场管理 · 农技工具",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            // V2.0 角标
            Surface(
                // FE Review: RoundedCornerShape(50) → ShapePill 统一入口
                shape = ShapePill,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(" V2.0 NEW ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)
        ) {
            // 我的农场
            QuickCard(
                mod = Modifier.weight(1f),
                icon = Icons.Filled.Agriculture,
                title = "我的农场",
                sub = "地块 · 用药 · 农情提醒",
                tone = brandGreen(),
                onClick = onFarm
            )
            // 农技搜索
            QuickCard(
                mod = Modifier.weight(1f),
                icon = Icons.Filled.Search,
                title = "农技搜索",
                sub = "知识库 RAG · 离线可用",
                tone = accentOrange(),
                onClick = onAgriSearch
            )
        }
    }
}

