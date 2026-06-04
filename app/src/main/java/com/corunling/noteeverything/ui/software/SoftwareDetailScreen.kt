// ============================================================
// SoftwareDetailScreen.kt — 软件详情页
// ============================================================
// 页面组成：
// 1. 计时器区域（开始/停止 + 手动录入时/分）
// 2. 感想笔记列表
// 3. 时长历史记录列表
//
// 计时器逻辑：
// - 点击"开始计时"记录当前时间戳
// - 点击"停止并保存"计算时间差 → 转换为分钟 → 存入 Room
// - 支持手动录入：输入时:分，点击保存

package com.corunling.noteeverything.ui.software

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.SoftwareEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.ui.navigation.Routes
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareDetailScreen(
    softwareId: Long,
    repository: NoteEverythingRepository,
    navController: NavHostController
) {
    val viewModel: SoftwareViewModel = viewModel(
        factory = SoftwareViewModel.Factory(repository)
    )

    // 加载软件信息（挂起函数，在 LaunchedEffect 中调用）
    var software by remember { mutableStateOf<SoftwareEntity?>(null) }
    LaunchedEffect(softwareId) {
        software = repository.getSoftware(softwareId)
    }

    // 订阅该软件下的时长和笔记（自动刷新的 Flow）
    val timeRecords by viewModel.getTimeRecords(softwareId)
        .collectAsState(initial = emptyList())
    val notes by viewModel.getNotes(softwareId)
        .collectAsState(initial = emptyList())

    // 计时器状态
    var isTimerRunning by remember { mutableStateOf(false) }
    var timerStartTime by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    // 折叠状态
    var notesExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(software?.name ?: "加载中...") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            // FAB 移除，改为放在"感想笔记"标题右侧
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══ 计时器 ═══
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTimerRunning)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("计时器", style = MaterialTheme.typography.titleSmall)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isTimerRunning) {
                            Text(
                                text = "计时中...",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val endTime = DateTimeUtils.now()
                                    val durationMinutes = (endTime - timerStartTime) / 60000
                                    scope.launch {
                                        repository.createTimeRecord(
                                            softwareId = softwareId,
                                            startTime = timerStartTime,
                                            endTime = endTime,
                                            durationMinutes = durationMinutes,
                                            date = DateTimeUtils.today(),
                                            source = "timer"
                                        )
                                    }
                                    isTimerRunning = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("停止并保存")
                            }
                        } else {
                            Button(onClick = {
                                timerStartTime = DateTimeUtils.now()
                                isTimerRunning = true
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("开始计时")
                            }
                        }

                        // 手动录入（展开/收起）
                        Spacer(modifier = Modifier.height(12.dp))
                        var showManual by remember { mutableStateOf(false) }
                        TextButton(onClick = { showManual = !showManual }) {
                            Text(if (showManual) "收起" else "手动录入时长")
                        }
                        if (showManual) {
                            // 日期（默认今天）
                            var date by remember { mutableStateOf(DateTimeUtils.today()) }
                            var showDatePicker by remember { mutableStateOf(false) }

                            // 开始/结束时间（用分钟数存储，默认 0=未设置）
                            var startHour by remember { mutableStateOf(0) }
                            var startMinute by remember { mutableStateOf(0) }
                            var endHour by remember { mutableStateOf(0) }
                            var endMinute by remember { mutableStateOf(0) }

                            val startTotal = startHour * 60 + startMinute
                            val endTotal = endHour * 60 + endMinute
                            val duration = endTotal - startTotal

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 日期选择
                                OutlinedButton(
                                    onClick = { showDatePicker = true }
                                ) {
                                    Text("📅 $date")
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 开始时间 — 两个 NumberPicker
                                Text("开始时间",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NumberPickerView(
                                        value = startHour,
                                        onValueChange = { startHour = it },
                                        range = 0..23,
                                        label = "时",
                                        modifier = Modifier.width(80.dp)
                                    )
                                    Text(" : ",
                                        style = MaterialTheme.typography.headlineSmall)
                                    NumberPickerView(
                                        value = startMinute,
                                        onValueChange = { startMinute = it },
                                        range = 0..59,
                                        label = "分",
                                        modifier = Modifier.width(80.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 结束时间 — 两个 NumberPicker
                                Text("结束时间",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NumberPickerView(
                                        value = endHour,
                                        onValueChange = { endHour = it },
                                        range = 0..23,
                                        label = "时",
                                        modifier = Modifier.width(80.dp)
                                    )
                                    Text(" : ",
                                        style = MaterialTheme.typography.headlineSmall)
                                    NumberPickerView(
                                        value = endMinute,
                                        onValueChange = { endMinute = it },
                                        range = 0..59,
                                        label = "分",
                                        modifier = Modifier.width(80.dp)
                                    )
                                }

                                // 时长预览
                                if (duration > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "时长: ${DateTimeUtils.formatDuration(duration.toLong())}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        if (duration > 0) {
                                            val todayStart = DateTimeUtils.todayMillis() + startTotal * 60000L
                                            val todayEnd = DateTimeUtils.todayMillis() + endTotal * 60000L
                                            scope.launch {
                                                repository.createTimeRecord(
                                                    softwareId = softwareId,
                                                    startTime = todayStart,
                                                    endTime = todayEnd,
                                                    durationMinutes = duration.toLong(),
                                                    date = date,
                                                    source = "manual"
                                                )
                                            }
                                            startHour = 0; startMinute = 0
                                            endHour = 0; endMinute = 0
                                            date = DateTimeUtils.today()
                                        }
                                    },
                                    enabled = duration > 0
                                ) { Text("保存") }
                            }

                            // ═══ 日期选择弹窗 ═══
                            if (showDatePicker) {
                                val datePickerState = rememberDatePickerState(
                                    initialSelectedDateMillis = DateTimeUtils.todayMillis()
                                )
                                DatePickerDialog(
                                    onDismissRequest = { showDatePicker = false },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            datePickerState.selectedDateMillis?.let { millis ->
                                                val sdf = java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd",
                                                    java.util.Locale.getDefault()
                                                )
                                                date = sdf.format(java.util.Date(millis))
                                            }
                                            showDatePicker = false
                                        }) { Text("确定") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDatePicker = false }) {
                                            Text("取消")
                                        }
                                    }
                                ) {
                                    DatePicker(state = datePickerState)
                                }
                            }
                        }
                    }
                }
            }

            // ═══ 感想笔记 ═══
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "感想笔记",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            navController.navigate(
                                Routes.NoteEditor.create(softwareId = softwareId)
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加笔记",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (notes.isEmpty()) {
                item {
                    Text(
                        "还没有笔记，点击右侧 + 开始记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val showNotes = if (notesExpanded) notes else notes.take(3)
                items(showNotes, key = { "note_${it.id}" }) { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(
                                    Routes.NoteEditor.create(noteId = note.id)
                                )
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(note.content, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))

                            // 关联时长摘要
                            var linkedRecords by remember {
                                mutableStateOf<List<TimeRecordEntity>>(emptyList())
                            }
                            LaunchedEffect(note.id) {
                                repository.getLinkedTimeRecords(note.id).collect { records ->
                                    linkedRecords = records
                                }
                            }
                            if (linkedRecords.isNotEmpty()) {
                                Text(
                                    "🔗 ${linkedRecords.map { DateTimeUtils.formatDuration(it.durationMinutes) }.joinToString(" + ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    DateTimeUtils.formatDate(note.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                IconButton(
                                    onClick = { viewModel.deleteNote(note) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                // 超过 3 条时显示展开/收起按钮
                if (notes.size > 3) {
                    item {
                        TextButton(onClick = { notesExpanded = !notesExpanded }) {
                            Text(if (notesExpanded) "收起" else "展开全部 (${notes.size})")
                        }
                    }
                }
            }

            // ═══ 时长记录 ═══
            item {
                Text(
                    "时长记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (timeRecords.isEmpty()) {
                item {
                    Text(
                        "还没有时长记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val showTime = if (timeExpanded) timeRecords else timeRecords.take(3)
                items(showTime, key = { "time_${it.id}" }) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${DateTimeUtils.formatTimestamp(record.startTime)} - ${DateTimeUtils.formatTimestamp(record.endTime)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "📅 ${record.date}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    DateTimeUtils.formatDuration(record.durationMinutes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (record.source == "timer") "计时器" else "手动录入",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteTimeRecord(record) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                // 超过 3 条时显示展开/收起
                if (timeRecords.size > 3) {
                    item {
                        TextButton(onClick = { timeExpanded = !timeExpanded }) {
                            Text(if (timeExpanded) "收起" else "展开全部 (${timeRecords.size})")
                        }
                    }
                }
            }
        }
    }
}

// ─── NumberPicker 转轮组件 ────────────────────────────────────
// 用 AndroidView 包裹原生 NumberPicker 实现滚动选择。
// 注意：NumberPicker 在初始化时也会触发 onValueChanged，
// 通过 isUpdating 标记防止 update 期间触发回调导致死循环。

@Composable
fun NumberPickerView(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier
) {
    val isUpdating = remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            modifier = modifier.height(100.dp),
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    this.value = value
                    // 捕获到稳定的回调引用，避免每次重组都重建
                    setOnValueChangedListener { _, _, newVal ->
                        if (!isUpdating.value) {
                            onValueChange(newVal)
                        }
                    }
                }
            },
            update = { picker ->
                isUpdating.value = true
                picker.value = value
                isUpdating.value = false
            }
        )
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
    }
}
