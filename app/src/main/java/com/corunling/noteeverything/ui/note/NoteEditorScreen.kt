// ============================================================
// NoteEditorScreen.kt — 笔记编辑页（富文本 WebView 版）
// ============================================================
// 功能：
// 1. 软件选择下拉 → 关联软件或自由随笔
// 2. 富文本编辑（WebView + contentEditable）
// 3. 格式工具栏（粗体/斜体/下划线/标题/列表/图片）
// 4. 图片插入（系统相册选取 → Base64 嵌入 HTML）
// 5. 时长关联：软件笔记默认关联当天全部时长
//    可勾选/取消勾选每条时长记录
// 6. 内容存为 HTML，图片作为 Base64 嵌入 content 字段

package com.corunling.noteeverything.ui.note

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.ui.editor.RichTextEditor
import com.corunling.noteeverything.ui.editor.RichTextEditorState
import com.corunling.noteeverything.ui.editor.rememberRichTextEditorState
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

// ─── 工具栏分类枚举 + 面板三态 ─────────────────────────
enum class ToolbarCategory { TEXT, LIST, ALIGN, IMAGE }
enum class PanelState { IDLE, TYPING, TOOLS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    softwareId: Long?,
    noteId: Long?,
    repository: NoteEverythingRepository,
    navController: NavHostController
) {
    var content by remember { mutableStateOf("") }
    var initialHtml by remember { mutableStateOf("") }
    var selectedSoftwareId by remember { mutableStateOf(softwareId) }
    var timestamp by remember { mutableStateOf(DateTimeUtils.now()) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val editorState = rememberRichTextEditorState()

    LaunchedEffect(noteId) {
        if (noteId != null) {
            val existing = repository.getNoteById(noteId)
            if (existing != null) {
                initialHtml = existing.content
                content = existing.content
                selectedSoftwareId = existing.softwareId
                timestamp = existing.timestamp
            }
        }
    }

    val allSoftware by repository.getAllSoftware()
        .collectAsState(initial = emptyList())

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val base64 = uriToBase64(context, it)
            if (base64 != null) {
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                editorState.insertImageBase64(base64, fileName)
            }
        }
    }

    var todayRecords by remember { mutableStateOf<List<TimeRecordEntity>>(emptyList()) }
    var linkedRecordIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var linksInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSoftwareId) {
        if (selectedSoftwareId != null) {
            todayRecords = repository.getTodayTimeRecordsForSoftware(
                selectedSoftwareId!!, DateTimeUtils.today()
            )
        } else {
            todayRecords = emptyList()
        }
    }

    LaunchedEffect(noteId, todayRecords, linksInitialized) {
        if (!linksInitialized && todayRecords.isNotEmpty()) {
            if (noteId != null) {
                repository.getLinksForNote(noteId).collect { links ->
                    linkedRecordIds = links.map { it.timeRecordId }.toSet()
                }
            } else if (selectedSoftwareId != null) {
                linkedRecordIds = todayRecords.map { it.id }.toSet()
            }
            linksInitialized = true
        }
    }

    val title = if (noteId != null) "编辑笔记" else "新建笔记"
    val selectedSoftware = allSoftware.find { it.id == selectedSoftwareId }

    fun save() {
        if (isSaving) return
        isSaving = true
        editorState.requestContent { html ->
            content = html
            scope.launch {
                try {
                    val savedNoteId: Long? = if (noteId != null) {
                        val existing = repository.getNoteById(noteId)
                        if (existing != null) {
                            repository.updateNote(
                                existing.copy(
                                    softwareId = selectedSoftwareId,
                                    content = html,
                                    timestamp = timestamp,
                                    type = if (selectedSoftwareId != null) "software" else "free"
                                )
                            )
                        }
                        noteId
                    } else {
                        repository.createNote(
                            softwareId = selectedSoftwareId,
                            content = html,
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
    }

    // ── 底部工具栏状态 ──────────────────────────────────
    var panelState by remember { mutableStateOf(PanelState.IDLE) }
    var activeToolCategory by remember { mutableStateOf<ToolbarCategory?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeHeight = with(density) {
        WindowInsets.ime.asPaddingValues().calculateBottomPadding().toPx().toDp()
    }

    // 仅键盘「从有到无」→ IDLE
    var prevIme by remember { mutableStateOf(0.dp) }
    LaunchedEffect(imeHeight) {
        val wasVisible = prevIme >= 10.dp
        prevIme = imeHeight
        if (wasVisible && imeHeight < 10.dp && panelState == PanelState.TYPING) {
            panelState = PanelState.IDLE
        }
    }

    // 离开 TOOLS 时保持 Box 高度 350ms，等键盘/动画跟上
    var leavingKey by remember { mutableStateOf(0L) }
    var lockHeight by remember { mutableStateOf(false) }
    LaunchedEffect(leavingKey) {
        if (leavingKey > 0L) { lockHeight = true; delay(350); lockHeight = false }
    }
    fun triggerLeave() { leavingKey++ }

    fun onCategoryTap(cat: ToolbarCategory) {
        when (panelState) {
            PanelState.IDLE -> {
                panelState = PanelState.TOOLS
                activeToolCategory = cat
            }
            PanelState.TYPING -> {
                keyboardController?.hide()
                panelState = PanelState.TOOLS
                activeToolCategory = cat
            }
            PanelState.TOOLS -> {
                if (activeToolCategory == cat) {
                    triggerLeave()
                    panelState = PanelState.IDLE
                    activeToolCategory = null
                } else {
                    activeToolCategory = cat
                }
            }
        }
    }

    fun onEditorTap() {
        when (panelState) {
            PanelState.IDLE -> {
                panelState = PanelState.TYPING
                editorState.focusEditor()
            }
            PanelState.TOOLS -> {
                triggerLeave()
                panelState = PanelState.TYPING
                activeToolCategory = null
                editorState.focusEditor()
            }
            PanelState.TYPING -> { /* already typing */ }
        }
    }

    LaunchedEffect(Unit) {
        if (noteId == null) {
            panelState = PanelState.TYPING
            editorState.focusEditor()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    TextButton(
                        onClick = { save() },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                if (isSaving) "保存中..." else "保存",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    var showSoftwarePicker by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedSoftware != null)
                                CategoryColors.forCategory(selectedSoftware.category).background
                            else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { showSoftwarePicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedSoftware != null) {
                                    val (g1, g2) = CategoryColors.gradientFor(selectedSoftware.category)
                                    Box(
                                        modifier = Modifier.size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Brush.linearGradient(listOf(g1, g2))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            selectedSoftware.name.take(1),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        selectedSoftware.name,
                                        fontWeight = FontWeight.Bold,
                                        color = CategoryColors.forCategory(selectedSoftware.category).onBackground
                                    )
                                } else {
                                    Text("自由随笔（不关联软件）", color = MaterialTheme.colorScheme.outline)
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        DropdownMenu(
                            expanded = showSoftwarePicker,
                            onDismissRequest = { showSoftwarePicker = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("自由随笔（不关联软件）") },
                                onClick = {
                                    selectedSoftwareId = null
                                    showSoftwarePicker = false
                                    linksInitialized = false
                                    linkedRecordIds = emptySet()
                                }
                            )
                            allSoftware.forEach { sw ->
                                DropdownMenuItem(
                                    text = { Text("${sw.name} (${sw.platform})") },
                                    onClick = {
                                        selectedSoftwareId = sw.id
                                        showSoftwarePicker = false
                                        linksInitialized = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    RichTextEditor(
                        state = editorState,
                        initialContent = initialHtml,
                        onContentChanged = { html -> content = html },
                        onTap = { onEditorTap() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp)
                    )
                }

                if (selectedSoftwareId != null && todayRecords.isNotEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF8E1)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "关联今日时长",
                                    color = Color(0xFFE65100),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "勾选需要关联的时长记录",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    items(todayRecords, key = { "link_${it.id}" }) { record ->
                        val isChecked = record.id in linkedRecordIds
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChecked) Color(0xFFFFF8E1)
                            else MaterialTheme.colorScheme.surface
                        ) {
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

            // ── 底部区域 ──
            val targetH = when {
                panelState == PanelState.TOOLS && activeToolCategory != ToolbarCategory.IMAGE ->
                    maxOf(imeHeight, 220.dp)
                lockHeight ->
                    maxOf(imeHeight, 220.dp)
                else -> imeHeight
            }
            var prevPanelState by remember { mutableStateOf(PanelState.IDLE) }
            val enterOrLeave = prevPanelState != panelState &&
                (prevPanelState == PanelState.TOOLS || panelState == PanelState.TOOLS)
            prevPanelState = panelState
            val bottomH by animateDpAsState(
                targetValue = targetH,
                animationSpec = if (enterOrLeave) tween(250) else tween(80),
                label = "bottomH"
            )
            BottomFormattingToolbar(
                editorState = editorState,
                panelState = panelState,
                activeCategory = activeToolCategory,
                bottomHeight = bottomH,
                onCategoryClick = { onCategoryTap(it) },
                onImageClick = { imagePickerLauncher.launch("image/*") }
            )
        }
    }
}

// ─── 底部工具栏 ────────────────────────────────────────────

@Composable
private fun BottomFormattingToolbar(
    editorState: RichTextEditorState,
    panelState: PanelState,
    activeCategory: ToolbarCategory?,
    bottomHeight: Dp,
    onCategoryClick: (ToolbarCategory) -> Unit,
    onImageClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column {
            CategoryIconsRow(
                isToolMode = panelState == PanelState.TOOLS,
                activeCategory = activeCategory,
                onCategoryClick = onCategoryClick,
                onImageClick = onImageClick
            )
            val showPanel = panelState == PanelState.TOOLS && activeCategory != null && activeCategory != ToolbarCategory.IMAGE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomHeight)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showPanel,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    if (activeCategory != null) {
                        ExpandedToolPanel(
                            category = activeCategory,
                            editorState = editorState
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedToolPanel(
    category: ToolbarCategory,
    editorState: RichTextEditorState
) {
    val fillV = category == ToolbarCategory.TEXT
    Surface(
        modifier = if (fillV) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        when (category) {
            ToolbarCategory.TEXT -> TextPanel(editorState)
            ToolbarCategory.LIST -> ListPanel(editorState)
            ToolbarCategory.ALIGN -> AlignPanel(editorState)
            ToolbarCategory.IMAGE -> { /* IMAGE 直接触发选择器 */ }
        }
    }
}

// ─── 共用 ──────────────────────────────────────────────
private val CardBg @Composable get() = Color.Black.copy(alpha = 0.06f)

// ─── 文字面板 ─────────────────────────────────────────

@Composable
private fun TextPanel(editorState: RichTextEditorState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 样式
        Surface(shape = RoundedCornerShape(10.dp), color = CardBg, modifier = Modifier.weight(1f)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ToolBtn("B", bold = true) { editorState.applyFormat("bold") }
                ThinDivider()
                ToolBtn("I", italic = true) { editorState.applyFormat("italic") }
                ThinDivider()
                ToolBtn("U", underline = true) { editorState.applyFormat("underline") }
                ThinDivider()
                ToolBtn("S", strike = true) { editorState.applyFormat("strikeThrough") }
            }
        }
        // 字号
        Surface(shape = RoundedCornerShape(10.dp), color = CardBg, modifier = Modifier.weight(1f)) {
            FontSizeWheel(editorState)
        }
        // 颜色
        Surface(shape = RoundedCornerShape(10.dp), color = CardBg, modifier = Modifier.weight(1f)) {
            ColorWheel(editorState)
        }
    }
}

@Composable
private fun ThinDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
}

@Composable
private fun FontSizeWheel(editorState: RichTextEditorState) {
    val items = listOf(
        "8" to "1", "10" to "2", "12" to "3", "14" to "4",
        "16" to "4", "18" to "5", "20" to "5", "24" to "6",
        "28" to "6", "36" to "7", "48" to "7"
    )
    var sel by remember { mutableStateOf(2) } // default 12
    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 4.dp, bottom = 2.dp)) {
        Text("字号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        LazyRow(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items.size) { i ->
                val isSel = i == sel
                Surface(
                    onClick = { sel = i; editorState.applyFormat("fontSize", items[i].second) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ) {
                    Text(
                        items[i].first,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorWheel(editorState: RichTextEditorState) {
    val colors = listOf(
        "#000000","#555555","#AAAAAA","#FF0000","#FF6600",
        "#FFCC00","#00AA00","#0088FF","#0000FF","#8800CC","#FF00AA"
    )
    var sel by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 4.dp, bottom = 2.dp)) {
        Text("颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        LazyRow(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(colors.size) { i ->
                val isSel = i == sel
                Surface(
                    onClick = { sel = i; editorState.applyFormat("foreColor", colors[i]) },
                    shape = CircleShape,
                    modifier = Modifier.size(if (isSel) 28.dp else 22.dp),
                    border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Box(Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(colors[i]))))
                }
            }
        }
    }
}

// ─── 序号面板 ─────────────────────────────────────────

@Composable
private fun ListPanel(editorState: RichTextEditorState) {
    Surface(shape = RoundedCornerShape(10.dp), color = CardBg, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth()) {
        Column {
            Row(Modifier.height(60.dp).fillMaxWidth()) {
                ListCell(icon = Icons.AutoMirrored.Filled.FormatListBulleted, label = "无序", modifier = Modifier.weight(1f)) {
                    editorState.applyFormat("insertUnorderedList")
                }
                VDivider()
                ListCell(text = "1.", label = "数字", modifier = Modifier.weight(1f)) {
                    editorState.applyFormat("insertOrderedList")
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
            Row(Modifier.height(60.dp).fillMaxWidth()) {
                ListCell(text = "a.", label = "小写字母", modifier = Modifier.weight(1f)) {
                    editorState.applyFormat("insertHTML", "<ol type=\"a\"><li></li></ol>")
                }
                VDivider()
                ListCell(text = "A.", label = "大写字母", modifier = Modifier.weight(1f)) {
                    editorState.applyFormat("insertHTML", "<ol type=\"A\"><li></li></ol>")
                }
            }
        }
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
}

@Composable
private fun ListCell(
    icon: ImageVector? = null,
    text: String? = null,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (icon != null) Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            else Text(text ?: "?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── 对齐面板 ─────────────────────────────────────────

@Composable
private fun AlignPanel(editorState: RichTextEditorState) {
    Surface(shape = RoundedCornerShape(10.dp), color = CardBg, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()) {
        Row(
            Modifier.height(50.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlignBtn(Icons.AutoMirrored.Filled.AlignHorizontalLeft, "左", Modifier.weight(1f)) { editorState.applyFormat("justifyLeft") }
            ThinDivider()
            AlignBtn(Icons.Default.AlignHorizontalCenter, "中", Modifier.weight(1f)) { editorState.applyFormat("justifyCenter") }
            ThinDivider()
            AlignBtn(Icons.AutoMirrored.Filled.AlignHorizontalRight, "右", Modifier.weight(1f)) { editorState.applyFormat("justifyRight") }
        }
    }
}

@Composable
private fun AlignBtn(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryIconsRow(
    isToolMode: Boolean,
    activeCategory: ToolbarCategory?,
    onCategoryClick: (ToolbarCategory) -> Unit,
    onImageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CategoryIconBtn(
            icon = Icons.Default.TextFields,
            isActive = isToolMode && activeCategory == ToolbarCategory.TEXT
        ) { onCategoryClick(ToolbarCategory.TEXT) }

        CategoryIconBtn(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            isActive = isToolMode && activeCategory == ToolbarCategory.LIST
        ) { onCategoryClick(ToolbarCategory.LIST) }

        CategoryIconBtn(
            icon = Icons.Default.AlignHorizontalCenter,
            isActive = isToolMode && activeCategory == ToolbarCategory.ALIGN
        ) { onCategoryClick(ToolbarCategory.ALIGN) }

        CategoryIconBtn(
            icon = Icons.Default.Image,
            isActive = false
        ) { onImageClick() }
    }
}

@Composable
private fun CategoryIconBtn(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(250),
        label = "iconBg"
    )
    val tint by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "iconTint"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bg
    ) {
        Box(modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = tint
            )
        }
    }
}

@Composable
private fun ToolBtn(
    text: String? = null,
    icon: ImageVector? = null,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit
) {
    val fw = if (bold) FontWeight.ExtraBold else FontWeight.Medium
    val fs = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null
    val deco = when {
        underline -> androidx.compose.ui.text.style.TextDecoration.Underline
        strike -> androidx.compose.ui.text.style.TextDecoration.LineThrough
        else -> null
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = text, modifier = Modifier.size(20.dp))
            } else {
                Text(text ?: "", fontWeight = fw, fontStyle = fs, textDecoration = deco, fontSize = 14.sp)
            }
        }
    }
}

// ─── 图片工具函数 ────────────────────────────────────────

private fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        inputStream.close()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val scale = maxOf(
            (options.outWidth / 1920f).let { if (it > 1) it.toInt() else 1 },
            (options.outHeight / 1920f).let { if (it > 1) it.toInt() else 1 }
        )
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        if (bitmap != null) {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            val compressedBytes = outputStream.toByteArray()
            bitmap.recycle()
            outputStream.close()
            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } else {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            "data:$mimeType;base64,$base64"
        }
    } catch (e: Exception) {
        null
    }
}
