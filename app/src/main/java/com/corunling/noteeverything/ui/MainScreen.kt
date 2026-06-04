// ============================================================
// MainScreen.kt — 主页面：三 Tab 导航 + FAB
// ============================================================
// Scaffold 是 Material3 的"页面脚手架"，提供：
// - topBar：顶部标题栏
// - bottomBar：底部导航栏
// - floatingActionButton：右下角浮动按钮
//
// 学习要点：
// - remember + mutableStateOf：Compose 的状态管理。
//   当 selectedTab 变化时，所有读取它的 Composable 会自动重组（重绘）。
// - when 表达式根据当前 Tab 切换显示内容。
// - FAB 的行为也根据当前 Tab 变化。

package com.corunling.noteeverything.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.ui.navigation.Routes
import com.corunling.noteeverything.ui.note.NoteListScreen
import com.corunling.noteeverything.ui.software.SoftwareListScreen
import com.corunling.noteeverything.ui.time.TimeOverviewScreen
import kotlinx.coroutines.launch

// 三个主 Tab 的枚举
enum class MainTab(val label: String) {
    SOFTWARE("记录"),
    NOTES("时间轴"),
    TIME("时长")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: NoteEverythingRepository,
    navController: NavHostController
) {
    var selectedTab by remember { mutableStateOf(MainTab.SOFTWARE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NoteEverything") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                    label = { Text("记录") },
                    selected = selectedTab == MainTab.SOFTWARE,
                    onClick = { selectedTab = MainTab.SOFTWARE }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    label = { Text("时间轴") },
                    selected = selectedTab == MainTab.NOTES,
                    onClick = { selectedTab = MainTab.NOTES }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    label = { Text("时长") },
                    selected = selectedTab == MainTab.TIME,
                    onClick = { selectedTab = MainTab.TIME }
                )
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                MainTab.SOFTWARE -> {
                    // "记录" Tab：可选择添加软件或写随笔
                    var showMenu by remember { mutableStateOf(false) }
                    var showAddSoftware by remember { mutableStateOf(false) }

                    Box {
                        FloatingActionButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加软件") },
                                onClick = {
                                    showMenu = false
                                    showAddSoftware = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Apps, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("写随笔") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Routes.NoteEditor.create())
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.EditNote, contentDescription = null)
                                }
                            )
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
                    // "时间轴" Tab：只读浏览，无 FAB
                }
                MainTab.TIME -> {
                    FloatingActionButton(onClick = { selectedTab = MainTab.SOFTWARE }) {
                        Icon(Icons.Default.Timer, contentDescription = "开始计时")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                MainTab.SOFTWARE -> SoftwareListScreen(
                    repository = repository,
                    onSoftwareClick = { softwareId ->
                        navController.navigate(Routes.SoftwareDetail.create(softwareId))
                    }
                )
                MainTab.NOTES -> NoteListScreen(
                    repository = repository,
                    onNoteClick = { noteId ->
                        navController.navigate(Routes.NoteEditor.create(noteId = noteId))
                    }
                )
                MainTab.TIME -> TimeOverviewScreen(repository = repository)
            }
        }
    }
}

// ─── 添加软件弹窗 ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSoftwareDialog(
    repository: NoteEverythingRepository,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("PC") }
    var category by remember { mutableStateOf("游戏") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加软件") },
        text = {
            Column {
                // 名称输入
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 平台选择下拉
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
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = platformExpanded)
                        },
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
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 分类选择下拉
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
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        listOf("游戏", "工具", "学习", "其他").forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    category = c
                                    categoryExpanded = false
                                }
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
                            repository.createSoftware(name, platform, category)
                        }
                        onDismiss()
                    }
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
