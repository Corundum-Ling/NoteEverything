// ============================================================
// SoftwareListScreen.kt — 记录页
// ============================================================
// 展示软件列表（按分类分组）+ 随笔入口 + 类别筛选

package com.corunling.noteeverything.ui.software

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareListScreen(
    repository: NoteEverythingRepository,
    onSoftwareClick: (Long) -> Unit
) {
    val viewModel: SoftwareViewModel = viewModel(
        factory = SoftwareViewModel.Factory(repository)
    )
    val softwareList by viewModel.softwareWithStats.collectAsState()

    // 类别筛选：null = 显示全部
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    // 空状态
    if (softwareList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "还没有添加软件\n点击右下角 + 开始",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 所有可用类别
    val allCategories = softwareList.map { it.software.category }.distinct().sorted()

    // 按分类分组
    val grouped = softwareList.groupBy { it.software.category }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ═══ 类别筛选条 ═══
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("全部") }
                )
                allCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedFilter == cat,
                        onClick = {
                            selectedFilter = if (selectedFilter == cat) null else cat
                        },
                        label = { Text(cat) }
                    )
                }
            }
        }

        // ═══ 按分类展示 ═══
        grouped
            .filter { selectedFilter == null || it.key == selectedFilter }
            .forEach { (category, items) ->
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(items, key = { it.software.id }) { stat ->
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSoftwareClick(stat.software.id) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stat.software.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stat.software.platform,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    IconButton(
                                        onClick = { showDeleteDialog = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "今日: ${DateTimeUtils.formatDuration(stat.todayDuration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("移除「${stat.software.name}」？") },
                            text = { Text("相关的笔记和时长记录不会被删除。") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteSoftware(stat.software)
                                        showDeleteDialog = false
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) { Text("移除") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                }
            }
    }
}
