// ============================================================
// SoftwareListScreen.kt — 记录页
// ============================================================
// 展示软件列表（按分类分组），搜索+分类筛选，长按多选。

package com.corunling.noteeverything.ui.software

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SoftwareListScreen(
    repository: NoteEverythingRepository,
    onSoftwareClick: (Long) -> Unit,
    selectionMode: Boolean = false,
    onSelectionChanged: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onRegisterActions: ((() -> Unit, () -> Unit) -> Unit)? = null,
    pendingAction: String? = null,
    onActionConsumed: () -> Unit = {}
) {
    val viewModel: SoftwareViewModel = viewModel(
        factory = SoftwareViewModel.Factory(repository)
    )
    val softwareList by viewModel.softwareWithStats.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    val allCategories = softwareList.map { it.software.category }.distinct().sorted()

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    fun updateSelection(newIds: Set<Long>) {
        selectedIds = newIds
        onSelectionChanged(true, newIds.size, softwareList.size)
    }

    LaunchedEffect(selectionMode) {
        if (!selectionMode) selectedIds = emptySet()
    }

    LaunchedEffect(softwareList) {
        val allIds = softwareList.map { it.software.id }.toSet()
        onRegisterActions?.invoke(
            {
                if (selectedIds.size < softwareList.size) updateSelection(allIds)
                else updateSelection(emptySet())
            },
            { updateSelection(emptySet()) }
        )
    }

    // ── 浮动框操作处理 ──
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(pendingAction) {
        if (pendingAction == null) return@LaunchedEffect
        when (pendingAction) {
            "pin" -> {
                val allPinned = selectedIds.all { id -> softwareList.find { it.software.id == id }?.software?.pinned == true }
                selectedIds.forEach { id -> scope.launch { repository.setSoftwarePinned(id, !allPinned) } }
                onActionConsumed()
            }
            "lock" -> {
                val allLocked = selectedIds.all { id -> softwareList.find { it.software.id == id }?.software?.locked == true }
                selectedIds.forEach { id -> scope.launch { repository.setSoftwareLocked(id, !allLocked) } }
                onActionConsumed()
            }
            "delete" -> { showDeleteConfirm = true }
            "tags" -> { onActionConsumed() }
        }
    }

    if (showDeleteConfirm) {
        val lockedCount = selectedIds.count { id -> softwareList.find { it.software.id == id }?.software?.locked == true }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; onActionConsumed() },
            title = { Text("批量删除") },
            text = {
                val text = buildString {
                    append("确定要删除选中的 ${selectedIds.size} 个软件吗？")
                    if (lockedCount > 0) append("\n\n其中 $lockedCount 个已锁定，不会被删除。")
                }
                Text(text)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            selectedIds.forEach { id ->
                                val sw = softwareList.find { it.software.id == id }?.software
                                if (sw != null && !sw.locked) repository.deleteSoftware(sw)
                            }
                            updateSelection(emptySet())
                            onActionConsumed()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false; onActionConsumed() }) { Text("取消") } }
        )
    }

    val focusManager = LocalFocusManager.current

    // 空状态
    if (softwareList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() },
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
                Text("还没有添加软件", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "添加软件后可以追踪时长、记录感想",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // 搜索+筛选
    val filteredList = softwareList.filter { stat ->
        (searchQuery.isBlank() || stat.software.name.contains(searchQuery, ignoreCase = true))
    }
    val pinnedItems = filteredList.filter { it.software.pinned }
    val unpinnedGrouped = filteredList.filter { !it.software.pinned }
        .groupBy { it.software.category }
        .filter { selectedFilter == null || it.key == selectedFilter }

    Box(
        modifier = Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { focusManager.clearFocus() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

        // ═══ 搜索+分类筛选（选择模式时隐藏）═══
        item {
            AnimatedVisibility(
                visible = !selectionMode,
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
                enter = fadeIn(tween(200)) + expandVertically(tween(200))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索软件") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                    var expanded by remember { mutableStateOf(false) }
                    val filterLabel = if (selectedFilter == null) "全部" else selectedFilter ?: "全部"
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            onClick = {},
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedFilter != null) {
                                    val cc = CategoryColors.forCategory(selectedFilter!!)
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(cc.primary))
                                    Spacer(Modifier.width(4.dp))
                                } else {
                                    Icon(Icons.Default.FilterList, null, Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(2.dp))
                                }
                                Text(filterLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部") },
                                onClick = { selectedFilter = null },
                                leadingIcon = {
                                    if (selectedFilter == null)
                                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                }
                            )
                            allCategories.forEach { cat ->
                                val cc = CategoryColors.forCategory(cat)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(cc.primary))
                                            Spacer(Modifier.width(8.dp))
                                            Text(cat)
                                        }
                                    },
                                    onClick = { selectedFilter = cat },
                                    leadingIcon = {
                                        if (selectedFilter == cat)
                                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ═══ 置顶区 ═══
        if (pinnedItems.isNotEmpty()) {
            item {
                Text(
                    text = "📌 置顶",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(pinnedItems, key = { it.software.id }) { stat ->
                val isSelected = stat.software.id in selectedIds
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(200),
                    label = "cardBg"
                )
                val cc = CategoryColors.forCategory(stat.software.category)
                val (gradStart, gradEnd) = CategoryColors.gradientFor(stat.software.category)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    updateSelection(
                                        if (isSelected) selectedIds - stat.software.id
                                        else selectedIds + stat.software.id
                                    )
                                } else {
                                    onSoftwareClick(stat.software.id)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    updateSelection(setOf(stat.software.id))
                                }
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 选择模式复选框（固定容器始终存在，卡片高度不突变）
                        val cbWidth by animateDpAsState(
                            targetValue = if (selectionMode) 48.dp else 0.dp,
                            animationSpec = tween(200),
                            label = "cbWidth"
                        )
                        val cbAlpha by animateFloatAsState(
                            targetValue = if (selectionMode) 1f else 0f,
                            animationSpec = tween(200),
                            label = "cbAlpha"
                        )
                        Box(Modifier.width(cbWidth), contentAlignment = Alignment.Center) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                updateSelection(
                                    if (isSelected) selectedIds - stat.software.id
                                    else selectedIds + stat.software.id
                                )
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .graphicsLayer { alpha = cbAlpha }
                        )
                        }

                        // 首字母头像
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(gradStart, gradEnd))),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (stat.software.locked) {
                                    Icon(Icons.Default.Lock, null, Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.width(2.dp))
                                }
                                Text(
                                    text = stat.software.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "今日: ${DateTimeUtils.formatDuration(stat.todayDuration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cc.onBackground
                            )
                        }

                        // 平台标签
                        Surface(shape = RoundedCornerShape(8.dp), color = cc.background) {
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
            }
        }

        // ═══ 各分类 ═══
        unpinnedGrouped.forEach { (category, catItems) ->
            item {
                val cc = CategoryColors.forCategory(category)
                Text(
                    text = "$category · ${catItems.size} 个",
                    style = MaterialTheme.typography.titleSmall,
                    color = cc.onBackground,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(catItems, key = { it.software.id }) { stat ->
                val isSelected = stat.software.id in selectedIds
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(200),
                    label = "cardBg"
                )
                val cc2 = CategoryColors.forCategory(stat.software.category)
                val (gradStart, gradEnd) = CategoryColors.gradientFor(stat.software.category)

                Card(
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    updateSelection(if (isSelected) selectedIds - stat.software.id else selectedIds + stat.software.id)
                                } else { onSoftwareClick(stat.software.id) }
                            },
                            onLongClick = { if (!selectionMode) { updateSelection(setOf(stat.software.id)) } }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val cbWidth by animateDpAsState(targetValue = if (selectionMode) 48.dp else 0.dp, tween(200), label = "cbW")
                        val cbAlpha by animateFloatAsState(targetValue = if (selectionMode) 1f else 0f, tween(200), label = "cbA")
                        Box(Modifier.width(cbWidth), contentAlignment = Alignment.Center) {
                            Checkbox(checked = isSelected, onCheckedChange = { updateSelection(if (isSelected) selectedIds - stat.software.id else selectedIds + stat.software.id) },
                                modifier = Modifier.padding(end = 8.dp).graphicsLayer { alpha = cbAlpha })
                        }
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(gradStart, gradEnd))), contentAlignment = Alignment.Center) {
                            Text(stat.software.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (stat.software.locked) { Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline); Spacer(Modifier.width(2.dp)) }
                                Text(stat.software.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Text("今日: ${DateTimeUtils.formatDuration(stat.todayDuration)}", style = MaterialTheme.typography.bodySmall, color = cc2.onBackground)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = cc2.background) {
                            Text(stat.software.platform, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = cc2.onBackground)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        }
    }
}
