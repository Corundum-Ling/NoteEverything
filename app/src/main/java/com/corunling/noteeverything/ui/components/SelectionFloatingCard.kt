// ============================================================
// SelectionFloatingCard.kt — 多选模式底部浮动操作卡片
// ============================================================
// 浮在内容层的圆角卡片，5 个操作按钮均匀排列。
// 无选中时所有按钮灰色不可点击，选中 ≥1 项后激活。
//
// 使用方式（在 MainScreen 中）：
//   SelectionFloatingCard(
//       hasSelection = selectedIds.isNotEmpty(),
//       onPin = { /* 置顶操作 */ },
//       onDelete = { /* 删除操作 */ },
//       ...
//       modifier = Modifier.align(Alignment.BottomCenter)
//   )

package com.corunling.noteeverything.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 多选底部浮动操作卡片。
 *
 * @param hasSelection 当前是否有选中项（控制按钮激活状态）
 * @param onPin 置顶/取消置顶
 * @param onLock 锁定（占位）
 * @param onDelete 批量删除
 * @param onTags 标签管理（占位）
 * @param onExport 批量导出（占位）
 * @param modifier 布局修饰符
 */
@Composable
fun SelectionFloatingCard(
    hasSelection: Boolean,
    showExport: Boolean = true,
    onPin: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
    onTags: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                icon = Icons.Default.PushPin,
                label = "置顶",
                enabled = hasSelection,
                onClick = onPin
            )
            ActionButton(
                icon = Icons.Default.Lock,
                label = "锁定",
                enabled = hasSelection,
                onClick = onLock
            )
            ActionButton(
                icon = Icons.Default.Delete,
                label = "删除",
                enabled = hasSelection,
                tint = if (hasSelection) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                onClick = onDelete
            )
            ActionButton(
                icon = Icons.AutoMirrored.Filled.Label,
                label = "标签",
                enabled = hasSelection,
                onClick = onTags
            )
            if (showExport) {
                ActionButton(
                    icon = Icons.Default.FileDownload,
                    label = "导出",
                    enabled = hasSelection,
                    onClick = onExport
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    // 防连点
    var lastClickTime by remember { mutableLongStateOf(0L) }

    val iconTint by animateColorAsState(
        targetValue = if (enabled) tint
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        animationSpec = tween(200),
        label = "iconTint"
    )
    val textColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onSurface
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        animationSpec = tween(200),
        label = "textColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .let { mod ->
                if (enabled) mod
                else mod // clickable handled by IconButton instead
            }
    ) {
        IconButton(
            onClick = {
                if (!enabled) return@IconButton
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 500L) return@IconButton
                lastClickTime = now
                onClick()
            },
            enabled = enabled,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
