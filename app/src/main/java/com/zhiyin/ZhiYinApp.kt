package com.zhiyin

import android.app.Application
import com.zhiyin.logic.AppHolder
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.logic.util.StickerManager
import org.json.JSONObject
import kotlin.concurrent.thread

class ZhiYinApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppHolder.init(this)
        ApiGateway.init(this)
        StickerManager.init(this)
        MsgNotifications.createChannels(this)
        fetchClientConfig()
    }

    private fun fetchClientConfig() {
        thread {
            try {
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/config/client-config", "GET", null, null
                )
                val json = JSONObject(resp)
                val server = json.optString("server_url", "")
                if (server.isNotEmpty()) ApiGateway.setServerUrl(this, server)
                val mem = json.optString("memory_service_url", "")
                if (mem.isNotEmpty()) ApiGateway.setMemoryServiceUrl(this, mem)
            } catch (_: Exception) {
            }
            com.zhiyin.logic.util.StickerManager.syncDefaultPackMeta(this)
        }
    }

    companion object {
        const val CHANNEL_AI_REPLY = "ai_reply_channel"
        const val CHANNEL_SERVICE = "message_notify_service"
    }
}
