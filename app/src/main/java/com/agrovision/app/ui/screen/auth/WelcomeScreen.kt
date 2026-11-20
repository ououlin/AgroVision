package com.agrovision.app.ui.screen.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.agrovision.app.BuildConfig
import com.agrovision.app.R
import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.ui.theme.*
import kotlinx.coroutines.delay

data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val color: Color
)

private val features = listOf(
    FeatureItem(Icons.Filled.CameraAlt, "拍照问诊", "拍清异常就能查", Green50),
    FeatureItem(Icons.Filled.PhotoLibrary, "相册问诊", "已有照片也能问", Blue50),
    FeatureItem(Icons.Filled.WifiOff, "离线可用", "田间无网也能用", Orange50),
    FeatureItem(Icons.Filled.Agriculture, "农事记录", "地块用药按时记", Purple50)
)

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onGuest: () -> Unit,
    onPrivacy: () -> Unit = {},
    onTerms: () -> Unit = {}
) {
    var agreed by remember { mutableStateOf(false) }
    var showAnim by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showAnim = true
    }

    val alphaAnim = animateFloatAsState(if (showAnim) 1f else 0f, tween(600))
    val logoScale = animateFloatAsState(if (showAnim) 1f else 0.8f, tween(800, easing = EaseOutBack))

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.XXL.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.HERO.dp))
            LogoSection(alphaAnim.value, logoScale.value)
            Spacer(Modifier.height(Spacing.XXL2.dp))
            FeaturesGrid(alphaAnim.value)
            Spacer(Modifier.height(Spacing.XXL.dp))
            ActionButtons(
                agreed = agreed,
                alpha = alphaAnim.value,
                accountEnabled = FeatureFlag.isV2AccountEnabled,
                onLogin = onLogin,
                onGuest = onGuest
            )
            AgreementRow(agreed, { agreed = it }, onPrivacy, onTerms)
            VersionInfo()
            Spacer(Modifier.height(Spacing.XL.dp))
        }
    }
}

@Composable
private fun LogoSection(alpha: Float, scale: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .scale(scale)
    ) {
        AsyncImage(
            model = R.drawable.nvtlogo,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(Spacing.MD.dp))
        HorizontalDivider(
            modifier = Modifier.height(32.dp).width(1.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        Spacer(Modifier.width(Spacing.MD.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = R.drawable.beilian_logo_simple,
                contentDescription = null,
                modifier = Modifier.height(28.dp)
            )
            Spacer(Modifier.width(Spacing.SM.dp))
            Column {
                Text(
                    "BEILIAN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onBackground
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

    Spacer(Modifier.height(Spacing.XL.dp))

    Text(
        "农智眼",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.alpha(alpha)
    )
    Text(
        "AgroVision AI",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.alpha(alpha)
    )

    Spacer(Modifier.height(Spacing.MD.dp))

    Surface(
        shape = ShapePill,
        color = Green50.copy(alpha = 0.08f)
    ) {
        Text(
            "让每一株作物，都被看见。",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Green50,
            modifier = Modifier
                .padding(horizontal = Spacing.LG.dp, vertical = Spacing.SM.dp)
                .alpha(alpha)
        )
    }
}

@Composable
private fun FeaturesGrid(alpha: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.MD.dp)) {
        features.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.MD.dp)
            ) {
                row.forEach { item ->
                    FeatureCard(item, alpha, Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(item: FeatureItem, alpha: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .alpha(alpha)
            .heightIn(min = 110.dp),
        shape = ShapeMD,
        color = item.color.copy(alpha = 0.06f),
        tonalElevation = Elevation.FLAT.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.MD.dp + Spacing.SM.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = ShapeXS,
                color = item.color.copy(alpha = 0.15f)
            ) {
                Icon(
                    item.icon, null,
                    Modifier.padding(Spacing.SM.dp).size(24.dp),
                    tint = item.color
                )
            }
            Spacer(Modifier.height(Spacing.MD.dp - Spacing.SM.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(Spacing.XS.dp))
            Text(
                item.desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun ActionButtons(
    agreed: Boolean,
    alpha: Float,
    accountEnabled: Boolean,
    onLogin: () -> Unit,
    onGuest: () -> Unit
) {
    Button(
        onClick = { if (agreed) (if (accountEnabled) onLogin else onGuest)() },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(alpha),
        shape = ShapeMD,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        enabled = agreed
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Green50, Green60)
                    ),
                    shape = ShapeMD
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(if (accountEnabled) "立即登录" else "开始使用",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White)
        }
    }

    if (accountEnabled) {
        Spacer(Modifier.height(Spacing.MD.dp))

        OutlinedButton(
            onClick = { if (agreed) onGuest() },
            modifier = Modifier.fillMaxWidth().height(50.dp).alpha(alpha),
            shape = ShapeMD,
            enabled = agreed,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text("游客体验",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium)
        }
    }

    Spacer(Modifier.height(Spacing.LG.dp))
}

@Composable
private fun AgreementRow(
    agreed: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit
) {
    val tagTerms = "terms"
    val tagPrivacy = "privacy"

    val annotatedText = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append("已阅读并同意")
        }
        pushStringAnnotation(tag = tagTerms, annotation = tagTerms)
        withStyle(SpanStyle(color = Blue50)) {
            append("《用户协议》")
        }
        pop()
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append("和")
        }
        pushStringAnnotation(tag = tagPrivacy, annotation = tagPrivacy)
        withStyle(SpanStyle(color = Blue50)) {
            append("《隐私政策》")
        }
        pop()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Center
    ) {
        Checkbox(
            checked = agreed,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(top = 2.dp),
            colors = CheckboxDefaults.colors(checkedColor = Green50)
        )
        Spacer(Modifier.width(Spacing.XS.dp))
        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodySmall,
            onClick = { offset ->
                annotatedText.getStringAnnotations(tag = tagTerms, start = offset, end = offset)
                    .firstOrNull()?.let { onTerms() }
                annotatedText.getStringAnnotations(tag = tagPrivacy, start = offset, end = offset)
                    .firstOrNull()?.let { onPrivacy() }
            }
        )
    }
    Spacer(Modifier.height(Spacing.MD.dp))
}

@Composable
private fun VersionInfo() {
    Text(
        "v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = Modifier.alpha(0.7f)
    )
}
