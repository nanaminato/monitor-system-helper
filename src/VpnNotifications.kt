package local.helperblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal fun captureNotification(context: Context): Notification {
    val channel = "capture"
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
        NotificationChannel(channel, "应用通信监控", NotificationManager.IMPORTANCE_LOW)
    )
    val open = PendingIntent.getActivity(
        context, 0, Intent(context, MonitorActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )
    val stop = PendingIntent.getService(
        context, 0, Intent(context, CaptureService::class.java).setAction(CaptureService.STOP),
        PendingIntent.FLAG_IMMUTABLE
    )
    return Notification.Builder(context, channel)
        .setContentIntent(open)
        .setOngoing(true)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("正在记录所选应用的通信")
        .addAction(Notification.Action.Builder(null, "停止抓包", stop).build())
        .build()
}
