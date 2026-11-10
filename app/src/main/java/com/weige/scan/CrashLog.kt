package com.weige.scan

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 把未捕获异常的堆栈落到私有目录，下次启动时先展示出来。
 *
 * 目的很实际：没有 adb 的时候，"闪退" 只能靠猜。有了它，用户再打开一次就能
 * 把整段堆栈复制发过来（含机型 / 系统版本 / App 版本）。
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            runCatching {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                val head = buildString {
                    append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append(" / Android ").append(Build.VERSION.RELEASE)
                    append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("应用: ").append(BuildConfig.VERSION_NAME)
                    append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                    append("线程: ").append(thread.name).append("\n")
                    append("时间: ").append(java.util.Date().toString()).append("\n\n")
                }
                File(app.filesDir, FILE).writeText(head + sw.toString())
            }
            if (prev != null) prev.uncaughtException(thread, ex)
            else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun peek(ctx: Context): String? = runCatching {
        val f = File(ctx.filesDir, FILE)
        if (f.exists() && f.length() > 0) f.readText() else null
    }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE).delete() }
    }
}
