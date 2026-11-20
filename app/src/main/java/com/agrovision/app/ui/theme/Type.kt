package com.agrovision.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AgroVision 排版系统 — 基于 taste-skill 层级纪律
 *
 * 原则：
 *  - 标题/正文/标签 三级对比必须鲜明
 *  - 移动端字号合理，不滥用超大标题
 *  - displayLarge 仅用于品牌首页大字，细节页用 headline
 *  - 正文 lineHeight 1.5x 保证呼吸感
 *
 * 字体策略：系统默认 sans-serif
 *  (Android minSdk=26 默认字体相当于现代无衬线，无需额外加载)
 *
 * 通过 [scaledTypography] 按用户设置的字体大小级别返回缩放后的 Typography。
 */
private fun scaledStyle(
    base: TextStyle, scale: Float
): TextStyle = base.copy(
    fontSize = (base.fontSize.value * scale).sp,
    lineHeight = (base.lineHeight.value * scale).sp
)

/** 基准（normal）Typography */
private val BaseTypography = Typography(
    // ===== 展示级 — 只用于首页 Hero =====
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),

    // ===== 标题级 =====
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),

    // ===== 卡片标题/列表项 =====
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),

    // ===== 正文 — 核心读感 =====
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp   // 1.5x
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp   // 1.5x
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp   // 1.5x
    ),

    // ===== 标签 — 微信息 / 辅助 =====
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 根据字体大小级别返回缩放后的 Typography。
 * letterSpacing 同步缩放以保持视觉比例。
 */
fun scaledTypography(fontSize: FontSize): Typography {
    val s = fontSize.scale
    return Typography(
        displayLarge = scaledStyle(BaseTypography.displayLarge, s),
        displayMedium = scaledStyle(BaseTypography.displayMedium, s),
        headlineLarge = scaledStyle(BaseTypography.headlineLarge, s),
        headlineMedium = scaledStyle(BaseTypography.headlineMedium, s),
        headlineSmall = scaledStyle(BaseTypography.headlineSmall, s),
        titleLarge = scaledStyle(BaseTypography.titleLarge, s),
        titleMedium = scaledStyle(BaseTypography.titleMedium, s),
        titleSmall = scaledStyle(BaseTypography.titleSmall, s),
        bodyLarge = scaledStyle(BaseTypography.bodyLarge, s),
        bodyMedium = scaledStyle(BaseTypography.bodyMedium, s),
        bodySmall = scaledStyle(BaseTypography.bodySmall, s),
        labelLarge = scaledStyle(BaseTypography.labelLarge, s),
        labelMedium = scaledStyle(BaseTypography.labelMedium, s),
        labelSmall = scaledStyle(BaseTypography.labelSmall, s)
    )
}

/** 默认排版（normal），保留旧 API 兼容 */
val AgroVisionTypography: Typography = BaseTypography
