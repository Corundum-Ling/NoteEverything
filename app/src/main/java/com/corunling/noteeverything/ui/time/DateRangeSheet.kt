// ============================================================
// DateRangeSheet.kt — 自定义日期范围选择 BottomSheet
// ============================================================
// 底部弹出窗口，支持快捷选择（今天/昨天/近7天/近30天/本月）
// 和自定义起止日期。
// ============================================================

package com.corunling.noteeverything.ui.time

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.launch

/**
 * 快捷选项
 */
private data class QuickOption(
    val label: String,
    val range: Pair<String, String> // startDate, endDate
)

/**
 * 日期范围选择 BottomSheet
 *
 * @param currentStart 当前起始日期 "YYYY-MM-DD"
 * @param currentEnd 当前结束日期 "YYYY-MM-DD"
 * @param onConfirm 确认回调 (startDate, endDate)
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSheet(
    currentStart: String,
    currentEnd: String,
    onConfirm: (startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 自定义起止日期
    var customStart by remember { mutableStateOf(currentStart) }
    var customEnd by remember { mutableStateOf(currentEnd) }

    // 是否正在显示 DatePicker
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // 快捷选项
    val today = DateTimeUtils.today()
    val quickOptions = remember {
        listOf(
            QuickOption("今天", today to today),
            QuickOption("昨天", DateTimeUtils.yesterday() to DateTimeUtils.yesterday()),
            QuickOption("近7天", DateTimeUtils.daysAgo(6) to today),
            QuickOption("近30天", DateTimeUtils.daysAgo(29) to today),
            QuickOption("本月", DateTimeUtils.startOfMonth() to today)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = "选择时间范围",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // ── 快捷选择 ──
            Text(
                text = "快捷选择",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickOptions.forEach { option ->
                    val isActive = option.range.first == customStart && option.range.second == customEnd
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            customStart = option.range.first
                            customEnd = option.range.second
                        },
                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            HorizontalDivider()

            // ── 自定义日期 ──
            Text(
                text = "自定义",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 开始日期
                OutlinedCard(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = formatDisplayDate(customStart),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 结束日期
                OutlinedCard(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = formatDisplayDate(customEnd),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 确定按钮 ──
            Button(
                onClick = {
                    onConfirm(customStart, customEnd)
                    scope.launch { sheetState.hide() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定", fontWeight = FontWeight.Medium)
            }
        }
    }

    // DatePicker dialogs
    if (showStartPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseDateToMillis(customStart)
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        customStart = DateTimeUtils.millisToDateStr(it)
                    }
                    showStartPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseDateToMillis(customEnd)
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        customEnd = DateTimeUtils.millisToDateStr(it)
                    }
                    showEndPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** "YYYY-MM-DD" → 显示用 "YYYY/M/D" */
private fun formatDisplayDate(dateStr: String): String {
    return dateStr.replace("-", "/")
        .replace("/0", "/")
        .replace("0", "/") // 只处理一次，用 regex 更好
        .let {
            // 简单处理：去掉 leading zero 但保留 YYYY/MM/DD 格式
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                "${parts[0]}/${parts[1].toInt()}/${parts[2].toInt()}"
            } else dateStr
        }
}

/** "YYYY-MM-DD" → millis */
private fun parseDateToMillis(dateStr: String): Long? {
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val cal = java.util.Calendar.getInstance()
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            cal.timeInMillis
        } else null
    } catch (_: Exception) { null }
}
