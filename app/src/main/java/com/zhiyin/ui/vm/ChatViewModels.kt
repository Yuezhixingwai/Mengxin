package com.zhiyin.ui.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhiyin.data.EngineBus
import com.zhiyin.logic.chat.ChatEngine
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.data.GroupManager
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.data.PersonaManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
data class UConv(
    val key: String,
    val name: String,
    val isGroup: Boolean,
    val friendId: Int,
    val persona: String,
    val mute: Boolean,
    val lastMessage: String,
    val time: Long,
    val unread: Int,
    val hasMessages: Boolean,
)
class ChatListViewModel(app: Application) : AndroidViewModel(app), ChatEngine.Listener {

    var conversations by mutableStateOf<List<UConv>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    init {
        EngineBus.register(this)
        refresh()
    }

    override fun onMessagesChanged() {
        refresh()
    }

    override fun onTypingChanged(typing: Boolean) {}

    override fun onNotice(msg: String) {}

    override fun onSendingChanged(sending: Boolean) {}

    fun refresh() {
        val ctx = getApplication<Application>()
        val token = com.zhiyin.data.AppSession.token()
        if (token.isEmpty()) {
            loading = false
            return
        }
        loading = true
        FriendManager.getAll(token, object : FriendManager.Callback {
            override fun onResult(list: MutableList<FriendManager.Friend>?) {
                val friends = list ?: emptyList()
                val out = mutableListOf<UConv>()
                for (f in friends) {
                    val sid = "persona_${f.name}"
                    val msgs = MsgRepo.getAll(ctx, sid)
                    val previewDefault = "打个招呼开始聊天吧"
                    val lastRaw = msgs.lastOrNull()?.get(1)
                    val preview = if (lastRaw != null) ChatEngine.previewText(lastRaw) else previewDefault
                    val time = msgs.lastOrNull()?.get(2)?.toLongOrNull() ?: 0L
                    out.add(
                        UConv(
                            key = sid,
                            name = f.name,
                            isGroup = false,
                            friendId = f.id,
                            persona = f.persona ?: "",
                            mute = f.mute,
                            lastMessage = preview.take(30),
                            time = time,
                            unread = MsgRepo.getUnreadCount(ctx, sid),
                            hasMessages = msgs.isNotEmpty(),
                        )
                    )
                }
                for (g in GroupManager.getGroupChats(ctx)) {
                    val sid = g[0]
                    val name = g[1]
                    val msgs = MsgRepo.getAll(ctx, sid)
                    val lastRaw = msgs.lastOrNull()?.get(1)
                    val preview = if (lastRaw != null) ChatEngine.previewText(lastRaw) else "群聊"
                    out.add(
                        UConv(
                            key = sid,
                            name = name,
                            isGroup = true,
                            friendId = -1,
                            persona = "",
                            mute = false,
                            lastMessage = preview.take(30),
                            time = msgs.lastOrNull()?.get(2)?.toLongOrNull() ?: 0L,
                            unread = MsgRepo.getUnreadCount(ctx, sid),
                            hasMessages = msgs.isNotEmpty(),
                        )
                    )
                }
                conversations = out.sortedWith(
                    compareByDescending<UConv> { it.hasMessages }.thenByDescending { it.time }
                )
                loading = false
            }

            override fun onError(err: String?) {
                loading = false
            }
        })
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ChatListViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }
}

data class ChatMsg(
    val index: Int,
    val role: String,
    val content: String,
    val time: Long,
)

class ChatViewModel(
    app: Application,
    val personaName: String,
    val personaDesc: String,
    val personaId: Int,
) : AndroidViewModel(app), ChatEngine.Listener {

    val sessionId = "persona_$personaName"

    val persona: ChatEngine.Persona
        get() = ChatEngine.Persona(
            personaName,
            personaDesc,
            PersonaManager.getPatByName(getApplication(), personaName),
        )

    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
        private set

    var typing by mutableStateOf(ChatEngine.sIsRequestPending.get())
        private set

    init {
        EngineBus.register(this)
    }

    fun enter() {
        MsgRepo.setActiveSession(sessionId)
        MsgRepo.markAllRead(getApplication(), sessionId)
        reload()
        ChatEngine.loadServerHistory(getApplication(), persona) {
            MsgRepo.syncFromMemoryService(getApplication(), sessionId) { changed ->
                if (changed) reload()
            }
        }
    }

    fun leave() {
        MsgRepo.setActiveSession(null)
    }

    fun reload() {
        val ctx = getApplication<Application>()
        messages = MsgRepo.getAll(ctx, sessionId).mapIndexed { i, m ->
            ChatMsg(i, m[0], m[1], m[2].toLongOrNull() ?: 0L)
        }
    }

    override fun onMessagesChanged() {
        reload()
    }

    override fun onTypingChanged(t: Boolean) {
        typing = t
    }

    override fun onNotice(msg: String) {}

    override fun onSendingChanged(sending: Boolean) {}

    fun send(text: String) = ChatEngine.sendText(getApplication(), persona, text)

    fun sendSticker(marker: String) = ChatEngine.sendText(getApplication(), persona, marker)

    fun regenerate(index: Int) = ChatEngine.regenerate(getApplication(), persona, index)

    fun deleteMessage(index: Int) {
        MsgRepo.deleteAt(getApplication(), sessionId, index)
        reload()
    }

    fun pat() = ChatEngine.patFriend(getApplication(), persona)

    fun sendImage(localPath: String) = ChatEngine.sendImage(getApplication(), persona, localPath)

    fun sendFile(fileName: String, localPath: String) =
        ChatEngine.sendFile(getApplication(), persona, fileName, localPath)

    fun sendVoice(filePath: String, durationSec: Long) =
        ChatEngine.sendVoice(getApplication(), persona, filePath, durationSec)

    fun sendTransfer(amount: Double, note: String) =
        ChatEngine.sendTransfer(getApplication(), persona, amount, note)

    fun sendRedpacket(total: Double, note: String) =
        ChatEngine.sendRedpacket(getApplication(), persona, total, note)

    fun clearChat() {
        MsgRepo.delete(getApplication(), sessionId)
        reload()
    }

    companion object {
        fun factory(name: String, desc: String, id: Int) = viewModelFactory {
            initializer {
                ChatViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                    name, desc, id
                )
            }
        }
    }
}

class GroupChatViewModel(
    app: Application,
    val groupName: String,
    initialMembers: List<String>?,
) : AndroidViewModel(app), ChatEngine.Listener {

    val sessionId = "group_$groupName"

    val group: ChatEngine.GroupInfo = ChatEngine.GroupInfo(
        groupName,
        (initialMembers?.takeIf { it.isNotEmpty() }
            ?: GroupManager.loadGroupMembers(app, groupName)).toTypedArray(),
    )

    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
        private set

    var sending by mutableStateOf(false)
        private set

    init {
        EngineBus.register(this)
        val sp = app.getSharedPreferences("zhiyin_msgs", Application.MODE_PRIVATE)
        if (!sp.contains(sessionId)) sp.edit().putString(sessionId, "[]").apply()
    }

    fun enter() {
        MsgRepo.setActiveSession(sessionId)
        MsgRepo.markAllRead(getApplication(), sessionId)
        reload()
    }

    fun reload() {
        val ctx = getApplication<Application>()
        messages = MsgRepo.getAll(ctx, sessionId).mapIndexed { i, m ->
            ChatMsg(i, m[0], m[1], m[2].toLongOrNull() ?: 0L)
        }
    }

    override fun onMessagesChanged() {
        reload()
    }

    override fun onTypingChanged(typing: Boolean) {}

    override fun onNotice(msg: String) {}

    override fun onSendingChanged(sending: Boolean) {
        this.sending = sending
    }

    fun send(text: String) = ChatEngine.groupSend(getApplication(), group, text)

    fun deleteMessage(index: Int) {
        MsgRepo.deleteAt(getApplication(), sessionId, index)
        reload()
    }

    fun leaveAndDelete() {
        GroupManager.deleteGroup(getApplication(), groupName)
    }

    fun sendRedpacket(total: Double, count: Int) =
        GroupManager.sendGroupRedpacket(getApplication(), group, total, count)

    companion object {
        fun factory(name: String, members: List<String>?) = viewModelFactory {
            initializer {
                GroupChatViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                    name, members
                )
            }
        }
    }
}

object TimeFmt {
    private val todayFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val yesterdayFmt = SimpleDateFormat("昨天", Locale.getDefault())
    private val weekFmt = SimpleDateFormat("E", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("M月d日", Locale.getDefault())
    private val fullFmt = SimpleDateFormat("yyyy.M.d HH:mm", Locale.getDefault())

    fun convListTime(epochMs: Long): String {
        if (epochMs <= 0) return ""
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        val dayDiff = daysBetween(then, now)
        return when {
            dayDiff == 0 -> todayFmt.format(Date(epochMs))
            dayDiff == 1 -> "昨天"
            dayDiff < 7 -> weekFmt.format(Date(epochMs))
            else -> dateFmt.format(Date(epochMs))
        }
    }

    fun fullTime(epochMs: Long): String =
        if (epochMs <= 0) "" else fullFmt.format(Date(epochMs))

    private fun daysBetween(a: java.util.Calendar, b: java.util.Calendar): Int {
        val aDay = a.get(java.util.Calendar.DAY_OF_YEAR)
        val bDay = b.get(java.util.Calendar.DAY_OF_YEAR)
        val aYear = a.get(java.util.Calendar.YEAR)
        val bYear = b.get(java.util.Calendar.YEAR)
        return if (aYear == bYear) bDay - aDay
        else (b.timeInMillis - a.timeInMillis).let { (it / 86400000L).toInt() }
    }
}
