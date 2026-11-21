package com.zhiyin.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.net.ApiGateway
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object MessageSyncScheduler {
    private const val REQUEST_CODE = 2001

    /**
     * 前台服务接管实时拉取（[MsgSyncService]）之后，闹钟只是「服务被系统或 ROM 杀掉」时的兜底。
     * 原来是 3 分钟，但 Doze 下 setAndAllowWhileIdle 本来就会被压到 9-15 分钟，
     * 定成 10 分钟既符合系统行为，也少掉大量无效唤醒。
     */
    private const val INTERVAL_MS = 10 * 60 * 1000L

    fun start(context: Context) {
        val app = context.applicationContext
        MsgSyncService.start(app)
        schedule(app)
        Thread {
            try {
                MessageSyncReceiver().poll(app)
            } catch (_: Exception) {
            }
        }.start()
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            buildPendingIntent(context),
        )
    }

    fun stop(context: Context) {
        MsgSyncService.stop(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, MessageSyncReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class MessageSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 关键：先把下一次闹钟排上，再去拉数据。
        // 原来只在 finally 里排，而 goAsync 只有约 10 秒窗口、内部却要串行发三个网络请求，
        // 弱网一次超时就会被系统直接杀进程，finally 执行不到 → 闹钟链从此断裂，
        // 必须等用户重新打开 App 才会恢复。先排一次，链就不会断。
        MessageSyncScheduler.schedule(context)
        val pending = goAsync()
        Thread {
            try {
                poll(context, POLL_BUDGET_MS)
            } catch (_: Exception) {
            } finally {
                MessageSyncScheduler.schedule(context)
                pending.finish()
            }
        }.start()
    }

    /**
     * @param budgetMs 本轮拉取的软时间预算。前台服务和页面内调用不需要限制（传默认值），
     *                 广播里必须限制，否则会超出 goAsync 的处理窗口被系统杀进程。
     */
    fun poll(context: Context, budgetMs: Long = Long.MAX_VALUE) {
        // 前台服务与页面内轮询可能同时触发，并发读写 seen 集合会导致同一句话弹两次通知
        if (!IN_FLIGHT.compareAndSet(false, true)) return
        val deadline =
            if (budgetMs == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + budgetMs
        try {
            pollInternal(context, deadline)
        } finally {
            IN_FLIGHT.set(false)
        }
    }

    private fun pollInternal(context: Context, deadline: Long) {
        val token = ApiGateway.getToken(context) ?: return
        if (token.isEmpty()) return
        pollLockCommand(context, token)
        if (overBudget(deadline)) return
        var changed = false
        val arrived = LinkedHashMap<String, String>()
        try {
            val resp = ApiGateway.getSync(ApiGateway.ZHIYIN_BASE + "/api/chat/active-messages", token)
            val messages = JSONObject(resp).optJSONArray("messages")
            if (messages != null) {
                val seen = context.getSharedPreferences("zhiyin_sync", 0)
                var seenSet = seen.getStringSet("seen", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (seenSet.size > 400) seenSet = mutableSetOf()
                val batches = LinkedHashMap<String, MutableList<MsgRepo.RemoteMsg>>()
                var dirty = false
                for (i in 0 until messages.length()) {
                    val msg = messages.getJSONObject(i)
                    val persona = msg.optString("persona_name", "")
                    val content = msg.optString("content", "").trim()
                    if (persona.isEmpty() || content.isEmpty()) continue
                    val time = parseMsgTime(msg)
                    val key = if (time > 0) "$persona|$time|${content.hashCode()}" else "$persona|${content.hashCode()}"
                    if (seenSet.contains(key)) continue
                    seenSet.add(key)
                    dirty = true
                    batches.getOrPut(persona) { mutableListOf() }.add(MsgRepo.RemoteMsg("ai", content, time))
                    arrived[persona] = content
                }
                for ((persona, batch) in batches) {
                    if (MsgRepo.addRemoteBatchIfAbsent(context, "persona_$persona", batch)) changed = true
                }
                if (dirty) seen.edit().putStringSet("seen", seenSet).apply()
            }
        } catch (_: Exception) {
        }
        if (overBudget(deadline)) {
            if (changed) {
                com.zhiyin.logic.chat.ChatEngine.notifyMessagesChanged()
                notifyArrivals(context, arrived)
            }
            return
        }
        if (pullExternal(context, arrived)) changed = true
        if (changed) {
            com.zhiyin.logic.chat.ChatEngine.notifyMessagesChanged()
            notifyArrivals(context, arrived)
        }
        if (overBudget(deadline)) return
        try {
            val resp = ApiGateway.getSync(ApiGateway.ZHIYIN_BASE + "/api/notifications/unread-count", token)
            val unread = JSONObject(resp).optInt("unread", 0)
            val prefs = context.getSharedPreferences("zhiyin_sync", 0)
            val lastNotified = prefs.getLong("notify_last_unread", 0L)
            if (unread > 0 && unread.toLong() != lastNotified) {
                prefs.edit().putLong("notify_last_unread", unread.toLong()).apply()
                com.zhiyin.MsgNotifications.showPlazaNotify(context, unread)
            } else if (unread == 0 && lastNotified != 0L) {
                prefs.edit().putLong("notify_last_unread", 0L).apply()
            }
        } catch (_: Exception) {
        }
    }

    private fun overBudget(deadline: Long): Boolean =
        deadline != Long.MAX_VALUE && System.currentTimeMillis() > deadline

    private fun pollLockCommand(context: Context, token: String) {
        if (!com.zhiyin.yandere.YandereManager.isEnabled(context) ||
            !com.zhiyin.yandere.YandereManager.isDeviceAdminActive(context)) return
        try {
            val response = ApiGateway.getSync(ApiGateway.ZHIYIN_BASE + "/api/yandere/command", token)
            val command = JSONObject(response)
            val timestamp = command.optLong("timestamp", 0L)
            val prefs = context.getSharedPreferences("zhiyin_sync", Context.MODE_PRIVATE)
            if (command.optString("action") != "lock" || timestamp <= prefs.getLong("last_lock_command", 0L)) return
            // Never replay a stale remote lock command after a long offline period.
            if (System.currentTimeMillis() - timestamp !in 0..(2 * 60 * 1000L)) return
            prefs.edit().putLong("last_lock_command", timestamp).apply()
            if (com.zhiyin.yandere.YandereManager.lockScreen(context)) {
                ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/yandere/clear", "{}", token)
            }
        } catch (_: Exception) {
        }
    }

    private fun pullExternal(context: Context, arrived: MutableMap<String, String>): Boolean {
        return try {
            ApiGateway.ensureMemoryServiceUrl(context)
            val base = ApiGateway.getMemoryServiceUrl()
            val userId = ApiGateway.getUserId(context)
            if (base.isNullOrEmpty() || userId.isNullOrEmpty()) return false
            val response = ApiGateway.memoryRequestSync("$base/api/external-messages", "GET", null, userId)
            val messages = JSONObject(response).optJSONArray("messages") ?: return false
            val batches = LinkedHashMap<String, MutableList<MsgRepo.RemoteMsg>>()
            for (i in 0 until messages.length()) {
                val m = messages.optJSONObject(i) ?: continue
                val sid = m.optString("sessionId", "")
                if (!sid.startsWith("persona_")) continue
                val content = m.optString("content", "")
                if (content.isBlank()) continue
                val role = if (m.optString("role") == "user") "user" else "ai"
                val time = parseMsgTime(m)
                batches.getOrPut(sid) { mutableListOf() }.add(MsgRepo.RemoteMsg(role, content, time))
                if (role == "ai") arrived[sid.removePrefix("persona_")] = content
            }
            var changed = false
            for ((sid, batch) in batches) {
                if (MsgRepo.addRemoteBatchIfAbsent(context, sid, batch)) changed = true
            }
            changed
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyArrivals(context: Context, arrived: Map<String, String>) {
        val enabled = context.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .getBoolean("notify_enabled", true)
        if (!enabled) return
        val active = MsgRepo.getActiveSession()
        val appVisible = com.zhiyin.ZhiYinApp.isAppVisible
        for ((persona, content) in arrived) {
            // 只有「App 真的在前台，且正看着这个会话」才静默，其余情况（后台、锁屏、桌面）一律通知。
            // 原实现只看 activeSessionId，而 leave() 从未被调用，activeSessionId 会永久停在
            // 最后打开过的那个角色上 —— 她的消息从此一条通知都不会弹。
            if (appVisible && active == "persona_$persona") continue
            com.zhiyin.MsgNotifications.showAiReply(
                context, persona, com.zhiyin.logic.chat.ChatEngine.previewText(content), -1
            )
        }
    }

    private fun parseMsgTime(msg: JSONObject): Long {
        val t = MsgRepo.normalizeEpoch(msg.optLong("time", 0L))
        if (t > 0) return t
        val s = msg.optString("created_at", "")
        if (s.isNotEmpty()) {
            try {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                val d = fmt.parse(s)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    companion object {
        /** goAsync 的处理窗口约 10 秒，留 2 秒余量。 */
        private const val POLL_BUDGET_MS = 8_000L

        private val IN_FLIGHT = AtomicBoolean(false)
    }
}

class MessageSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val notificationsEnabled = context.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .getBoolean("notify_enabled", true)
        if (notificationsEnabled && !ApiGateway.getToken(context).isNullOrEmpty()) {
            // BOOT_COMPLETED 属于「后台启动前台服务」的豁免场景，这里可以直接把服务拉起来
            MessageSyncScheduler.start(context)
        }
    }
}
