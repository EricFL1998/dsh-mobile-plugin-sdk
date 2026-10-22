package com.deepseek.dsh.plugin.nativesdk.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 插件可见的主题只读快照。
 *
 * 深色模式与系统字体缩放由 App 统一映射进快照：插件不写 `isSystemInDarkTheme()`
 * 分支，读到的就是当前正确取值。快照变化触发宿主容器重组，插件 UI 随之更新。
 *
 * 插件**禁止**硬编码颜色/字号——这是商店审核的一条红线，也是两个前端（Web 与
 * 原生）视觉一致的前提。
 */
data class PluginTheme(
    // 颜色
    val colorPrimary: Color,
    val colorOnPrimary: Color,
    val colorSurface: Color,
    val colorOnSurface: Color,
    val colorSurfaceVariant: Color,
    val colorOnSurfaceVariant: Color,
    val colorError: Color,
    val colorOnError: Color,
    val colorOutline: Color,
    // 浮层（与 App 的 DshMenuCard 同一套材质：表面色 + 发丝边 + 大圆角）
    val colorPopupSurface: Color = colorSurface,
    val colorPopupBorder: Color = colorOutline.copy(alpha = 0.35f),
    val cornerPopup: Dp = 16.dp,
    // 文字
    val textTitle: TextStyle,
    val textBody: TextStyle,
    val textCaption: TextStyle,
    val textPopupTitle: TextStyle = textCaption,
    val textPopupBody: TextStyle = textBody,
    // 形状与间距
    val cornerCard: Dp = 16.dp,
    val cornerChip: Dp = 8.dp,
    val spacingUnit: Dp = 8.dp,
    val isDark: Boolean,
    // 设置页/面板（与 App 的 DshPanel 同一套 token：隔层底色、发丝边、
    // 三级文字色、44dp 行高）。插件设置页用 design/DshPanel.kt 里的组件，
    // 这里的分组标题/分隔线/动作色随 App 主题变化。
    val colorPanel: Color = colorSurface,
    val colorPanelBorder: Color = colorOutline.copy(alpha = 0.25f),
    val colorPanelElevated: Color = colorSurfaceVariant,
    val colorLabelPrimary: Color = colorOnSurface,
    val colorLabelSecondary: Color = colorOnSurfaceVariant,
    val colorLabelTertiary: Color = colorOutline,
    val colorLink: Color = colorPrimary,
    val panelCorner: Dp = cornerCard,
    val rowMinHeight: Dp = 44.dp,
    // 控件（与 App DshDialogButton / DshSwitch / 输入框同一套 token）
    val colorButtonPrimary: Color = colorPrimary,
    val colorButtonPrimaryHover: Color = colorPrimary,
    val colorButtonTextOnPrimary: Color = colorOnPrimary,
    val colorButtonSecondaryBorder: Color = colorOutline.copy(alpha = 0.25f),
    val colorButtonSecondaryText: Color = colorOnSurface,
    val colorButtonDanger: Color = colorError,
    val colorSwitchTrackOff: Color = colorOutline.copy(alpha = 0.3f),
    val colorSwitchThumb: Color = colorOnPrimary,
    val colorInputBackground: Color = colorSurfaceVariant,
    val colorInputBorder: Color = colorOutline.copy(alpha = 0.25f),
    val colorChipBackground: Color = colorSurfaceVariant,
    val colorChipText: Color = colorOnSurfaceVariant,
) {
    /** 卡片圆角形状，直接用。 */
    val shapeCard get() = RoundedCornerShape(cornerCard)

    /** 胶囊/标签圆角形状。 */
    val shapeChip get() = RoundedCornerShape(cornerChip)

    /** 浮层圆角形状（与 App 菜单一致）。 */
    val shapePopup get() = RoundedCornerShape(cornerPopup)
}
