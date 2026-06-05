package com.corunling.noteeverything.ui.theme

import androidx.compose.ui.graphics.Color

data class CategoryColor(
    val primary: Color,
    val background: Color,
    val onBackground: Color
)

object CategoryColors {
    val map = mapOf(
        "游戏" to CategoryColor(
            primary = Color(0xFFFF9800),
            background = Color(0xFFFFF3E0),
            onBackground = Color(0xFFE65100)
        ),
        "工具" to CategoryColor(
            primary = Color(0xFF2196F3),
            background = Color(0xFFE3F2FD),
            onBackground = Color(0xFF1565C0)
        ),
        "学习" to CategoryColor(
            primary = Color(0xFF4CAF50),
            background = Color(0xFFE8F5E9),
            onBackground = Color(0xFF2E7D32)
        ),
        "其他" to CategoryColor(
            primary = Color(0xFF9C27B0),
            background = Color(0xFFF3E5F5),
            onBackground = Color(0xFF7B1FA2)
        ),
        "随笔" to CategoryColor(
            primary = Color(0xFFE91E63),
            background = Color(0xFFFCE4EC),
            onBackground = Color(0xFFC2185B)
        )
    )

    fun forCategory(category: String): CategoryColor =
        map[category] ?: CategoryColor(
            primary = Color(0xFF757575),
            background = Color(0xFFF5F5F5),
            onBackground = Color(0xFF424242)
        )

    /** 根据分类色生成渐变起止色（用于头像背景） */
    fun gradientFor(category: String): Pair<Color, Color> {
        val c = forCategory(category).primary
        return c to Color(
            red = c.red * 0.8f,
            green = c.green * 0.8f,
            blue = c.blue * 0.8f
        )
    }
}
