package com.zhiyin.logic.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * 通话全双工引擎（对标豆包实时语音）
 *
 *  · 上行：麦克风 16k PCM 每 100ms 一片，持续推给服务端（服务端转发百炼流式识别）
 *  · 下行：服务端回推 24k PCM 音频片，AudioTrack 直接流式播放（无文件、无等待）
 *  · 打断：服务端识别出你开口（partial）即刻中止 AI 合成并回推 interrupted，客户端立即清空播放缓冲
 *  · 兜底：WebSocket 连不上时自动回退到 HTTP 引擎（CallEngine）
 */
object CallWsEngine {

    enum class Phase { IDLE, CONNECTING, DIALING, GREETING, LISTENING, THINKING, SPEAKING, ENDED }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val seconds: Int = 0,
        val subtitle: String = "",
        val level: Float = 0f,
        val micMuted: Boolean = false,
        val mode: String = "ws", // ws / http
    )

    val state = MutableStateFlow(UiState())

    private const val WS_HOST = "wss://api.zhiyin.zhendeqiang.top/ws/call"
    private const val MIC_RATE = 16000
    private const val OUT_RATE = 24000

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + Job())
    private var sessionJob: Job? = null
    private val running = AtomicBoolean(false)

    private var okHttp: OkHttpClient? = null
    private var ws: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var micThread: Thread? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var audioManager: AudioManager? = null
    private var ctxRef: Context? = null

    private var personaName = ""
    private var personaDesc = ""
    private var connectAt = 0L
    private val speaking = AtomicBoolean(false)

    // ---------- 对外 API（与 CallEngine 对齐，CallScreen 可无缝切换） ----------

    fun isVoiceConfigured(context: Context, name: String): Boolean =
        prefs(context).getString(voiceKey(name), null) != null

    fun saveVoice(context: Context, name: String, voiceId: String) {
        prefs(context).edit().putString(voiceKey(name), voiceId).apply()
    }

    fun currentVoice(context: Context, name: String): String? =
        prefs(context).getString(voiceKey(name), null)

    private fun voiceKey(name: String) = "call_voice_persona_$name"
    private fun prefs(context: Context) = context.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)

    fun start(context: Context, name: String, desc: String) {
        if (running.get()) return
        running.set(true)
        ctxRef = context.applicationContext
        personaName = name
        personaDesc = desc
        connectAt = 0L
        state.value = UiState(phase = Phase.CONNECTING)
        sessionJob = scope.launch { runSession(context.applicationContext) }
    }

    fun hangup() {
        running.set(false)
        state.value = state.value.copy(phase = Phase.ENDED)
        writeCallLog()
        try { ws?.send(JSONObject().put("type", "stop").toString()) } catch (_: Exception) {}
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        stopAudio()
        cleanup()
    }

    fun toggleMute() {
        val s = state.value
        state.value = s.copy(micMuted = !s.micMuted)
    }

    fun toggleSpeaker(on: Boolean) {
        try { audioManager?.isSpeakerphoneOn = on } catch (_: Exception) {}
    }

    /** 手动打断（服务端也会自动打断，这里只是保险） */
    fun interruptSpeaking() {
        if (!speaking.get()) return
        try { ws?.send(JSONObject().put("type", "interrupt").toString()) } catch (_: Exception) {}
        flushPlayback()
        speaking.set(false)
        state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "好，你先说～")
    }

    // ---------- 会话 ----------

    private suspend fun runSession(context: Context) {
        val token = SessionStore(context).getToken()
        if (token.isNullOrEmpty()) {
            state.value = state.value.copy(phase = Phase.ENDED, subtitle = "未登录")
            return
        }
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true

            delay(Random.nextLong(500, 1500))
            state.value = state.value.copy(phase = Phase.DIALING, subtitle = "正在等待对方接听…")

            startAudio(context)

            val ok = connectWs(context, token)
            if (!ok) {
                // 兜底：HTTP 模式
                state.value = state.value.copy(mode = "http", subtitle = "已切换兼容模式")
                stopAudio()
                running.set(false)
                CallEngine.start(context, personaName, personaDesc)
                return
            }
            // 主循环只负责计时；交互全部由 WS 事件驱动
            while (running.get()) {
                delay(1000)
                if (state.value.phase != Phase.CONNECTING && state.value.phase != Phase.IDLE) {
                    state.value = state.value.copy(seconds = state.value.seconds + 1)
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                state.value = state.value.copy(phase = Phase.ENDED, subtitle = "通话异常: ${e.message}")
                running.set(false)
            }
        }
    }

    private suspend fun connectWs(context: Context, token: String): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        okHttp = client
        val url = WS_HOST + "?token=" + token
        val done = java.util.concurrent.CountDownLatch(1)
        var ready = false

        val request = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ws = webSocket
                val sys = callStylePrompt()
                val start = JSONObject()
                    .put("type", "start")
                    .put("persona", personaName)
                    .put("system", sys)
                    .put("voice", prefs(context).getString(voiceKey(personaName), "longwan_v2") ?: "longwan_v2")
                    .put("history", loadHistory(context))
                webSocket.send(start.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try { onServerEvent(context, webSocket, JSONObject(text), done) } catch (_: Exception) {}
                if (text.contains("\"ready\"")) ready = true
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                // 24k PCM 音频片 → 直接写 AudioTrack 流式播放
                playPcm(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running.get() && !ready) {
                    state.value = state.value.copy(subtitle = "连接失败，切换兼容模式")
                }
                done.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.countDown()
            }
        }

        ws = client.newWebSocket(request, listener)
        // 等 ready（最多 10 秒）
        var waited = 0
        while (waited < 10000 && !ready && running.get()) {
            delay(100)
            waited += 100
        }
        return ready
    }

    private fun onServerEvent(context: Context, webSocket: WebSocket, m: JSONObject, done: java.util.concurrent.CountDownLatch) {
        when (m.optString("type")) {
            "ready" -> {
                connectAt = System.currentTimeMillis()
                state.value = state.value.copy(phase = Phase.GREETING, subtitle = "已接通")
                // 人设先开口
                val greet = JSONObject()
                    .put("type", "text")
                    .put("text", "[电话刚刚接通] 请你先开口，像真人接电话一样自然地先说第一句话，简短口语。")
                webSocket.send(greet.toString())
            }
            "asr_partial" -> {
                state.value = state.value.copy(subtitle = "你说：${m.optString("text")}")
            }
            "asr_final" -> {
                state.value = state.value.copy(subtitle = "你说：${m.optString("text")}")
            }
            "reply_delta" -> {
                // 字幕实时滚动
                val cur = state.value.subtitle
                val prefix = if (cur.startsWith("你说：") || cur.isEmpty() || cur.startsWith("AI：")) "AI：" else "AI："
                state.value = state.value.copy(subtitle = (if (cur.startsWith("AI：")) cur else prefix) + m.optString("text"))
            }
            "speak_start" -> {
                speaking.set(true)
                state.value = state.value.copy(phase = Phase.SPEAKING)
            }
            "speak_done" -> {
                speaking.set(false)
                state.value = state.value.copy(phase = Phase.LISTENING, subtitle = m.optString("text"))
            }
            "interrupted" -> {
                speaking.set(false)
                flushPlayback()
                state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "好，你先说～")
            }
            "error" -> {
                state.value = state.value.copy(subtitle = "出错了：" + m.optString("message"))
            }
        }
    }

    private fun loadHistory(context: Context): JSONArray {
        val arr = JSONArray()
        try {
            val history = MsgRepo.getAll(context, "persona_$personaName")
            var count = 0
            for (i in history.indices.reversed()) {
                if (count >= 6) break
                val clean = com.zhiyin.logic.chat.ChatEngine.cleanHistoryContent(context, history[i][1]) ?: continue
                arr.put(JSONObject().put("role", if (history[i][0] == "ai") "ai" else "user").put("content", clean))
                count++
            }
        } catch (_: Exception) {}
        return arr
    }

    private fun callStylePrompt(): String {
        val base = if (personaDesc.isNotBlank()) personaDesc + "\n\n" else ""
        return base + "【当前是实时语音通话，不是文字聊天】\n" +
            "你正在和用户打电话。保持你的人设性格，但说话方式必须切换成\"打电话的活人\"。\n" +
            "一、硬性规则：每次只说1~2句，每句不超过18个字；禁止旁白、动作描写、括号、星号、emoji、书面语；只输出要说出口的话。\n" +
            "二、语气：多用语气词（嗯、啊、呀、欸、诶、嘛、哈），会反问、会接话头、会撒娇，像真人煲电话粥。\n" +
            "三、情绪感应：对方低落就放轻放缓先关心；兴奋就一起嗨；烦躁就少问多哄；很久没声音就催一句\"喂？还在吗\"。\n" +
            "四、被打断时顺着对方新话题说，不要重复上一句。"
    }

    // ---------- 音频 ----------

    private fun startAudio(context: Context) {
        // 播放：24k 单声道 16bit PCM 流式
        try {
            val outBuf = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUT_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(outBuf * 2, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            audioTrack = track
        } catch (_: Exception) {}

        // 录音：16k 单声道，持续推流
        val minBuf = AudioRecord.getMinBufferSize(MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var rec = tryInitAudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, minBuf)
        if (rec == null) rec = tryInitAudioRecord(MediaRecorder.AudioSource.MIC, minBuf)
        if (rec == null) return
        audioRecord = rec
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rec.state == AudioRecord.STATE_INITIALIZED) {
            try { aec = AcousticEchoCanceler.create(rec.audioSessionId) } catch (_: Exception) {}
            try { ns = NoiseSuppressor.create(rec.audioSessionId) } catch (_: Exception) {}
        }
        rec.startRecording()

        micThread = thread(name = "call-ws-mic") {
            val chunk = ByteArray(3200) // 100ms @16k mono 16bit
            while (running.get()) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) { Thread.sleep(5); continue }
                val s = state.value
                if (s.micMuted) continue
                // 音量指示
                var acc = 0.0
                var i = 0
                while (i + 1 < n) {
                    val v = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
                    val sv = if (v > 32767) v - 65536 else v
                    acc += sv * sv
                    i += 2
                }
                val rms = Math.sqrt(acc / (n / 2.0))
                state.value = state.value.copy(level = (rms / 6000.0).coerceIn(0.0, 1.0).toFloat())
                try {
                    ws?.send(okio.ByteString.of(*chunk.copyOf(n)))
                } catch (_: Exception) {}
            }
            try { rec.stop() } catch (_: Exception) {}
            try { rec.release() } catch (_: Exception) {}
        }
    }

    private fun tryInitAudioRecord(source: Int, minBuf: Int): AudioRecord? {
        return try {
            val rec = AudioRecord(source, MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 2, 8192))
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else { rec.release(); null }
        } catch (_: Exception) { null }
    }

    private fun playPcm(bytes: ByteArray) {
        val track = audioTrack ?: return
        try {
            track.write(bytes, 0, bytes.size)
        } catch (_: Exception) {}
    }

    private fun flushPlayback() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (_: Exception) {}
    }

    private fun stopAudio() {
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        aec = null; ns = null
        micThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        try { audioManager?.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
    }

    private fun cleanup() {
        sessionJob?.cancel()
        try { audioManager?.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
    }

    private fun writeCallLog() {
        val context = ctxRef ?: return
        if (personaName.isEmpty() || connectAt <= 0L) return
        val secs = ((System.currentTimeMillis() - connectAt) / 1000).toInt().coerceAtLeast(0)
        try {
            MsgRepo.add(context, "persona_$personaName", "user", "[calllog]$secs")
        } catch (_: Exception) {}
    }
}
