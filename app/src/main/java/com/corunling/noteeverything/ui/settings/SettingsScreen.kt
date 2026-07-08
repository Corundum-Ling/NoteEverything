// ============================================================
// SettingsScreen.kt — 设置页面
// ============================================================
// 包含：
//   1. 深色模式开关（通过 SettingsManager 持久化）
//   2. 清除所有数据（危险操作，需确认）
//   3. 关于信息
//
// 数据流：
//   SettingsManager.settingsFlow → collectAsState → UI
//   UI 交互 → SettingsManager.setDarkMode() / database.clearAllTables()

package com.corunling.noteeverything.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.corunling.noteeverything.App
import com.corunling.noteeverything.util.AppSettings
import com.corunling.noteeverything.util.SettingsManager
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    app: App,
    navController: NavHostController
) {
    val context = LocalContext.current
    val repository = app.repository
    val settings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 清除数据确认对话框
    var showClearDialog by remember { mutableStateOf(false) }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("清除所有数据") },
            text = {
                Text(
                    "确定要清除所有数据吗？\n\n" +
                    "这将删除所有软件条目、笔记和时长记录。\n" +
                    "此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            try {
                                app.database.clearAllTables()
                                snackbarHostState.showSnackbar("已清除所有数据")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("清除失败：${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ═══════════════════════════════════════
            // 区域一：主题设置
            // ═══════════════════════════════════════
            Text(
                text = "主题设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "使用深色背景减少眼部疲劳",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.darkMode,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setDarkMode(enabled) }
                        }
                    )
                }
            }

            // ═══════════════════════════════════════
            // 区域二：统计图显示
            // ═══════════════════════════════════════
            Text(
                text = "统计图显示",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingSwitch(
                        label = "时长趋势图",
                        description = "折线+面积图展示每日时长变化",
                        checked = settings.showLineChart,
                        onCheckedChange = { scope.launch { settingsManager.setShowLineChart(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingSwitch(
                        label = "分类分布图",
                        description = "环形图展示各分类占比",
                        checked = settings.showDonutChart,
                        onCheckedChange = { scope.launch { settingsManager.setShowDonutChart(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingSwitch(
                        label = "软件排行",
                        description = "各软件时长排行列表",
                        checked = settings.showRanking,
                        onCheckedChange = { scope.launch { settingsManager.setShowRanking(it) } }
                    )
                }
            }

            // ═══════════════════════════════════════
            // 区域三：自动时长同步
            // ═══════════════════════════════════════
            Text(
                text = "自动时长同步",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    // 权限状态
                    val hasPermission = app.autoTracker.checkPermission()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("使用情况访问权限", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (hasPermission) "已授权" else "未授权 — 需前往系统设置开启",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasPermission) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!hasPermission) {
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("去设置")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 自动同步开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启动时自动同步", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "应用启动时自动获取昨日使用数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoTrackEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsManager.setAutoTrackEnabled(enabled) }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 手动同步按钮 + 上次同步时间
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("手动同步", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (settings.lastAutoSyncDate.isNotEmpty())
                                        "上次同步：${settings.lastAutoSyncDate}"
                                    else "尚未同步过",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            var isSyncing by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    if (!hasPermission) {
                                        Toast.makeText(context, "请先授予使用情况访问权限", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isSyncing = true
                                    scope.launch {
                                        try {
                                            val count = app.autoTracker.syncRecent(repository, settings.lastAutoSyncDate)
                                            app.settingsManager.setLastAutoSyncDate(DateTimeUtils.today())
                                            Toast.makeText(context, "同步完成：共 $count 条记录", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "同步失败：${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSyncing = false
                                        }
                                    }
                                },
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(if (isSyncing) "同步中..." else "立即同步")
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // 区域四：时长合并阈值
            // ═══════════════════════════════════════
            Text(
                text = "时长合并阈值",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "自动同步时，同一软件一天内间隔 ${settings.mergeThresholdMinutes} 分钟的时段合并显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val options = listOf(30, 60, 90, 120, 180, 300)
                        options.forEach { opt ->
                            val isSel = settings.mergeThresholdMinutes == opt
                            FilterChip(
                                selected = isSel,
                                onClick = { scope.launch { settingsManager.setMergeThreshold(opt) } },
                                label = { Text(if (opt >= 60) "${opt / 60}h" else "${opt}min", fontSize = 12.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ═══════════════════════════════════════
            // 区域五：数据管理
            // ═══════════════════════════════════════
            Text(
                text = "数据管理",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "清除所有数据",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "删除所有软件条目、笔记和时长记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清除所有数据")
                    }
                }
            }

            // ═══════════════════════════════════════
            // 区域六：关于
            // ═══════════════════════════════════════
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NoteEverything", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "版本 v1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "以软件/游戏为锚点的个人记录工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Kotlin + Jetpack Compose + Room",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
