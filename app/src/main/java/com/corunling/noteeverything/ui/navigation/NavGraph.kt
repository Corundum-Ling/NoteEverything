// ============================================================
// NavGraph.kt — 导航图（页面路由表）
// ============================================================
// NavHost：Compose 的页面容器，管理页面栈。
// 每个 composable() 调用注册一条路由。
//
// 页面切换流程：
// MainScreen → 点击软件 → navController.navigate("software/123")
//            → NavHost 匹配到 SoftwareDetail 路由
//            → 进入 SoftwareDetailScreen
//
// navArgument 用于从 URL 中提取参数，如 "software/{softwareId}"
// 中的 softwareId 会被自动解析为 Long 类型传给页面。

package com.corunling.noteeverything.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.ui.MainScreen
import com.corunling.noteeverything.ui.software.SoftwareDetailScreen
import com.corunling.noteeverything.ui.note.NoteEditorScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    repository: NoteEverythingRepository
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Main.route
    ) {
        // 主页（三 Tab）
        composable(Routes.Main.route) {
            MainScreen(
                repository = repository,
                navController = navController
            )
        }

        // 软件详情页
        composable(
            route = Routes.SoftwareDetail.route,
            arguments = listOf(
                navArgument("softwareId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val softwareId = backStackEntry.arguments?.getLong("softwareId") ?: return@composable
            SoftwareDetailScreen(
                softwareId = softwareId,
                repository = repository,
                navController = navController
            )
        }

        // 笔记编辑页
        composable(
            route = Routes.NoteEditor.route,
            arguments = listOf(
                navArgument("softwareId") {
                    type = NavType.LongType
                    defaultValue = -1L  // -1 表示"未传入"
                },
                navArgument("noteId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val softwareId = backStackEntry.arguments
                ?.getLong("softwareId")
                ?.takeIf { it != -1L }
            val noteId = backStackEntry.arguments
                ?.getLong("noteId")
                ?.takeIf { it != -1L }
            NoteEditorScreen(
                softwareId = softwareId,
                noteId = noteId,
                repository = repository,
                navController = navController
            )
        }
    }
}
