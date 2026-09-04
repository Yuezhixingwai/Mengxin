package com.zhiyin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object MsgNotifications {
    private const val NOTIFICATION_ID_BASE = 1000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val reply = NotificationChannel(
                ZhiYinApp.CHANNEL_AI_REPLY, "AI回复通知", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当AI人设回复消息时通知你"
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(reply)
            nm.deleteNotificationChannel(ZhiYinApp.CHANNEL_SERVICE)
            val service = NotificationChannel(
                ZhiYinApp.CHANNEL_SERVICE, "后台消息同步", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持应用后台静默同步消息"
                setShowBadge(false)
            }
            nm.createNotificationChannel(service)
        }
    }

    fun showAiReply(context: Context, personaName: String, reply: String, personaId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(context, ZhiYinApp.CHANNEL_AI_REPLY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(personaName)
            .setContentText(reply)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(
            NOTIFICATION_ID_BASE + if (personaId >= 0) personaId else (personaName.hashCode() % 1000).let { if (it < 0) -it else it },
            notification
        )
    }

    fun showPlazaNotify(context: Context, unread: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            7701,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(context, ZhiYinApp.CHANNEL_AI_REPLY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("灵心消息")
            .setContentText("你有 $unread 条新消息")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(7799, notification)
    }
}
