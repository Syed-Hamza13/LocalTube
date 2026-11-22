package dev.hamza.localtube.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.hamza.localtube.MainActivity
import dev.hamza.localtube.R
import dev.hamza.localtube.domain.DownloadMode

object Intents {
    const val ACTION_CANCEL_JOB = "dev.hamza.localtube.action.CANCEL_JOB"
    const val EXTRA_WORK_ID = "extra_work_id"
    const val EXTRA_SHARED_URL = "extra_shared_url"
    const val EXTRA_OPEN_DOWNLOAD = "extra_open_download"
}

/**
 * Two channels only: an always-silent progress channel (it backs the dataSync foreground
 * service) and a results channel. Silent channels keep the device from buzzing on every file.
 */
object Notifier {

    const val CHANNEL_PROGRESS = "downloads_running"
    const val CHANNEL_RESULTS = "downloads_results"

    fun ensureChannels(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_downloads_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULTS,
                context.getString(R.string.notif_channel_finished),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_finished_desc)
                setShowBadge(false)
            }
        )
    }

    fun running(
        context: Context,
        workId: String,
        title: String,
        mode: DownloadMode,
        percent: Float,
        speed: String?,
        etaSeconds: Long,
    ): Notification {
        val status = buildString {
            append(mode.short)
            append(" · ")
            append(Fmt.percent(percent))
            if (!speed.isNullOrBlank()) {
                append(" · ")
                append(speed)
            }
            if (percent in 0.01f..99.99f) {
                append(" · ETA ")
                append(Fmt.eta(etaSeconds))
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(status)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_BEHAVIOR_DEFAULT)
            .setProgress(100, percent.toInt().coerceIn(0, 100), percent <= 0f)
            .setContentIntent(openAppIntent(context, workId.hashCode()))
            .addAction(
                0,
                "Cancel",
                cancelIntent(context, workId, workId.hashCode() + 1),
            )
            .build()
    }

    fun finished(context: Context, title: String, success: Boolean, detail: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(if (success) "Downloaded" else "Download failed")
            .setContentText(if (success) title else "$title — $detail")
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (success) title else "$title — $detail"))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context, title.hashCode()))
            .build()

    fun push(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("nav_tab", "library")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelIntent(context: Context, workId: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                action = Intents.ACTION_CANCEL_JOB
                putExtra(Intents.EXTRA_WORK_ID, workId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
