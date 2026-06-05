// ============================================================
// SoftwareListScreen.kt — 记录页
// ============================================================
// 展示软件列表（按分类分组）+ 随笔入口 + 类别筛选

package com.corunling.noteeverything.ui.software

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "还没有添加软件",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "添加软件后可以追踪时长、记录感想",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                    val cc = CategoryColors.forCategory(cat)
                    FilterChip(
                        selected = selectedFilter == cat,
                        onClick = {
                            selectedFilter = if (selectedFilter == cat) null else cat
                        },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cc.background,
                            selectedLabelColor = cc.onBackground
                        )
                    )
                }
            }
        }

        // ═══ 按分类展示 ═══
        grouped
            .filter { selectedFilter == null || it.key == selectedFilter }
            .forEach { (category, items) ->
                item {
                    val cc = CategoryColors.forCategory(category)
                    Text(
                        text = "$category · ${items.size} 个",
                        style = MaterialTheme.typography.titleSmall,
                        color = cc.onBackground,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(items, key = { it.software.id }) { stat ->
                    var showDeleteDialog by remember { mutableStateOf(false) }
                    var showMenu by remember { mutableStateOf(false) }

                    val cc = CategoryColors.forCategory(stat.software.category)
                    val (gradStart, gradEnd) = CategoryColors.gradientFor(stat.software.category)

                    Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSoftwareClick(stat.software.id) },
                                onLongClick = { showMenu = true }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 首字母头像
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(listOf(gradStart, gradEnd))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stat.software.name.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 名称 + 今日时长
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stat.software.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "今日: ${DateTimeUtils.formatDuration(stat.todayDuration)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cc.onBackground
                                )
                            }

                            // 平台标签
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cc.background
                            ) {
                                Text(
                                    text = stat.software.platform,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cc.onBackground
                                )
                            }

                            // 右侧箭头
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // 长按弹出菜单
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }

                    // 删除确认对话框
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
}
