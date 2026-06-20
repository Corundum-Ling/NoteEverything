package com.corunling.noteeverything.util

import android.content.Context
import android.net.Uri
import com.corunling.noteeverything.data.entity.NoteEntity

object NoteExporter {

    /**
     * 导出单条笔记为 HTML。
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
        val html = buildString {
            append("""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"><title>NoteEverything</title>""")
            append("<style>")
            append("body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;max-width:800px;margin:0 auto;padding:24px;color:#333;line-height:1.8;background:#fff}")
            append(".header{border-bottom:2px solid #1A73E8;padding-bottom:16px;margin-bottom:24px}")
            append(".header h1{font-size:24px;color:#1A73E8;margin:0}")
            append(".meta{background:#f5f5f5;padding:12px 16px;border-radius:8px;margin-bottom:24px;font-size:14px;color:#666}")
            append(".content{font-size:16px}")
            append(".content img{max-width:100%;border-radius:4px;margin:8px 0}")
            append(".content p{margin:8px 0}")
            append(".footer{margin-top:32px;padding-top:16px;border-top:1px solid #eee;font-size:12px;color:#ccc;text-align:center}")
            append("</style></head><body>")
            append("<div class=\"header\"><h1>📝 笔记</h1></div>")
            append("<div class=\"meta\"><p>📅 $dateStr</p><p>🏷️ $softwareLabel</p></div>")
            append("<div class=\"content\">$noteContent</div>")
            append("<div class=\"footer\">由 NoteEverything 生成</div>")
            append("</body></html>")
        }
        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            out.write(html.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("无法打开文件")
    }

    /**
     * 导出单条笔记为 Word 兼容格式。
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
        val escaped = noteContent.replace("style=\"", "style=\"mso-line-height-rule:exactly;")
        val doc = buildString {
            appendLine("""<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">""")
            appendLine("<head><meta charset=\"UTF-8\">")
            appendLine("""<!--[if gte mso 9]><xml><w:WordDocument><w:View>Print</w:View></w:WordDocument></xml><![endif]-->""")
            appendLine("<style>")
            appendLine("body{font-family:'PingFang SC','Microsoft YaHei',sans-serif;font-size:12pt;line-height:1.8;margin:2cm}")
            appendLine("h1{font-size:18pt;color:#1A73E8}")
            appendLine(".meta{font-size:10pt;color:#666;margin-bottom:16pt}")
            appendLine(".content{font-size:12pt}")
            appendLine(".content img{max-width:100%}")
            appendLine(".footer{font-size:8pt;color:#ccc;margin-top:24pt;text-align:center}")
            appendLine("</style></head><body>")
            appendLine("<h1>📝 笔记</h1>")
            appendLine("<div class=\"meta\"><p>时间：$dateStr</p><p>关联：$softwareLabel</p></div>")
            appendLine("<div class=\"content\">")
            appendLine(escaped)
            appendLine("</div>")
            appendLine("<div class=\"footer\">由 NoteEverything 生成</div>")
            appendLine("</body></html>")
        }
        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            out.write(doc.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("无法打开文件")
    }

    /**
     * 批量导出笔记为 zip 压缩包（每条笔记一个独立文件）。
     * @param extension "html" 或 "doc"
     */
    suspend fun exportNotesZip(
        context: Context,
        notes: List<NoteEntity>,
        softwareNames: Map<Long, String>,
        extension: String,
        outputUri: Uri
    ): Result<Unit> = runCatching {
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault())
        val workDir = java.io.File(context.cacheDir, "export_${System.currentTimeMillis()}")
        workDir.mkdirs()

        notes.sortedByDescending { it.timestamp }.forEachIndexed { i, note ->
            val time = dateFmt.format(java.util.Date(note.timestamp))
            val swName = note.softwareId?.let { softwareNames[it] } ?: "自由随笔"
            val label = if (note.type == "free") "随笔" else "笔记"
            val title = note.content.replace(Regex("<[^>]+>"), "").take(20)
                .replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
            val fname = "${i + 1}_${title}.$extension"

            val content = if (extension == "html") {
                buildString {
                    append("""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>笔记</title><style>""")
                    append("body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;max-width:800px;margin:0 auto;padding:24px;color:#333;line-height:1.8}")
                    append(".meta{background:#f5f5f5;padding:12px;border-radius:8px;margin-bottom:16px;font-size:14px;color:#666}")
                    append(".content img{max-width:100%}")
                    append("</style></head><body>")
                    append("<div class=\"meta\"><p>[$label] $time · $swName</p></div>")
                    append("<div class=\"content\">${note.content}</div>")
                    append("</body></html>")
                }
            } else {
                buildString {
                    append("""<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">""")
                    append("<head><meta charset=\"UTF-8\">")
                    append("<style>body{font-family:'PingFang SC','Microsoft YaHei',sans-serif;font-size:12pt;line-height:1.8;margin:2cm}</style></head><body>")
                    append("<p><b>[$label]</b> $time · $swName</p>")
                    append("<div>${note.content.replace("style=\"", "style=\"mso-line-height-rule:exactly;")}</div>")
                    append("</body></html>")
                }
            }
            java.io.File(workDir, fname).writeText(content, Charsets.UTF_8)
        }

        val zipFile = java.io.File(context.cacheDir, "NoteEverything_${System.currentTimeMillis()}.zip")
        val zos = java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile))
        workDir.listFiles()?.forEach { f ->
            zos.putNextEntry(java.util.zip.ZipEntry(f.name))
            zos.write(f.readBytes())
            zos.closeEntry()
            f.delete()
        }
        zos.close()
        workDir.delete()

        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            out.write(zipFile.readBytes())
        } ?: throw Exception("无法打开文件")
        zipFile.delete()
    }

    fun suggestFileName(content: String, extension: String): String {
        val clean = content.replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ").trim().take(30)
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return if (clean.isNotBlank()) "笔记_${clean}.$extension" else "笔记_export.$extension"
    }
}
