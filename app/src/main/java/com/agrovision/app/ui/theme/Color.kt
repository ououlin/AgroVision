package com.agrovision.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 语义色板（严格 5 色系统）
 *
 * 所有 UI 层代码中，除特殊声明的展示常量外，必须优先使用 SemanticColors.kt 中的
 * Composable 函数（brandGreen()/brandBlue()/accentOrange()/brandRed()）
 * — 这些函数会根据深浅模式自动选色，避免深色模式对比度灾难。
 *
 * 命名语义：
 *   BrandMain   → 品牌主绿（主按钮、CTA、Logo 背景）
 *   StateSuccess → 成功、健康、完成（指示灯、状态图标）
 *   BrandBlue   → 品牌蓝（次要导航、标签、链接）
 *   StateWarn   → 警示橙（中风险、提示）
 *   StateDanger → 危险红（破坏性操作、错误、高风险）
 */
val BrandMain    = Color(0xFF2E7D32) // 原 Green50  — 品牌主绿 700
val StateSuccess = Color(0xFF4CAF50) // 原 Green40 — 成功绿 500（与 LightSecondary 区分）
val BrandBlue    = Color(0xFF1976D2) // 原 Blue50   — 信息蓝
val StateWarn    = Color(0xFFFF9800) // 原 Orange50 — 警示橙
val StateDanger  = Color(0xFFD32F2F) // 原 Red50    — 危险红
val Purple50    = Color(0xFF7B1FA2) // 保留：紫色辅助

/* 向后兼容别名（老代码可继续使用，新代码请用语义命名） */
@Deprecated("使用 BrandMain 或 SemanticColors.brandGreen() 代替")
val Green50 = BrandMain
@Deprecated("使用 StateSuccess 或 SemanticColors.brandGreen() 代替")
val Green40 = StateSuccess
@Deprecated("使用 StateSuccess 代替")
val Success50 = StateSuccess
@Deprecated("使用 StateSuccess 代替")
val Success40 = StateSuccess
@Deprecated("不再鼓励直接使用数值别名，请用 StateWarn")
val Orange50 = StateWarn
@Deprecated("使用 StateWarn 代替")
val Warning50 = StateWarn
@Deprecated("不再鼓励直接使用数值别名，请用 BrandBlue")
val Blue50 = BrandBlue
@Deprecated("不再鼓励直接使用数值别名，请用 StateDanger")
val Red50 = StateDanger

// ===== 中性色 =====
val Green5   = Color(0xFFE8F5E9)
val Green60  = Color(0xFF1B5E20)
val Neutral0  = Color(0xFFFFFFFF)
val Neutral2  = Color(0xFFF8FAF8)
val Neutral5  = Color(0xFFF3F5F3)
val Neutral10 = Color(0xFFE8EBE8)
val Neutral20 = Color(0xFFD2D6D2)
val Neutral30 = Color(0xFF9DA39D)
val Neutral50 = Color(0xFF4B554B)
val Neutral70 = Color(0xFF1F2B1F)
val Neutral80 = Color(0xFF111A11)
val Neutral90 = Color(0xFF0A0F0A)

val DarkBgDeep   = Color(0xFF0D1117)
val DarkBgCard   = Color(0xFF161B22)
val DarkTextPri  = Color(0xFFE6EDF3)
val DarkTextSec  = Color(0xFF8B949E)
val DarkBorder   = Color(0xFF30363D)

// ===== Material3 Theme Tokens =====
val LightPrimary = BrandMain
val LightOnPrimary = Color.White
val LightPrimaryContainer = Green5
val LightOnPrimaryContainer = Green60
val LightSecondary = BrandBlue
val LightOnSecondary = Color.White
val LightSecondaryContainer = Color(0xFFE3F2FD)
val LightTertiary = StateWarn
val LightOnTertiary = Color.White
val LightBackground = Neutral2
val LightOnBackground = Neutral70
val LightSurface = Neutral0
val LightOnSurface = Neutral70
val LightSurfaceVariant = Neutral5
val LightOnSurfaceVariant = Neutral50
val LightError = StateDanger
val LightErrorContainer = Color(0xFFFFCDD2)
val LightOutline = Neutral20

val DarkPrimary = Color(0xFFA5D6A7)
val DarkOnPrimary = Green60
val DarkPrimaryContainer = Green60
val DarkSecondary = Color(0xFF90CAF9)
val DarkOnSecondary = Color(0xFF001D32)
val DarkTertiary = Color(0xFFFFB74D)
val DarkBackground = DarkBgDeep
val DarkOnBackground = DarkTextPri
val DarkSurface = DarkBgCard
val DarkOnSurface = DarkTextPri
val DarkSurfaceVariant = Color(0xFF1C2129)
val DarkOnSurfaceVariant = DarkTextSec
val DarkError = Color(0xFFFFB4AB)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOutline = DarkBorder
