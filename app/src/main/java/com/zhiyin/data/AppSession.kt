package com.zhiyin.data

import android.content.Context
import com.zhiyin.logic.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppSession {
    private lateinit var store: SessionStore
    private lateinit var appCtx: Context

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        if (!AppSession::store.isInitialized) {
            store = SessionStore(appCtx)
        }
        _loggedIn.value = store.isLoggedIn
    }

    fun token(): String = if (AppSession::store.isInitialized) store.token else ""

    fun userId(): String = if (AppSession::store.isInitialized) store.userId else ""

    fun username(): String = if (AppSession::store.isInitialized) store.user else ""

    fun onLoggedIn(token: String, username: String, userId: String) {
        store.saveToken(token)
        store.saveUser(username)
        store.saveUserId(userId)
        _loggedIn.value = true
    }

    fun logout() {
        store.clear()
        appCtx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE).edit().clear().apply()
        _loggedIn.value = false
    }
}
