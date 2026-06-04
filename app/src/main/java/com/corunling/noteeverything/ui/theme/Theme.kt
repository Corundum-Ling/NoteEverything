// ============================================================
// Theme.kt — 主题配置
// ============================================================
// Material3 使用 dynamicColor（Android 12+ 的 Material You），
// 会自动从用户壁纸中提取颜色方案。
// 低版本 Android 退回到自定义的 LightColorScheme。

package com.corunling.noteeverything.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 自定义浅色主题（在不支持 dynamic color 的设备上使用）
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFF5F6368),
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFF8F9FA),
)

@Composable
fun NoteEverythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+：使用系统壁纸颜色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
