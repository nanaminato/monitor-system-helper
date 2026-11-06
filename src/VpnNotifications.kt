package local.helperblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal fun blockerNotification(context: Context): Notification =
    notification(context, "block", "Network blocker", MainActivity::class.java)
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setContentTitle("SystemHelper network blocker")
        .build()

internal fun captureNotification(context: Context): Notification {
    val stop = PendingIntent.getService(
        context, 0, Intent(context, CaptureService::class.java).setAction(CaptureService.STOP),
        PendingIntent.FLAG_IMMUTABLE
    )
    return notification(context, "capture", "应用通信监控", MonitorActivity::class.java)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("正在记录所选应用的通信")
        .addAction(Notification.Action.Builder(null, "停止抓包", stop).build())
        .build()
}

private fun notification(
    context: Context, channel: String, name: String, activity: Class<*>
): Notification.Builder {
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
        NotificationChannel(channel, name, NotificationManager.IMPORTANCE_LOW)
    )
    val open = PendingIntent.getActivity(
        context, 0, Intent(context, activity), PendingIntent.FLAG_IMMUTABLE
    )
    return Notification.Builder(context, channel).setContentIntent(open).setOngoing(true)
}
