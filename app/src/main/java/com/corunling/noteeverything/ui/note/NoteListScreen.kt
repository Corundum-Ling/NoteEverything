// ============================================================
// NoteListScreen.kt — 时间轴
// ============================================================
// 所有笔记按日期分组展示，支持搜索、类型筛选、批量删除。

package com.corunling.noteeverything.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.launch
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

    Column(modifier = Modifier.fillMaxSize()) {
        // ═══ 搜索栏 ═══
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("搜索记录") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

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
                label = { Text("自由随笔") }
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
                else list.filter { it.content.contains(searchQuery, ignoreCase = true) }
            }
            .let { list ->
                if (typeFilter == null) list
                else list.filter { it.type == typeFilter }
            }

        if (filteredNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isNotBlank() || typeFilter != null) "没有找到匹配的记录"
                    else "还没有记录\n切换到「记录」页面开始记录",
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
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
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
                                        Text(
                                            text = if (note.type == "free") "自由随笔" else "软件笔记",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (note.type == "free")
                                                MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = DateTimeUtils.formatTimestamp(note.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.content.take(100),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2
                                    )
                                    if (note.content.length > 100) {
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
                            }
                        }
                    }
                }
            }
        }
    }
}
