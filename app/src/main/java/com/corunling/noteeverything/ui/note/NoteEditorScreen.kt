// NoteEditorScreen.kt
package com.corunling.noteeverything.ui.note

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.ui.editor.RichTextEditor
import com.corunling.noteeverything.ui.editor.RichTextEditorState
import com.corunling.noteeverything.ui.editor.rememberRichTextEditorState
import com.corunling.noteeverything.ui.theme.CategoryColors
import com.corunling.noteeverything.util.DateTimeUtils
import com.corunling.noteeverything.util.NoteExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class ToolbarCategory { TEXT, LIST, ALIGN, IMAGE }
enum class PanelState { IDLE, TYPING, TOOLS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    softwareId: Long?, noteId: Long?,
    repository: NoteEverythingRepository, navController: NavHostController
) {
    var content by remember { mutableStateOf("") }; var initialHtml by remember { mutableStateOf("") }
    var selectedSoftwareId by remember { mutableStateOf(softwareId) }; var isSaving by remember { mutableStateOf(false) }
    var timestamp by remember { mutableStateOf(DateTimeUtils.now()) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current

    // 导出状态
    var exportTargetFormat by remember { mutableStateOf<String?>(null) } // "html" / "doc" / null
    var exportContent by remember { mutableStateOf("") } // 编辑器实时内容快照
    val editorState = rememberRichTextEditorState()

    LaunchedEffect(noteId) { if (noteId != null) { val e = repository.getNoteById(noteId); if (e != null) { initialHtml = e.content; content = e.content; selectedSoftwareId = e.softwareId; timestamp = e.timestamp } } }
    val allSoftware by repository.getAllSoftware().collectAsState(initial = emptyList())

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { val b64 = uriToBase64(context, it); if (b64 != null) editorState.insertImageBase64(b64, "img_${System.currentTimeMillis()}.jpg") } }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let { uri -> val b64 = uriToBase64(context, uri); if (b64 != null) editorState.insertImageBase64(b64, "camera_${System.currentTimeMillis()}.jpg") } }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { val dir = File(context.cacheDir, "camera"); dir.mkdirs(); val file = File(dir, "nc_${System.currentTimeMillis()}.jpg"); file.createNewFile(); camUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); cameraLauncher.launch(camUri!!) } }
    fun launchCamera() { if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) { val dir = File(context.cacheDir, "camera"); dir.mkdirs(); val file = File(dir, "nc_${System.currentTimeMillis()}.jpg"); file.createNewFile(); camUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); cameraLauncher.launch(camUri!!) } else { permLauncher.launch(android.Manifest.permission.CAMERA) } }

    // ── 导出 SAF Launchers ──
    val exportHtmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = NoteExporter.exportAsHtml(
                    context = context,
                    noteContent = exportContent,
                    noteTimestamp = timestamp,
                    softwareName = allSoftware.find { it.id == selectedSoftwareId }?.name,
                    outputUri = uri
                )
                result.onSuccess { android.widget.Toast.makeText(context, "导出成功", android.widget.Toast.LENGTH_SHORT).show() }
                result.onFailure { android.widget.Toast.makeText(context, "导出失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val exportDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/msword")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = NoteExporter.exportAsDoc(
                    context = context,
                    noteContent = exportContent,
                    noteTimestamp = timestamp,
                    softwareName = allSoftware.find { it.id == selectedSoftwareId }?.name,
                    outputUri = uri
                )
                result.onSuccess { android.widget.Toast.makeText(context, "导出成功", android.widget.Toast.LENGTH_SHORT).show() }
                result.onFailure { android.widget.Toast.makeText(context, "导出失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
    }

    var allTimeRecords by remember { mutableStateOf<List<TimeRecordEntity>>(emptyList()) }; var linkedRecordIds by remember { mutableStateOf<Set<Long>>(emptySet()) }; var linksInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSoftwareId) { if (selectedSoftwareId != null) repository.getTimeRecordsBySoftware(selectedSoftwareId!!).collect { allTimeRecords = it.sortedByDescending { r -> r.startTime } } else allTimeRecords = emptyList() }
    LaunchedEffect(noteId, allTimeRecords, linksInitialized) { if (!linksInitialized) { if (noteId != null) repository.getLinksForNote(noteId).collect { linkedRecordIds = it.map { l -> l.timeRecordId }.toSet() } else if (selectedSoftwareId != null) linkedRecordIds = allTimeRecords.map { it.id }.toSet(); linksInitialized = true } }

    val title = if (noteId != null) "编辑笔记" else "新建笔记"; val selectedSoftware = allSoftware.find { it.id == selectedSoftwareId }

    fun startExport(format: String) {
        editorState.requestContent { html ->
            exportContent = html
            exportTargetFormat = null
            val filename = NoteExporter.suggestFileName(html, if (format == "html") "html" else "doc")
            if (format == "html") exportHtmlLauncher.launch(filename)
            else exportDocLauncher.launch(filename)
        }
    }

    var fmtB by remember { mutableStateOf(false) }; var fmti by remember { mutableStateOf(false) }; var fmtU by remember { mutableStateOf(false) }; var fmtS by remember { mutableStateOf(false) }
    var fmtAL by remember { mutableStateOf(true) }; var fmtAC by remember { mutableStateOf(false) }; var fmtAR by remember { mutableStateOf(false) }
    var fmtUL by remember { mutableStateOf(false) }; var fmtOL by remember { mutableStateOf(false) }; var fmtOLT by remember { mutableStateOf("1") }
    var fmtSize by remember { mutableStateOf("3") }; var fmtColor by remember { mutableStateOf("#000000") }
    fun parseFmt(json: String) { try { val o = org.json.JSONObject(json); fmtB = o.optBoolean("bold"); fmti = o.optBoolean("italic"); fmtU = o.optBoolean("underline"); fmtS = o.optBoolean("strikeThrough"); fmtAL = o.optBoolean("justifyLeft"); fmtAC = o.optBoolean("justifyCenter"); fmtAR = o.optBoolean("justifyRight"); fmtUL = o.optBoolean("insertUnorderedList"); fmtOL = o.optBoolean("insertOrderedList"); fmtOLT = o.optString("orderedListType", "1"); fmtSize = o.optString("fontSize", "16px"); fmtColor = o.optString("foreColor", "#000000") } catch (_: Exception) {} }

    fun save() { if (isSaving) return; isSaving = true; editorState.requestContent { html -> content = html; scope.launch { try { val id: Long? = if (noteId != null) { val e = repository.getNoteById(noteId); if (e != null) { repository.updateNote(e.copy(softwareId = selectedSoftwareId, content = html, timestamp = timestamp, type = if (selectedSoftwareId != null) "software" else "free")); noteId } else null } else repository.createNote(softwareId = selectedSoftwareId, content = html, timestamp = timestamp); if (id != null) repository.setNoteLinks(id, linkedRecordIds.toList()) } catch (_: Exception) { isSaving = false; return@launch }; navController.popBackStack() } } }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除笔记") },
            text = { Text("确定要删除此笔记吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        noteId?.let { scope.launch { val e = repository.getNoteById(it); if (e != null) { repository.deleteNote(e); navController.popBackStack() } } }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    var panelState by remember { mutableStateOf(PanelState.IDLE) }; var activeToolCategory by remember { mutableStateOf<ToolbarCategory?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current; val density = LocalDensity.current
    val imeHeight = with(density) { WindowInsets.ime.asPaddingValues().calculateBottomPadding().toPx().toDp() }
    var prevIme by remember { mutableStateOf(0.dp) }
    LaunchedEffect(imeHeight) { val wasVisible = prevIme >= 10.dp; prevIme = imeHeight; if (wasVisible && imeHeight < 10.dp && panelState == PanelState.TYPING) panelState = PanelState.IDLE }
    var leavingKey by remember { mutableStateOf(0L) }; var lockHeight by remember { mutableStateOf(false) }
    LaunchedEffect(leavingKey) { if (leavingKey > 0L) { lockHeight = true; delay(350); lockHeight = false } }
    fun triggerLeave() { if (imeHeight >= 10.dp) leavingKey++ }

    fun onCategoryTap(cat: ToolbarCategory) { when (panelState) { PanelState.IDLE -> { panelState = PanelState.TOOLS; activeToolCategory = cat }; PanelState.TYPING -> { keyboardController?.hide(); panelState = PanelState.TOOLS; activeToolCategory = cat }; PanelState.TOOLS -> if (activeToolCategory == cat) { editorState.focusEditor(); scope.launch { delay(400); panelState = PanelState.TYPING; activeToolCategory = null } } else { activeToolCategory = cat } } }
    fun onEditorTap() { when (panelState) { PanelState.IDLE -> { panelState = PanelState.TYPING; editorState.focusEditor() }; PanelState.TOOLS -> { editorState.focusEditor(); scope.launch { delay(400); panelState = PanelState.TYPING; activeToolCategory = null } }; PanelState.TYPING -> {} } }
    LaunchedEffect(Unit) { if (noteId == null) { panelState = PanelState.TYPING; editorState.focusEditor() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    // 导出按钮
                    IconButton(onClick = { exportTargetFormat = "select" }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导出")
                    }
                    // 删除按钮（仅编辑模式显示）
                    if (noteId != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    // 保存按钮
                    TextButton(onClick = { save() }, enabled = !isSaving, shape = RoundedCornerShape(16.dp)) { Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) { Text(if (isSaving) "保存中..." else "保存", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimary) } }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            var showSoftwarePicker by remember { mutableStateOf(false) }; var showTimeLinker by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (selectedSoftware != null) CategoryColors.forCategory(selectedSoftware.category).background else MaterialTheme.colorScheme.surfaceVariant, onClick = { keyboardController?.hide(); showSoftwarePicker = true }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectedSoftware != null) { val (g1, g2) = CategoryColors.gradientFor(selectedSoftware.category); Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(g1, g2))), contentAlignment = Alignment.Center) { Text(selectedSoftware.name.take(1), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(8.dp)); Text(selectedSoftware.name, fontWeight = FontWeight.Bold, color = CategoryColors.forCategory(selectedSoftware.category).onBackground) }
                            else { Text("选择软件", color = MaterialTheme.colorScheme.outline) }
                            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                    DropdownMenu(expanded = showSoftwarePicker, onDismissRequest = { showSoftwarePicker = false; panelState = PanelState.IDLE }) { DropdownMenuItem(text = { Text("自由随笔（不关联软件）") }, onClick = { selectedSoftwareId = null; showSoftwarePicker = false; linksInitialized = false; linkedRecordIds = emptySet(); panelState = PanelState.IDLE }); allSoftware.forEach { sw -> DropdownMenuItem(text = { Text("${sw.name} (${sw.platform})") }, onClick = { selectedSoftwareId = sw.id; showSoftwarePicker = false; linksInitialized = false; panelState = PanelState.IDLE }) } }
                }
                if (selectedSoftware != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val linkedCount = linkedRecordIds.count { id -> allTimeRecords.any { it.id == id } }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF8E1), onClick = { keyboardController?.hide(); showTimeLinker = true }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("关联时长", color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                if (linkedCount > 0) { Spacer(Modifier.width(4.dp)); Surface(shape = CircleShape, color = Color(0xFFE65100)) { Text("$linkedCount", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                                Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFE65100))
                            }
                        }
                        DropdownMenu(expanded = showTimeLinker, onDismissRequest = { showTimeLinker = false; panelState = PanelState.IDLE }, modifier = Modifier.heightIn(max = 280.dp)) {
                            if (allTimeRecords.isEmpty()) {
                                DropdownMenuItem(text = { Text("暂无时长记录", color = TxtT) }, onClick = { showTimeLinker = false }, enabled = false)
                            } else {
                                allTimeRecords.forEach { record ->
                                    val isChecked = record.id in linkedRecordIds
                                    DropdownMenuItem(
                                        text = {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = isChecked, onCheckedChange = { if (it) linkedRecordIds = linkedRecordIds + record.id else linkedRecordIds = linkedRecordIds - record.id }, modifier = Modifier.size(24.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Column {
                                                    Text("${DateTimeUtils.formatTimestamp(record.startTime)} - ${DateTimeUtils.formatTimestamp(record.endTime)}", style = MaterialTheme.typography.bodySmall)
                                                    Text(DateTimeUtils.formatDuration(record.durationMinutes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                                }
                                            }
                                        },
                                        onClick = { if (record.id in linkedRecordIds) linkedRecordIds = linkedRecordIds - record.id else linkedRecordIds = linkedRecordIds + record.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(BorderC))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                RichTextEditor(state = editorState, initialContent = initialHtml, onContentChanged = { content = it; editorState.queryFormatState() }, onFormatChanged = { parseFmt(it) }, onTap = { onEditorTap() }, onRequestFocus = { panelState = PanelState.TYPING; activeToolCategory = null; editorState.focusEditor() }, modifier = Modifier.fillMaxSize())
            }
            val targetH = when { panelState == PanelState.TOOLS -> maxOf(imeHeight, 220.dp); lockHeight -> maxOf(imeHeight, 220.dp); else -> imeHeight }
            val bottomH by animateDpAsState(targetH, tween(100), label = "bh")
            val im = panelState == PanelState.TOOLS
            Surface(Modifier.fillMaxWidth(), color = BarBg) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Ci(Icons.Default.TextFields, im && activeToolCategory == ToolbarCategory.TEXT) { onCategoryTap(ToolbarCategory.TEXT) }
                        Ci(Icons.AutoMirrored.Filled.FormatListBulleted, im && activeToolCategory == ToolbarCategory.LIST) { onCategoryTap(ToolbarCategory.LIST) }
                        Ci(Icons.Default.AlignHorizontalCenter, im && activeToolCategory == ToolbarCategory.ALIGN) { onCategoryTap(ToolbarCategory.ALIGN) }
                        Ci(Icons.Default.Image, im && activeToolCategory == ToolbarCategory.IMAGE) { onCategoryTap(ToolbarCategory.IMAGE) }
                    }
                    val show = im && activeToolCategory != null
                    Box(Modifier.fillMaxWidth().height(bottomH)) { if (show) Pnl(activeToolCategory!!, editorState, fmtB, fmti, fmtU, fmtS, fmtAL, fmtAC, fmtAR, fmtUL, fmtOL, fmtOLT, fmtSize, fmtColor, { galleryLauncher.launch("image/*") }, { launchCamera() }) }
                }
            }
        }
    }

    // ── 导出格式选择对话框 ──
    if (exportTargetFormat == "select") {
        AlertDialog(
            onDismissRequest = { exportTargetFormat = null },
            icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
            title = { Text("导出笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("选择导出格式：")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { startExport("html") }
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("HTML")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { startExport("doc") }
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Word")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { exportTargetFormat = null }) { Text("取消") }
            }
        )
    }
}

@Composable private fun Pnl(c: ToolbarCategory, es: RichTextEditorState, fB: Boolean, fI: Boolean, fU: Boolean, fS: Boolean, fAL: Boolean, fAC: Boolean, fAR: Boolean, fUL: Boolean, fOL: Boolean, fOLT: String, fSize: String, fColor: String, og: () -> Unit, oc: () -> Unit) { Box(if (c == ToolbarCategory.TEXT) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) { when (c) { ToolbarCategory.TEXT -> Tp(es, fB, fI, fU, fS, fSize, fColor); ToolbarCategory.LIST -> Lp(es, fUL, fOL, fOLT); ToolbarCategory.ALIGN -> Ap(es, fAL, fAC, fAR); ToolbarCategory.IMAGE -> Ip(og, oc) } } }

private val BarBg @Composable get() = Color(0xFFEBF0FD); private val Surf @Composable get() = Color(0xFFFFFFFF)
private val BorderC @Composable get() = Color(0xFFE7EDF5); private val Pri @Composable get() = Color(0xFF2D8CFF)
private val PriL @Composable get() = Color(0xFFEAF4FF); private val TxtP @Composable get() = Color(0xFF1F2937)
private val TxtS @Composable get() = Color(0xFF6B7280); private val TxtT @Composable get() = Color(0xFF9AA4B2)

@Composable private fun Tp(es: RichTextEditorState, fB: Boolean, fI: Boolean, fU: Boolean, fS: Boolean, fSize: String, fColor: String) { Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Crd(Modifier.weight(1f)) { Row(Modifier.fillMaxSize()) { Tb("B", modifier = Modifier.weight(1f).fillMaxHeight(), bold = true, active = fB) { es.applyFormat("bold") }; Dv(); Tb("I", modifier = Modifier.weight(1f).fillMaxHeight(), italic = true, active = fI) { es.applyFormat("italic") }; Dv(); Tb("U", modifier = Modifier.weight(1f).fillMaxHeight(), underline = true, active = fU) { es.applyFormat("underline") }; Dv(); Tb("S", modifier = Modifier.weight(1f).fillMaxHeight(), strike = true, active = fS) { es.applyFormat("strikeThrough") } } }; Crd(Modifier.weight(1f)) { Fs(es, fSize) }; Crd(Modifier.weight(1f)) { Cw(es, fColor) } } }
@Composable private fun Dv() { Box(Modifier.width(1.dp).fillMaxHeight().background(BorderC)) }
@Composable private fun Crd(modifier: Modifier = Modifier, content: @Composable () -> Unit) { Surface(shape = RoundedCornerShape(12.dp), color = Surf, shadowElevation = 2.dp, tonalElevation = 2.dp, modifier = modifier, content = content) }
@Composable private fun Fs(es: RichTextEditorState, fSize: String) { val items = listOf("8" to "8px","10" to "10px","12" to "12px","14" to "14px","16" to "16px","18" to "18px","20" to "20px","24" to "24px","28" to "28px","36" to "36px","48" to "48px"); val sel = items.indexOfFirst { it.second == fSize }.let { if (it >= 0) it else 4 }; Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 4.dp, bottom = 4.dp)) { Text("字号", style = MaterialTheme.typography.labelMedium, color = TxtT); Spacer(Modifier.height(4.dp)); LazyRow(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(items.size) { i -> val isSel = i == sel; Surface(onClick = { es.applyFormat("fontSize", items[i].second) }, shape = RoundedCornerShape(8.dp), color = if (isSel) PriL else Color.Transparent) { Text(items[i].first, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) Pri else TxtS, fontSize = 13.sp) } } } } }
@Composable private fun Cw(es: RichTextEditorState, fColor: String) { val cols = listOf("#FFFFFF","#CCCCCC","#888888","#444444","#000000","#FF4444","#FF8800","#FFCC00","#44CC44","#4488FF","#AA44FF","#FF44AA"); val sel = try { val hex = if (fColor.startsWith("rgb")) { val p = fColor.removePrefix("rgb(").removeSuffix(")").split(",").map { it.trim().toInt() }; java.lang.String.format("#%02X%02X%02X", p[0], p[1], p[2]) } else fColor; cols.indexOfFirst { it.equals(hex, ignoreCase = true) }.let { if (it >= 0) it else 0 } } catch (_: Exception) { 0 }; Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 4.dp, bottom = 4.dp)) { Text("颜色", style = MaterialTheme.typography.labelMedium, color = TxtT); Spacer(Modifier.height(4.dp)); LazyRow(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(cols.size) { i -> val isSel = i == sel; Surface(onClick = { es.applyFormat("foreColor", cols[i]) }, shape = CircleShape, modifier = Modifier.size(if (isSel) 28.dp else 24.dp), border = if (isSel) BorderStroke(2.dp, Pri) else null, color = Color(0xFFF0F0F0)) { Box(Modifier.padding(4.dp).fillMaxSize()) { Surface(shape = CircleShape, modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(cols[i])))) } } } } } } }
@Composable private fun Lp(es: RichTextEditorState, fUL: Boolean, fOL: Boolean, fOLT: String) { Crd(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) { Column { Row(Modifier.height(54.dp).fillMaxWidth()) { Lc(Icons.AutoMirrored.Filled.FormatListBulleted, null, null, Modifier.weight(1f), fUL) { es.applyFormat("insertUnorderedList") }; Vd(); Lc(null, "1.", null, Modifier.weight(1f), fOL && fOLT == "1") { es.evalJs("changeOrderedListType('1')") } }; Box(Modifier.fillMaxWidth().height(1.dp).background(BorderC)); Row(Modifier.height(54.dp).fillMaxWidth()) { Lc(null, "a.", null, Modifier.weight(1f), fOL && fOLT == "a") { es.evalJs("changeOrderedListType('a')") }; Vd(); Lc(null, "A.", null, Modifier.weight(1f), fOL && fOLT == "A") { es.evalJs("changeOrderedListType('A')") } } } } }
@Composable private fun Vd() { Box(Modifier.fillMaxHeight().width(1.dp).background(BorderC)) }
@Composable private fun Lc(icon: ImageVector? = null, text: String? = null, label: String? = null, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) { val bg by animateColorAsState(if (active) PriL else Color.Transparent, tween(80), label = "lcBg"); Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).then(modifier)) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = TxtP); else Text(text ?: "?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TxtP); if (label != null) { Spacer(Modifier.height(2.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = TxtT) } } } }
@Composable private fun Ap(es: RichTextEditorState, fAL: Boolean, fAC: Boolean, fAR: Boolean) { Crd(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) { Row(Modifier.height(48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { Ab(Icons.AutoMirrored.Filled.AlignHorizontalLeft, null, Modifier.weight(1f), fAL) { es.evalJs("applyAlignment('left')") }; Dv(); Ab(Icons.Default.AlignHorizontalCenter, null, Modifier.weight(1f), fAC) { es.evalJs("applyAlignment('center')") }; Dv(); Ab(Icons.AutoMirrored.Filled.AlignHorizontalRight, null, Modifier.weight(1f), fAR) { es.evalJs("applyAlignment('right')") } } } }
@Composable private fun Ab(icon: ImageVector, label: String? = null, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) { val bg by animateColorAsState(if (active) PriL else Color.Transparent, tween(80), label = "abBg"); val tint by animateColorAsState(if (active) Pri else TxtS, tween(80), label = "abTint"); Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).then(modifier)) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, Modifier.size(20.dp), tint = tint); if (label != null) { Spacer(Modifier.height(2.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = TxtT) } } } }
@Composable private fun Ip(og: () -> Unit, oc: () -> Unit) { Crd(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) { Row(Modifier.height(54.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { Lc(Icons.Default.PhotoLibrary, null, "相册", Modifier.weight(1f)) { og() }; Vd(); Lc(Icons.Default.CameraAlt, null, "相机", Modifier.weight(1f)) { oc() } } } }
@Composable private fun Tb(text: String? = null, icon: ImageVector? = null, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, strike: Boolean = false, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) { val fw = if (bold) FontWeight.ExtraBold else FontWeight.Medium; val fs = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null; val deco = when { underline -> androidx.compose.ui.text.style.TextDecoration.Underline; strike -> androidx.compose.ui.text.style.TextDecoration.LineThrough; else -> null }; val bg by animateColorAsState(if (active) PriL else Color.Transparent, tween(80), label = "tbBg"); Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).then(modifier), contentAlignment = Alignment.Center) { if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = TxtP); else Text(text ?: "", fontWeight = fw, fontStyle = fs, textDecoration = deco, fontSize = 14.sp, color = TxtP) } }
@Composable private fun Ci(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) { val tint by animateColorAsState(if (isActive) Pri else TxtS, tween(80), label = "ciTint"); Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).padding(10.dp)) { Icon(icon, null, Modifier.size(22.dp), tint = tint) } }

private fun uriToBase64(context: Context, uri: Uri): String? { return try { val i = context.contentResolver.openInputStream(uri) ?: return null; val bytes = i.readBytes(); i.close(); val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o); val s = maxOf((o.outWidth / 1920f).let { if (it > 1) it.toInt() else 1 }, (o.outHeight / 1920f).let { if (it > 1) it.toInt() else 1 }); BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })?.let { b -> val out = java.io.ByteArrayOutputStream(); b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out); val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP); b.recycle(); out.close(); "data:image/jpeg;base64,$b64" } ?: run { "data:${context.contentResolver.getType(uri) ?: "image/jpeg"};base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}" } } catch (_: Exception) { null } }
