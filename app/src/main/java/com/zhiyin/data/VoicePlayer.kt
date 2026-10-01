package com.zhiyin.data

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

object VoicePlayer {

    private var player: MediaPlayer? = null

    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath

    fun toggle(path: String) {
        if (_playingPath.value == path) {
            stop()
            return
        }
        stop()
        if (path.startsWith("http")) {
            thread {
                try {
                    val ctx = com.zhiyin.logic.AppHolder.app() ?: return@thread
                    val cache = java.io.File(ctx.cacheDir, "dl_voice_" + path.hashCode() + ".mp3")
                    if (!cache.exists() || cache.length() == 0L) {
                        val data = com.zhiyin.logic.net.ApiGateway.getRaw(path, ctx)
                        if (data == null || data.isEmpty()) return@thread
                        java.io.FileOutputStream(cache).use { it.write(data) }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post { playFile(cache.absolutePath) }
                } catch (_: Exception) {
                }
            }
        } else {
            playFile(path)
        }
    }

    private fun playFile(path: String) {
        try {
            val p = MediaPlayer()
            p.setDataSource(path)
            p.setOnCompletionListener {
                stop()
            }
            p.prepare()
            p.start()
            player = p
            _playingPath.value = path
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _playingPath.value = null
    }
}
