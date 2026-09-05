package com.zhiyin.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.zhiyin.logic.net.ApiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object RemoteImageCache {
    private const val MAX_MEMORY_ENTRIES = 24
    private val memory: MutableMap<String, ImageBitmap> = java.util.Collections.synchronizedMap(LinkedHashMap())

    fun get(key: String): ImageBitmap? = synchronized(memory) { memory[key] }
    fun put(key: String, bmp: ImageBitmap) {
        synchronized(memory) {
            memory[key] = bmp
            if (memory.size > MAX_MEMORY_ENTRIES) {
                val it = memory.entries.iterator()
                var remove = memory.size - MAX_MEMORY_ENTRIES / 2
                while (remove > 0 && it.hasNext()) { it.next(); it.remove(); remove-- }
            }
        }
    }

    fun diskDir(ctx: android.content.Context): File =
        File(ctx.filesDir, "plaza_img").apply { if (!exists()) mkdirs() }

    fun fileFor(ctx: android.content.Context, url: String): File {
        val md5 = MessageDigest.getInstance("MD5").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(diskDir(ctx), md5 + ".img")
    }

    fun absolute(url: String): String =
        if (url.startsWith("http")) url else ApiGateway.getBaseUrl() + url
}

private fun decodeSampled(data: ByteArray, maxDim: Int): android.graphics.Bitmap? {
    return try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable () -> Unit)? = null,
    maxDim: Int = 1440,
) {
    val ctx = LocalContext.current
    val abs = remember(url) { if (url.isBlank()) "" else RemoteImageCache.absolute(url) }
    val cacheKey = remember(abs, maxDim) { if (abs.isEmpty()) "" else "$abs@$maxDim" }
    val bmp by produceState<ImageBitmap?>(
        initialValue = if (cacheKey.isEmpty()) null else RemoteImageCache.get(cacheKey),
        key1 = cacheKey,
    ) {
        if (cacheKey.isEmpty()) return@produceState
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    val cacheFile = RemoteImageCache.fileFor(ctx, abs)
                    val data = if (cacheFile.exists() && cacheFile.length() > 0) {
                        cacheFile.readBytes()
                    } else {
                        val d = ApiGateway.getRaw(abs, ctx)
                        if (d != null && d.isNotEmpty()) {
                            try { cacheFile.writeBytes(d) } catch (_: Exception) {}
                        }
                        d
                    }
                    if (data != null && data.isNotEmpty()) {
                        decodeSampled(data, maxDim)?.asImageBitmap()?.also {
                            RemoteImageCache.put(cacheKey, it)
                        }
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = bmp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "remoteImage",
        ) { cur ->
            if (cur != null) {
                Image(
                    bitmap = cur,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                if (placeholder != null) placeholder()
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
    }
}
