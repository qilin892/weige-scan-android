package com.weige.scan.model

import org.json.JSONArray
import org.json.JSONObject

data class User(
    val id: Int,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("nickname", nickname ?: "")
        .put("avatar_url", avatarUrl ?: "")

    companion object {
        fun from(o: JSONObject?): User? {
            if (o == null) return null
            val name = o.optString("username").ifBlank { return null }
            return User(
                id = o.optInt("id"),
                username = name,
                nickname = o.optString("nickname").takeIf { it.isNotBlank() }
                    ?: o.optString("display_name").takeIf { it.isNotBlank() },
                avatarUrl = o.optString("avatar_url").takeIf { it.isNotBlank() },
            )
        }
    }
}

data class Limits(
    val maxFileMb: Int = 50,
    val maxImageMb: Int = 10,
    val maxPages: Int = 50,
)

/** 转换产物。output_id ≠ task_id，下载/预览都必须用它。 */
data class Output(
    val outputId: String,
    val name: String,
    val size: Long,
    val pages: Int,
    val locked: Boolean,
)

/**
 * 任务状态。
 *
 * ⚠️ 服务端解锁后 `state` 仍是 "locked"，只把 unlocked 置 true、output.locked 置 false。
 * 判断「能不能下载」必须看 [canDownload]，不要判 state == "unlocked"。
 */
data class TaskItem(
    val id: String,
    val fileName: String,
    val state: String,
    val progress: Int,
    val stageText: String,
    val target: String,
    val output: Output?,
    val unlocked: Boolean,
    val createdAt: String,
    val err: String,
    val errCode: String,
) {
    val active: Boolean get() = state in ACTIVE_STATES

    /** 有产物就算完成：免费通道没有「待授权」这一态 */
    val finished: Boolean get() = output != null && state != "failed"

    val canDownload: Boolean get() = output != null && (unlocked || !output.locked)

    val targetLabel: String get() = Target.byValue(target).label

    val stateText: String
        get() = when (state) {
            "queued" -> "排队中"
            "uploading" -> "上传中"
            "parsing" -> "解析中"
            "downloading" -> "下载中"
            "building" -> "生成中"
            "failed" -> "失败"
            else -> if (output != null) "转换完成" else "处理中"
        }

    /** 进度条文案：优先用服务端阶段文案，缺了再用状态文案 */
    val stageLine: String
        get() = stageText.ifBlank { stateText }

    val sizeText: String get() = if (output != null && output.size > 0) formatSize(output.size) else ""

    companion object {
        val ACTIVE_STATES = listOf("queued", "uploading", "parsing", "downloading", "building")

        fun from(o: JSONObject, freeChannel: Boolean = false): TaskItem {
            val out = o.optJSONObject("output")?.let {
                Output(
                    outputId = it.optString("output_id"),
                    name = it.optString("name"),
                    size = it.optLong("size"),
                    pages = it.optInt("pages"),
                    // ⚠️ 服务端下发的 locked 恒为 true（免费通道也不改），直接拿它做判据
                    // 会让每个已完成的免费任务都显示「未授权」。免费通道按「有产物=可下」归一。
                    locked = it.optBoolean("locked", true) && !freeChannel,
                )
            }
            var progress = o.optDouble("progress", 0.0)
            // 服务端可能给 0~1 也可能给 0~100，统一成百分数
            if (progress <= 1.0) progress *= 100
            val created = when (val raw = o.opt("created_at")) {
                is Number -> raw.toLong().let { if (it > 1_000_000_000L) epochToText(it) else "" }
                is String -> shortTime(raw)
                else -> ""
            }
            return TaskItem(
                id = o.optString("task_id").ifBlank { o.optString("id") },
                fileName = o.optString("file_name").ifBlank { o.optString("name") },
                state = o.optString("state", "queued"),
                progress = progress.coerceIn(0.0, 100.0).toInt(),
                stageText = o.optString("stage_text"),
                target = o.optString("target", "word"),
                output = out,
                unlocked = o.optBoolean("unlocked") || (out != null && !out.locked),
                createdAt = created,
                err = o.optString("err"),
                errCode = o.optString("err_code"),
            )
        }

        fun listFrom(arr: JSONArray?, freeChannel: Boolean = false): List<TaskItem> {
            if (arr == null) return emptyList()
            val list = ArrayList<TaskItem>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(from(it, freeChannel)) }
            }
            return list
        }

        /** 服务端时间形如 "2026-10-29 17:06:16"，卡片上只留 "10-29 17:06" */
        private fun shortTime(raw: String): String {
            val m = Regex("""(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})""").find(raw)
            if (m != null) {
                val g = m.groupValues
                return "${g[2]}-${g[3]} ${g[4]}:${g[5]}"
            }
            return raw.take(16)
        }

        private fun epochToText(sec: Long): String {
            val f = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
            return f.format(java.util.Date(sec * 1000))
        }
    }
}

/** 目标格式。value 直接作为 /api/convert 的 target。 */
enum class Target(val value: String, val label: String, val short: String, val ext: String) {
    WORD("word", "Word 文档", "Word", "docx"),
    EXCEL("excel", "Excel 表格", "Excel", "xlsx"),
    HTML("html", "HTML 网页", "HTML", "html"),
    PDF("pdf", "PDF 文件", "PDF", "pdf");

    companion object {
        fun byValue(v: String): Target = entries.firstOrNull { it.value == v } ?: WORD
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.CHINA, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.CHINA, "%.1f MB", mb)
    return String.format(java.util.Locale.CHINA, "%.2f GB", mb / 1024.0)
}
