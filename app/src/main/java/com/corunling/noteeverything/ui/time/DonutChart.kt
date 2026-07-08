// ============================================================
// DonutChart.kt — Canvas 环形图组件
// ============================================================
// 纯 Compose Canvas 实现，无第三方依赖。
// 支持：drawArc 分段绘制、中心标签、右侧图例、入场动画。
//
// 参考：
// - developer.android.com/develop/ui/compose/graphics/draw
// - github.com/aylar23/ChartingLib
// - github.com/giorgospat/compose-charts
// ============================================================

package com.corunling.noteeverything.ui.time

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corunling.noteeverything.ui.theme.CategoryColors

/**
 * 环形图数据切片
 */
data class DonutSlice(
    val label: String,
    val value: Float,
    val color: Color
)

/**
 * Canvas 自绘环形图 + 右侧图例
 *
 * @param slices 数据切片列表
 * @param modifier Modifier
 * @param animDurationMs 入场动画时长
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    animDurationMs: Int = 800,
    hasAnimated: Boolean = false,
    onAnimated: () -> Unit = {}
) {
    if (slices.isEmpty()) return

    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val animProgress = remember { Animatable(if (hasAnimated) 1f else 0f) }
    LaunchedEffect(slices) {
        if (!hasAnimated && slices.isNotEmpty()) {
            delay(300L)
            animProgress.snapTo(0f)
            animProgress.animateTo(1f, tween(animDurationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            onAnimated()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── 环形图 Canvas ──
        Canvas(
            modifier = Modifier.size(140.dp)
        ) {
            val strokeWidth = 32.dp.toPx()
            val arcSize = size.minDimension - strokeWidth
            val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcRect = Size(arcSize, arcSize)

            // 顺时针从顶部展开：总角度 0→360
            val totalSweep = 360f * animProgress.value
            var startAngle = -90f
            var accumulated = 0f

            slices.forEach { slice ->
                val sliceFull = (slice.value / total) * 360f
                // 当前切片在 totalSweep 中的占比
                val sweep = if (accumulated + sliceFull <= totalSweep) sliceFull
                else (totalSweep - accumulated).coerceAtLeast(0f)
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRect,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                accumulated += sliceFull
                startAngle += sliceFull
            }

            // 中心标签：总小时数
            val totalHours = total / 60f
            val centerText = if (totalHours >= 1f) {
                String.format("%.1f", totalHours)
            } else {
                "${total.toInt()}"
            }
            val centerUnit = if (totalHours >= 1f) "h" else "min"
            val bigTextSize = with(density) { 24.sp.toPx() }
            val smallTextSize = with(density) { 12.sp.toPx() }

            val textPaint = android.graphics.Paint().apply {
                color = primaryColor.hashCode()
                this.textAlign = android.graphics.Paint.Align.CENTER
                textSize = bigTextSize
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText(
                centerText,
                size.width / 2f,
                size.height / 2f + bigTextSize / 3f,
                textPaint
            )

            val unitPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                this.textAlign = android.graphics.Paint.Align.CENTER
                textSize = smallTextSize
            }
            drawContext.canvas.nativeCanvas.drawText(
                centerUnit,
                size.width / 2f,
                size.height / 2f + bigTextSize + 4.dp.toPx(),
                unitPaint
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // ── 右侧图例 ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEach { slice ->
                val percentage = (slice.value / total * 100).toInt()
                val hours = slice.value / 60f
                val detail = if (hours >= 1f) {
                    String.format("%.1fh", hours)
                } else {
                    "${slice.value.toInt()}min"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 色块
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(10.dp)
                    ) {
                        drawCircle(color = slice.color)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$detail  $percentage%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 根据已有分类色系自动生成 DonutSlice 列表
 */
fun createCategorySlices(
    categoryTotals: Map<String, Long>
): List<DonutSlice> {
    return categoryTotals.entries
        .sortedByDescending { it.value }
        .map { (category, total) ->
            DonutSlice(
                label = category,
                value = total.toFloat(),
                color = CategoryColors.forCategory(category).primary
            )
        }
}
