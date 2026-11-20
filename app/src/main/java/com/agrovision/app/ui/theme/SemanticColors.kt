package com.agrovision.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 语义色解析：根据系统深色模式返回适配的颜色，避免硬编码浅色在深色背景下对比度不足。
 *
 * 设计原则：
 * - 浅色模式沿用 [Color.kt] 中既有的品牌/状态色（Green50/Orange50/Red50 等）
 * - 深色模式使用提亮后的同色系，保证 WCAG AA 对比度
 */

/** 危险等级色：高=红、中=橙、其它=绿 */
@Composable
fun dangerColor(level: String): Color = when (level) {
    "高" -> if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Red50
    "中" -> if (isSystemInDarkTheme()) Color(0xFFFFCC80) else Orange50
    else -> if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Success50
}

/** 置信度色：>=0.7 绿、>=0.5 橙、否则红 */
@Composable
fun confidenceColor(confidence: Float): Color = when {
    confidence >= 0.7f -> if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Success50
    confidence >= 0.5f -> if (isSystemInDarkTheme()) Color(0xFFFFCC80) else Orange50
    else -> if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Red50
}

/** 品牌绿（用于强调文字、图标、指示灯等） */
@Composable
fun brandGreen(): Color = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Green50

/** 品牌蓝（用于信息标签等） */
@Composable
fun brandBlue(): Color = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Blue50

/** 警示橙（用于药剂列表圆点等次要装饰） */
@Composable
fun accentOrange(): Color = if (isSystemInDarkTheme()) Color(0xFFFFCC80) else Orange50

/** 危险红（用于注销、错误状态、破坏性操作按钮） */
@Composable
fun brandRed(): Color = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Red50
