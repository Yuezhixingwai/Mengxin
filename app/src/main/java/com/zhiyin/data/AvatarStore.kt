package com.zhiyin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.zhiyin.logic.net.ApiGateway
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object AvatarStore {

    val AVATAR_COLORS = intArrayOf(
        0xFFFF6B6B.toInt(), 0xFF4ECDC4.toInt(), 0xFF45B7D1.toInt(), 0xFF96CEB4.toInt(), 0xFFFECA57.toInt(),
        0xFFDDA0DD.toInt(), 0xFF98D8C8.toInt(), 0xFFF7DC6F.toInt(), 0xFF82E0AA.toInt(), 0xFF85C1E9.toInt(),
        0xFFF0B27A.toInt(), 0xFFD7BDE2.toInt(), 0xFFA3E4D7.toInt(), 0xFFFAD7A0.toInt(), 0xFFA9CCE3.toInt(),
        0xFFE8DAEF.toInt(), 0xFFD5F5E3.toInt(), 0xFFFCF3CF.toInt(), 0xFFD6EAF8.toInt(), 0xFFFAE5D3.toInt()
    )

    private val memoryCache = ConcurrentHashMap<Int, ImageBitmap>()

    fun avatarsDir(ctx: Context): File {
        val d = File(ctx.filesDir, "avatars")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun personaAvatarFile(ctx: Context, contactId: Int): File =
        File(avatarsDir(ctx), "persona_${contactId}_avatar.jpg")

    fun userAvatarFile(ctx: Context): File = File(avatarsDir(ctx), "user_avatar.jpg")

    fun colorFor(name: String?): Int {
        val key = name ?: ""
        return AVATAR_COLORS[((key.hashCode() and 0x7FFFFFFF)) % AVATAR_COLORS.size]
    }

    fun cropCircle(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (src.width - size) / 2
        val top = (src.height - size) / 2
        canvas.drawBitmap(src, -left.toFloat(), -top.toFloat(), paint)
        return output
    }

    fun loadPersonaAvatar(
        ctx: Context,
        contactId: Int,
        onLoaded: (ImageBitmap?) -> Unit,
    ) {
        memoryCache[contactId]?.let { onLoaded(it); return }
        val local = personaAvatarFile(ctx, contactId)
        if (local.exists()) {
            val bmp = BitmapFactory.decodeFile(local.absolutePath)
            if (bmp != null) {
                val img = cropCircle(bmp).asImageBitmap()
                memoryCache[contactId] = img
                onLoaded(img)
                return
            }
        }
        thread {
            try {
                val data = ApiGateway.getRaw("/api/user/persona/$contactId/avatar", ctx)
                if (data != null && data.isNotEmpty()) {
                    FileOutputStream(local).use { it.write(data) }
                    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bmp != null) {
                        val img = cropCircle(bmp).asImageBitmap()
                        memoryCache[contactId] = img
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onLoaded(img) }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun invalidate(contactId: Int) {
        memoryCache.remove(contactId)
    }

    fun invalidateUser() {
        memoryCache.remove(USER_AVATAR_KEY)
    }

    fun loadUserAvatar(
        ctx: Context,
        avatarUrl: String?,
        onLoaded: (ImageBitmap?) -> Unit,
    ) {
        memoryCache[USER_AVATAR_KEY]?.let { onLoaded(it); return }
        val local = userAvatarFile(ctx)
        if (local.exists()) {
            val bmp = BitmapFactory.decodeFile(local.absolutePath)
            if (bmp != null) {
                val img = bmp.asImageBitmap()
                memoryCache[USER_AVATAR_KEY] = img
                onLoaded(img)
                return
            }
        }
        if (avatarUrl.isNullOrEmpty()) {
            onLoaded(null)
            return
        }
        thread {
            try {
                val data = ApiGateway.getRaw(avatarUrl, ctx)
                if (data != null && data.isNotEmpty()) {
                    FileOutputStream(local).use { it.write(data) }
                    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bmp != null) {
                        val img = bmp.asImageBitmap()
                        memoryCache[USER_AVATAR_KEY] = img
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onLoaded(img) }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun uploadPersonaAvatar(ctx: Context, contactId: Int, bytes: ByteArray, onDone: (Boolean, String?) -> Unit) {
        thread {
            try {
                ApiGateway.postMultipart("/api/user/persona/$contactId/avatar", "avatar", bytes, ctx)
                FileOutputStream(personaAvatarFile(ctx, contactId)).use { it.write(bytes) }
                invalidate(contactId)
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(true, null) }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(false, e.message) }
            }
        }
    }

    fun uploadUserAvatar(ctx: Context, bytes: ByteArray, onDone: (Boolean, String?) -> Unit) {
        thread {
            try {
                ApiGateway.postMultipart("/api/user/avatar", "avatar", bytes, ctx)
                FileOutputStream(userAvatarFile(ctx)).use { it.write(bytes) }
                invalidateUser()
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(true, null) }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(false, e.message) }
            }
        }
    }

    fun toDrawable(ctx: Context, bmp: Bitmap): BitmapDrawable = BitmapDrawable(ctx.resources, bmp)

    private const val USER_AVATAR_KEY = -1
}
