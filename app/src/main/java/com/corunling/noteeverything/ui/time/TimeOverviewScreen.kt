// ============================================================
// TimeOverviewScreen.kt — 时长总览页
// ============================================================
// 顶部三个 Tab（今日/本周/本月），显示总计时长 + 软件排行。

package com.corunling.noteeverything.ui.time

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.dao.SoftwareDuration
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOverviewScreen(repository: NoteEverythingRepository) {
    val viewModel: TimeViewModel = viewModel(
        factory = TimeViewModel.Factory(repository)
    )
    val todayStats by viewModel.todayStats.collectAsState()
    val weekStats by viewModel.weekStats.collectAsState()
    val monthStats by viewModel.monthStats.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    // 每次进入页面重新加载
    LaunchedEffect(Unit) { viewModel.loadAll() }

    Column(modifier = Modifier.fillMaxSize()) {
        // ═══ 时间维度选择 ═══
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
        }

        val stats = when (selectedPeriod) {
            TimeViewModel.Period.TODAY -> todayStats
            TimeViewModel.Period.WEEK -> weekStats
            TimeViewModel.Period.MONTH -> monthStats
        }
        val totalMinutes = stats.sumOf { it.total }

        // ═══ 总计卡片 ═══
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("总计", style = MaterialTheme.typography.labelMedium)
                Text(
                    DateTimeUtils.formatDuration(totalMinutes),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ═══ 排行列表 ═══
        if (stats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val maxMinutes = stats.maxOf { it.total }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stats) { stat ->
                    val rank = stats.indexOf(stat) + 1
                    StatRow(
                        repository = repository,
                        stat = stat,
                        rank = rank,
                        maxMinutes = maxMinutes
                    )
                }
            }
        }
    }
}

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
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
                    fontWeight = FontWeight.Medium
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
