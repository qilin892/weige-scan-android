package com.weige.scan.data

import com.weige.scan.model.Limits
import com.weige.scan.model.TaskItem
import com.weige.scan.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** 服务端明确给出的业务错误（带 err 码与中文 msg） */
class ApiException(
    val code: String?,
    override val message: String,
    val status: Int = 0,
) : Exception(message)

/** 会话失效（401）——由 ViewModel 统一清会话并回登录页 */
class UnauthorizedException(message: String = "登录已过期，请重新登录") : Exception(message)

data class StateSnapshot(
    val user: User?,
    val limits: Limits,
    val tasks: List<TaskItem>,
    val tagline: String,
    val version: String,
)

data class PreviewInfo(
    val ptoken: String,
    val pages: Int,
    val hasMore: Boolean,
    val name: String,
)

/**
 * 后端接口封装（严格对齐 docs/DEVELOPMENT-HANDOFF.md §8）。
 *
 * 关键契约：
 * - /api 接口只认 Cookie: dc_web=<token>，token 只在 Set-Cookie 里下发；
 * - 免费/付费由 `X-DC-Channel: mp` 区分，漏带会把本应用当成 Web 付费版（下载报未授权）；
 * - 解锁后 state 仍是 "locked"，只置 unlocked=true；
 * - 上传文件名靠 multipart 的 filename，所以暂存文件必须用原始文件名。
 */
object Api {
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()
    private val OCTET = "application/octet-stream".toMediaType()

    @Volatile
    private var client: OkHttpClient? = null
    private var clientKey: String = ""

    private fun http(): OkHttpClient {
        val key = Prefs.baseUrl + "|" + Prefs.freeChannel
        val cached = client
        if (cached != null && key == clientKey) return cached
        synchronized(this) {
            val again = client
            if (again != null && key == clientKey) return again
            val built = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)   // 预览要在服务端跑 LibreOffice，别给 30s
                .writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .addInterceptor(AuthInterceptor())
                .build()
            client = built
            clientKey = key
            return built
        }
    }

    /** 带鉴权头、并顺手把下发的 dc_web 存下来 */
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val origin = chain.request()
            val b = origin.newBuilder()
            val token = Prefs.token
            if (token.isNotEmpty()) {
                b.header("Cookie", "dc_web=$token")
                b.header("X-DC-Token", token)
            }
            // 渠道标识：不是鉴权凭据，但决定「要不要授权码」，必须一直带
            if (Prefs.freeChannel) b.header("X-DC-Channel", "mp")
            val res = chain.proceed(b.build())
            saveTokenFrom(res)
            return res
        }
    }

    private fun saveTokenFrom(res: Response) {
        for (raw in res.headers.values("Set-Cookie")) {
            if (!raw.startsWith("dc_web=")) continue
            val v = raw.substringAfter("dc_web=").substringBefore(';').trim()
            if (v.isNotEmpty() && v != "deleted") Prefs.token = v
        }
    }

    private fun url(path: String) = Prefs.baseUrl + path

    private suspend fun exec(req: Request): String = withContext(Dispatchers.IO) {
        http().newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 401) throw UnauthorizedException()
            val body = runCatching { JSONObject(text) }.getOrNull()
            if (res.code !in 200..299 || (body != null && body.optBoolean("ok", true).not())) {
                throw ApiException(
                    code = body?.optString("err_code")?.ifBlank { body.optString("err") },
                    message = body?.optString("msg")?.ifBlank { body.optString("err") }
                        ?.ifBlank { "请求失败(${res.code})" } ?: "请求失败(${res.code})",
                    status = res.code,
                )
            }
            if (body == null) throw ApiException(null, "服务返回异常(${res.code})", res.code)
            text
        }
    }

    /* ------------------------- 认证 ------------------------- */

    suspend fun login(username: String, password: String): User {
        val payload = JSONObject().put("username", username).put("password", password)
        val text = exec(
            Request.Builder().url(url("/api/v1/web/login"))
                .post(payload.toString().toRequestBody(JSON_MT)).build()
        )
        val body = JSONObject(text)
        val user = User.from(body.optJSONObject("user"))
            ?: throw ApiException(null, "登录响应异常")
        Prefs.userJson = user.toJson().toString()
        Prefs.loggedOut = false
        return user
    }

    suspend fun logout() {
        runCatching {
            exec(Request.Builder().url(url("/api/v1/web/logout")).post(EMPTY).build())
        }
    }

    /** 免登录的远端配置：限额 + 价格文案 */
    suspend fun remoteConfig(): JSONObject? = runCatching {
        val text = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url("/api/v1/config")).get().build()
            http().newCall(req).execute().use { it.body?.string().orEmpty() }
        }
        JSONObject(text).optJSONObject("config")
    }.getOrNull()

    /* ------------------------- 状态 ------------------------- */

    suspend fun state(): StateSnapshot {
        val text = exec(Request.Builder().url(url("/api/state")).get().build())
        val b = JSONObject(text)
        val lim = b.optJSONObject("limits")
        val limits = Limits(
            maxFileMb = lim?.optInt("max_mb", 50) ?: 50,
            maxImageMb = lim?.optInt("max_image_mb", 10) ?: 10,
            maxPages = lim?.optInt("max_pages", 50) ?: 50,
        )
        return StateSnapshot(
            user = User.from(b.optJSONObject("user")),
            limits = limits,
            tasks = TaskItem.listFrom(b.optJSONArray("tasks"), Prefs.freeChannel),
            tagline = b.optString("tagline"),
            version = b.optString("version"),
        )
    }

    /* ------------------------- 转换 ------------------------- */

    /**
     * 提交转换。文件名靠 multipart 的 filename 传递——传入的 [file] 必须是
     * 「文件名 = 原始文件名」的暂存副本，否则任务名会变成 tmp_xxxx。
     * @return 任务 id（服务端没给就返回空串，由 state 轮询兜底）
     */
    suspend fun convert(
        file: File,
        target: String,
        pageRanges: String,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val body = ProgressBody(file, OCTET, onProgress)
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("target", target)
            .addFormDataPart("is_ocr", "true")      // 扫描件必须走 OCR，界面上不暴露
            .addFormDataPart("model", "vlm")        // 精度优先
            .addFormDataPart("page_ranges", pageRanges)
            .addFormDataPart("file", file.name, body)
            .build()
        val req = Request.Builder().url(url("/api/convert")).post(multipart).build()
        val text = http().newCall(req).execute().use { res ->
            val t = res.body?.string().orEmpty()
            if (res.code == 401) throw UnauthorizedException()
            if (res.code !in 200..299) {
                val j = runCatching { JSONObject(t) }.getOrNull()
                throw ApiException(
                    j?.optString("err_code")?.ifBlank { j.optString("err") },
                    j?.optString("msg")?.ifBlank { j.optString("err") } ?: "上传失败(${res.code})",
                    res.code,
                )
            }
            t
        }
        runCatching { JSONObject(text).optString("task_id") }.getOrDefault("")
    }

    suspend fun task(id: String): TaskItem {
        val text = exec(Request.Builder().url(url("/api/task/$id")).get().build())
        return TaskItem.from(JSONObject(text), Prefs.freeChannel)
    }

    suspend fun clearFinished() {
        exec(Request.Builder().url(url("/api/tasks/clear")).post(EMPTY).build())
    }

    /* ------------------------- 预览 ------------------------- */

    suspend fun preview(outputId: String): PreviewInfo {
        val payload = JSONObject().put("output_id", outputId).toString().toRequestBody(JSON_MT)
        val text = exec(Request.Builder().url(url("/api/preview")).post(payload).build())
        val b = JSONObject(text)
        return PreviewInfo(
            ptoken = b.optString("ptoken"),
            pages = b.optInt("pages"),
            hasMore = b.optBoolean("has_more"),
            name = b.optString("name"),
        )
    }

    /** 单页带水印 PNG（index 从 1 起） */
    suspend fun previewImage(ptoken: String, index: Int): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("/api/preview/img/$ptoken/$index")).get().build()
        http().newCall(req).execute().use { res ->
            if (res.code == 401) throw UnauthorizedException()
            if (res.code !in 200..299) throw ApiException(null, "预览图加载失败(${res.code})", res.code)
            res.body?.bytes() ?: ByteArray(0)
        }
    }

    /* ------------------------- 授权 / 下载 ------------------------- */

    suspend fun unlock(outputId: String, code: String) {
        val payload = JSONObject().put("output_id", outputId).put("code", code)
            .toString().toRequestBody(JSON_MT)
        exec(Request.Builder().url(url("/api/unlock")).post(payload).build())
    }

    /** 下载产物到 [dest]；失败时抛 ApiException（msg 可能是「该文件尚未授权」） */
    suspend fun download(outputId: String, dest: File): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("/api/download/$outputId")).get().build()
        http().newCall(req).execute().use { res ->
            if (res.code == 401) throw UnauthorizedException()
            val body = res.body ?: throw ApiException(null, "下载失败：响应为空")
            if (res.code !in 200..299) {
                val text = runCatching { body.string() }.getOrDefault("")
                val j = runCatching { JSONObject(text) }.getOrNull()
                throw ApiException(
                    j?.optString("err_code")?.ifBlank { j?.optString("err") },
                    j?.optString("msg")?.ifBlank { j?.optString("err") }
                        ?: "下载失败(${res.code})",
                    res.code,
                )
            }
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                dest.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
            }
            dest
        }
    }

    private val EMPTY: RequestBody = ByteArray(0).toRequestBody(null)

    /** 带进度的请求体（上传用） */
    private class ProgressBody(
        private val file: File,
        private val contentType: okhttp3.MediaType?,
        private val onProgress: (Long, Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = contentType
        override fun contentLength(): Long = file.length()
        override fun writeTo(sink: BufferedSink) {
            val total = file.length()
            var sent = 0L
            file.source().use { src ->
                val buf = Buffer()
                while (true) {
                    val read = src.read(buf, 64L * 1024)
                    if (read == -1L) break
                    sink.write(buf, read)
                    sent += read
                    onProgress(sent, total)
                }
            }
        }
    }

}
