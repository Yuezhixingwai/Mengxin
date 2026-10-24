package com.zhiyin.service

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App lock settings stay on the device and are never sent to the model or server. */
object AppLockStore {
    private const val PREFS = "zhiyin_app_lock"
    private const val ITERATIONS = 120_000

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPin(ctx: Context): Boolean = prefs(ctx).getString("pin_hash", null) != null

    fun setPin(ctx: Context, pin: String): Boolean {
        if (hasPin(ctx) || !pin.matches(Regex("[0-9]{4,8}"))) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs(ctx).edit().putString("pin_salt", salt.toHex())
            .putString("pin_hash", hash.toHex()).apply()
        return true
    }

    fun verify(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString("pin_salt", null)?.hexBytes() ?: return false
        val expected = p.getString("pin_hash", null)?.hexBytes() ?: return false
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    fun lockedPackages(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("packages", emptySet())?.toSet() ?: emptySet()

    fun aiAllowedPackages(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("ai_allowed_packages", emptySet())?.toSet() ?: emptySet()

    fun setAiAllowed(ctx: Context, packageName: String, allowed: Boolean) {
        if (!hasPin(ctx) || packageName == ctx.packageName ||
            !packageName.matches(Regex("[a-zA-Z0-9_.]+"))) return
        val next = aiAllowedPackages(ctx).toMutableSet()
        if (allowed) next.add(packageName) else next.remove(packageName)
        prefs(ctx).edit().putStringSet("ai_allowed_packages", next).apply()
    }

    fun lockFromAi(ctx: Context, packageName: String, personaName: String, reason: String): Boolean {
        if (!hasPin(ctx) || packageName !in aiAllowedPackages(ctx) ||
            packageName == ctx.packageName || packageName == "com.android.settings" ||
            packageName == "com.android.systemui") return false
        setLocked(ctx, packageName, true)
        if (!isLocked(ctx, packageName)) return false
        prefs(ctx).edit().putString("ai_owner_$packageName", personaName.take(40))
            .putString("ai_reason_$packageName", reason.take(100)).apply()
        return true
    }

    fun lockDescription(ctx: Context, packageName: String): Pair<String, String>? {
        val owner = prefs(ctx).getString("ai_owner_$packageName", null) ?: return null
        return owner to (prefs(ctx).getString("ai_reason_$packageName", "") ?: "")
    }

    fun isLocked(ctx: Context, packageName: String): Boolean =
        hasPin(ctx) && lockedPackages(ctx).contains(packageName)

    fun setLocked(ctx: Context, packageName: String, locked: Boolean) {
        if (!hasPin(ctx) || packageName == ctx.packageName ||
            !packageName.matches(Regex("[a-zA-Z0-9_.]+"))) return
        val next = lockedPackages(ctx).toMutableSet()
        if (locked) next.add(packageName) else next.remove(packageName)
        val edit = prefs(ctx).edit().putStringSet("packages", next)
        if (!locked) edit.remove("ai_owner_$packageName").remove("ai_reason_$packageName")
        edit.apply()
    }

    fun clear(ctx: Context, currentPin: String): Boolean {
        if (!verify(ctx, currentPin)) return false
        prefs(ctx).edit().clear().apply()
        return true
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexBytes(): ByteArray? = try {
        if (length % 2 != 0) null else chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    } catch (_: Exception) { null }
}
