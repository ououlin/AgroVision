package com.agrovision.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary, onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    tertiary = LightTertiary, onTertiary = LightOnTertiary,
    background = LightBackground, onBackground = LightOnBackground,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError, errorContainer = LightErrorContainer,
    outline = LightOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = DarkSecondary, onSecondary = DarkOnSecondary,
    tertiary = DarkTertiary, onTertiary = Color(0xFF3E2000),
    background = DarkBackground, onBackground = DarkOnBackground,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError, errorContainer = DarkErrorContainer,
    outline = DarkOutline
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
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontSize),
        shapes = AppShapes,
        content = content
    )
}
