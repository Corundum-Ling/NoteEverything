package com.corunling.noteeverything.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun NoteEverythingTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FixedLightColorScheme,
        content = content
    )
}
