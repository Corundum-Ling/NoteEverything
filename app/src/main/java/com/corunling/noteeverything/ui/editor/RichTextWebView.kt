// ============================================================
// RichTextWebView.kt — Compose 富文本编辑器组件
// ============================================================
// 使用 AndroidView 包装 WebView，加载本地 assets/rich_editor.html，
// 通过 JavaScript Bridge 实现格式化、图片插入等功能。
//
// 用法：
//   val editorState = rememberRichTextEditorState()
//   RichTextEditor(
//       state = editorState,
//       modifier = Modifier.weight(1f)
//   )
//   // 调用格式化：editorState.applyFormat("bold")
//   // 获取内容：editorState.requestContent { html -> ... }

package com.corunling.noteeverything.ui.editor

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 富文本编辑器状态控制器。
 * 通过 rememberRichTextEditorState() 创建，用于与 WebView 编辑器交互。
 */
class RichTextEditorState {

    /** WebView 实例引用（初始化后赋值） */
    internal var webView: WebView? = null

    /** 页面是否已加载完成（Compose 状态，变更会触发重组） */
    internal var isPageLoaded by mutableStateOf(false)

    /** 待页面加载完成后执行的任务队列 */
    private val pendingTasks = mutableListOf<() -> Unit>()

    internal fun onPageLoaded() {
        isPageLoaded = true
        pendingTasks.forEach { it() }
        pendingTasks.clear()
    }

    /** 在页面加载完成后执行任务，若已加载则立即执行 */
    private fun executeWhenReady(task: () -> Unit) {
        if (isPageLoaded) {
            task()
        } else {
            pendingTasks.add(task)
        }
    }

    /** 执行格式化命令 */
    fun applyFormat(cmd: String, value: String? = null) {
        executeWhenReady {
            val js = if (value != null) {
                "execFormat('$cmd', '${value.replace("'", "\\'")}')"
            } else {
                "execFormat('$cmd')"
            }
            webView?.evaluateJavascript(js, null)
        }
    }

    /** 执行任意 JS（用于自定义格式化） */
    fun evalJs(js: String) {
        executeWhenReady {
            webView?.evaluateJavascript(js, null)
        }
    }

    /** 插入 Base64 图片 */
    fun insertImageBase64(base64: String, fileName: String? = null) {
        executeWhenReady {
            val safeFileName = fileName?.replace("'", "\\'") ?: ""
            val js = "insertImageBase64('$base64', '$safeFileName')"
            webView?.evaluateJavascript(js, null)
        }
    }

    /** 获取编辑器 HTML 内容，通过回调返回 */
    fun requestContent(onResult: (String) -> Unit) {
        executeWhenReady {
            webView?.evaluateJavascript("getContent()") { raw ->
                // evaluateJavascript 返回 JSON 编码的值，用 JSONTokener 标准解析
                val result = if (raw != null && raw.startsWith("\"")) {
                    try {
                        org.json.JSONTokener(raw).nextValue() as? String ?: ""
                    } catch (e: Exception) {
                        // 降级：手动去外引 + Java unescape
                        raw.substring(1, raw.length - 1)
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                    }
                } else {
                    raw ?: ""
                }
                onResult(result)
            }
        }
    }

    /** 设置编辑器 HTML 内容（通过 JSON 传递，避免 JS 转义问题） */
    fun setContent(html: String) {
        executeWhenReady {
            val json = org.json.JSONObject.quote(html)
            webView?.evaluateJavascript("setContentFromJson($json)", null)
        }
    }

    /** 查询格式状态 */
    fun queryFormatState() {
        executeWhenReady {
            webView?.evaluateJavascript("queryFormatState()", null)
        }
    }

    /** 设置编辑器只读/可编辑 */
    fun setReadOnly(readOnly: Boolean) {
        executeWhenReady {
            webView?.evaluateJavascript("setReadOnly($readOnly)", null)
        }
    }

    /** 聚焦编辑器（用于唤起键盘） */
    fun focusEditor() {
        executeWhenReady {
            webView?.requestFocus()
            webView?.evaluateJavascript("editor.focus()", null)
        }
    }
}

/**
 * 创建并记住 [RichTextEditorState]。
 */
@Composable
fun rememberRichTextEditorState(): RichTextEditorState {
    return remember { RichTextEditorState() }
}

/**
 * 富文本编辑器 Composable。
 *
 * @param state 状态控制器，通过 [rememberRichTextEditorState] 创建
 * @param initialContent 初始 HTML 内容（编辑已有笔记时传入）
 * @param onContentChanged 内容变更回调（用户打字时触发，含自动防抖）
 * @param modifier Modifier
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun RichTextEditor(
    state: RichTextEditorState = rememberRichTextEditorState(),
    initialContent: String = "",
    onContentChanged: (String) -> Unit = {},
    onFormatChanged: ((String) -> Unit)? = null,
    onTap: () -> Unit = {},
    onRequestFocus: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 仅首次设置初始内容（防止 auto-save 更新 initialHtml 后重置编辑器）
    val initialContentSet = remember { mutableStateOf(false) }
    LaunchedEffect(state.isPageLoaded, initialContent) {
        if (!initialContentSet.value && state.isPageLoaded) {
            if (initialContent.isNotEmpty()) state.setContent(initialContent)
            initialContentSet.value = true
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // ── 基础配置 ──
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true

                // 隐藏 WebView 滚动条（由外层的 LazyColumn/Column 管理滚动）
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                // ── 触摸收起面板 ──
                setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_DOWN) onTap(); false }

                // ── JS Bridge ──
                val bridge = RichEditorBridge(
                    onContentChanged = { html -> onContentChanged(html) },
                    onFormatState = { json -> onFormatChanged?.invoke(json) },
                    onRequestFocus = onRequestFocus?.let { { it() } }
                )
                addJavascriptInterface(bridge, "RichEditorBridge")

                // ── WebViewClient ──
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        state.onPageLoaded()
                    }
                }

                webChromeClient = WebChromeClient()

                // ── 加载编辑器页面 ──
                loadUrl("file:///android_asset/rich_editor.html")

                // 保存 WebView 引用
                state.webView = this
            }
        },
        update = { _ -> /* 初始内容设置已交由 LaunchedEffect 处理 */ }
    )
}
