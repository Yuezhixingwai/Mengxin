package com.zhiyin.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun MessageEntrance(mine: Boolean, animate: Boolean, content: @Composable () -> Unit) {
    if (!animate) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
        return
    }
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            played = true
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationX = (1f - progress.value) * (if (mine) 28.dp.toPx() else -28.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

object VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var startTime = 0L
    private var path: String? = null

    @Suppress("DEPRECATION")
    fun start(ctx: Context): String? {
        return try {
            val p = ctx.cacheDir.absolutePath + "/voice_" + System.currentTimeMillis() + ".m4a"
            val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(128000)
            r.setOutputFile(p)
            r.prepare()
            r.start()
            recorder = r
            path = p
            startTime = System.currentTimeMillis()
            p
        } catch (_: Exception) {
            release()
            null
        }
    }

    fun stop(): Pair<String, Long>? {
        val p = path
        try {
            recorder?.stop()
        } catch (_: Exception) {
            path = null
        }
        release()
        if (p == null) return null
        val durationSec = (System.currentTimeMillis() - startTime) / 1000
        return if (durationSec < 1) null else Pair(p, durationSec)
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        release()
    }

    private fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        path = null
    }
}

object ContentCopy {

    data class Copied(val path: String, val displayName: String)

    fun copyToCache(ctx: Context, uri: Uri, prefix: String): Copied? {
        return try {
            var name = "file_${System.currentTimeMillis()}"
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val n = cursor.getString(idx)
                    if (!n.isNullOrEmpty()) name = n
                }
            }
            val safe = name.replace(Regex("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            val cacheFile = File(ctx.cacheDir, "${prefix}_$safe")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Copied(cacheFile.absolutePath, safe)
        } catch (_: Exception) {
            null
        }
    }
}
