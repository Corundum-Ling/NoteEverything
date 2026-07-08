// ============================================================
// PeriodPickerSheet.kt — 层级日期选择器 BottomSheet
// ============================================================
// 日/周：可点击标题弹出月选择器 → 月：可点击标题弹出年选择器
// 每层都是独立 ModalBottomSheet，叠在上一层之上。
// 参考 参考_月选择.png、参考_日周月.png、参考_日历选择.png
// ============================================================

package com.corunling.noteeverything.ui.time

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corunling.noteeverything.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.*

enum class PickerMode { DAY, WEEK, MONTH }

// ═══════════════════════════════════════════════
// 顶层选择器
// ═══════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodPickerSheet(
    mode: PickerMode,
    currentStart: String,
    currentEnd: String,
    onSelect: (startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    try { cal.time = sdf.parse(currentStart) ?: Date() } catch (_: Exception) {}

    var selYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }

    // 子弹窗状态
    var showMonthOverlay by remember { mutableStateOf(false) }
    var showYearOverlay by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // ═══ 标题行：← 可点击标题 → ═══
            val titleText = when (mode) {
                PickerMode.DAY, PickerMode.WEEK -> "${selYear}年${selMonth + 1}月"
                PickerMode.MONTH -> "${selYear}年"
            }
            val onTitleClick = when (mode) {
                PickerMode.DAY, PickerMode.WEEK -> { { showMonthOverlay = true } }
                PickerMode.MONTH -> { { showYearOverlay = true } }
            }

            when (mode) {
                PickerMode.DAY -> NavHeader(
                    title = titleText,
                    onTitleClick = onTitleClick,
                    year = selYear, month = selMonth,
                    onYearMonthChange = { y, m -> selYear = y; selMonth = m }
                )
                PickerMode.WEEK -> NavHeader(
                    title = titleText,
                    onTitleClick = onTitleClick,
                    year = selYear, month = selMonth,
                    onYearMonthChange = { y, m -> selYear = y; selMonth = m }
                )
                PickerMode.MONTH -> YearNavHeader(
                    title = titleText,
                    onTitleClick = onTitleClick,
                    year = selYear,
                    onYearChange = { y -> selYear = y; selMonth = -1 }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ═══ 内容 ═══
            when (mode) {
                PickerMode.DAY -> DayCalendar(
                    year = selYear, month = selMonth,
                    selectedDate = currentStart,
                    onSelect = { date -> onSelect(date, date); onDismiss() }
                )
                PickerMode.WEEK -> WeekGrid(
                    year = selYear, month = selMonth,
                    currentStart = currentStart, currentEnd = currentEnd,
                    onSelect = { s, e -> onSelect(s, e); onDismiss() }
                )
                PickerMode.MONTH -> {
                    // 月模式标题已有箭头控制，内容直接用选中年的月网格
                    MonthContent(
                        year = selYear, selectedMonth = selMonth,
                        onSelect = { y, m ->
                            selYear = y; selMonth = m
                            val c = Calendar.getInstance().apply { set(y, m, 1) }
                            val start = sdf.format(c.time)
                            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
                            val end = sdf.format(c.time)
                            onSelect(start, end); onDismiss()
                        }
                    )
                }
            }
        }
    }

    // ═══ 月选择器叠层（独立的 BottomSheet） ═══
    if (showMonthOverlay) {
        MonthOverlaySheet(
            year = selYear, selectedMonth = selMonth,
            onSelect = { y, m ->
                selYear = y; selMonth = m
                showMonthOverlay = false
            },
            onDismiss = { showMonthOverlay = false },
            onYearClick = { showYearOverlay = true }
        )
    }

    // ═══ 年选择器叠层（独立的 BottomSheet） ═══
    if (showYearOverlay) {
        YearOverlaySheet(
            selectedYear = selYear,
            onSelect = { y ->
                selYear = y
                showYearOverlay = false
            },
            onDismiss = { showYearOverlay = false }
        )
    }
}

// ═══════════════════════════════════════════════
// 导航标题行：← 可点击标题 →
// ═══════════════════════════════════════════════

@Composable
private fun NavHeader(
    title: String,
    onTitleClick: () -> Unit,
    year: Int,
    month: Int,
    onYearMonthChange: (year: Int, month: Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            var y = year; var m = month
            if (m == 0) { y--; m = 11 } else m--
            onYearMonthChange(y, m)
        }) { Icon(Icons.Default.KeyboardArrowLeft, "上个月") }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clickable { onTitleClick() }
        )

        IconButton(onClick = {
            var y = year; var m = month
            if (m == 11) { y++; m = 0 } else m++
            onYearMonthChange(y, m)
        }) { Icon(Icons.Default.KeyboardArrowRight, "下个月") }
    }
}

/** 年导航标题行：← 2026年 → （切年不切月） */
@Composable
private fun YearNavHeader(
    title: String,
    onTitleClick: () -> Unit,
    year: Int,
    onYearChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onYearChange(year - 1) }) {
            Icon(Icons.Default.KeyboardArrowLeft, "上一年")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clickable { onTitleClick() }
        )
        IconButton(onClick = { onYearChange(year + 1) }) {
            Icon(Icons.Default.KeyboardArrowRight, "下一年")
        }
    }
}

// ═══════════════════════════════════════════════
// 月选择器（叠层 BottomSheet）
// ═══════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthOverlaySheet(
    year: Int,
    selectedMonth: Int,
    onSelect: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit,
    onYearClick: () -> Unit
) {
    var browseYear by remember(year) { mutableIntStateOf(year) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题：← 2026年 → 可点跳年
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { browseYear-- }) {
                    Icon(Icons.Default.KeyboardArrowLeft, "上一年")
                }
                Text(
                    text = "${browseYear}年",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable { onYearClick() }
                )
                IconButton(onClick = { browseYear++ }) {
                    Icon(Icons.Default.KeyboardArrowRight, "下一年")
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4×3 月网格
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(min = 200.dp, max = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((0..11).toList(), key = { it }) { m ->
                    val isSelected = browseYear == year && m == selectedMonth
                    val isCurrent = browseYear == currentYear && m == currentMonth
                    Surface(
                        modifier = Modifier
                            .aspectRatio(1.2f)
                            .clickable { onSelect(browseYear, m) },
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            isCurrent -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${m + 1}月",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// 年选择器（叠层 BottomSheet）
// ═══════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearOverlaySheet(
    selectedYear: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var decadeStart by remember {
        mutableIntStateOf((selectedYear / 10) * 10)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题行：← 2020—2029 →
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { decadeStart -= 10 }) {
                    Icon(Icons.Default.KeyboardArrowLeft, "前十年")
                }
                Text(
                    text = "${decadeStart}—${decadeStart + 9}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                IconButton(onClick = { decadeStart += 10 }) {
                    Icon(Icons.Default.KeyboardArrowRight, "后十年")
                }
            }

            Spacer(Modifier.height(12.dp))

            val years = (decadeStart..decadeStart + 9).toList()

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(min = 200.dp, max = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(years, key = { it }) { year ->
                    val isSelected = year == selectedYear
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 60.dp)
                            .clickable { onSelect(year) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${year}年",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// 日选择（日历）
// ═══════════════════════════════════════════════

@Composable
private fun DayCalendar(
    year: Int,
    month: Int,
    selectedDate: String,
    onSelect: (String) -> Unit
) {
    val weekDays = listOf("一", "二", "三", "四", "五", "六", "日")
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        val todayStr = DateTimeUtils.today()
        val days = remember(year, month) { generateCalendarDays(year, month) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.heightIn(min = 200.dp, max = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(days, key = { it.key }) { day ->
                if (day.isEmpty) Box(modifier = Modifier.aspectRatio(1f))
                else {
                    val isSelected = day.dateStr == selectedDate
                    val isToday = day.dateStr == todayStr
                    Surface(
                        modifier = Modifier.aspectRatio(1f).clickable { onSelect(day.dateStr!!) },
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isSelected || isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${day.dayNum}", style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

private data class CalendarDay(val key: String, val isEmpty: Boolean = false, val dayNum: Int = 0, val dateStr: String? = null)

private fun generateCalendarDays(year: Int, month: Int): List<CalendarDay> {
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val days = mutableListOf<CalendarDay>()
    cal.set(year, month, 1)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val emptyStart = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
    for (i in 0 until emptyStart) days.add(CalendarDay("empty-$i", isEmpty = true))
    for (d in 1..daysInMonth) {
        cal.set(year, month, d)
        days.add(CalendarDay("day-$d", dateStr = sdf.format(cal.time), dayNum = d))
    }
    return days
}

// ═══════════════════════════════════════════════
// 周选择（横版按钮排布）
// ═══════════════════════════════════════════════

@Composable
private fun WeekGrid(
    year: Int,
    month: Int,
    currentStart: String,
    currentEnd: String,
    onSelect: (String, String) -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = Calendar.getInstance()
    val weeks = remember(year, month) { generateWeeks(year, month) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.heightIn(min = 200.dp, max = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(weeks, key = { it.label }) { week ->
            val isSel = week.start == currentStart
            val isThisWeek = week.start <= sdf.format(today.time) && week.end >= sdf.format(today.time)
            Surface(
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .clickable { onSelect(week.start, week.end) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isThisWeek) "本周" else week.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${fmtShortDate(week.start)}—${fmtShortDate(week.end)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

// ═══════════════════════════════════════════════
// 月选择（网格，顶层用，不含导航行）
// ═══════════════════════════════════════════════

@Composable
private fun MonthContent(
    year: Int,
    selectedMonth: Int,
    onSelect: (year: Int, month: Int) -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.heightIn(min = 200.dp, max = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items((0..11).toList(), key = { it }) { m ->
            val isSel = m == selectedMonth && selectedMonth >= 0
            val isCur = m == currentMonth && year == currentYear
            Surface(
                modifier = Modifier.aspectRatio(1.2f).clickable { onSelect(year, m) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${m + 1}月",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// 辅助
// ═══════════════════════════════════════════════

private data class WeekInfo(val label: String, val start: String, val end: String)

private fun generateWeeks(year: Int, month: Int): List<WeekInfo> {
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val weeks = mutableListOf<WeekInfo>()
    cal.set(year, month, 1)
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_MONTH, if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow)
    for (i in 0..5) {
        val start = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val end = sdf.format(cal.time)
        if (cal.get(Calendar.MONTH) > month && i == 0) { cal.add(Calendar.DAY_OF_MONTH, 1); continue }
        if (cal.get(Calendar.MONTH) > month && i > 0) break
        weeks.add(WeekInfo("第${cal.get(Calendar.WEEK_OF_YEAR)}周", start, end))
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return weeks
}

private fun fmtShortDate(s: String): String = try {
    val p = s.split("-"); "${p[1].toInt()}/${p[2].toInt()}"
} catch (_: Exception) { s }
