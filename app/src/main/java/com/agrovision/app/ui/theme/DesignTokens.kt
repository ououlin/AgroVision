package com.agrovision.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation

/**
 * 全局设计令牌（Design Tokens）—— 单一事实来源
 *
 * 「施工纪律」：
 * 1) 禁止在 UI 层硬编码 4.dp / 8.dp / 14.dp 等魔法数字——必须从本文件取令牌。
 * 2) 圆角体系只允许 3 档：Pill(胶囊/按钮) · MD(卡片/输入框) · LG(大卡片/BottomSheet)。
 * 3) 间距体系基于 4dp 步长：8/12/16/20/24/28/32/40/48。
 *
 * 如果需要某个数值不在下面列表中，先自问「真的需要打破 8 的步长吗？」——
 * 90% 的情况答案是否，回到现有令牌即可。
 */

// ============================================================
// 圆角 Radius （3 档体系：XS 标签 / MD 交互 / LG 容器）
// ============================================================
object Radius {
    /** 标签、小 Chip、状态徽标 —— 8dp  */
    const val XS = 8
    /** 按钮、输入框、列表行 —— 14dp（移动端友好中等圆角）  */
    const val MD = 14
    /** 卡片、大 Surface、Dialog、BottomSheet 顶部 —— 20dp  */
    const val LG = 20
}

/** 便捷构造圆角 shape —— 全局唯一入口，便于后续统一替换 */
val ShapeXS: RoundedCornerShape get() = RoundedCornerShape(Radius.XS.dp)
val ShapeMD: RoundedCornerShape get() = RoundedCornerShape(Radius.MD.dp)
val ShapeLG: RoundedCornerShape get() = RoundedCornerShape(Radius.LG.dp)
/** 全圆角胶囊按钮/Chip（高度一半） */
val ShapePill: RoundedCornerShape get() = RoundedCornerShape(percent = 50)

// 覆盖 Material3 Shapes ，保证系统组件也用本项目的圆角尺度
val AppShapes = Shapes(
    extraSmall = ShapeXS,
    small      = ShapeXS,
    medium     = ShapeMD,
    large      = ShapeLG,
    extraLarge = ShapeLG
)

// ============================================================
// 间距 Spacing （基于 4dp，步长统一）
// ============================================================
object Spacing {
    const val XS = 4
    const val SM = 8
    const val MD = 12
    const val LG = 16    // 列表行左、Card 内边距
    const val XL = 20    // 页面左右内容边距
    const val XXL = 24   // 区块之间的主 Spacer
    const val XXL2 = 28  // 大区块 Spacer
    const val XXL3 = 32  // 标题/副标题与控件区距离
    const val HERO = 40  // 顶部安全区
    const val HERO_LG = 48
}

// ============================================================
// 尺寸 Size （触控目标 MD 硬线 48×48）
// ============================================================
object TouchTarget {
    const val MIN = 48    // MD 硬性要求：所有 clickable 组件 ≥48dp
    const val MIN_ALT = 44 // Apple HIG 要求（BottomNav 图标区）
    const val ICON = 20   // 常规行内 icon
    const val ICON_LG = 24 // 主操作 icon
    const val AVATAR = 48 // 列表头像/Chat 头像
}

// ============================================================
// 阴影/高度 Elevation （层级感统一：Flat / Card / Float）
// ============================================================
object Elevation {
    /** 列表行、纯表面色块：0dp（靠分割线区分） */
    const val FLAT = 0
    /** 卡片、对话框、内容面板：1dp（Material3 的 tonal surface 默认） */
    const val CARD = 1
    /** QuickCard 快捷功能卡片：2dp（突出可点击层级） */
    const val TAPPABLE_CARD = 2
    /** FAB、BottomSheet 顶部浮层：4dp */
    const val FLOATING = 4
}

/** 便捷构造 CardElevation —— 需 @Composable（Material3 内部使用了主题色调） */
val FlatCardElevation: CardElevation
    @Composable get() = CardDefaults.cardElevation(defaultElevation = Elevation.FLAT.dp)
val ContentCardElevation: CardElevation
    @Composable get() = CardDefaults.cardElevation(defaultElevation = Elevation.CARD.dp)
val ActionCardElevation: CardElevation
    @Composable get() = CardDefaults.cardElevation(defaultElevation = Elevation.TAPPABLE_CARD.dp)

// ============================================================
// 动效时长
// ============================================================
object Motion {
    const val FAST_MS = 120
    const val NORMAL_MS = 250
    const val SLOW_MS = 400
    const val HERO_MS = 600
}