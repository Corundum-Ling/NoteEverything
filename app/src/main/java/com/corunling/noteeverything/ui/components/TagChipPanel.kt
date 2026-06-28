// ============================================================
// TagChipPanel.kt — 标签管理面板（可复用组件）
// ============================================================
// 用于编辑器和多选模式下的标签添加/移除。
// 显示已有标签的 Chip 行，下方有 "+" 按钮行。
// 点击 "+" → 按钮图标动画切换，输入框从按钮处向右展开。
// "+"/输入行高度固定，不改变卡片大小。

package com.corunling.noteeverything.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 标签管理面板组件。
 *
 * @param tags 当前已添加的标签列表
 * @param onAddTag 添加标签回调
 * @param onRemoveTag 移除标签回调
 * @param modifier Modifier
 */
@Composable
fun TagChipPanel(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdding by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }

    fun confirmAdd() {
        if (newTagText.isNotBlank()) {
            onAddTag(newTagText.trim())
            newTagText = ""
        }
        isAdding = false
    }

    // 动画值
    val plusRotation by animateFloatAsState(
        targetValue = if (isAdding) 90f else 0f, animationSpec = tween(200), label = "plusRotation"
    )
    val plusAlpha by animateFloatAsState(
        targetValue = if (isAdding) 0f else 1f, animationSpec = tween(150), label = "plusAlpha"
    )
    val checkRotation by animateFloatAsState(
        targetValue = if (isAdding) 0f else -90f, animationSpec = tween(200), label = "checkRotation"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (isAdding) 1f else 0f, animationSpec = tween(150), label = "checkAlpha"
    )

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        // 标题
        Text(
            text = "标签管理",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 已有标签 Chip 行
        if (tags.isEmpty()) {
            Text(
                text = "暂无标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { },
                        label = { Text(tag, fontSize = 13.sp) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onRemoveTag(tag) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "移除 $tag",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // 添加行（固定高度）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            FilledTonalIconButton(
                onClick = { if (isAdding) confirmAdd() else isAdding = true },
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(Modifier.size(18.dp)) {
                    Icon(
                        Icons.Default.Add, contentDescription = "添加标签",
                        modifier = Modifier.size(18.dp).rotate(plusRotation).alpha(plusAlpha)
                    )
                    Icon(
                        Icons.Default.Check, contentDescription = "确认添加",
                        modifier = Modifier.size(18.dp).rotate(checkRotation).alpha(checkAlpha)
                    )
                }
            }

            // 输入框从按钮处向右展开
            AnimatedVisibility(
                visible = isAdding,
                enter = expandHorizontally(animationSpec = tween(200), expandFrom = Alignment.Start) + fadeIn(tween(100)),
                exit = shrinkHorizontally(animationSpec = tween(200), shrinkTowards = Alignment.End) + fadeOut(tween(100))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        placeholder = { Text("输入标签...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirmAdd() })
                    )
                }
            }
        }
    }
}
