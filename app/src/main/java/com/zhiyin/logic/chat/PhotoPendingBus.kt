package com.zhiyin.logic.chat

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 生图占位状态（2026-10-24）
 * 服务端接受生图后回 photo_pending + photo_hint + photo_eta，客户端先在消息列表末尾显示一个
 * "在拍了…"的灰块（带预估秒数），图 ready 后清掉占位、插入真正的图片消息。
 * 用全局单例是因为同一时刻只会有一个人设在生图。
 */
object PhotoPendingBus {

    data class State(
        val persona: String,
        val hint: String,
        val etaSec: Int,
        val startedAt: Long = System.currentTimeMillis(),
    )

    val state = MutableStateFlow<State?>(null)

    fun start(persona: String, hint: String, etaSec: Int) {
        val h = if (hint.isBlank()) "在拍了，稍等一下" else hint
        val e = if (etaSec <= 0) 50 else etaSec
        state.value = State(persona ?: "", h, e)
    }

    fun clear(persona: String) {
        val cur = state.value ?: return
        if (cur.persona == (persona ?: "")) state.value = null
    }
}
