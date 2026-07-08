// ============================================================
// MainScreen.kt --- 主页面：三 Tab 导航 + 选择模式
// ============================================================

package com.corunling.noteeverything.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.corunling.noteeverything.App
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.auto.InstalledApp
import com.corunling.noteeverything.data.auto.InstalledAppFetcher
import com.corunling.noteeverything.util.DateTimeUtils
import com.corunling.noteeverything.ui.components.SelectionFloatingCard
import com.corunling.noteeverything.ui.navigation.Routes
import com.corunling.noteeverything.ui.note.NoteListScreen
import com.corunling.noteeverything.ui.software.SoftwareListScreen
import com.corunling.noteeverything.ui.time.TimeOverviewScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class MainTab(val label: String) {
    SOFTWARE("软件"),
    NOTES("笔记"),
    TIME("统计")
}

typealias SelectionCallback = (Boolean, Int, Int) -> Unit
typealias ActionRegistrar = (() -> Unit, () -> Unit) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: NoteEverythingRepository,
    navController: NavHostController
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.SOFTWARE) }

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectionCount by rememberSaveable { mutableIntStateOf(0) }
    var selectionTotal by rememberSaveable { mutableIntStateOf(0) }
    var selectAllAction by remember { mutableStateOf<() -> Unit>({}) }
    var clearSelectionAction by remember { mutableStateOf<() -> Unit>({}) }

    val onRegisterActions: ActionRegistrar = { selectAll, clearSelection ->
        selectAllAction = selectAll
        clearSelectionAction = clearSelection
    }

    var pendingAction by remember { mutableStateOf<String?>(null) }
    fun consumeAction() { pendingAction = null }

    // ═══ App 启动时自动同步 ═══
    val mainContext = LocalContext.current
    val mainApp = mainContext.applicationContext as App
    LaunchedEffect(Unit) {
        val settings = mainApp.settingsManager.settingsFlow.first()
        if (settings.autoTrackEnabled) {
            mainApp.autoTracker.syncRecent(mainApp.repository, settings.lastAutoSyncDate)
            mainApp.settingsManager.setLastAutoSyncDate(DateTimeUtils.today())
        }
    }

    BackHandler(enabled = selectionMode) {
        clearSelectionAction()
        selectionMode = false
        selectionCount = 0
        selectionTotal = 0
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (selectionMode) {
                CenterAlignedTopAppBar(
                    title = { Text(if (selectionCount > 0) "已选 $selectionCount 项" else "选择项目") },
                    navigationIcon = {
                        TextButton(onClick = { selectAllAction() }) {
                            Text(if (selectionCount >= selectionTotal) "全不选" else "全选")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            clearSelectionAction()
                            selectionMode = false
                            selectionCount = 0
                            selectionTotal = 0
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("NoteEverything") },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.Settings.route) }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = !selectionMode,
                    enter = slideInVertically(animationSpec = tween(200)) { it },
                    exit = slideOutVertically(animationSpec = tween(200)) { it }
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = NavigationBarDefaults.Elevation
                        ) {
                            MainTab.entries.forEach { tab ->
                                val isSelected = selectedTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { selectedTab = tab },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Spacer(Modifier.height(6.dp))
                                        Icon(
                                            imageVector = when (tab) {
                                                MainTab.SOFTWARE -> Icons.Default.Apps
                                                MainTab.NOTES -> Icons.Default.EditNote
                                                MainTab.TIME -> Icons.Default.BarChart
                                            },
                                            contentDescription = tab.label,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        // 指示器横线覆盖层（补偿 NavigationBar 默认 12dp 左右内边距）
                        val tabCount = MainTab.entries.size
                        val selectedIndex = selectedTab.ordinal
                        BoxWithConstraints(
                            modifier = Modifier.matchParentSize().padding(bottom = 48.dp)
                        ) {
                            if (maxWidth != 0.dp) {
                                val lineWidth = 70.dp
                                val gapSpacing = 8.dp
                                val itemWidth = (maxWidth - gapSpacing * (tabCount - 1)) / tabCount
                                val iconCenterX = itemWidth * (selectedIndex + 0.5f) + gapSpacing * selectedIndex
                                val targetOffset = iconCenterX - lineWidth / 2
                                val animatedOffset by animateDpAsState(
                                    targetValue = targetOffset,
                                    animationSpec = tween(200),
                                    label = "navLineOffset"
                                )
                                Box(
                                    modifier = Modifier
                                        .offset(x = animatedOffset)
                                        .width(lineWidth)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            val fabVisible = selectedTab != MainTab.TIME && !selectionMode
            // 记住上次可见时的 Tab，退出动画期间保持内容不消失
            var fabContent by remember { mutableStateOf(MainTab.SOFTWARE) }
            if (fabVisible) fabContent = selectedTab
            val fabProgress by animateFloatAsState(
                targetValue = if (fabVisible) 1f else 0f,
                animationSpec = tween(200),
                label = "fabProgress"
            )
            if (fabProgress > 0f || fabVisible) {
                Box(modifier = Modifier.graphicsLayer {
                    scaleX = fabProgress
                    scaleY = fabProgress
                    alpha = fabProgress
                }) {
                    when (fabContent) {
                        MainTab.SOFTWARE -> {
                            var showAddSoftware by remember { mutableStateOf(false) }
                            Box {
                                FloatingActionButton(
                                    onClick = { showAddSoftware = true },
                                    shape = MaterialTheme.shapes.medium,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "添加软件")
                                }
                                if (showAddSoftware) {
                                    AddSoftwareDialog(
                                        repository = repository,
                                        onDismiss = { showAddSoftware = false }
                                    )
                                }
                            }
                        }
                        MainTab.NOTES -> {
                            FloatingActionButton(
                                onClick = { navController.navigate(Routes.NoteEditor.create()) },
                                shape = MaterialTheme.shapes.medium,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "写笔记")
                            }
                        }
                        else -> { }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                // 统计页不做任何动画（还没做完，避免干扰）
                if (targetState == MainTab.TIME || initialState == MainTab.TIME) {
                    fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                } else {
                    (slideInVertically(
                        animationSpec = tween(200),
                        initialOffsetY = { it / 40 }
                    ) + fadeIn(animationSpec = tween(200)))
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                }
            },
            label = "tabContent"
        ) { tab ->
            when (tab) {
                MainTab.SOFTWARE -> SoftwareListScreen(
                    repository = repository,
                    onSoftwareClick = { softwareId ->
                        navController.navigate(Routes.SoftwareDetail.create(softwareId))
                    },
                    selectionMode = selectionMode,
                    onSelectionChanged = { mode, count, total ->
                        selectionMode = mode
                        selectionCount = count
                        selectionTotal = total
                    },
                    onRegisterActions = onRegisterActions,
                    pendingAction = pendingAction,
                    onActionConsumed = { consumeAction() }
                )
                MainTab.NOTES -> NoteListScreen(
                    repository = repository,
                    onNoteClick = { noteId ->
                        navController.navigate(Routes.NoteEditor.create(noteId = noteId))
                    },
                    selectionMode = selectionMode,
                    onSelectionChanged = { mode, count, total ->
                        selectionMode = mode
                        selectionCount = count
                        selectionTotal = total
                    },
                    onRegisterActions = onRegisterActions,
                    pendingAction = pendingAction,
                    onActionConsumed = { consumeAction() }
                )
                MainTab.TIME -> TimeOverviewScreen(repository = repository)
            }
        }
    }

    // ── 浮动操作卡片（独立覆盖层，与 Scaffold 布局无关）──
    AnimatedVisibility(
        visible = selectionMode && selectedTab != MainTab.TIME,
        enter = slideInVertically(animationSpec = tween(250)) { it + 200 } + fadeIn(tween(150)),
        exit = slideOutVertically(animationSpec = tween(250)) { it + 200 } + fadeOut(tween(150)),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
    ) {
        SelectionFloatingCard(
            hasSelection = selectionCount > 0,
            showExport = selectedTab == MainTab.NOTES,
            onPin = { pendingAction = "pin" },
            onLock = { pendingAction = "lock" },
            onDelete = { pendingAction = "delete" },
            onTags = { pendingAction = "tags" },
            onExport = { pendingAction = "export" }
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSoftwareDialog(
    repository: NoteEverythingRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val installedAppFetcher = remember { app.let { InstalledAppFetcher(it) } }

    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("PC") }
    var category by remember { mutableStateOf("游戏") }
    var trackMode by remember { mutableStateOf("manual") }
    var selectedPackageName by remember { mutableStateOf<String?>(null) }

    // 搜索联想状态（空列表 = 无结果，非 null 避免崩溃）
    var suggestions by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var noMatch by remember { mutableStateOf(false) }
    var excludePackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 加载已添加的包名（去重用）
    LaunchedEffect(Unit) {
        excludePackages = repository.getAllPackageNames().filterNotNull().toSet()
        installedAppFetcher.invalidateCache()
    }

    // 搜索联想的防抖
    LaunchedEffect(name, trackMode, platform) {
        if (platform == "Android" && trackMode == "auto" && name.length >= 2) {
            isSearching = true
            noMatch = false
            delay(200) // 200ms 防抖
            val results = installedAppFetcher.searchApps(name, excludePackages)
            suggestions = results
            noMatch = results.isEmpty()
            isSearching = false
            // 有结果时自动选中第一位
            if (results.isNotEmpty() && selectedPackageName == null) {
                selectedPackageName = results.first().packageName
            }
        } else {
            suggestions = emptyList()
            noMatch = false
            if (platform != "Android" || trackMode != "auto") {
                selectedPackageName = null
            }
        }
    }

    val isAutoAndroid = platform == "Android" && trackMode == "auto"
    val isAuto = trackMode != "manual"
    // Android auto 必须选联想结果才能添加；PC auto 只需有名字
    val canAdd = name.isNotBlank() && (!isAutoAndroid || (!noMatch && selectedPackageName != null))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加软件") },
        text = {
            Column {
                // ── 名称行 + Auto/Manual 下拉 ──
                if (platform == "Android" || platform == "PC") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                selectedPackageName = null
                            },
                            label = { Text("名称") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        var modeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modeExpanded,
                            onExpandedChange = { modeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = if (trackMode != "manual") "自动" else "手动",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("模式") },
                                modifier = Modifier.width(100.dp).menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded)
                                },
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = modeExpanded,
                                onDismissRequest = { modeExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("手动") },
                                    onClick = {
                                        trackMode = "manual"
                                        modeExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("自动") },
                                    onClick = {
                                        trackMode = if (platform == "Android") "auto" else "pc_sync"
                                        modeExpanded = false
                                        selectedPackageName = null
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── 搜索联想下拉 ──
                if (isAutoAndroid) {
                    Spacer(Modifier.height(2.dp))
                    if (suggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                                items(suggestions) { appInfo ->
                                    val isSelected = appInfo.packageName == selectedPackageName
                                    Surface(
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    name = appInfo.appName
                                                    selectedPackageName = appInfo.packageName
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                appInfo.appName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                appInfo.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (noMatch && name.length >= 2) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                "未匹配到应用，请更换关键词",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── 平台 ──
                var platformExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = platformExpanded,
                    onExpandedChange = { platformExpanded = it }
                ) {
                    OutlinedTextField(
                        value = platform,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("平台") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = platformExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = platformExpanded,
                        onDismissRequest = { platformExpanded = false }
                    ) {
                        listOf("PC", "Android", "iOS", "Switch", "Other").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    platform = p
                                    platformExpanded = false
                                    if (p != "Android") trackMode = "manual"
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── 分类 ──
                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        listOf("游戏", "工具", "学习", "其他").forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = { category = c; categoryExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        scope.launch {
                            // Android auto：自动取包名；PC auto：用户随便填名字
                            val finalPkg = if (isAutoAndroid) {
                                selectedPackageName ?: suggestions.firstOrNull()?.packageName
                            } else null
                            val finalName = if (isAutoAndroid && finalPkg != null && selectedPackageName == null) {
                                suggestions.firstOrNull()?.appName ?: name
                            } else name
                            val finalTrackMode = when {
                                isAutoAndroid -> "auto"
                                platform == "PC" && trackMode == "pc_sync" -> "pc_sync"
                                else -> "manual"
                            }
                            val newId = repository.createSoftware(
                                name = finalName,
                                platform = platform,
                                category = category,
                                packageName = finalPkg,
                                trackMode = finalTrackMode
                            )
                            // Android auto 创建后立即同步最近 7 天
                            if (isAutoAndroid && finalPkg != null) {
                                app.autoTracker.syncRange(repository, DateTimeUtils.today(), 7)
                            }
                            onDismiss()
                        }
                    }
                },
                enabled = canAdd
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
