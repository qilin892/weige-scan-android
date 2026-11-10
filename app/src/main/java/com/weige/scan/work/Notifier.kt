package com.weige.scan.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.weige.scan.MainActivity
import com.weige.scan.R

/**
 * 转换进度 / 完成通知。
 *
 * 渠道经验（来自播放器工程的实测）：
 * - IMPORTANCE_LOW 会让通知在华为/荣耀锁屏上直接不显示（groupKey=silent），
 *   所以这里用 IMPORTANCE_DEFAULT + 静音 + 不振动 = 可见但不打扰；
 * - 同一个 channel id 改 importance 不生效，要换 id 重建。
 */
object Notifier {
    const val CHANNEL = "scan_convert_v1"
    const val ID_PROGRESS = 1101

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(
            CHANNEL,
            ctx.getString(R.string.channel_convert_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = ctx.getString(R.string.channel_convert_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        mgr.createNotificationChannel(ch)
    }

    private fun contentIntent(ctx: Context, outputId: String?, name: String?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OUTPUT_ID, outputId)
            putExtra(MainActivity.EXTRA_OUTPUT_NAME, name)
        }
        return PendingIntent.getActivity(
            ctx, (outputId ?: "open").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun progress(
        ctx: Context,
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean = false,
    ): Notification = NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(R.drawable.ic_notify_scan)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setProgress(100, progress.coerceIn(0, 100), indeterminate)
        .setContentIntent(contentIntent(ctx, null, null))
        .build()

    fun post(
        ctx: Context,
        title: String,
        text: String,
        outputId: String?,
        name: String?,
        ok: Boolean = true,
    ) {
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_scan)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent(ctx, outputId, name))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_PROGRESS, n) }
        if (!ok) return
    }

    fun cancel(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(ID_PROGRESS) }
    }
}
