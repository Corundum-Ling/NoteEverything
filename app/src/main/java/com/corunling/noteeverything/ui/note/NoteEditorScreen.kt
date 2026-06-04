// ============================================================
// NoteEditorScreen.kt — 笔记编辑页
// ============================================================
// 功能：
// 1. 软件选择下拉 → 关联软件或自由随笔
// 2. 正文编辑
// 3. 时长关联：软件笔记默认关联当天全部时长，自由随笔默认不关联
//    可勾选/取消勾选每条时长记录

package com.corunling.noteeverything.ui.note

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    softwareId: Long?,
    noteId: Long?,
    repository: NoteEverythingRepository,
    navController: NavHostController
) {
    var content by remember { mutableStateOf("") }
    var selectedSoftwareId by remember { mutableStateOf(softwareId) }
    var timestamp by remember { mutableStateOf(DateTimeUtils.now()) }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    // 加载已有笔记（一次性查询，不会持续监听）
    LaunchedEffect(noteId) {
        if (noteId != null) {
            val existing = repository.getNoteById(noteId)
            if (existing != null) {
                content = existing.content
                selectedSoftwareId = existing.softwareId
                timestamp = existing.timestamp
            }
        }
    }

    val allSoftware by repository.getAllSoftware()
        .collectAsState(initial = emptyList())

    // ── 时长关联状态 ──
    var todayRecords by remember { mutableStateOf<List<TimeRecordEntity>>(emptyList()) }
    var linkedRecordIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var linksInitialized by remember { mutableStateOf(false) }

    // 当关联软件变化时，加载该软件今天的时长记录
    LaunchedEffect(selectedSoftwareId) {
        if (selectedSoftwareId != null) {
            todayRecords = repository.getTodayTimeRecordsForSoftware(
                selectedSoftwareId!!, DateTimeUtils.today()
            )
        } else {
            todayRecords = emptyList()
        }
    }

    // 初始化关联勾选状态
    LaunchedEffect(noteId, todayRecords, linksInitialized) {
        if (!linksInitialized && todayRecords.isNotEmpty()) {
            if (noteId != null) {
                // 编辑模式：加载已有链接
                repository.getLinksForNote(noteId).collect { links ->
                    linkedRecordIds = links.map { it.timeRecordId }.toSet()
                }
            } else if (selectedSoftwareId != null) {
                // 新建软件笔记：默认全选当天时长
                linkedRecordIds = todayRecords.map { it.id }.toSet()
            }
            // 自由随笔：linkedRecordIds 保持空
            linksInitialized = true
        }
    }

    val title = if (noteId != null) "编辑笔记" else "新建笔记"
    val selectedSoftware = allSoftware.find { it.id == selectedSoftwareId }

    // ── 保存逻辑 ──
    fun save() {
        if (isSaving) return
        isSaving = true  // 立即禁用按钮
        scope.launch {
            try {
                val savedNoteId: Long? = if (noteId != null) {
                    val existing = repository.getNoteById(noteId)
                    if (existing != null) {
                        repository.updateNote(
                            existing.copy(
                                softwareId = selectedSoftwareId,
                                content = content,
                                timestamp = timestamp,
                                type = if (selectedSoftwareId != null) "software" else "free"
                            )
                        )
                    }
                    noteId
                } else {
                    repository.createNote(
                        softwareId = selectedSoftwareId,
                        content = content,
                        timestamp = timestamp
                    )
                }
                if (savedNoteId != null) {
                    repository.setNoteLinks(savedNoteId, linkedRecordIds.toList())
                }
            } catch (e: Exception) {
                isSaving = false
                return@launch
            }
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { save() },
                        enabled = content.isNotBlank() && !isSaving
                    ) { Text(if (isSaving) "保存中..." else "保存") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 关联软件下拉 ──
            item {
                var pickerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = pickerExpanded,
                    onExpandedChange = { pickerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSoftware?.name ?: "自由随笔（不关联软件）",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("关联软件") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = pickerExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = pickerExpanded,
                        onDismissRequest = { pickerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("自由随笔（不关联软件）") },
                            onClick = {
                                selectedSoftwareId = null
                                pickerExpanded = false
                                linksInitialized = false
                                linkedRecordIds = emptySet()
                            }
                        )
                        allSoftware.forEach { sw ->
                            DropdownMenuItem(
                                text = { Text("${sw.name} (${sw.platform})") },
                                onClick = {
                                    selectedSoftwareId = sw.id
                                    pickerExpanded = false
                                    linksInitialized = false
                                }
                            )
                        }
                    }
                }
            }

            // ── 正文输入 ──
            item {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("写点什么...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    maxLines = Int.MAX_VALUE
                )
            }

            // ── 时长关联（仅关联了软件时显示） ──
            if (selectedSoftwareId != null && todayRecords.isNotEmpty()) {
                item {
                    Text(
                        "关联时长记录",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "勾选需要关联的时长记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                items(todayRecords, key = { "link_${it.id}" }) { record ->
                    val isChecked = record.id in linkedRecordIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                linkedRecordIds = if (checked) {
                                    linkedRecordIds + record.id
                                } else {
                                    linkedRecordIds - record.id
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${DateTimeUtils.formatTimestamp(record.startTime)} - ${DateTimeUtils.formatTimestamp(record.endTime)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                DateTimeUtils.formatDuration(record.durationMinutes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            if (selectedSoftwareId != null && todayRecords.isEmpty()) {
                item {
                    Text(
                        "今天还没有时长记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
