package com.zhiyin.data

import com.zhiyin.logic.chat.ChatEngine
import java.util.concurrent.CopyOnWriteArrayList

object EngineBus : ChatEngine.Listener {

    private val listeners = CopyOnWriteArrayList<ChatEngine.Listener>()

    init {
        ChatEngine.setActiveListener(this)
    }

    fun register(listener: ChatEngine.Listener) {
        listeners.addIfAbsent(listener)
    }

    fun unregister(listener: ChatEngine.Listener) {
        listeners.remove(listener)
    }

    override fun onMessagesChanged() {
        listeners.forEach { it.onMessagesChanged() }
    }

    override fun onTypingChanged(typing: Boolean) {
        listeners.forEach { it.onTypingChanged(typing) }
    }

    override fun onNotice(msg: String) {
        listeners.forEach { it.onNotice(msg) }
    }

    override fun onSendingChanged(sending: Boolean) {
        listeners.forEach { it.onSendingChanged(sending) }
    }
}
