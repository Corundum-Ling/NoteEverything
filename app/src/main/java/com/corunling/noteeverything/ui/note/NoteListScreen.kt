// ============================================================
// NoteListScreen.kt — 时间轴
// ============================================================
// 所有笔记按日期分组展示，支持搜索、类型筛选、批量删除。

package com.corunling.noteeverything.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.text.Html
import android.util.Base64
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    repository: NoteEverythingRepository,
    onNoteClick: (Long) -> Unit
) {
    val viewModel: NoteViewModel = viewModel(
        factory = NoteViewModel.Factory(repository)
    )
    val notes by viewModel.allNotes.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) } // null=全部, "software", "free"

    // 批量选择
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val scope = rememberCoroutineScope()

    // 退出选择模式时清空
    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // ═══ 空状态（全局无数据）═══
    if (notes.isEmpty() && searchQuery.isBlank() && typeFilter == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "还没有笔记记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "切换到「记录」页面开始记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ═══ 搜索栏 ═══
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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

        // ═══ 类型筛选 ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null },
                label = { Text("全部") }
            )
            FilterChip(
                selected = typeFilter == "software",
                onClick = { typeFilter = if (typeFilter == "software") null else "software" },
                label = { Text("软件笔记") }
            )
            FilterChip(
                selected = typeFilter == "free",
                onClick = { typeFilter = if (typeFilter == "free") null else "free" },
                label = { Text("随笔") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CategoryColors.forCategory("随笔").background,
                    selectedLabelColor = CategoryColors.forCategory("随笔").onBackground
                )
            )
        }

        // ═══ 批量操作栏 ═══
        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { exitSelection() }) { Text("取消") }
                Spacer(modifier = Modifier.weight(1f))
                Text("已选 ${selectedIds.size} 项", style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = {
                        scope.launch {
                            selectedIds.forEach { id ->
                                val note = repository.getNoteById(id)
                                if (note != null) viewModel.deleteNote(note)
                            }
                            exitSelection()
                        }
                    },
                    enabled = selectedIds.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("删除")
                }
            }
        } else {
            TextButton(
                onClick = { selectionMode = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) { Text("选择") }
        }

        // ═══ 笔记列表 ═══
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
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val groupedByDate = filteredNotes.groupBy {
                    dateFormat.format(Date(it.timestamp))
                }

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
                        val isSelected = note.id in selectedIds
                        val borderColor = if (note.type == "free")
                            CategoryColors.forCategory("随笔").primary
                        else
                            MaterialTheme.colorScheme.primary

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (isSelected)
                                                selectedIds - note.id
                                            else
                                                selectedIds + note.id
                                        } else {
                                            onNoteClick(note.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds = setOf(note.id)
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = 60.dp)
                                        .background(borderColor)
                                )
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (selectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedIds = if (isSelected)
                                                    selectedIds - note.id
                                                else
                                                    selectedIds + note.id
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (note.type == "free") {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = CategoryColors.forCategory("随笔").background
                                                ) {
                                                    Text(
                                                        text = "随笔",
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
                                                        text = "软件笔记",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = DateTimeUtils.formatTimestamp(note.timestamp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val plainText = remember(note) {
                                            Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT).toString()
                                        }
                                        Text(
                                            text = plainText.take(100),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2
                                        )
                                        if (plainText.length > 100) {
                                            Text(
                                                text = "...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        // 关联时长
                                        var linkedRecords by remember {
                                            mutableStateOf<List<TimeRecordEntity>>(emptyList())
                                        }
                                        LaunchedEffect(note.id) {
                                            repository.getLinkedTimeRecords(note.id).collect {
                                                linkedRecords = it
                                            }
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
                                    // ── 图片缩略图 ──
                                    NoteImageThumbnail(htmlContent = note.content)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── 图片缩略图组件 ──────────────────────────────────
@Composable
private fun NoteImageThumbnail(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    val imageSrc = remember(htmlContent) { extractFirstImageSrc(htmlContent) }
    if (imageSrc == null) return

    val bitmap = remember(imageSrc) {
        try {
            val base64Data = imageSrc.substringAfter("base64,")
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Exception) { null }
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

/** 从 HTML 中提取第一张图片的 src 属性值 */
private fun extractFirstImageSrc(html: String): String? {
    val regex = """src=["'](data:image/[^"']+)["']""".toRegex()
    return regex.find(html)?.groupValues?.getOrNull(1)
}
