// ============================================================
// Routes.kt — 导航路由定义
// ============================================================
// sealed class 的好处：所有路由集中管理，不会写错字符串。
// 每个路由定义自己的路径模板和参数。
//
// 导航参数传递：
// - SoftwareDetail：路径包含 {softwareId}，通过 NavArgument 自动解析
// - NoteEditor：可选参数 softwareId 和 noteId 用查询参数传递

package com.corunling.noteeverything.ui.navigation

sealed class Routes(val route: String) {
    // 主页（三 Tab）
    object Main : Routes("main")

    // 设置页
    object Settings : Routes("settings")

    // 软件详情页：路径参数 softwareId
    object SoftwareDetail : Routes("software/{softwareId}") {
        fun create(softwareId: Long) = "software/$softwareId"
    }

    // 笔记编辑页：可选查询参数
    // - softwareId：新建时预设关联的软件（不传 = 自由随笔）
    // - noteId：编辑已有笔记时传入
    object NoteEditor : Routes("note/editor?softwareId={softwareId}&noteId={noteId}") {
        fun create(softwareId: Long? = null, noteId: Long? = null): String {
            val parts = mutableListOf("note/editor")
            val params = mutableListOf<String>()
            if (softwareId != null) params.add("softwareId=$softwareId")
            if (noteId != null) params.add("noteId=$noteId")
            if (params.isNotEmpty()) parts.add(params.joinToString("&"))
            return parts.joinToString("?")
        }
    }
}
