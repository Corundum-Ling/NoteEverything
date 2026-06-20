// ============================================================
// MainActivity.kt — 唯一的 Activity，Compose UI 的宿主
// ============================================================
// 在 Jetpack Compose 架构中，Activity 只做一件事：
// 调用 setContent {} 来加载 Composable 组件树。
// 所有 UI 逻辑都在 Composable 函数里，Activity 本身非常薄。
//
// 关键概念：
// - setContent {}：告诉 Android "这个 Activity 的 UI 由 Compose 来画"
// - rememberNavController()：创建一个导航控制器，管理页面跳转
// - Surface：Compose 的"画布"，提供背景色和主题
//
// 主题设置：
// - 从 App.settingsManager.settingsFlow 读取 darkMode 设置
// - 将 darkTheme 参数传递给 NoteEverythingTheme
// - 状态栏适配：深色模式用浅色图标，浅色模式用深色图标

package com.corunling.noteeverything

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.corunling.noteeverything.ui.navigation.NavGraph
import com.corunling.noteeverything.ui.theme.NoteEverythingTheme
import com.corunling.noteeverything.util.AppSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = application as App
            val settings by app.settingsManager.settingsFlow.collectAsState(initial = AppSettings())

            // 根据主题设置状态栏图标颜色
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !settings.darkMode
            }

            NoteEverythingTheme(darkTheme = settings.darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        repository = app.repository
                    )
                }
            }
        }
    }
}
