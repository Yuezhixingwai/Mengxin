package com.zhiyin.data

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

object PrivacyManager {
    private const val PREFS = "zhiyin_privacy"
    private var appContext: Context? = null

    var secureFlagOn by mutableStateOf(false)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        secureFlagOn = prefs().getBoolean("secure_flag", false)
    }

    private fun prefs() = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setSecureFlag(on: Boolean) {
        prefs().edit().putBoolean("secure_flag", on).apply()
        secureFlagOn = on
    }

    fun isLockEnabled(): Boolean = prefs().getBoolean("lock_enabled", false)

    fun isBiometricEnabled(): Boolean = prefs().getBoolean("biometric_enabled", false)

    fun pinLength(): Int = prefs().getInt("pin_len", 0)

    fun canBiometric(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val bm = appContext!!.getSystemService(BiometricManager::class.java) ?: return false
        return bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enable(pin: String, biometric: Boolean) {
        val salt = (0 until 16).joinToString("") { "%02x".format((0..255).random()) }
        prefs().edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", hash(pin, salt))
            .putInt("pin_len", pin.length)
            .putBoolean("lock_enabled", true)
            .putBoolean("biometric_enabled", biometric && canBiometric())
            .apply()
    }

    fun setBiometric(enabled: Boolean) {
        prefs().edit().putBoolean("biometric_enabled", enabled).apply()
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
