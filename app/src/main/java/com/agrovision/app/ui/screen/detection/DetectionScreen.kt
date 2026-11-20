package com.agrovision.app.ui.screen.detection

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.domain.model.DetectionResult
import com.agrovision.app.ui.components.PermissionRationaleDialog
import com.agrovision.app.ui.theme.*
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(
    onNavigateToPestDetail: (String) -> Unit,
    onNavigateToDiagnosis: (String, Float, String) -> Unit = { _, _, _ -> },
    initialOpenGallery: Boolean = false,
    onGalleryConsumed: () -> Unit = {},
    viewModel: DetectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cropOptions = remember {
        listOf(
            "自动识别", "苹果", "蓝莓", "樱桃", "玉米", "葡萄", "柑橘", "桃",
            "辣椒", "马铃薯", "树莓", "大豆", "南瓜", "草莓", "番茄"
        )
    }

    // ================ 权限：相机 Rationale 统一走 PermissionRationaleDialog ================
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var showCameraRationale by remember { mutableStateOf(false) }
    /** 是否已经弹过一次系统权限框（用于判断永久拒绝） */
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // 实时识别开关：默认关闭，避免一进入就持续推理
    var realtimeEnabled by remember { mutableStateOf(false) }

    // DisposableEffect 移到顶层，确保 Composable 销毁时清理相机线程
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedOnce = true
        hasCameraPermission = granted
        Timber.d("相机权限: $granted")
    }

    /** 统一入口：弹 Rationale → 真正请求相机权限 */
    fun requestCameraPermissionWithRationale() {
        // 1) 已经授予 → 直接返回
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
            return
        }
        // 2) 否则先弹 Rationale
        showCameraRationale = true
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        Timber.d("相册选择: $uri")
        if (uri != null) handleGalleryImage(context, uri, viewModel, uiState.selectedCropType)
    }

    // 启动时：不直接弹系统权限请求，按合规先弹 Rationale（仅从未授予时）
    LaunchedEffect(Unit) {
        Timber.d("=== 进入识别页：先检查相机权限并准备 Rationale ===")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // 延迟一点点，让页面先渲染出来再弹框，体验更好
            kotlinx.coroutines.delay(300)
            showCameraRationale = true
        }
    }

    // ================ 相机 Rationale Dialog ================
    if (showCameraRationale) {
        val permanentlyDenied = remember(hasRequestedOnce) {
            hasRequestedOnce &&
                (context as? androidx.activity.ComponentActivity)
                    ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        }
        PermissionRationaleDialog(
            icon = Icons.Filled.CameraAlt,
            title = "相机权限说明",
            purpose = "用于拍摄作物叶片、花朵、果实的异常部位进行问诊。",
            whatHappens = "照片默认在设备本地分析，不会把原始图片上传到云端；您拍摄的照片仅在您点击「保存」时写入本地相册。",
            whatIfDeny = "拒绝后仍可使用「相册问诊」，但无法直接拍摄照片。",
            permanentlyDenied = permanentlyDenied,
            onConfirm = {
                showCameraRationale = false
                if (permanentlyDenied) openAppSettings()
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = { showCameraRationale = false; openAppSettings() },
            onDismiss = { showCameraRationale = false }
        )
    }

    // 从首页"图片识别"入口进入时，自动唤起相册选择
    LaunchedEffect(initialOpenGallery) {
        if (initialOpenGallery) {
            imagePickerLauncher.launch("image/*")
            onGalleryConsumed()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // 监听结果：仅当已发起过一次检测且检测完成时，才根据结果做反馈
    LaunchedEffect(uiState.results, uiState.isDetecting) {
        if (uiState.hasAttemptedDetection && !uiState.isDetecting) {
            if (uiState.results.isNotEmpty()) {
                if (realtimeEnabled) {
                    // 实时模式：不弹 BottomSheet，结果通过浮层展示
                    Timber.d("实时识别完成，${uiState.results.size}个结果，浮层展示")
                } else {
                    Timber.d("检测完成，${uiState.results.size}个结果，弹出BottomSheet")
                    showBottomSheet = true
                }
            } else {
                // 检测完成但无可靠结果，不能直接推断为健康。
                Timber.w("检测完成但无结果")
                if (!realtimeEnabled) {
                    snackbarHostState.showSnackbar("未得到可靠结果，请切换“自动识别”或重新拍摄")
                }
            }
        }
    }

    // 监听错误
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Timber.e("UI错误: $it")
            snackbarHostState.showSnackbar(it)
        }
    }

    // ========== 拍照主流程 ==========
    fun takePhoto() {
        Timber.d("=== 拍照按钮点击 ===")
        val capture = imageCapture
        if (capture == null) {
            Timber.e("ImageCapture 未初始化!")
            return
        }
        // 防止重复触发
        if (uiState.isDetecting) {
            Timber.w("正在识别中，忽略本次点击")
            return
        }

        Timber.d("ImageCapture OK, 创建临时文件...")
        val photoFile = createTempFile(context)
        Timber.d("临时文件: ${photoFile.absolutePath}")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        Timber.d("开始拍照 takePicture()...")

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Timber.d("拍照成功! 文件: ${photoFile.absolutePath}, 大小: ${photoFile.length()}bytes")
                    viewModel.setCapturedImagePath(photoFile.absolutePath)

                    Timber.d("加载Bitmap...")
                    val bmp = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bmp == null) {
                        Timber.e("Bitmap 加载失败!")
                        return
                    }
                    Timber.d("Bitmap OK: ${bmp.width}x${bmp.height}")
                    // Bitmap 传入 ViewModel 后由 ViewModel 在推理完成后 recycle
                    viewModel.detectImage(bmp, uiState.selectedCropType)
                }

                override fun onError(exc: ImageCaptureException) {
                    Timber.e(exc, "拍照失败")
                    viewModel.setCapturedImagePath("")
                }
            }
        )
    }

    // ========== UI ==========
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hasCameraPermission) {
                CameraPreviewView(
                    lifecycleOwner = lifecycleOwner,
                    onImageCaptureReady = { imageCapture = it },
                    realtimeEnabled = realtimeEnabled,
                    onFrameAnalyzed = { bmp ->
                        if (realtimeEnabled && !uiState.isDetecting) {
                            // 帧分析 bitmap 传入 ViewModel 后由 ViewModel recycle
                            viewModel.detectImage(bmp, uiState.selectedCropType)
                        } else {
                            // 未用于推理的帧 bitmap 立即回收
                            bmp.recycle()
                        }
                    }
                )
            } else {
                Column(
                    Modifier.fillMaxSize()
                        // FE Review: 40→Spacing.HERO
                        .padding(Spacing.HERO.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.CameraAlt, null, Modifier.size(CAMERA_EMPTY_ICON.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    // FE Review: 20→Spacing.XL
                    Spacer(Modifier.height(Spacing.XL.dp))
                    Text("需要相机权限", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(Spacing.SM.dp))
                    Text("点击下方按钮，先查看权限用途说明，再授予相机权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // FE Review: 12→Spacing.MD
                        modifier = Modifier.padding(horizontal = Spacing.MD.dp))
                    Spacer(Modifier.height(Spacing.LG.dp))
                    FilledTonalButton(
                        onClick = { requestCameraPermissionWithRationale() },
                        shape = ShapeMD,
                        modifier = Modifier.heightIn(min = TouchTarget.MIN_ALT.dp)
                    ) {
                        Icon(Icons.Filled.Info, null, modifier = Modifier.size(BUTTON_INFO_ICON.dp))
                        Spacer(Modifier.width(Spacing.XS.dp))
                        Text("查看权限说明并授予")
                    }
                    Spacer(Modifier.height(Spacing.SM.dp))
                    TextButton(
                        onClick = { openAppSettings() },
                        modifier = Modifier.heightIn(min = TouchTarget.MIN_ALT.dp)
                    ) {
                        Icon(Icons.Filled.Settings, null, modifier = Modifier.size(BUTTON_INFO_ICON.dp))
                        Spacer(Modifier.width(Spacing.XS.dp))
                        Text("或前往系统设置开启")
                    }
                }
            }

            if (hasCameraPermission) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(CAMERA_TOP_SCRIM_HEIGHT.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                )

                CameraHeader(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = Spacing.LG.dp, vertical = Spacing.MD.dp),
                    selectedCrop = uiState.selectedCropType,
                    cropOptions = cropOptions,
                    modelReady = uiState.isModelReady,
                    onCropSelected = viewModel::setSelectedCropType
                )

                FocusFrame(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XXL2.dp)
                        .offset(y = (-Spacing.XL).dp)
                        .aspectRatio(FOCUS_FRAME_ASPECT_RATIO),
                    isScanning = uiState.isDetecting || realtimeEnabled
                )

                BottomControls(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onTakePicture = { takePhoto() },
                    onOpenGallery = { imagePickerLauncher.launch("image/*") },
                    realtimeEnabled = realtimeEnabled,
                    onRealtimeToggle = {
                        realtimeEnabled = !realtimeEnabled
                        viewModel.clearResults()
                    },
                    isDetecting = uiState.isDetecting,
                    realtimeResult = if (realtimeEnabled) uiState.results.firstOrNull() else null
                )
            }

            // 模型未就绪遮罩 — 显示明确错误，而非静默失败
            if (!uiState.isModelReady && uiState.error != null) {
                Box(
                    // Color.Black遮罩在相机预览上是合法场景（非主题层），保留
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        // FE Review: 32→Spacing.XXL3
                        Modifier.padding(Spacing.XXL3.dp),
                        // FE Review: 20→Radius.LG 20 正好
                        shape = ShapeLG,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            // FE Review: 24→Spacing.XXL
                            Modifier.padding(Spacing.XXL.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.ErrorOutline, null,
                                // FE Review: 48→TouchTarget.AVATAR
                                Modifier.size(TouchTarget.AVATAR.dp),
                                tint = accentOrange())
                            // FE Review: 16→Spacing.LG
                            Spacer(Modifier.height(Spacing.LG.dp))
                            Text("问诊功能暂时不可用", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            // FE Review: 8→Spacing.SM
                            Spacer(Modifier.height(Spacing.SM.dp))
                            Text(
                                "识别组件加载失败，请重新打开应用后再试。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // BottomSheet — 在 Box 内条件渲染
            if (showBottomSheet && uiState.results.isNotEmpty()) {
                val capturedPath = uiState.capturedImagePath ?: ""
                DetectionResultSheet(
                    result = uiState.results.first(),
                    onDismiss = { showBottomSheet = false; viewModel.clearResults() },
                    onViewDetail = { r -> onNavigateToPestDetail(r.label) },
                    onDiagnosis = { r -> onNavigateToDiagnosis(r.cnName.ifEmpty { r.label }, r.confidence, capturedPath) }
                )
            }
        }
    }
}

@Composable
private fun CameraHeader(
    modifier: Modifier = Modifier,
    selectedCrop: String,
    cropOptions: List<String>,
    modelReady: Boolean,
    onCropSelected: (String) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = ShapeMD,
        color = Color.Black.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.LG.dp, vertical = Spacing.MD.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(HEADER_ICON_SIZE.dp),
                    shape = ShapeXS,
                    color = brandGreen().copy(alpha = 0.20f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Eco,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(TouchTarget.ICON.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.MD.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "AI 植保识别",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "38 类病害模型 · 本地分析",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(STATUS_DOT.dp)
                            .background(
                                if (modelReady) brandGreen() else accentOrange(),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(Spacing.XS.dp))
                    Text(
                        if (modelReady) "模型就绪" else "加载中",
                        color = Color.White.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(Spacing.MD.dp))
            CropSelectorBar(
                selectedCrop = selectedCrop,
                cropOptions = cropOptions,
                onCropSelected = onCropSelected
            )
        }
    }
}

@Composable
private fun FocusFrame(
    modifier: Modifier = Modifier,
    isScanning: Boolean
) {
    val accent = brandGreen()
    val transition = rememberInfiniteTransition(label = "focus_scan")
    val scanProgress by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(SCAN_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_progress"
    )

    Box(
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.22f), ShapeLG),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val corner = FOCUS_CORNER_LENGTH.dp.toPx()
            val inset = FOCUS_CORNER_INSET.dp.toPx()
            val stroke = FOCUS_CORNER_STROKE.dp.toPx()
            val maxX = size.width - inset
            val maxY = size.height - inset

            fun cornerLine(start: Offset, end: Offset) {
                drawLine(Color.White, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
            }

            cornerLine(Offset(inset, inset), Offset(inset + corner, inset))
            cornerLine(Offset(inset, inset), Offset(inset, inset + corner))
            cornerLine(Offset(maxX, inset), Offset(maxX - corner, inset))
            cornerLine(Offset(maxX, inset), Offset(maxX, inset + corner))
            cornerLine(Offset(inset, maxY), Offset(inset + corner, maxY))
            cornerLine(Offset(inset, maxY), Offset(inset, maxY - corner))
            cornerLine(Offset(maxX, maxY), Offset(maxX - corner, maxY))
            cornerLine(Offset(maxX, maxY), Offset(maxX, maxY - corner))

            if (isScanning) {
                val y = size.height * scanProgress
                drawLine(
                    color = accent.copy(alpha = 0.92f),
                    start = Offset(inset + corner, y),
                    end = Offset(maxX - corner, y),
                    strokeWidth = SCAN_LINE_STROKE.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.MD.dp),
            shape = ShapePill,
            color = Color.Black.copy(alpha = 0.42f)
        ) {
            Text(
                if (isScanning) "正在扫描" else "将异常部位置于框内",
                modifier = Modifier.padding(horizontal = Spacing.MD.dp, vertical = Spacing.XS.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.90f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionResultSheet(
    result: DetectionResult,
    onDismiss: () -> Unit,
    onViewDetail: (DetectionResult) -> Unit,
    onDiagnosis: (DetectionResult) -> Unit
) {
    val isHealthy = result.label.endsWith("healthy", ignoreCase = true)
    val dc = if (isHealthy) brandGreen() else dangerColor(result.pestInfo?.dangerLevel ?: "中")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // FE Review: 24→Radius.LG=20 稍小可接受；BottomSheet顶圆角保留SHEET_CORNER=24
        shape = RoundedCornerShape(topStart = SHEET_CORNER.dp, topEnd = SHEET_CORNER.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            // FE Review: 24→XXL, 8→SM
            Modifier.padding(horizontal = Spacing.XXL.dp, vertical = Spacing.SM.dp)
        ) {
            // 顶部拖拽指示条
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    // FE Review: 36→Spacing.XXL3=32; 4→XS; 2→保留为小圆角（Material拖拽条规范）
                    .width(Spacing.XXL3.dp).height(Spacing.XS.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(DRAG_HANDLE_CORNER.dp)
                    )
            )
            // FE Review: 16→LG
            Spacer(Modifier.height(Spacing.LG.dp))

            Text("问诊结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.LG.dp))

            // 病害头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    // FE Review: 48→TouchTarget.AVATAR
                    modifier = Modifier.size(TouchTarget.AVATAR.dp),
                    // FE Review: 12→Radius.MD
                    shape = ShapeMD,
                    color = dc.copy(alpha = 0.10f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // FE Review: padding(10)→Box居中+ICON_LG
                        Icon(if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.BugReport, null,
                            Modifier.size(TouchTarget.ICON_LG.dp),
                            tint = dc)
                    }
                }
                // FE Review: 12→MD
                Spacer(Modifier.width(Spacing.MD.dp))
                Column {
                    val certainty = when {
                        isHealthy -> "叶片状态正常"
                        result.confidence >= 0.8f -> "较确定"
                        result.confidence >= 0.55f -> "可能是"
                        else -> "需要补拍确认"
                    }
                    Text(result.cnName.ifEmpty { result.label },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(certainty,
                        style = MaterialTheme.typography.labelMedium, color = dc)
                }
            }

            if (result.pestInfo != null && !isHealthy) {
                Spacer(Modifier.height(Spacing.MD.dp))
                Text("严重程度：${result.pestInfo.dangerLevel}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = dc)
                Text(result.pestInfo.description.take(100),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // FE Review: 24→XXL
            Spacer(Modifier.height(Spacing.XXL.dp))

            if (result.confidence < 0.55f) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMD,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        "这张照片的信息不够。请靠近异常部位、保持清晰并换一个角度重拍。",
                        modifier = Modifier.padding(Spacing.MD.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(Spacing.MD.dp))
            }

            Row(Modifier.fillMaxWidth(),
                // FE Review: 10→SM=8 稍小；保留 ACTION_GAP=10
                horizontalArrangement = Arrangement.spacedBy(ACTION_GAP.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    // FE Review: 14→Radius.MD
                    shape = ShapeMD
                ) { Text("重新拍照") }
                Button(
                    onClick = { onDiagnosis(result); onDismiss() },
                    modifier = Modifier.weight(1f),
                    shape = ShapeMD,
                    enabled = result.confidence >= 0.35f
                ) { Text(if (isHealthy) "查看养护建议" else "查看处理建议") }
            }
            // FE Review: 10→SM=8
            Spacer(Modifier.height(Spacing.SM.dp))
            TextButton(onClick = { onViewDetail(result); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.MenuBook, null, Modifier.size(BUTTON_INFO_ICON.dp))
                Spacer(Modifier.width(Spacing.XS.dp))
                Text(if (isHealthy) "查看作物百科" else "先查病虫百科")
            }
            // FE Review: 16→LG
            Spacer(Modifier.height(Spacing.LG.dp))
        }
    }
}

// ========== CameraX Preview ==========
// 关键修复：用 key(realtimeEnabled) 强制 Compose 在开关变化时销毁并重建 AndroidView
// 确保 ImageAnalysis 的绑定/解绑立即生效
@Composable
private fun CameraPreviewView(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onImageCaptureReady: (ImageCapture) -> Unit,
    realtimeEnabled: Boolean,
    onFrameAnalyzed: (Bitmap) -> Unit
) {
    // key() 确保 realtimeEnabled 变化时 View 完全销毁重建
    key(realtimeEnabled) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val provider = ProcessCameraProvider.getInstance(ctx)
                provider.addListener({
                    val cp = provider.get()
                    Timber.d("CameraProvider ready, realtime=$realtimeEnabled")

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(this.surfaceProvider)
                    }

                    val ic = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    onImageCaptureReady(ic)
                    Timber.d("ImageCapture 就绪")

                    try {
                        cp.unbindAll()

                        if (!realtimeEnabled) {
                            // 默认模式：纯预览 + 拍照。不绑 ImageAnalysis。
                            cp.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, ic)
                            Timber.d("拍照模式: Preview+ImageCapture (不运行分析)")
                        } else {
                            // 实时模式：降低分析频率，避免相机阻塞
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()
                            Timber.d("ImageAnalysis 构建完成")

                            var lastAnalyzeTime = 0L
                            val analyzer = ImageAnalysis.Analyzer { proxy ->
                                try {
                                    // 帧率节流：每 500ms 最多处理一帧，避免每帧 YUV→JPEG→Bitmap 解码造成卡顿
                                    val now = System.currentTimeMillis()
                                    if (now - lastAnalyzeTime < 500) {
                                        return@Analyzer
                                    }
                                    lastAnalyzeTime = now

                                    val img = proxy.image ?: return@Analyzer
                                    val y = img.planes[0].buffer
                                    val u = img.planes[1].buffer
                                    val v = img.planes[2].buffer
                                    val nv21 = ByteArray(y.remaining() + u.remaining() + v.remaining())
                                    y.get(nv21, 0, y.remaining())
                                    v.get(nv21, y.remaining(), v.remaining())
                                    u.get(nv21, y.remaining() + v.remaining(), u.remaining())
                                    val yuv = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, img.width, img.height, null)
                                    val out = java.io.ByteArrayOutputStream()
                                    yuv.compressToJpeg(android.graphics.Rect(0, 0, img.width, img.height), 80, out)
                                    val bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                                    if (bmp != null) onFrameAnalyzed(bmp)
                                } catch (e: Exception) {
                                    Timber.e(e, "帧分析异常")
                                } finally {
                                    proxy.close()
                                }
                            }
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx), analyzer)
                            cp.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, ic, analysis)
                            Timber.d("[实时] Preview+ImageCapture+ImageAnalysis")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "CameraX绑定失败")
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        })
    }
}

// ========== 辅助方法 ==========
private fun createTempFile(ctx: android.content.Context): File {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir = File(ctx.cacheDir, "image_cache")
    if (!dir.exists()) {
        dir.mkdirs()
        Timber.d("创建缓存目录: ${dir.absolutePath}")
    }
    return File(dir, "agrovision_${stamp}.jpg")
}

private fun handleGalleryImage(
    ctx: android.content.Context,
    uri: Uri,
    vm: DetectionViewModel,
    cropType: String
) {
    Timber.d("=== 相册图片处理 ===")
    Timber.d("URI: $uri")
    try {
        val inputStream = ctx.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Timber.e("openInputStream 返回 null")
            return
        }
        val bmp = inputStream.use { BitmapFactory.decodeStream(it) }
        if (bmp == null) {
            Timber.e("decodeStream 失败，文件可能不是有效图片")
            return
        }
        Timber.d("相册Bitmap: ${bmp.width}x${bmp.height}, 大小: ${bmp.byteCount / 1024}KB")

        // 保存到缓存
        val cacheDir = File(ctx.cacheDir, "image_cache")
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
        cacheFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Timber.d("已缓存到: ${cacheFile.absolutePath}")

        vm.setCapturedImagePath(cacheFile.absolutePath)
        Timber.d("开始推理...")
        // Bitmap 传入 ViewModel 后由 ViewModel 在推理完成后 recycle
        vm.detectImage(bmp, cropType)
    } catch (e: Exception) {
        Timber.e(e, "相册处理异常")
    }
}

@Composable
private fun BottomControls(
    modifier: Modifier = Modifier,
    onTakePicture: () -> Unit,
    onOpenGallery: () -> Unit,
    realtimeEnabled: Boolean,
    onRealtimeToggle: () -> Unit,
    isDetecting: Boolean,
    realtimeResult: DetectionResult?
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.XL.dp,
                    end = Spacing.XL.dp,
                    top = BOTTOM_DOCK_TOP_PAD.dp,
                    bottom = Spacing.XL.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecognitionStatus(
                isDetecting = isDetecting,
                realtimeEnabled = realtimeEnabled,
                realtimeResult = realtimeResult
            )
            Spacer(Modifier.height(Spacing.LG.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "相册",
                    onClick = onOpenGallery
                )
                Surface(
                    modifier = Modifier.size(SHUTTER_SIZE.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = if (isDetecting) 0.45f else 0.98f),
                    border = BorderStroke(SHUTTER_RING_STROKE.dp, Color.White.copy(alpha = 0.42f)),
                    enabled = !isDetecting,
                    onClick = onTakePicture
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(SHUTTER_INNER_SIZE.dp),
                            shape = CircleShape,
                            color = brandGreen()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.CenterFocusStrong,
                                    contentDescription = "拍照识别",
                                    modifier = Modifier.size(Spacing.XXL3.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
                ControlButton(
                    icon = if (realtimeEnabled) Icons.Filled.Videocam else Icons.Outlined.Videocam,
                    label = "实时",
                    active = realtimeEnabled,
                    onClick = onRealtimeToggle
                )
            }
        }
    }
}

@Composable
private fun RecognitionStatus(
    isDetecting: Boolean,
    realtimeEnabled: Boolean,
    realtimeResult: DetectionResult?
) {
    val statusColor = when {
        realtimeResult != null -> dangerColor(realtimeResult.pestInfo?.dangerLevel ?: "中")
        isDetecting || realtimeEnabled -> brandGreen()
        else -> Color.White.copy(alpha = 0.72f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeMD,
        color = Color.Black.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.LG.dp, vertical = Spacing.MD.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDetecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(TouchTarget.ICON.dp),
                    color = statusColor,
                    strokeWidth = STATUS_PROGRESS_STROKE.dp
                )
            } else {
                Icon(
                    imageVector = when {
                        realtimeResult?.label?.endsWith("healthy", ignoreCase = true) == true -> Icons.Filled.CheckCircle
                        realtimeResult != null -> Icons.Filled.BugReport
                        realtimeEnabled -> Icons.Filled.CenterFocusStrong
                        else -> Icons.Outlined.Lightbulb
                    },
                    contentDescription = null,
                    modifier = Modifier.size(TouchTarget.ICON.dp),
                    tint = statusColor
                )
            }
            Spacer(Modifier.width(Spacing.MD.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        isDetecting -> "AI 正在分析"
                        realtimeResult != null -> realtimeResult.cnName.ifEmpty { realtimeResult.label }
                        realtimeEnabled -> "实时识别中"
                        else -> "对准叶片异常部位"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isDetecting -> "正在运行本地 MobileNetV3 模型"
                        realtimeResult != null -> "置信度 ${realtimeResult.confidencePercent}"
                        realtimeEnabled -> "保持画面稳定，结果会自动更新"
                        else -> "保持清晰，避免逆光和阴影遮挡"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (realtimeEnabled && !isDetecting) {
                Surface(
                    shape = ShapePill,
                    color = brandGreen().copy(alpha = 0.18f)
                ) {
                    Text(
                        "LIVE",
                        modifier = Modifier.padding(horizontal = Spacing.SM.dp, vertical = Spacing.XS.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = brandGreen(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CropSelectorBar(
    modifier: Modifier = Modifier,
    selectedCrop: String,
    cropOptions: List<String>,
    onCropSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeXS,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            onClick = { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.MD.dp, vertical = Spacing.SM.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Spa,
                    contentDescription = null,
                    tint = brandGreen(),
                    modifier = Modifier.size(BUTTON_INFO_ICON.dp)
                )
                Spacer(Modifier.width(Spacing.SM.dp))
                Text(
                    "识别作物",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f)
                )
                Spacer(Modifier.width(Spacing.SM.dp))
                Text(
                    selectedCrop,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = "选择识别作物",
                    tint = Color.White.copy(alpha = 0.76f)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = CROP_MENU_MIN_WIDTH.dp, max = CROP_MENU_MAX_WIDTH.dp)
        ) {
            cropOptions.forEach { crop ->
                DropdownMenuItem(
                    text = { Text(crop) },
                    leadingIcon = {
                        if (crop == selectedCrop) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = brandGreen())
                        } else {
                            Spacer(Modifier.size(TouchTarget.ICON_LG.dp))
                        }
                    },
                    onClick = {
                        onCropSelected(crop)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(CONTROL_BUTTON_SIZE.dp),
            shape = CircleShape,
            color = if (active) brandGreen().copy(alpha = 0.24f) else Color.White.copy(alpha = 0.12f),
            border = BorderStroke(
                1.dp,
                if (active) brandGreen().copy(alpha = 0.72f) else Color.White.copy(alpha = 0.12f)
            ),
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    Modifier.size(TouchTarget.ICON_LG.dp),
                    tint = if (active) brandGreen() else Color.White
                )
            }
        }
        Spacer(Modifier.height(Spacing.XS.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) brandGreen() else Color.White.copy(alpha = 0.82f)
        )
    }
}

// FE Review: DetectionScreen 文件级规格常量（相机/识别场景的专属展示尺寸，不适合放入全局 DesignTokens）
private const val CAMERA_EMPTY_ICON = 56
private const val BUTTON_INFO_ICON = 18
private const val SHEET_CORNER = 24
private const val DRAG_HANDLE_CORNER = 2
private const val PROGRESS_HEIGHT = 6
private const val ACTION_GAP = 10
private const val STATUS_DOT = 6
private const val SHUTTER_SIZE = 78
private const val SHUTTER_INNER_SIZE = 62
private const val SHUTTER_RING_STROKE = 3
private const val CONTROL_BUTTON_SIZE = 52
private const val HEADER_ICON_SIZE = 40
private const val CAMERA_TOP_SCRIM_HEIGHT = 228
private const val BOTTOM_DOCK_TOP_PAD = 44
private const val FOCUS_CORNER_LENGTH = 34
private const val FOCUS_CORNER_INSET = 10
private const val FOCUS_CORNER_STROKE = 3
private const val SCAN_LINE_STROKE = 2
private const val STATUS_PROGRESS_STROKE = 2
private const val SCAN_DURATION_MS = 1400
private const val CROP_MENU_MIN_WIDTH = 220
private const val CROP_MENU_MAX_WIDTH = 320
private const val FOCUS_FRAME_ASPECT_RATIO = 1.0f
