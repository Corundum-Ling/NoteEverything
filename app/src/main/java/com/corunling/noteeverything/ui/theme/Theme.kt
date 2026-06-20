// ============================================================
// Theme.kt — NoteEverything 主题定义（固定配色方案）
// ============================================================
// 使用两套预制配色（浅色/深色），不依赖系统壁纸动态取色。
// 通过 NoteEverythingTheme(darkTheme) 切换。
//
// 分类色系（CategoryColors）定义在 CategoryColors.kt 中，
// 独立于主色体系，用于软件卡片、标签等辅助元素。

package com.corunling.noteeverything.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════
// 浅色配色方案（默认）
// ═══════════════════════════════════════════════════════════
private val FixedLightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF5F6368),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAED),
    onSecondaryContainer = Color(0xFF1F1F1F),
    tertiary = Color(0xFFE91E63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE4EC),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF5F6368),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1F1F1F),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    outline = Color(0xFFBDBDBD)
)

// ═══════════════════════════════════════════════════════════
// 深色配色方案
// ═══════════════════════════════════════════════════════════
private val FixedDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A9F),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFF9AA0A6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3C4043),
    onSecondaryContainer = Color(0xFFE8EAED),
    tertiary = Color(0xFFFF8A9B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF60142B),
    surface = Color(0xFF1F1F1F),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF9AA0A6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8EAED),
    error = Color(0xFFEF5350),
    onError = Color.White,
    errorContainer = Color(0xFF8C1D18),
    outline = Color(0xFF5F6368)
)

/**
 * NoteEverything 主题入口。
 *
 * @param darkTheme true = 深色模式，false = 浅色模式（默认）
 * @param content 主题作用域内的 Composable 内容
 */
@Composable
fun NoteEverythingTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FixedDarkColorScheme else FixedLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
