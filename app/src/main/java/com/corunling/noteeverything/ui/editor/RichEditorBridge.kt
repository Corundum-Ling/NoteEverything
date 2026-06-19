// ============================================================
// RichEditorBridge.kt — WebView → Compose 通信桥接
// ============================================================
// 在 HTML 页面中通过 window.RichEditorBridge.onContentChanged(html)
// 调用此接口，将编辑器内容传递给 Compose 层。
//
// 关键：@JavascriptInterface 方法在后台线程调用，
// 需要 post 到主线程再更新 Compose 状态。

package com.corunling.noteeverything.ui.editor

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class RichEditorBridge(
    private val onContentChanged: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onContentChanged(html: String) {
        mainHandler.post {
            onContentChanged(html)
        }
    }
}
