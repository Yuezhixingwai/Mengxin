package com.zhiyin.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtils {

    fun compressJpeg(src: Bitmap, maxDim: Int, maxBytes: Int, qualityStart: Int = 88): ByteArray {
        var bmp = src
        val w = bmp.width
        val h = bmp.height
        val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h).toFloat())
        if (scale < 1f) {
            bmp = Bitmap.createScaledBitmap(
                bmp, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true
            )
        }
        var quality = qualityStart
        var out: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            out = baos.toByteArray()
            quality -= 10
        } while (out.size > maxBytes && quality >= 40)
        if (bmp !== src) bmp.recycle()
        return out
    }

    fun compressPng(src: Bitmap, maxDim: Int, maxBytes: Int): ByteArray {
        var bmp = src
        var curMax = maxDim
        var out: ByteArray
        do {
            val w = bmp.width
            val h = bmp.height
            val scale = minOf(1f, curMax.toFloat() / maxOf(w, h).toFloat())
            val cur = if (scale < 1f) {
                Bitmap.createScaledBitmap(bmp, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
            } else bmp
            val baos = ByteArrayOutputStream()
            cur.compress(Bitmap.CompressFormat.PNG, 100, baos)
            out = baos.toByteArray()
            if (cur !== bmp) cur.recycle()
            curMax = (curMax * 0.8f).toInt().coerceAtLeast(128)
        } while (out.size > maxBytes && curMax >= 128)
        return out
    }

    fun decodeSampled(path: String, maxDim: Int = 2048): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    fun decodeSampledFile(file: File, maxDim: Int = 2048): Bitmap? = decodeSampled(file.absolutePath, maxDim)

    object Limits {
        const val COVER_MAX_DIM = 1080
        const val COVER_MAX_BYTES = 800 * 1024
        const val AVATAR_MAX_DIM = 512
        const val AVATAR_MAX_BYTES = 300 * 1024
        const val BACKGROUND_MAX_DIM = 1280
        const val BACKGROUND_MAX_BYTES = 1200 * 1024
        const val STICKER_MAX_DIM = 512
        const val STICKER_MAX_BYTES = 300 * 1024
    }
}
