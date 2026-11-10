package com.weige.scan.data

import android.content.Context
import android.content.SharedPreferences
import com.weige.scan.BuildConfig

/**
 * 本地偏好：会话 token、用户、主题、渠道模式、服务地址。
 *
 * 会话 token 就是 webd 下发的 dc_web Cookie 值。部署版 webd 的 /api 接口只认
 * `Cookie: dc_web=<token>`（X-DC-Token 头会 401），所以这里必须原样回塞 Cookie。
 */
object Prefs {
    private const val FILE = "weige_scan"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    var token: String
        get() = sp.getString("dc_web", "").orEmpty()
        set(value) = sp.edit().putString("dc_web", value).apply()

    var userJson: String
        get() = sp.getString("dc_user", "").orEmpty()
        set(value) = sp.edit().putString("dc_user", value).apply()

    /** 是否已明确退出（有它就不再自动恢复会话） */
    var loggedOut: Boolean
        get() = sp.getBoolean("dc_logged_out", false)
        set(value) = sp.edit().putBoolean("dc_logged_out", value).apply()

    /** 深色模式：null 跟随系统，true/false 强制 */
    var darkOverride: Int
        get() = sp.getInt("dark", -1)
        set(value) = sp.edit().putInt("dark", value).apply()

    /**
     * 免费通道：带 `X-DC-Channel: mp` 头时服务端按「小程序免费版」处理，产物产出即可下载；
     * 关掉则不带头 → 按 Web 付费版处理，下载要用授权码解锁。
     * 默认开着（与小程序端一致）。
     */
    var freeChannel: Boolean
        get() = sp.getBoolean("free_channel", true)
        set(value) = sp.edit().putBoolean("free_channel", value).apply()

    var baseUrl: String
        get() = sp.getString("base_url", "").orEmpty().ifBlank { BuildConfig.BASE_URL }
        set(value) = sp.edit().putString("base_url", value.trim().trimEnd('/')).apply()

    fun clearSession() {
        sp.edit().remove("dc_web").remove("dc_user").apply()
    }
}
