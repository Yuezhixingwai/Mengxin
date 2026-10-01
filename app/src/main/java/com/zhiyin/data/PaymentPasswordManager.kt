package com.zhiyin.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

object PaymentPasswordManager {
    private const val PREFS = "zhiyin_paypass"
    private var appContext: Context? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        enabled = prefs().getBoolean("enabled", false)
    }

    private fun prefs() = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSet(): Boolean = enabled

    fun pinLength(): Int = prefs().getInt("pin_len", 0)

    fun set(pin: String) {
        if (pin.isEmpty()) return
        val salt = (0 until 16).joinToString("") { "%02x".format((0..255).random()) }
        prefs().edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", hash(pin, salt))
            .putInt("pin_len", pin.length)
            .putBoolean("enabled", true)
            .apply()
        enabled = true
    }

    fun clear() {
        prefs().edit().clear().apply()
        enabled = false
    }

    fun verify(pin: String): Boolean {
        val salt = prefs().getString("pin_salt", "") ?: ""
        val saved = prefs().getString("pin_hash", "") ?: ""
        return saved.isNotEmpty() && hash(pin, salt) == saved
    }

    private fun hash(pin: String, salt: String): String {
        var data = (salt + pin).toByteArray()
        repeat(2048) {
            data = MessageDigest.getInstance("SHA-256").digest(data)
        }
        return data.joinToString("") { "%02x".format(it) }
    }
}