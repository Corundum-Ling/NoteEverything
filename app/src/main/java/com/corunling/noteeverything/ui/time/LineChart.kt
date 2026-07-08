package com.corunling.noteeverything.ui.time

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
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

data class LineChartPoint(
    val label: String,
    val value: Float
)

/**
 * Canvas 自绘折线+面积图。
 * 入场动画：所有点同步从横轴底部升起。
 */
@Composable
fun LineChart(
    data: List<LineChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF1A73E8),
    fillColor: Color = Color(0xFF1A73E8).copy(alpha = 0.12f),
    animDurationMs: Int = 800,
    hasAnimated: Boolean = false,
    animVersion: Int = 0,
    onAnimated: () -> Unit = {}
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    val animProgress = remember { Animatable(if (hasAnimated) 1f else 0f) }
    LaunchedEffect(data, animVersion) {
        if (!hasAnimated && data.isNotEmpty()) {
            delay(80L)
            animProgress.snapTo(0f)
            animProgress.animateTo(1f, tween(animDurationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            onAnimated()
        } else {
            animProgress.snapTo(1f)
        }
    }

    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Normal)
    val yLabelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)

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
        val baselineY = paddingTop + chartHeight  // X 轴底边

        // ── 所有点的目标坐标 ──
        val targetPoints = data.mapIndexed { index, point ->
            val x = paddingLeft + stepX * index
            val y = paddingTop + chartHeight - ((point.value - minValue) / range * chartHeight)
            Offset(x, y)
        }

        // ── 竖升起动画：所有点的 Y 从 baseline 向 target 插值 ──
        val animatedPoints = targetPoints.map { pt ->
            val ay = baselineY + (pt.y - baselineY) * animProgress.value
            Offset(pt.x, ay)
        }

        // ── 面积填充（跟随动画点） ──
        val fillPath = Path().apply {
            moveTo(animatedPoints.first().x, size.height - paddingBottom)
            animatedPoints.forEach { lineTo(it.x, it.y) }
            lineTo(animatedPoints.last().x, size.height - paddingBottom)
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
            moveTo(animatedPoints.first().x, animatedPoints.first().y)
            for (i in 1 until animatedPoints.size) {
                val prev = animatedPoints[i - 1]
                val curr = animatedPoints[i]
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
        animatedPoints.forEach { point ->
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
                val labelLayout = textMeasurer.measure(text = point.label, style = labelStyle)
                val x = paddingLeft + stepX * index
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(x - labelLayout.size.width / 2f, size.height - paddingBottom + 6.dp.toPx())
                )
            }
        }

        // ── Y 轴标签（3档） ──
        val ySteps = 3
        for (i in 0..ySteps) {
            val value = minValue + (range * i / ySteps)
            val y = paddingTop + chartHeight - (value / range * chartHeight)
            val labelLayout = textMeasurer.measure(text = formatDurationLabel(value), style = yLabelStyle)
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(paddingLeft - labelLayout.size.width - 4.dp.toPx(), y - labelLayout.size.height / 2f)
            )
            if (i > 0) {
                drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(paddingLeft, y), Offset(size.width - paddingRight, y), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

private fun formatDurationLabel(minutes: Float): String {
    val totalMin = minutes.toInt()
    return if (totalMin >= 60) "${totalMin / 60}h" else "${totalMin}min"
}
