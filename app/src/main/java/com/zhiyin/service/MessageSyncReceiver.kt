package com.zhiyin.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.net.ApiGateway
import org.json.JSONObject

object MessageSyncScheduler {
    private const val REQUEST_CODE = 2001
    private const val INTERVAL_MS = 3 * 60 * 1000L

    fun start(context: Context) {
        val app = context.applicationContext
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
        val pending = goAsync()
        Thread {
            try {
                poll(context)
            } catch (_: Exception) {
            } finally {
                MessageSyncScheduler.schedule(context)
                pending.finish()
            }
        }.start()
    }

    fun poll(context: Context) {
        val token = ApiGateway.getToken(context) ?: return
        if (token.isEmpty()) return
        pollLockCommand(context, token)
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
        if (pullExternal(context, arrived)) changed = true
        if (changed) {
            com.zhiyin.logic.chat.ChatEngine.notifyMessagesChanged()
            notifyArrivals(context, arrived)
        }
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
        for ((persona, content) in arrived) {
            if (active == "persona_$persona") continue
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
}

class MessageSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val notificationsEnabled = context.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .getBoolean("notify_enabled", true)
        if (notificationsEnabled && !ApiGateway.getToken(context).isNullOrEmpty()) {
            MessageSyncScheduler.start(context)
        }
    }
}
