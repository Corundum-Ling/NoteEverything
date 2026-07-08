// ============================================================
// TimeOverviewScreen.kt — 统计页主界面
// ============================================================
// 全新 v0.6 布局：
//   TabRow → 时间范围条 → 摘要卡片 → 趋势图(卡片) → 环形图(卡片) → 排行列表
// ============================================================

package com.corunling.noteeverything.ui.time

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.App
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.dao.SoftwareDuration
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOverviewScreen(
    repository: NoteEverythingRepository
) {
    val app = LocalContext.current.applicationContext as App
    val viewModel: TimeViewModel = viewModel(
        factory = TimeViewModel.Factory(repository)
    )
    val settings by app.settingsManager.settingsFlow.collectAsState(initial = com.corunling.noteeverything.util.AppSettings())

    // ─── 状态收集 ───
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val rangeLabel by viewModel.rangeLabel.collectAsState()
    val rangeStart by viewModel.rangeStart.collectAsState()
    val rangeEnd by viewModel.rangeEnd.collectAsState()
    val rankingStats by viewModel.rankingStats.collectAsState()
    val dailyTrends by viewModel.dailyTrends.collectAsState()
    val categoryStats by viewModel.categoryStats.collectAsState()
    val totalMinutes by viewModel.totalMinutes.collectAsState()
    val dailyAvg by viewModel.dailyAvg.collectAsState()
    val daysCount by viewModel.daysCount.collectAsState()
    val topSoftwareName by viewModel.topSoftwareName.collectAsState()
    val topSoftwareMinutes by viewModel.topSoftwareMinutes.collectAsState()
    val softwareList by viewModel.softwareList.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val showArrows by viewModel.showArrows.collectAsState()

    // ─── 弹出窗口状态 ───
    var showDateSheet by remember { mutableStateOf(false) }
    var showPeriodSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ═══ TabRow ═══
        TabRow(selectedTabIndex = selectedPeriod.ordinal) {
            Tab(
                selected = selectedPeriod == TimeViewModel.Period.TODAY,
                onClick = { viewModel.selectPeriod(TimeViewModel.Period.TODAY) },
                text = { Text("今日") }
            )
            Tab(
                selected = selectedPeriod == TimeViewModel.Period.WEEK,
                onClick = { viewModel.selectPeriod(TimeViewModel.Period.WEEK) },
                text = { Text("本周") }
            )
            Tab(
                selected = selectedPeriod == TimeViewModel.Period.MONTH,
                onClick = { viewModel.selectPeriod(TimeViewModel.Period.MONTH) },
                text = { Text("本月") }
            )
            Tab(
                selected = selectedPeriod == TimeViewModel.Period.CUSTOM,
                onClick = { viewModel.selectPeriod(TimeViewModel.Period.CUSTOM) },
                text = { Text("自定义") }
            )
        }

        // ═══ 时间范围条 + 筛选 ═══
        TimeRangeBar(
            label = rangeLabel,
            showLeftArrow = showArrows && selectedPeriod != TimeViewModel.Period.TODAY,
            showRightArrow = showArrows && selectedPeriod != TimeViewModel.Period.TODAY,
            onPrevious = { viewModel.previousPeriod() },
            onNext = { viewModel.nextPeriod() },
            onLabelClick = {
                when (selectedPeriod) {
                    TimeViewModel.Period.TODAY,
                    TimeViewModel.Period.WEEK,
                    TimeViewModel.Period.MONTH -> showPeriodSheet = true
                    TimeViewModel.Period.CUSTOM -> showDateSheet = true
                }
            },
            filterActive = filterState.isActive,
            onFilterClick = { showFilterSheet = true }
        )

        // ═══ 滚动内容 ═══
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 摘要卡片（2×2） ──
            item {
                SummaryCards(
                    totalMinutes = totalMinutes,
                    dailyAvg = dailyAvg,
                    daysCount = daysCount,
                    topSoftwareName = topSoftwareName,
                    topSoftwareMinutes = topSoftwareMinutes
                )
            }

            // ── 趋势图（卡片包裹） ──
            if (settings.showLineChart) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "时长趋势",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LineChart(
                                data = dailyTrends,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }
            }

            // ── 环形图分类分布（卡片包裹） ──
            if (settings.showDonutChart && categoryStats.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "分类分布",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DonutChart(
                                slices = createCategorySlices(
                                    categoryStats.associate { it.category to it.total }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── 软件排行 ──
            if (settings.showRanking && rankingStats.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "软件排行",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${rankingStats.size} 个软件",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(rankingStats) { stat ->
                    val rank = rankingStats.indexOf(stat) + 1
                    StatRow(
                        repository = repository,
                        stat = stat,
                        rank = rank,
                        maxMinutes = rankingStats.firstOrNull()?.total ?: 1L
                    )
                }
            }

            // ── 空状态 ──
            if (rankingStats.isEmpty() && categoryStats.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "暂无数据",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "开始记录软件使用时长吧",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // 底部留白
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // ── BottomSheet：日/周/月选择器 ──
    if (showPeriodSheet) {
        val pickerMode = when (selectedPeriod) {
            TimeViewModel.Period.TODAY -> PickerMode.DAY
            TimeViewModel.Period.WEEK -> PickerMode.WEEK
            TimeViewModel.Period.MONTH -> PickerMode.MONTH
            else -> PickerMode.WEEK
        }
        PeriodPickerSheet(
            mode = pickerMode,
            currentStart = rangeStart,
            currentEnd = rangeEnd,
            onSelect = { start, end ->
                when (selectedPeriod) {
                    TimeViewModel.Period.TODAY -> viewModel.navigateToDay(start)
                    TimeViewModel.Period.WEEK -> viewModel.navigateToWeek(start, end)
                    TimeViewModel.Period.MONTH -> viewModel.navigateToMonth(start, end)
                    else -> {}
                }
                showPeriodSheet = false
            },
            onDismiss = { showPeriodSheet = false }
        )
    }

    // ── BottomSheet：日期选择 ──
    if (showDateSheet) {
        DateRangeSheet(
            currentStart = rangeStart,
            currentEnd = rangeEnd,
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showDateSheet = false
            },
            onDismiss = { showDateSheet = false }
        )
    }

    // ── BottomSheet：软件筛选 ──
    if (showFilterSheet) {
        FilterSheet(
            softwareList = softwareList,
            currentFilter = filterState,
            onApply = { filter ->
                viewModel.applyFilter(filter)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

// ═══════════════════════════════════════════════
// 时间范围条
// ═══════════════════════════════════════════════

@Composable
private fun TimeRangeBar(
    label: String,
    showLeftArrow: Boolean,
    showRightArrow: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLabelClick: () -> Unit,
    filterActive: Boolean,
    onFilterClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左箭头 + 标签 + 右箭头
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = showLeftArrow,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        contentDescription = "上一周期",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onLabelClick() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    onClick = onNext,
                    enabled = showRightArrow,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "下一周期",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 筛选按钮（边框 + 图标，激活时高亮）
            Surface(
                onClick = onFilterClick,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = if (filterActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "筛选",
                    modifier = Modifier.padding(6.dp).size(18.dp),
                    tint = if (filterActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ═══════════════════════════════════════════════
// 摘要卡片（2×2 网格）
// ═══════════════════════════════════════════════

@Composable
private fun SummaryCards(
    totalMinutes: Long,
    dailyAvg: Long,
    daysCount: Int,
    topSoftwareName: String,
    topSoftwareMinutes: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                label = "总时长",
                value = DateTimeUtils.formatDuration(totalMinutes),
                color = MaterialTheme.colorScheme.primary
            )
            SummaryCard(
                label = "记录天数",
                value = "${daysCount}天",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                label = "日均",
                value = DateTimeUtils.formatDuration(dailyAvg),
                color = MaterialTheme.colorScheme.secondary
            )
            SummaryCard(
                label = "最多软件",
                value = if (topSoftwareName.isNotEmpty()) {
                    "$topSoftwareName"
                } else "—",
                subtitle = if (topSoftwareMinutes > 0) DateTimeUtils.formatDuration(topSoftwareMinutes) else null,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    color: Color,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// 排行行（保留原 StatRow 逻辑，优化 UI）
// ═══════════════════════════════════════════════

@Composable
fun StatRow(
    repository: NoteEverythingRepository,
    stat: SoftwareDuration,
    rank: Int,
    maxMinutes: Long
) {
    var softwareName by remember { mutableStateOf("加载中...") }
    var softwareCategory by remember { mutableStateOf("其他") }

    LaunchedEffect(stat.softwareId) {
        val sw = repository.getSoftware(stat.softwareId)
        softwareName = sw?.name ?: "已删除"
        softwareCategory = sw?.category ?: "其他"
    }

    val rankColor = when (rank) {
        1 -> Color(0xFFFF9800)
        2 -> Color(0xFF1A73E8)
        3 -> Color(0xFF9C27B0)
        else -> MaterialTheme.colorScheme.outline
    }

    val progress = if (maxMinutes > 0) stat.total.toFloat() / maxMinutes else 0f
    val barColor = CategoryColors.forCategory(softwareCategory).primary

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = rankColor,
                modifier = Modifier.width(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = softwareName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = DateTimeUtils.formatDuration(stat.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}
