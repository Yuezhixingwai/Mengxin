package com.zhiyin

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
        trackForeground()
        fetchClientConfig()
    }

    private var startedActivities = 0

    /**
     * 记录 App 是否真的在用户眼前。
     *
     * 通知要不要弹取决于「App 是否可见」，而不是「最后一次打开过哪个会话」。
     * 之前只靠 MsgRepo.activeSessionId 判断，而 leave() 从未被调用，
     * 一旦进过某个角色的聊天页，她的主动消息就会被永久当成「正在看」而静默。
     */
    private fun trackForeground() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isAppVisible = true
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
                if (startedActivities == 0) isAppVisible = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
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

        /** 是否有 Activity 处于前台（按计数实现，覆盖多 Activity / 分屏场景）。 */
        @Volatile
        var isAppVisible: Boolean = false
            private set
    }
}
