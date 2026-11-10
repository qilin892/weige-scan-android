package com.weige.scan.work

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.weige.scan.data.Api
import com.weige.scan.data.Prefs
import com.weige.scan.data.TaskStore
import com.weige.scan.data.UnauthorizedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 上传 + 转换进度跟踪的前台服务。
 *
 * 为什么要有服务：一次转换常常十几秒到几分钟，用户切到微信再回来、甚至锁屏，
 * 放在 Activity/ViewModel 里做上传会被系统在后台掐掉，进度与通知也就断了。
 *
 * 服务职责：上传（带进度）→ 每 2 秒轮询 /api/state → 刷通知 → 结束时发完成通知。
 * 结果统一写 [TaskStore]，界面订阅即可，不必自己再轮询。
 */
class ConvertService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_CONVERT) {
            stopSelf()
            return START_NOT_STICKY
        }
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank() || !File(path).exists()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val target = intent.getStringExtra(EXTRA_TARGET) ?: "word"
        val ranges = intent.getStringExtra(EXTRA_RANGES).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()

        // 必须在 5 秒内进入前台，否则系统直接抛 ANR 级异常
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(
            this, Notifier.ID_PROGRESS,
            Notifier.progress(this, "伟哥扫描王", "准备上传…", 0, indeterminate = true),
            type
        )
        run(File(path), target, ranges, label)
        return START_NOT_STICKY
    }

    private fun run(file: File, target: String, ranges: String, label: String) {
        scope.launch {
            try {
                var notifiedAt = 0L
                val taskId = Api.convert(file, target, ranges) { sent, total ->
                    if (total <= 0) return@convert
                    val now = System.currentTimeMillis()
                    if (now - notifiedAt < 400) return@convert
                    notifiedAt = now
                    val pct = ((sent * 100) / total).toInt().coerceIn(0, 100)
                    push("上传中 $pct%", pct, false)
                }
                push("已提交，排队转换…", 0, true)
                track(taskId, label)
            } catch (e: UnauthorizedException) {
                Prefs.clearSession()
                Notifier.post(
                    this@ConvertService, "登录已过期", "请打开伟哥扫描王重新登录", null, null, false
                )
            } catch (e: Exception) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
                Notifier.post(this@ConvertService, "转换失败", msg, null, null, false)
            } finally {
                // 上传用的暂存文件（含合成的 PDF）到这里就没用了
                runCatching { if (file.parentFile?.name == "stage") file.delete() }
                // DETACH：让完成/失败通知留在通知栏，不要跟着前台状态一起消失
                ServiceCompat.stopForeground(
                    this@ConvertService, ServiceCompat.STOP_FOREGROUND_DETACH
                )
                stopSelf()
            }
        }
    }

    /** 轮询任务直到结束；成功发「转换完成」通知并写 TaskStore */
    private suspend fun track(taskId: String, label: String) {
        val deadline = System.currentTimeMillis() + 20 * 60 * 1000L
        while (scope.isActive) {
            delay(2000)
            if (System.currentTimeMillis() > deadline) {
                Notifier.post(this, "转换超时", "任务仍在服务端处理，可稍后在列表里查看", null, null, false)
                return
            }
            val snap = runCatching { Api.state() }.getOrNull() ?: continue
            TaskStore.publish(snap.tasks)
            val task = snap.tasks.firstOrNull { it.id == taskId }
                ?: snap.tasks.firstOrNull { it.active }
                ?: return
            if (task.active) {
                push(task.stageLine, task.progress, false)
                continue
            }
            if (task.state == "failed") {
                val msg = task.err.ifBlank { "云端解析失败，请换个文件试试" }
                Notifier.post(this, "转换失败", msg, null, null, false)
                return
            }
            val out = task.output
            val name = out?.name?.ifBlank { label } ?: label
            Notifier.post(
                this, "转换完成",
                "$name 已生成，点击预览与保存",
                out?.outputId, out?.name, true
            )
            return
        }
    }

    private fun push(text: String, pct: Int, indeterminate: Boolean) {
        runCatching {
            val n = Notifier.progress(this, "伟哥扫描王", text, pct, indeterminate)
            val mgr = androidx.core.app.NotificationManagerCompat.from(this)
            mgr.notify(Notifier.ID_PROGRESS, n)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONVERT = "com.weige.scan.action.CONVERT"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_RANGES = "ranges"
        private const val EXTRA_LABEL = "label"

        fun start(ctx: Context, file: File, target: String, ranges: String, label: String) {
            val i = Intent(ctx, ConvertService::class.java).apply {
                action = ACTION_CONVERT
                putExtra(EXTRA_PATH, file.absolutePath)
                putExtra(EXTRA_TARGET, target)
                putExtra(EXTRA_RANGES, ranges)
                putExtra(EXTRA_LABEL, label)
            }
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}
