package com.agrovision.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 浅色主题。
 *
 * 除基础角色外，补齐了 Material3 1.2+ 的 surface 容器色阶：
 * Card / NavigationBar / ModalBottomSheet / AlertDialog 的默认底色全部取自
 * surfaceContainerLow ~ surfaceContainerHighest，缺省时会退回 M3 基线色板（紫灰色偏），
 * 与品牌绿系冲突，表现为浅色下发灰、深色下发紫。
 */
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,

    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,

    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,

    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,

    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,

    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,

    error = LightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim
)

/**
 * 深色主题 —— 与浅色使用同一套色阶命名，保证同层级组件的相对高低关系一致。
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFE8F5E9),
    inversePrimary = DarkInversePrimary,

    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,

    tertiary = DarkTertiary,
    onTertiary = Color(0xFF3E2000),
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,

    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,

    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,

    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,

    error = DarkError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim
)

/**
 * 主题模式枚举
 */
enum class ThemeMode(val key: String, val label: String) {
    Light("light", "浅色"),
    Dark("dark", "深色"),
    System("system", "跟随系统");

    companion object {
        fun fromKey(key: String): ThemeMode = entries.find { it.key == key } ?: System
    }
}

/**
 * 字体大小级别
 */
enum class FontSize(val key: String, val label: String, val scale: Float) {
    Small("small", "小", 0.88f),
    Normal("normal", "标准", 1.0f),
    Large("large", "大", 1.18f);

    companion object {
        fun fromKey(key: String): FontSize = entries.find { it.key == key } ?: Normal
    }
}

/**
 * AgroVision 主题
 *
 * @param themeMode 主题模式：light/dark/system
 * @param dynamicColor 是否启用 Material You 动态取色（Android 12+）
 * @param fontSize 字体大小级别
 */
@Composable
fun AgroVisionTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    fontSize: FontSize = FontSize.Normal,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }

    // 动态取色是本项目的显式选项：仅在用户开启时才使用系统壁纸色，
    // 且保持 surface 容器色阶来自系统，避免与自定义色板混搭出错。
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // API 35 起应用强制 edge-to-edge，statusBarColor 已废弃且不再生效；
            // 低版本仍保留与背景同色，避免状态栏出现割裂色块。
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                run { window.statusBarColor = colorScheme.background.toArgb() }
            }
            // 手势导航下系统默认会给导航栏加一层对比度遮罩，与自绘底栏叠加后发灰。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            // 深浅模式切换时同步系统栏图标明暗，避免白底白字
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontSize),
        shapes = AppShapes,
        content = content
    )
}
