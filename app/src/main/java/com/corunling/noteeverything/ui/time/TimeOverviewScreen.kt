// ============================================================
// TimeOverviewScreen.kt — 时长总览页
// ============================================================
// 顶部三个 Tab（今日/本周/本月），显示总计时长 + 软件排行。

package com.corunling.noteeverything.ui.time

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.dao.SoftwareDuration
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
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
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
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stats) { stat ->
                    val rank = stats.indexOf(stat) + 1
                    StatRow(repository = repository, stat = stat, rank = rank)
                }
            }
        }
    }
}

@Composable
fun StatRow(
    repository: NoteEverythingRepository,
    stat: SoftwareDuration,
    rank: Int
) {
    var softwareName by remember { mutableStateOf("加载中...") }

    LaunchedEffect(stat.softwareId) {
        val sw = repository.getSoftware(stat.softwareId)
        softwareName = sw?.name ?: "已删除"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(12.dp))
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
    }
}
