// ============================================================
// SoftwareDetailScreen.kt — 软件详情页
// ============================================================
// 页面组成：
// 1. 软件信息头（头像、名称、平台/分类标签）
// 2. 计时器区域（开始/停止 + 手动录入时/分）
// 3. 笔记列表
// 4. 时长历史记录列表
//
// 计时器逻辑：
// - 点击"开始计时"记录当前时间戳
// - 点击"停止并保存"计算时间差 → 转换为分钟 → 存入 Room
// - 支持手动录入：输入时/分，点击保存

package com.corunling.noteeverything.ui.software

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import android.widget.NumberPicker
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import android.text.Html
import android.util.Base64
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavHostController
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.SoftwareEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.ui.navigation.Routes
import com.corunling.noteeverything.ui.theme.CategoryColor
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SoftwareDetailScreen(
    softwareId: Long,
    repository: NoteEverythingRepository,
    navController: NavHostController
) {
    val viewModel: SoftwareViewModel = viewModel(
        factory = SoftwareViewModel.Factory(repository)
    )

    // 加载软件信息
    var software by remember { mutableStateOf<SoftwareEntity?>(null) }
    LaunchedEffect(softwareId) {
        software = repository.getSoftware(softwareId)
    }

    // 订阅时长和笔记（自动刷新的 Flow）
    val timeRecords by viewModel.getTimeRecords(softwareId)
        .collectAsState(initial = emptyList())
    val notes by viewModel.getNotes(softwareId)
        .collectAsState(initial = emptyList())

    // 计时器状态
    var isTimerRunning by remember { mutableStateOf(false) }
    var timerStartTime by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    // 实时更新计时
    if (isTimerRunning) {
        LaunchedEffect(Unit) {
            while (true) {
                elapsedSeconds = (DateTimeUtils.now() - timerStartTime) / 1000
                delay(1000)
            }
        }
    }

    // 折叠状态
    var notesExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }

    val sw = software
    val catColor = sw?.let { CategoryColors.forCategory(it.category) }
    val (gradStart, gradEnd) = sw?.let { CategoryColors.gradientFor(it.category) }
        ?: (Color.Gray to Color.DarkGray)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sw?.name ?: "加载中...") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══ 1. 软件信息头 ═══
            sw?.let { swNotNull ->
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 头像（首字母 + 渐变色背景）
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(listOf(gradStart, gradEnd))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    swNotNull.name.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    swNotNull.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = catColor?.background ?: Color.Gray
                                    ) {
                                        Text(
                                            swNotNull.platform,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = catColor?.onBackground ?: Color.DarkGray
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = catColor?.background ?: Color.Gray
                                    ) {
                                        Text(
                                            swNotNull.category,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = catColor?.onBackground ?: Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ═══ 2. 计时器 ═══
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTimerRunning)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isTimerRunning) {
                            // 运行中：显示经过时间
                            Text(
                                text = formatElapsed(elapsedSeconds),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
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
                            // 空闲：显示开始计时按钮
                            Button(onClick = {
                                timerStartTime = DateTimeUtils.now()
                                elapsedSeconds = 0
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
                            ManualTimeEntry(
                                softwareId = softwareId,
                                repository = repository,
                                scope = scope
                            )
                        }
                    }
                }
            }

            // ═══ 3. 笔记 ═══
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "笔记",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledIconButton(
                        onClick = {
                            navController.navigate(
                                Routes.NoteEditor.create(softwareId = softwareId)
                            )
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加笔记",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (notes.isEmpty()) {
                item {
                    Text(
                        "还没有笔记",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val showNotes = if (notesExpanded) notes else notes.take(3)
                items(showNotes, key = { "note_${it.id}" }) { note ->
                    NoteCard(
                        note = note,
                        catColor = catColor,
                        repository = repository,
                        onDelete = { viewModel.deleteNote(note) },
                        onClick = {
                            navController.navigate(
                                Routes.NoteEditor.create(noteId = note.id)
                            )
                        }
                    )
                }
                if (notes.size > 3) {
                    item {
                        TextButton(onClick = { notesExpanded = !notesExpanded }) {
                            Text(
                                if (notesExpanded) "收起" else "展开全部 (${notes.size})"
                            )
                        }
                    }
                }
            }

            // ═══ 4. 时长记录 ═══
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
                    TimeRecordCard(
                        record = record,
                        onDelete = { viewModel.deleteTimeRecord(record) }
                    )
                }
                if (timeRecords.size > 3) {
                    item {
                        TextButton(onClick = { timeExpanded = !timeExpanded }) {
                            Text(
                                if (timeExpanded) "收起" else "展开全部 (${timeRecords.size})"
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 手动录入组件 ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTimeEntry(
    softwareId: Long,
    repository: NoteEverythingRepository,
    scope: CoroutineScope
) {
    var date by remember { mutableStateOf(DateTimeUtils.today()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var startHour by remember { mutableStateOf(0) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(0) }
    var endMinute by remember { mutableStateOf(0) }

    val startTotal = startHour * 60 + startMinute
    val endTotal = endHour * 60 + endMinute
    val duration = endTotal - startTotal

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 日期选择
        OutlinedButton(
            onClick = { showDatePicker = true }
        ) {
            Text("📅 $date")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 开始时间
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

        // 结束时间
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
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        date = sdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─── 笔记卡片 ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteEntity,
    catColor: CategoryColor?,
    repository: NoteEverythingRepository,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 左侧颜色条
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(catColor?.primary ?: Color.Gray)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                val plainText = remember(note) {
                    Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT).toString()
                }
                Text(
                    plainText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
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

                Text(
                    DateTimeUtils.formatDate(note.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // 图片缩略图
            NoteImageThumbnail(note.content)
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    onDelete()
                    showMenu = false
                }
            )
        }
    }
}

// ─── 时长记录卡片 ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TimeRecordCard(
    record: TimeRecordEntity,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
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
                    if (record.source == "timer") "计时器" else "手动录入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                DateTimeUtils.formatDuration(record.durationMinutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    onDelete()
                    showMenu = false
                }
            )
        }
    }
}

// ─── NumberPicker 转轮组件 ────────────────────────────────────
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
                android.widget.NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    this.value = value
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

// ─── 格式化秒数为 HH:MM:SS ──────────────────────────────
private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
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
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                .size(64.dp)
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
