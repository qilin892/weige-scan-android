package com.weige.scan

import android.app.Application
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weige.scan.data.Api
import com.weige.scan.data.ApiException
import com.weige.scan.data.Downloader
import com.weige.scan.data.Files
import com.weige.scan.data.PdfComposer
import com.weige.scan.data.Prefs
import com.weige.scan.data.StateSnapshot
import com.weige.scan.data.TaskStore
import com.weige.scan.data.UnauthorizedException
import com.weige.scan.model.Limits
import com.weige.scan.model.Target
import com.weige.scan.model.TaskItem
import com.weige.scan.model.User
import com.weige.scan.work.ConvertService
import com.weige.scan.work.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class Tab { SCAN, TASKS, ME }

/**
 * 全部界面状态都挂在这里，Compose 直接读 mutableStateOf（不做 Flow→State 的二次转换）。
 * 任务列表本身放在 [TaskStore]，因为前台服务也要往里写。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext

    var booting by mutableStateOf(true)
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var limits by mutableStateOf(Limits())
        private set
    var priceText by mutableStateOf("")
        private set
    var version by mutableStateOf("")
        private set

    var tab by mutableStateOf(Tab.SCAN)
    var preview by mutableStateOf<TaskItem?>(null)
        private set

    /** 深色模式：显式开关，首次进入跟随系统 */
    var dark by mutableStateOf(initialDark())
        private set

    var freeChannel by mutableStateOf(Prefs.freeChannel)
        private set

    /* ---------------- 扫描会话 ---------------- */

    var images by mutableStateOf<List<File>>(emptyList())
        private set
    var docFile by mutableStateOf<File?>(null)
        private set
    var target by mutableStateOf(Target.WORD)
    var pageRanges by mutableStateOf("")

    var submitting by mutableStateOf(false)
        private set

    /** 提交后的强制轮询窗口（毫秒时间戳），见 startPolling */
    private var pollUntil = 0L

    /* ---------------- 提示 / 弹框 ---------------- */

    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var unlockTarget by mutableStateOf<TaskItem?>(null)
        private set

    val tasks: StateFlow<List<TaskItem>> get() = TaskStore.tasks

    init {
        loadRemoteConfig()
        boot()
        startPolling()
    }

    private fun initialDark(): Boolean {
        val override = Prefs.darkOverride
        if (override >= 0) return override == 1
        val mode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    /* ---------------- 启动 ---------------- */

    private fun boot() {
        viewModelScope.launch {
            try {
                if (Prefs.token.isEmpty() || Prefs.loggedOut) {
                    loggedIn = false
                } else {
                    user = runCatching {
                        User.from(JSONObject(Prefs.userJson))
                    }.getOrNull()
                    applyState(Api.state())
                    loggedIn = true
                }
            } catch (e: UnauthorizedException) {
                Prefs.clearSession()
                loggedIn = false
            } catch (e: Exception) {
                // 网络抖动不该把人踢下线：保留会话，只提示
                loggedIn = Prefs.token.isNotEmpty()
                message = e.message ?: "网络异常，请检查连接"
            } finally {
                booting = false
            }
        }
    }

    private fun loadRemoteConfig() {
        viewModelScope.launch {
            val c = Api.remoteConfig() ?: return@launch
            val up = c.optInt("max_upload_mb", 0)
            val img = c.optInt("max_image_mb", 0)
            val pages = c.optInt("max_pages", 0)
            limits = limits.copy(
                maxFileMb = if (up > 0) up else limits.maxFileMb,
                maxImageMb = if (img > 0) img else limits.maxImageMb,
                maxPages = if (pages > 0) pages else limits.maxPages,
            )
            priceText = c.optString("price_text")
        }
    }

    private fun applyState(s: StateSnapshot) {
        s.user?.let { user = it }
        limits = s.limits
        version = s.version
        TaskStore.publish(s.tasks)
    }

    /** 有进行中的任务才轮询，空闲时不打扰服务端 */
    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(2500)
                if (!loggedIn) continue
                // 提交后有一段时间服务端还没登记任务（正在上传），此时 hasActive 为 false，
                // 不能就此停轮询，否则列表会一直空着直到用户手动刷新
                if (!TaskStore.hasActive && System.currentTimeMillis() > pollUntil) continue
                runCatching { applyState(Api.state()) }
            }
        }
    }

    fun onAppForeground() {
        if (!loggedIn) return
        viewModelScope.launch { runCatching { applyState(Api.state()) } }
    }

    fun refresh() {
        if (!loggedIn) return
        viewModelScope.launch { runCatching { applyState(Api.state()) } }
    }

    /* ---------------- 登录 ---------------- */

    fun login(name: String, pass: String) {
        if (submitting) return
        if (name.isBlank() || pass.isBlank()) {
            message = "请输入账号和密码"
            return
        }
        viewModelScope.launch {
            busy = "登录中…"
            try {
                user = Api.login(name.trim(), pass)
                if (Prefs.token.isEmpty()) {
                    throw ApiException(null, "登录成功但服务端没下发会话，请检查地址是否正确")
                }
                Prefs.loggedOut = false
                applyState(Api.state())
                loggedIn = true
            } catch (e: Exception) {
                message = e.message ?: "登录失败"
            } finally {
                busy = null
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { Api.logout() }
            Prefs.clearSession()
            Prefs.loggedOut = true
            TaskStore.clear()
            images = emptyList()
            docFile = null
            user = null
            loggedIn = false
            preview = null
            Notifier.cancel(ctx)
        }
    }

    /* ---------------- 选文件 / 拍照 ---------------- */

    /** 相机要一个可写的目标文件，先建好再交给系统相机 */
    fun newShotTarget(): File? {
        val dir = Files.shotsDir(ctx)
        return runCatching {
            File.createTempFile("shot_", ".jpg", dir)
        }.getOrNull()
    }

    /** 相机回调前暂存的目标文件（拍成功就入库，取消就删） */
    var pendingShot: File? = null
        private set

    fun setPendingShot(file: File) {
        pendingShot = file
    }

    fun finishShot(ok: Boolean) {
        val f = pendingShot
        pendingShot = null
        if (f == null) return
        if (ok) addShot(f) else runCatching { f.delete() }
    }

    /** 拍完一张：把临时文件改成「扫描件_时间.jpg」并加入本次扫描 */
    fun addShot(file: File) {
        if (!file.exists() || file.length() == 0L) return
        val named = Files.uniqueFile(Files.shotsDir(ctx), "扫描件_${Files.stamp()}.jpg")
        val ok = runCatching { file.renameTo(named) }.getOrDefault(false)
        images = images + (if (ok) named else file)
        docFile = null
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        Files.copyTo(ctx, uri, Files.shotsDir(ctx))
                    }.getOrNull()
                }
            }
            if (added.isEmpty()) {
                message = "读取图片失败，请换一张试试"
                return@launch
            }
            images = images + added
            docFile = null
        }
    }

    fun pickDocument(uri: Uri, name: String?) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching { Files.copyTo(ctx, uri, Files.stageDir(ctx), name) }.getOrNull()
            }
            if (f == null) {
                message = "读取文件失败"
                return@launch
            }
            // 文档模式与拍照模式互斥：一次任务只吃一个文件
            images.forEach { runCatching { it.delete() } }
            docFile?.let { runCatching { it.delete() } }
            images = emptyList()
            docFile = f
        }
    }

    fun removeImage(index: Int) {
        val list = images.toMutableList()
        if (index !in list.indices) return
        val f = list.removeAt(index)
        runCatching { f.delete() }
        images = list
    }

    fun clearPicked() {
        images.forEach { runCatching { it.delete() } }
        docFile?.let { runCatching { it.delete() } }
        images = emptyList()
        docFile = null
        pageRanges = ""
    }

    /* ---------------- 提交转换 ---------------- */

    fun submit() {
        if (submitting) return
        val doc = docFile
        val pics = images
        if (doc == null && pics.isEmpty()) {
            message = "先拍一张或选一个文件"
            return
        }
        submitting = true
        viewModelScope.launch {
            try {
                busy = if (doc == null) "正在合成 PDF（${pics.size} 页）…" else "正在准备上传…"
                val upload: File = withContext(Dispatchers.IO) {
                    if (doc != null) doc
                    else PdfComposer.compose(
                        pics,
                        File(Files.stageDir(ctx), "扫描件_${Files.stamp()}.pdf")
                    )
                }
                val maxBytes = limits.maxFileMb.toLong() * 1024 * 1024
                if (upload.length() > maxBytes) {
                    message = "文件超过 ${limits.maxFileMb}MB，请减少页数或压缩后重试"
                    if (doc == null) runCatching { upload.delete() }
                    return@launch
                }
                busy = "提交中…"
                ConvertService.start(
                    ctx, upload, target.value, pageRanges.trim(),
                    doc?.name?.substringBeforeLast('.') ?: "扫描件"
                )
                // ⚠️ 这里**不能**调 clearPicked()：upload 正是要上传的那份文件，
                // 提前删掉会让服务读到 0 字节。只清源图片，upload 的所有权交给服务。
                pics.forEach { runCatching { it.delete() } }
                images = emptyList()
                docFile = null
                pageRanges = ""
                tab = Tab.TASKS
                pollUntil = System.currentTimeMillis() + 3 * 60 * 1000
                delay(800)
                runCatching { applyState(Api.state()) }
            } catch (e: UnauthorizedException) {
                loggedIn = false
                Prefs.clearSession()
                message = "登录已过期，请重新登录"
            } catch (e: Exception) {
                message = e.message ?: "提交失败，请重试"
            } finally {
                submitting = false
                busy = null
            }
        }
    }

    /* ---------------- 预览 / 下载 / 分享 ---------------- */

    fun openPreview(task: TaskItem) {
        preview = task
    }

    fun closePreview() {
        preview = null
    }

    /** 通知点进来：按 output_id 找到任务再打开预览 */
    fun openOutput(outputId: String, name: String) {
        tab = Tab.TASKS
        viewModelScope.launch {
            TaskStore.tasks.value.firstOrNull { it.output?.outputId == outputId }
                ?.let { preview = it }
                ?: run {
                    runCatching { applyState(Api.state()) }
                    TaskStore.tasks.value.firstOrNull { it.output?.outputId == outputId }
                        ?.let { preview = it }
                        ?: run { message = "该文件已不在任务列表中" }
                }
        }
    }

    private fun cachePath(task: TaskItem): File {
        val out = task.output ?: throw ApiException(null, "该任务还没有产物")
        val safeName = Files.sanitize(out.name.ifBlank { "文档" })
        // ⚠️ 文件名里**不能**带产物 ID：这份缓存会原样分享给微信/邮件，
        // 拼成 "<outputId>__名字.docx" 用户看到的就是一串乱码。用子目录隔离即可。
        return File(File(Files.outDir(ctx), out.outputId), safeName)
    }

    private suspend fun cachedOutput(task: TaskItem): File {
        val dest = cachePath(task)
        if (dest.exists() && dest.length() > 0) return dest
        val out = task.output ?: throw ApiException(null, "该任务还没有产物")
        return Api.download(out.outputId, dest)
    }

    fun save(task: TaskItem) = downloadThen(task, share = false)

    fun share(task: TaskItem) = downloadThen(task, share = true)

    private fun downloadThen(task: TaskItem, share: Boolean) {
        viewModelScope.launch {
            busy = if (share) "准备分享…" else "正在保存…"
            try {
                val file = cachedOutput(task)
                val name = task.output?.name?.ifBlank { file.name } ?: file.name
                if (share) {
                    Downloader.share(ctx, file, Files.mimeOf(name), name)
                } else {
                    // 保存要拷一份到下载目录，几 MB 到几十 MB，别放在主线程
                    message = withContext(Dispatchers.IO) {
                        Downloader.saveToDevice(ctx, file, name)
                    }
                }
            } catch (e: UnauthorizedException) {
                loggedIn = false
                Prefs.clearSession()
                message = "登录已过期，请重新登录"
            } catch (e: ApiException) {
                val msg = e.message ?: "下载失败"
                if (msg.contains("授权") || e.code == "not_unlocked") {
                    unlockTarget = task
                } else {
                    message = msg
                }
            } catch (e: Exception) {
                message = e.message ?: "下载失败，请重试"
            } finally {
                busy = null
            }
        }
    }

    /* ---------------- 授权码（付费通道） ---------------- */

    fun askUnlock(task: TaskItem) {
        unlockTarget = task
    }

    fun dismissUnlock() {
        unlockTarget = null
    }

    fun unlock(code: String) {
        val task = unlockTarget ?: return
        val out = task.output ?: return
        if (code.isBlank()) {
            message = "请输入授权码"
            return
        }
        viewModelScope.launch {
            busy = "正在校验…"
            try {
                Api.unlock(out.outputId, code.trim())
                unlockTarget = null
                message = "解锁成功"
                runCatching { applyState(Api.state()) }
            } catch (e: Exception) {
                message = e.message ?: "解锁失败"
            } finally {
                busy = null
            }
        }
    }

    /* ---------------- 其他 ---------------- */

    fun clearFinished() {
        viewModelScope.launch {
            try {
                Api.clearFinished()
                runCatching { applyState(Api.state()) }
                message = "已清除完成的任务"
            } catch (e: Exception) {
                message = e.message ?: "清除失败"
            }
        }
    }

    fun toggleDark() {
        dark = !dark
        Prefs.darkOverride = if (dark) 1 else 0
    }

    /** 免费通道开关：关掉后按 Web 付费版走，下载需要授权码 */
    fun updateFreeChannel(value: Boolean) {
        freeChannel = value
        Prefs.freeChannel = value
        refresh()
    }

    fun consumeMessage() {
        message = null
    }

    /** 供界面抛一条提示（例如相机启动失败） */
    fun note(text: String) {
        message = text
    }
}
