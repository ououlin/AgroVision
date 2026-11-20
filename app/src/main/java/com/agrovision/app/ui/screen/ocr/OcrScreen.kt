package com.agrovision.app.ui.screen.ocr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agrovision.app.data.ocr.FertilizerInfo
import com.agrovision.app.data.ocr.PesticideInfo
import com.agrovision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onNavigateBack: () -> Unit,
    viewModel: OcrViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { imageUri = it } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR 识别", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // 模式选择
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = uiState.ocrMode == OcrMode.PESTICIDE,
                    onClick = { viewModel.setMode(OcrMode.PESTICIDE) },
                    label = { Text("农药说明书") },
                    leadingIcon = { Icon(Icons.Filled.Medication, null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState.ocrMode == OcrMode.FERTILIZER,
                    onClick = { viewModel.setMode(OcrMode.FERTILIZER) },
                    label = { Text("肥料包装") },
                    leadingIcon = { Icon(Icons.Filled.Eco, null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 图片区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { imagePicker.launch("image/*") },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(8.dp))
                            Text("点击选择图片", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text("支持农药说明书、肥料包装袋", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }

            // 识别按钮
            if (imageUri != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { imageUri?.let { viewModel.recognize(context, it) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !uiState.isRecognizing
                ) {
                    if (uiState.isRecognizing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Green50)
                    } else {
                        Icon(Icons.Filled.DocumentScanner, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isRecognizing) "识别中..." else "开始识别")
                }
            }

            // 结果
            when {
                uiState.isRecognizing -> {
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Green50)
                            Spacer(Modifier.height(12.dp))
                            Text("正在识别文字", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                uiState.pesticideResult != null -> {
                    Spacer(Modifier.height(16.dp))
                    PesticideResultCard(uiState.pesticideResult!!)
                }
                uiState.fertilizerResult != null -> {
                    Spacer(Modifier.height(16.dp))
                    FertilizerResultCard(uiState.fertilizerResult!!)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PesticideResultCard(info: PesticideInfo) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("解析结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp))
        OcrRow("产品名称", info.productName)
        OcrRow("有效成分", info.activeIngredient)
        OcrRow("含量/浓度", info.content)
        OcrRow("剂型", info.dosageForm)
        OcrRow("防治对象", info.target)
        OcrRow("使用剂量", info.dosage)
        OcrRow("安全间隔期", info.safetyInterval)
        OcrRow("注意事项", info.precautions)
    }
}

@Composable
private fun FertilizerResultCard(info: FertilizerInfo) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("解析结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp))
        OcrRow("产品名称", info.productName)
        OcrRow("N-P-K 养分", info.npk)
        OcrRow("有机质", info.organicMatter)
        OcrRow("适用作物", info.applicableCrops)
        OcrRow("使用方法", info.usage)
        OcrRow("施用量", info.dosage)
    }
}

@Composable
private fun OcrRow(label: String, value: String) {
    if (value.isNotBlank()) {
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(Modifier.padding(12.dp)) {
                Text("$label：", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
