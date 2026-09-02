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
    private const val INTERVAL_MS = 10 * 60 * 1000L

    fun start(context: Context) {
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
                pollSilently(context)
            } catch (_: Exception) {
            } finally {
                MessageSyncScheduler.start(context)
                pending.finish()
            }
        }.start()
    }

    private fun pollSilently(context: Context) {
        val token = ApiGateway.getToken(context) ?: return
        if (token.isEmpty()) return
        try {
            val resp = ApiGateway.getSync(ApiGateway.ZHIYIN_BASE + "/api/chat/active-messages", token)
            val messages = JSONObject(resp).optJSONArray("messages") ?: return
            val seen = context.getSharedPreferences("zhiyin_sync", 0)
            var seenSet = seen.getStringSet("seen", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (seenSet.size > 400) seenSet = mutableSetOf()
            var dirty = false
            for (i in 0 until messages.length()) {
                val msg = messages.getJSONObject(i)
                val persona = msg.optString("persona_name", "")
                val content = msg.optString("content", "").trim()
                if (persona.isEmpty() || content.isEmpty()) continue
                val time = parseMsgTime(msg)
                val key = "$persona|$time|${content.hashCode()}"
                if (seenSet.contains(key)) continue
                seenSet.add(key)
                dirty = true
                MsgRepo.addSilent(context, "persona_$persona", "ai", content, time)
            }
            if (dirty) seen.edit().putStringSet("seen", seenSet).apply()
        } catch (_: Exception) {
        }
    }

    private fun parseMsgTime(msg: JSONObject): Long {
        val t = msg.optLong("time", 0L)
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
        return System.currentTimeMillis()
    }
}
