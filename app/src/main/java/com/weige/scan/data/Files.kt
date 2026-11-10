package com.weige.scan.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** 文件小工具：内容 Uri 落盘、取名、mime 判断 */
object Files {

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "bmp", "gif", "webp", "tiff", "heic", "heif")

    fun extOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    fun isImage(name: String): Boolean = extOf(name) in IMAGE_EXT

    fun mimeOf(name: String): String = when (extOf(name)) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "html", "htm" -> "text/html"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    /** 从内容 Uri 读真实文件名（系统文件选择器的 DISPLAY_NAME） */
    fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                val n = c.getString(idx)
                if (!n.isNullOrBlank()) return n
            }
        }
        // 相册里常取不到，退化成 uri 末段
        return uri.lastPathSegment?.substringAfterLast('/') ?: "扫描件"
    }

    fun sizeOf(ctx: Context, uri: Uri): Long {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) return c.getLong(idx)
        }
        return runCatching {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L)
    }

    /** 把内容 Uri 复制到 cache 目录，文件名保持原名（服务端只认 multipart 的 filename） */
    fun copyTo(ctx: Context, uri: Uri, dir: File, name: String? = null): File {
        val real = sanitize(name ?: displayName(ctx, uri))
        dir.mkdirs()
        val out = uniqueFile(dir, real)
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取该文件" }
            out.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        return out
    }

    /** 去掉路径分隔符等危险字符，保留中文原名 */
    fun sanitize(name: String): String {
        var n = name.replace('/', '_').replace('\\', '_').replace("\u0000", "").trim()
        if (n.isEmpty() || n == "." || n == "..") n = "扫描件_" + stamp() + ".jpg"
        if (n.length > 120) {
            val ext = extOf(n)
            val base = n.substringBeforeLast('.', n).take(100)
            n = if (ext.isEmpty()) base else "$base.$ext"
        }
        return n
    }

    fun uniqueFile(dir: File, name: String): File {
        val f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = extOf(name)
        var i = 1
        while (true) {
            val candidate = if (ext.isEmpty()) File(dir, "$base($i)") else File(dir, "$base($i).$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    fun stamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())

    fun shotsDir(ctx: Context) = File(ctx.cacheDir, "shots").apply { mkdirs() }
    fun stageDir(ctx: Context) = File(ctx.cacheDir, "stage").apply { mkdirs() }
    fun outDir(ctx: Context) = File(ctx.cacheDir, "out").apply { mkdirs() }
}
