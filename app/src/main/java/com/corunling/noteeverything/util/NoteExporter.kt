// ============================================================
// NoteExporter.kt — 单条笔记导出工具
// ============================================================
// 支持导出为 HTML 和 Word 兼容格式（.doc）。
// 两者共享同一套 HTML 内容模板，Word 版本额外添加 mso 命名空间。
//
// 使用 SAF（Storage Access Framework）写入，
// 不需要 WRITE_EXTERNAL_STORAGE 权限。
//
// 图片处理：笔记内容中的 Base64 内联图片保持原样嵌入，
// 在 HTML 和 Word 中均可正常显示。

package com.corunling.noteeverything.util

import android.content.Context
import android.net.Uri

object NoteExporter {

    private const val HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>NoteEverything - 笔记导出</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif; max-width: 800px; margin: 0 auto; padding: 24px; color: #333; line-height: 1.8; background: #fff; }
.header { border-bottom: 2px solid #1A73E8; padding-bottom: 16px; margin-bottom: 24px; }
.header h1 { font-size: 24px; color: #1A73E8; margin: 0; }
.header .subtitle { font-size: 13px; color: #999; margin-top: 4px; }
.meta { background: #f5f5f5; padding: 12px 16px; border-radius: 8px; margin-bottom: 24px; font-size: 14px; color: #666; }
.meta p { margin: 4px 0; }
.content { font-size: 16px; }
.content img { max-width: 100%%; height: auto; border-radius: 4px; margin: 8px 0; }
.content p { margin: 8px 0; }
.content h1, .content h2, .content h3, .content h4 { margin: 16px 0 8px; }
.content ul, .content ol { margin: 8px 0; padding-left: 24px; }
.content blockquote { border-left: 3px solid #1A73E8; margin: 8px 0; padding: 8px 16px; background: #f8f9fa; color: #666; }
.content code { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 14px; }
.footer { margin-top: 32px; padding-top: 16px; border-top: 1px solid #eee; font-size: 12px; color: #ccc; text-align: center; }
</style>
</head>
<body>
<div class="header">
<h1>📝 笔记</h1>
<div class="subtitle">NoteEverything 导出</div>
</div>
<div class="meta">
<p>📅 时间：%s</p>
<p>🏷️ 关联：%s</p>
</div>
<div class="content">
%s
</div>
<div class="footer">由 NoteEverything 生成</div>
</body>
</html>"""

    /**
     * 导出为 HTML 文件。
     * @param context Android 上下文
     * @param noteContent 笔记 HTML 正文（来自编辑器的 innerHTML）
     * @param noteTimestamp 笔记时间戳
     * @param softwareName 关联软件名（null = 自由随笔）
     * @param outputUri SAF 返回的输出 URI
     * @return 成功时返回 Unit
     */
    suspend fun exportAsHtml(
        context: Context,
        noteContent: String,
        noteTimestamp: Long,
        softwareName: String?,
        outputUri: Uri
    ): Result<Unit> = runCatching {
        val dateStr = DateTimeUtils.formatDate(noteTimestamp)
        val softwareLabel = softwareName ?: "自由随笔"
        val html = String.format(HTML_TEMPLATE, dateStr, softwareLabel, noteContent)

        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            out.write(html.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("无法打开文件")
    }

    /**
     * 导出为 Word 兼容格式（.doc）。
     * 使用 Word 可识别的 HTML 结构，应用 mso 命名空间 CSS 以确保兼容性。
     */
    suspend fun exportAsDoc(
        context: Context,
        noteContent: String,
        noteTimestamp: Long,
        softwareName: String?,
        outputUri: Uri
    ): Result<Unit> = runCatching {
        val dateStr = DateTimeUtils.formatDate(noteTimestamp)
        val softwareLabel = softwareName ?: "自由随笔"
        val escapedContent = noteContent
            .replace("style=\"", "style=\"mso-line-height-rule:exactly;")

        val doc = buildString {
            appendLine("""<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">""")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("""<!--[if gte mso 9]><xml><w:WordDocument><w:View>Print</w:View></w:WordDocument></xml><![endif]>""")
            appendLine("<style>")
            appendLine("body { font-family: \"PingFang SC\", \"Microsoft YaHei\", sans-serif; font-size: 12pt; line-height: 1.8; margin: 2cm; }")
            appendLine("h1 { font-size: 18pt; color: #1A73E8; }")
            appendLine(".meta { font-size: 10pt; color: #666; margin-bottom: 16pt; }")
            appendLine(".content { font-size: 12pt; }")
            appendLine(".content img { max-width: 100%; height: auto; }")
            appendLine(".footer { font-size: 8pt; color: #ccc; margin-top: 24pt; text-align: center; }")
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<h1>📝 笔记</h1>")
            appendLine("<div class=\"meta\"><p>时间：$dateStr</p><p>关联软件：$softwareLabel</p></div>")
            appendLine("<div class=\"content\">")
            appendLine(escapedContent)
            appendLine("</div>")
            appendLine("<div class=\"footer\">由 NoteEverything 生成</div>")
            appendLine("</body>")
            appendLine("</html>")
        }

        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            out.write(doc.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("无法打开文件")
    }

    /** 根据笔记内容生成默认文件名 */
    fun suggestFileName(content: String, extension: String): String {
        val clean = content
            .replace(Regex("<[^>]+>"), "")  // 去除 HTML 标签
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(30)
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return if (clean.isNotBlank()) "笔记_${clean}.$extension" else "笔记_export.$extension"
    }
}
