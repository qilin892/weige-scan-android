package com.weige.scan.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.weige.scan.model.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 任务列表的唯一数据源。
 *
 * 前台服务在轮询时往里写，界面订阅它——这样即使界面被回收，通知里的进度也不会断。
 */
object TaskStore {
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks

    fun publish(list: List<TaskItem>) {
        _tasks.value = list
    }

    fun clear() {
        _tasks.value = emptyList()
    }

    val hasActive: Boolean get() = _tasks.value.any { it.active }
}

/** 保存到手机 + 系统分享（沿用播放器工程验证过的 MediaStore / FileProvider 做法） */
object Downloader {

    /** 保存到「下载/伟哥扫描王」，返回给人看的位置描述 */
    fun saveToDevice(ctx: Context, src: java.io.File, displayName: String): String {
        val name = Files.sanitize(displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, Files.mimeOf(name))
                put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/伟哥扫描王")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法写入下载目录")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out, 64 * 1024) }
                } ?: throw IllegalStateException("无法打开输出流")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                // 失败必须删掉半截文件，否则下载目录里躺着 0 字节的残骸
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return "已保存到 下载/伟哥扫描王/$name"
        }
        // Android 8/9：写公共目录要 WRITE_EXTERNAL_STORAGE，为一个保存功能弹权限不划算，
        // 退到应用私有目录，再让用户从分享面板发到微信/文件管理器。
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: ctx.filesDir
        dir.mkdirs()
        val dst = java.io.File(dir, name)
        src.inputStream().use { input -> dst.outputStream().use { input.copyTo(it, 64 * 1024) } }
        return "已保存到 $dst"
    }

    fun share(ctx: Context, file: java.io.File, mime: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            ctx, "com.weige.scan.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享到").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(chooser)
    }

    fun openWith(ctx: Context, file: java.io.File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "com.weige.scan.fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(view) }
    }
}
