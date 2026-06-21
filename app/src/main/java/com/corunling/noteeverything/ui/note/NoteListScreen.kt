// ============================================================
// NoteListScreen.kt — 笔记时间轴
// ============================================================
// 所有笔记按日期分组展示，紧凑筛选下拉，长按多选。

package com.corunling.noteeverything.ui.note

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils
import com.corunling.noteeverything.util.NoteExporter
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    repository: NoteEverythingRepository,
    onNoteClick: (Long) -> Unit,
    // 选择模式参数
    selectionMode: Boolean = false,
    onSelectionChanged: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onRegisterActions: ((() -> Unit, () -> Unit) -> Unit)? = null,
    pendingAction: String? = null,
    onActionConsumed: () -> Unit = {}
) {
    val viewModel: NoteViewModel = viewModel(
        factory = NoteViewModel.Factory(repository)
    )
    val notes by viewModel.allNotes.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) } // null=全部, "software", "free"

    // 选中 ID
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 通知父级选择状态变化（不清空时不退出选择模式）
    fun updateSelection(newIds: Set<Long>) {
        selectedIds = newIds
        onSelectionChanged(true, newIds.size, notes.size)
    }

    // 父级强制退出选择时清空本地状态
    LaunchedEffect(selectionMode) {
        if (!selectionMode) selectedIds = emptySet()
    }

    // 注册全选（切换）/取消回调
    LaunchedEffect(notes) {
        val allIds = notes.map { it.id }.toSet()
        onRegisterActions?.invoke(
            {  // 全选/全不选切换
                if (selectedIds.size < notes.size) updateSelection(allIds)
                else updateSelection(emptySet())
            },
            { updateSelection(emptySet()) }
        )
    }

    // ── 浮动框操作处理 ──
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExportPicker by remember { mutableStateOf(false) }
    var exportNotesContent by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    var exportSoftwareNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var exportFormat by remember { mutableStateOf("html") }

    val exportZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val result = NoteExporter.exportNotesZip(context, exportNotesContent, exportSoftwareNames, exportFormat, uri)
            result.onSuccess { android.widget.Toast.makeText(context, "导出成功：${exportNotesContent.size} 条笔记", android.widget.Toast.LENGTH_SHORT).show() }
            result.onFailure { android.widget.Toast.makeText(context, "导出失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(pendingAction) {
        if (pendingAction == null) return@LaunchedEffect
        when (pendingAction) {
            "pin" -> {
                val allPinned = selectedIds.all { id -> notes.find { it.id == id }?.pinned == true }
                selectedIds.forEach { id -> scope.launch { repository.setNotePinned(id, !allPinned) } }
                onActionConsumed()
            }
            "lock" -> {
                val allLocked = selectedIds.all { id -> notes.find { it.id == id }?.locked == true }
                selectedIds.forEach { id -> scope.launch { repository.setNoteLocked(id, !allLocked) } }
                onActionConsumed()
            }
            "delete" -> { showDeleteConfirm = true }
            "tags" -> { onActionConsumed() }
            "export" -> { showExportPicker = true }
        }
    }

    if (showDeleteConfirm) {
        val lockedCount = selectedIds.count { id -> notes.find { it.id == id }?.locked == true }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; onActionConsumed() },
            title = { Text("批量删除") },
            text = {
                val text = buildString {
                    append("确定要删除选中的 ${selectedIds.size} 条笔记吗？")
                    if (lockedCount > 0) append("\n\n其中 $lockedCount 条已锁定，不会被删除。")
                }
                Text(text)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            selectedIds.forEach { id ->
                                val note = notes.find { it.id == id }
                                if (note != null && !note.locked) repository.deleteNote(note)
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

    // 导出格式选择
    if (showExportPicker) {
        AlertDialog(
            onDismissRequest = { showExportPicker = false; onActionConsumed() },
            icon = { Icon(Icons.Default.FileDownload, null) },
            title = { Text("导出笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导出选中的 ${selectedIds.size} 条笔记：")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                            showExportPicker = false
                            onActionConsumed()
                            scope.launch {
                                val selected = selectedIds.mapNotNull { repository.getNoteById(it) }
                                val sw = repository.getAllSoftwareSync().associate { it.id to it.name }
                                exportNotesContent = selected
                                exportSoftwareNames = sw
                                exportFormat = "html"
                                exportZipLauncher.launch("NoteEverything_${selected.size}条.html.zip")
                            }
                        }) {
                            Icon(Icons.Default.Code, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("HTML")
                        }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                            showExportPicker = false
                            onActionConsumed()
                            scope.launch {
                                val selected = selectedIds.mapNotNull { repository.getNoteById(it) }
                                val sw = repository.getAllSoftwareSync().associate { it.id to it.name }
                                exportNotesContent = selected
                                exportSoftwareNames = sw
                                exportFormat = "doc"
                                exportZipLauncher.launch("NoteEverything_${selected.size}条.doc.zip")
                            }
                        }) {
                            Icon(Icons.Default.Description, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Word")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExportPicker = false; onActionConsumed() }) { Text("取消") } }
        )
    }

    // 搜索+筛选后的笔记
    val filteredNotes = notes
        .let { list ->
            if (searchQuery.isBlank()) list
            else list.filter {
                val plain = Html.fromHtml(it.content, Html.FROM_HTML_MODE_COMPACT).toString()
                plain.contains(searchQuery, ignoreCase = true)
            }
        }
        .let { list ->
            if (typeFilter == null) list
            else list.filter { it.type == typeFilter }
        }

    val focusManager = LocalFocusManager.current

    // 空状态（全局无数据）
    if (notes.isEmpty() && searchQuery.isBlank() && typeFilter == null) {
        Box(modifier = Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { focusManager.clearFocus() }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("还没有笔记记录", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("切换到「记录」页面开始记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { focusManager.clearFocus() }) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ═══ 搜索栏 + 类型筛选（选择模式时隐藏）═══
        AnimatedVisibility(
            visible = !selectionMode,
            exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 搜索栏（左 2/3）
                Surface(
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索笔记") },
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

                // 类型筛选（右 1/3 紧凑下拉）
                var typeExpanded by remember { mutableStateOf(false) }
                val typeLabel = when (typeFilter) {
                    null -> "全部"
                    "software" -> "软件笔记"
                    "free" -> "随笔"
                    else -> "全部"
                }

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
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
                            // 筛选类型色点
                            if (typeFilter == "free") {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(CategoryColors.forCategory("随笔").primary))
                                Spacer(Modifier.width(4.dp))
                            } else if (typeFilter == "software") {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(4.dp))
                            } else {
                                Icon(Icons.Default.FilterList, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(2.dp))
                            }
                            Text(typeLabel, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部") },
                            onClick = { typeFilter = null },
                            leadingIcon = {
                                if (typeFilter == null) Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("软件笔记") },
                            onClick = { typeFilter = "software" },
                            leadingIcon = {
                                if (typeFilter == "software") Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("随笔") },
                            onClick = { typeFilter = "free" },
                            leadingIcon = {
                                if (typeFilter == "free") Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
        }

        // ═══ 笔记列表 ═══
        if (filteredNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "没有找到匹配的记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val pinnedNotes = filteredNotes.filter { it.pinned }
                val unpinnedNotes = filteredNotes.filter { !it.pinned }
                val groupedByDate = unpinnedNotes.groupBy {
                    dateFormat.format(Date(it.timestamp))
                }

                // 置顶区
                if (pinnedNotes.isNotEmpty()) {
                    item {
                        Text(
                            text = "📌 置顶",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(pinnedNotes, key = { it.id }) { note ->
                        NoteCardItem(note = note, selectionMode = selectionMode, isSelected = note.id in selectedIds,
                            onToggle = { updateSelection(if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id) },
                            onLongPress = { if (!selectionMode) { updateSelection(setOf(note.id)) } },
                            onClick = { if (selectionMode) { updateSelection(if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id) } else { onNoteClick(note.id) } },
                            repository = repository)
                    }
                }

                // 按日期分组
                groupedByDate.forEach { (date, dayNotes) ->
                    item {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(dayNotes, key = { it.id }) { note ->
                        NoteCardItem(note = note, selectionMode = selectionMode, isSelected = note.id in selectedIds,
                            onToggle = { updateSelection(if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id) },
                            onLongPress = { if (!selectionMode) { updateSelection(setOf(note.id)) } },
                            onClick = { if (selectionMode) { updateSelection(if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id) } else { onNoteClick(note.id) } },
                            repository = repository)
                    }
                }
            }
            }
        }
    }
}

// ═══ 笔记卡片组件 ═══

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCardItem(
    note: NoteEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    repository: NoteEverythingRepository
) {
    val borderColor = if (note.type == "free")
        CategoryColors.forCategory("随笔").primary
    else
        MaterialTheme.colorScheme.primary

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "noteCardBg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!selectionMode) onLongPress() }
                )
        ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧色条
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 60.dp)
                    .background(borderColor)
            )

            // 选择框（向右侧展开，卡片总宽不变）
            androidx.compose.animation.AnimatedVisibility(
                visible = selectionMode,
                enter = expandHorizontally(animationSpec = tween(200)) + fadeIn(tween(200)),
                exit = shrinkHorizontally(animationSpec = tween(200)) + fadeOut(tween(200))
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() }
                )
            }

            // 内容区
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // 类型标签 + 时间
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (note.type == "free") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CategoryColors.forCategory("随笔").background
                            ) {
                                Text(
                                    "随笔",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CategoryColors.forCategory("随笔").onBackground,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "软件笔记",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (note.locked) {
                            Icon(Icons.Default.Lock, null, Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(4.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = DateTimeUtils.formatTimestamp(note.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // 正文摘要
                    val plainText = remember(note) {
                        Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT).toString()
                    }
                    Text(
                        text = plainText.take(100),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                    if (plainText.length > 100) {
                        Text("...", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }

                    // 关联时长
                    var linkedRecords by remember { mutableStateOf<List<TimeRecordEntity>>(emptyList()) }
                    LaunchedEffect(note.id) {
                        repository.getLinkedTimeRecords(note.id).collect { linkedRecords = it }
                    }
                    if (linkedRecords.isNotEmpty()) {
                        Text(
                            text = "🔗 ${linkedRecords.joinToString(" ") { DateTimeUtils.formatDuration(it.durationMinutes) }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                // 图片缩略图
                NoteImageThumbnail(htmlContent = note.content)
            }
        }
        }
    }
}

// ─── 图片缩略图组件 ────────────────
@Composable
private fun NoteImageThumbnail(htmlContent: String, modifier: Modifier = Modifier) {
    val imageSrc = remember(htmlContent) { extractFirstImageSrc(htmlContent) }
    if (imageSrc == null) return

    val bitmap = remember(imageSrc) {
        try {
            val base64Data = imageSrc.substringAfter("base64,")
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) { null }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .padding(start = 8.dp)
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun extractFirstImageSrc(html: String): String? {
    val regex = """src=["'](data:image/[^"']+)["']""".toRegex()
    return regex.find(html)?.groupValues?.getOrNull(1)
}
