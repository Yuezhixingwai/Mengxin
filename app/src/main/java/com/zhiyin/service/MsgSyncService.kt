package com.zhiyin.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zhiyin.MainActivity
import com.zhiyin.ZhiYinApp
import com.zhiyin.logic.net.ApiGateway

/**
 * 后台消息同步前台服务。
 *
 * 为什么需要它：原来后台唯一的通道是 AlarmManager 的一次性闹钟，
 * 而 onReceive 里 goAsync 只有约 10 秒窗口、内部却要串行发三个网络请求，
 * 弱网一次超时就会被系统杀进程、下一次闹钟排不上，链条直接断掉 ——
 * 之后必须等用户重新打开 App 才会再拉一次，这就是"不开 App 收不到通知"。
 *
 * 为什么是 remoteMessaging 类型：targetSdk 36，Android 15+ 对 dataSync 类型的
 * 前台服务有 6 小时/24h 硬超时，remoteMessaging 专为「从服务器接收消息」设计，无超时限制。
 *
 * 通知本身是 IMPORTANCE_MIN 的静默常驻项，不响不震、折叠显示，只保证服务不被回收。
 */
class MsgSyncService : Service() {

    @Volatile
    private var running = false

    @Volatile
    private var foregroundStarted = false

    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 渠道必须存在，否则 startForeground 会抛异常
        com.zhiyin.MsgNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (!isEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            worker = Thread({ loop() }, "lingxin-msg-sync").also { it.start() }
        }
        return START_STICKY
    }

    private fun loop() {
        while (running) {
            if (!isEnabled()) {
                stopSelf()
                return
            }
            try {
                // 复用广播接收器里的同一套拉取逻辑（含去重与到货通知）
                MessageSyncReceiver().poll(this)
            } catch (_: Throwable) {
                // 单轮失败不影响下一轮，服务本身绝不能因为一次网络异常退出
            }
            try {
                Thread.sleep(LOOP_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun isEnabled(): Boolean {
        val enabled = getSharedPreferences("zhiyin", MODE_PRIVATE)
            .getBoolean("notify_enabled", true)
        if (!enabled) return false
        return !ApiGateway.getToken(this).isNullOrEmpty()
    }

    private fun startAsForeground() {
        if (foregroundStarted) return
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                )
            } else {
                startForeground(FOREGROUND_ID, notification)
            }
            foregroundStarted = true
        } catch (_: Exception) {
            // 类型不匹配或权限缺失时不要留下一个前台状态不明的服务
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            8802,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, ZhiYinApp.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("灵心")
            .setContentText("消息同步中")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        foregroundStarted = false
        super.onDestroy()
    }

    companion object {
        private const val FOREGROUND_ID = 8801

        /** 主动消息由服务端每 10 分钟生成一轮，2 分钟拉一次足够及时，也避免把服务器和电量打满。 */
        private const val LOOP_INTERVAL_MS = 120_000L

        fun start(context: Context) {
            val intent = Intent(context, MsgSyncService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Android 12+ 允许后台启动前台服务的场景有限（BOOT_COMPLETED 属豁免，
                // 其他广播可能被拒），失败就交给闹钟兜底，不能影响主流程。
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                }
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MsgSyncService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
