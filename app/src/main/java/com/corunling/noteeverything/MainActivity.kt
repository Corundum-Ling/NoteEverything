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

package com.corunling.noteeverything

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.corunling.noteeverything.ui.navigation.NavGraph
import com.corunling.noteeverything.ui.theme.NoteEverythingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // NoteEverythingTheme：应用主题色、字体等样式
            NoteEverythingTheme {
                // Surface：一个带背景色的容器，相当于"画纸"
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 获取 Application 实例 → 拿到 repository
                    val app = application as App
                    // 创建导航控制器
                    val navController = rememberNavController()
                    // NavGraph：定义所有页面的路由表
                    NavGraph(
                        navController = navController,
                        repository = app.repository
                    )
                }
            }
        }
    }
}
