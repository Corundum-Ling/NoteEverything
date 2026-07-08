// ============================================================
// LineChart.kt — Canvas 折线+面积图组件
// ============================================================
// 纯 Compose Canvas 实现，无第三方依赖。
// 支持：贝塞尔平滑曲线、渐变面积填充、轴标签、入场动画。
//
// 参考：
// - developer.android.com/develop/ui/compose/graphics/draw
// - github.com/aylar23/ChartingLib
// - github.com/giorgospat/compose-charts
// ============================================================

package com.corunling.noteeverything.ui.time

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 折线图数据点
 * @param label X 轴标签（如 "7/1"）
 * @param value Y 轴值（分钟）
 */
data class LineChartPoint(
    val label: String,
    val value: Float
)

/**
 * Canvas 自绘折线+面积图
 *
 * @param data 数据点列表（按 label 顺序）
 * @param modifier Modifier
 * @param lineColor 折线颜色
 * @param fillColor 面积填充颜色（半透明）
 * @param animDurationMs 入场动画时长
 */
@Composable
fun LineChart(
    data: List<LineChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF1A73E8),
    fillColor: Color = Color(0xFF1A73E8).copy(alpha = 0.12f),
    animDurationMs: Int = 800
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    // 入场动画
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(animDurationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "lineChartAnim"
    )

    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = Color.Gray,
        fontWeight = FontWeight.Normal
    )

    val yLabelStyle = TextStyle(
        fontSize = 10.sp,
        color = Color.Gray
    )

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (data.size < 2) return@Canvas

        val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
        val minValue = 0f
        val range = (maxValue - minValue).coerceAtLeast(1f)

        val paddingLeft = 40.dp.toPx()
        val paddingRight = 12.dp.toPx()
        val paddingTop = 12.dp.toPx()
        val paddingBottom = 28.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        val stepX = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth

        // ── 计算每个点的屏幕坐标 ──
        val points = data.mapIndexed { index, point ->
            val x = paddingLeft + stepX * index
            val y = paddingTop + chartHeight - ((point.value - minValue) / range * chartHeight)
            Offset(x, y)
        }

        // 动画：只显示部分点
        val visibleCount = (points.size * animProgress).toInt().coerceAtLeast(2)
        val visiblePoints = points.take(visibleCount)

        // ── 面积填充 ──
        val fillPath = Path().apply {
            moveTo(visiblePoints.first().x, size.height - paddingBottom)
            visiblePoints.forEach { lineTo(it.x, it.y) }
            lineTo(visiblePoints.last().x, size.height - paddingBottom)
            close()
        }
        drawPath(
            fillPath,
            Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = paddingTop,
                endY = size.height - paddingBottom
            )
        )

        // ── 平滑折线（贝塞尔） ──
        val linePath = Path().apply {
            moveTo(visiblePoints.first().x, visiblePoints.first().y)
            for (i in 1 until visiblePoints.size) {
                val prev = visiblePoints[i - 1]
                val curr = visiblePoints[i]
                val cpX = (prev.x + curr.x) / 2f
                cubicTo(cpX, prev.y, cpX, curr.y, curr.x, curr.y)
            }
        }
        drawPath(
            linePath,
            lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // ── 数据点 ──
        visiblePoints.forEach { point ->
            drawCircle(Color.White, radius = 4.dp.toPx(), center = point)
            drawCircle(lineColor, radius = 2.5.dp.toPx(), center = point)
        }

        // ── X 轴标签 ──
        val labelInterval = when {
            data.size <= 7 -> 1
            data.size <= 14 -> 2
            data.size <= 31 -> 5
            else -> 7
        }
        data.forEachIndexed { index, point ->
            if (index % labelInterval == 0 || index == data.size - 1) {
                val labelLayout = textMeasurer.measure(
                    text = point.label,
                    style = labelStyle
                )
                val x = paddingLeft + stepX * index
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(
                        x - labelLayout.size.width / 2f,
                        size.height - paddingBottom + 6.dp.toPx()
                    )
                )
            }
        }

        // ── Y 轴标签（显示3档） ──
        val ySteps = 3
        for (i in 0..ySteps) {
            val value = minValue + (range * i / ySteps)
            val y = paddingTop + chartHeight - (value / range * chartHeight)
            val labelLayout = textMeasurer.measure(
                text = formatDurationLabel(value),
                style = yLabelStyle
            )
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    paddingLeft - labelLayout.size.width - 4.dp.toPx(),
                    y - labelLayout.size.height / 2f
                )
            )

            // 水平参考线
            if (i > 0) {
                drawLine(
                    Color.LightGray.copy(alpha = 0.3f),
                    start = Offset(paddingLeft, y),
                    end = Offset(size.width - paddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

/** 把分钟数格式化为简洁的时长标签 */
private fun formatDurationLabel(minutes: Float): String {
    val totalMin = minutes.toInt()
    return when {
        totalMin >= 60 -> "${totalMin / 60}h"
        else -> "${totalMin}min"
    }
}
