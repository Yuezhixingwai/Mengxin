package com.zhiyin.logic.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
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

    private const val API_HOST = "api.zhiyin.yuezhixingwai.cn"
    private const val WS_HOST = "wss://" + API_HOST + "/ws/call"
    private const val MIC_RATE = 16000
    private const val OUT_RATE = 24000
    // 起播水位：先攒够 180ms 音频再开播，抹平首包到达不均匀造成的 underrun
    private const val PREFILL_BYTES = OUT_RATE * 2 * 180 / 1000
    // 队列上限 5s，防服务端异常刷包把内存吃光
    private const val QUEUE_MAX_BYTES = OUT_RATE * 2 * 5

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + Job())
    private var sessionJob: Job? = null
    private var fallbackStateJob: Job? = null
    private val running = AtomicBoolean(false)

    private var okHttp: OkHttpClient? = null
    private var ws: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var micThread: Thread? = null
    // 抖动缓冲：网络线程只入队，独立播放线程按序写出。
    // 之前是收到就 write，包到得忽快忽慢会直接把 AudioTrack 抖到 underrun（听感一顿一顿）。
    private var playThread: Thread? = null
    private val audioQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
    private val queuedBytes = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var playStarted = false
    @Volatile private var firstQueuedAt = 0L
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var audioManager: AudioManager? = null
    private var ctxRef: Context? = null

    private var personaName = ""
    private var personaDesc = ""
    private var connectAt = 0L
    private var startupRetryCount = 0
    private val speaking = AtomicBoolean(false)
    private val fellBack = AtomicBoolean(false) // 已回退到 HTTP 引擎（挂断时要一起停）
    private val aecReady = AtomicBoolean(false) // 系统回声消除是否真的生效（决定插嘴要不要放行）

    // ---------- 对外 API（与 CallEngine 对齐，CallScreen 可无缝切换） ----------

    fun isVoiceConfigured(context: Context, name: String): Boolean =
        prefs(context).getString(voiceKey(name), null) != null

    /** 进入通话页时调用：上一通电话结束后 phase 残留 ENDED，不复位的话新通话页会在几百毫秒内自动退出回聊天页 */
    fun resetIfEnded() {
        if (!running.get() && state.value.phase == Phase.ENDED) {
            state.value = UiState()
        }
    }

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
        startupRetryCount = 0
        fellBack.set(false)
        fallbackStateJob?.cancel()
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
        // 兼容模式（HTTP 引擎）在跑时必须一起停掉，否则挂断后它还在继续说
        if (fellBack.get()) { try { CallEngine.hangup() } catch (_: Exception) {} }
        fallbackStateJob?.cancel()
    }

    fun toggleMute() {
        if (fellBack.get()) {
            CallEngine.toggleMute()
            return
        }
        val s = state.value
        state.value = s.copy(micMuted = !s.micMuted)
    }

    fun toggleSpeaker(on: Boolean) {
        if (fellBack.get()) {
            CallEngine.toggleSpeaker(on)
            return
        }
        try { audioManager?.isSpeakerphoneOn = on } catch (_: Exception) {}
    }

    /** 本地能量抢占打断：先停播、再让服务端掐掉合成，最后靠 ASR 确认用户到底说了什么 */
    private fun triggerLocalBarge() {
        if (!speaking.get()) return
        Log.i("CallWs", "本地能量抢占打断")
        flushPlayback()
        speaking.set(false)
        try { ws?.send(JSONObject().put("type", "interrupt").toString()) } catch (_: Exception) {}
        state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "好，你先说～")
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
            running.set(false)
            state.value = state.value.copy(phase = Phase.ENDED, subtitle = "未登录")
            return
        }
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true

            delay(Random.nextLong(250, 700)) // 接通更快，别让用户干等
            state.value = state.value.copy(phase = Phase.DIALING, subtitle = "正在接通…")

            startAudio(context)

            val ok = connectWs(context, token)
            if (!ok) {
                // 兜底：HTTP 模式
                startHttpFallback(context, "实时连接不可用，已切换兼容模式")
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
                running.set(false)
                state.value = state.value.copy(phase = Phase.ENDED, subtitle = "通话异常: ${e.message}")
            }
            stopAudio()
            cleanup()
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
                // 提示词已在服务端统一（call_ws.js）：客户端只传人设名/音色/历史，不再上行 system
                val start = JSONObject()
                    .put("type", "start")
                    .put("persona", personaName)
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
        while (waited < 6000 && !ready && running.get()) {
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
                sendGreeting(webSocket)
                // 部分华为/鸿蒙设备能完成 WS 握手，但服务端首句事件迟迟不返回。
                // 两次催发仍没有任何回复事件时，切到 HTTP 兼容引擎继续通话。
                scope.launch {
                    repeat(2) {
                        delay(5_000)
                        if (!running.get() || state.value.phase != Phase.GREETING) return@launch
                        startupRetryCount++
                        state.value = state.value.copy(
                            subtitle = "对方那边有点延迟，正在重新接通，请稍候…"
                        )
                        sendGreeting(webSocket)
                    }
                    delay(5_000)
                    if (running.get() && state.value.phase == Phase.GREETING) {
                        startHttpFallback(context, "实时语音无响应，已切换兼容模式")
                    }
                }
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
                // 这一轮音频已经发完：还没攒够水位就直接起播，避免短句（"嗯"）干等 400ms
                if (!playStarted && firstQueuedAt > 0L) firstQueuedAt = System.currentTimeMillis() - 500L
                state.value = state.value.copy(phase = Phase.LISTENING, subtitle = m.optString("text"))
            }
            "interrupted" -> {
                speaking.set(false)
                flushPlayback()
                state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "好，你先说～")
            }
            "error" -> {
                val duringGreeting = state.value.phase == Phase.GREETING ||
                        (connectAt > 0L && System.currentTimeMillis() - connectAt < 15_000L)
                if (duringGreeting && startupRetryCount < 2 && running.get()) {
                    startupRetryCount++
                    state.value = state.value.copy(phase = Phase.GREETING, subtitle = "刚刚没接上，正在重试…")
                    scope.launch {
                        delay(700L * startupRetryCount)
                        if (running.get()) sendGreeting(webSocket)
                    }
                } else {
                    // 单次模型/语音服务失败不应结束整通电话，回到聆听态即可继续说。
                    speaking.set(false)
                    state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "刚才没听清，请再说一次")
                }
            }
        }
    }

    private fun sendGreeting(webSocket: WebSocket) {
        val greet = JSONObject()
            .put("type", "text")
            .put("text", "[电话刚刚接通] 请你先开口，像真人接电话一样自然地先说第一句话，简短口语。")
        try { webSocket.send(greet.toString()) } catch (_: Exception) {}
    }

    private fun startHttpFallback(context: Context, message: String) {
        if (!fellBack.compareAndSet(false, true)) return
        try { ws?.close(1000, "fallback") } catch (_: Exception) {}
        stopAudio()
        running.set(false)
        state.value = state.value.copy(phase = Phase.DIALING, mode = "http", subtitle = message)
        CallEngine.start(context, personaName, personaDesc)
        fallbackStateJob?.cancel()
        fallbackStateJob = scope.launch {
            CallEngine.state.collect { http ->
                val phase = when (http.phase) {
                    CallEngine.Phase.IDLE -> Phase.IDLE
                    CallEngine.Phase.DIALING -> Phase.DIALING
                    CallEngine.Phase.GREETING -> Phase.GREETING
                    CallEngine.Phase.LISTENING -> Phase.LISTENING
                    CallEngine.Phase.THINKING -> Phase.THINKING
                    CallEngine.Phase.SPEAKING -> Phase.SPEAKING
                    CallEngine.Phase.ENDED -> Phase.ENDED
                }
                state.value = UiState(
                    phase = phase,
                    seconds = http.seconds,
                    subtitle = http.subtitle,
                    level = http.level,
                    micMuted = http.micMuted,
                    mode = "http",
                )
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

    // ---------- 音频 ----------

    private fun startAudio(context: Context) {
        // 播放：24k 单声道 16bit PCM 流式
        // ⚠️ 必须走 USAGE_VOICE_COMMUNICATION：外放时只有把播放挂到"通话"通路上，
        //    系统 AEC 才拿得到回声参考信号去抵消它（免提已由 isSpeakerphoneOn=true 打开）。
        //    早前用 USAGE_MEDIA 播放 → AEC 拿不到参考 → AI 的声音被麦克风原样收回去
        //    → 自己被自己打断、AI 接自己的话，听起来"情绪和语气都不对"。
        try {
            val outBuf = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
                .setBufferSizeInBytes(maxOf(outBuf * 3, 16384))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track
            // 不在 build 后立刻 play：交给播放线程攒够水位再起播
            startPlaybackLoop(track)
        } catch (_: Exception) {}

        // 录音：16k 单声道，持续推流
        val minBuf = AudioRecord.getMinBufferSize(MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var rec = tryInitAudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, minBuf)
        if (rec == null) rec = tryInitAudioRecord(MediaRecorder.AudioSource.MIC, minBuf)
        if (rec == null) return
        audioRecord = rec
        if (rec.state == AudioRecord.STATE_INITIALIZED) {
            try { aec = AcousticEchoCanceler.create(rec.audioSessionId); aec?.enabled = true } catch (_: Exception) {}
            try { ns = NoiseSuppressor.create(rec.audioSessionId); ns?.enabled = true } catch (_: Exception) {}
            // AGC 自动增益：手机离嘴远、说话轻时能明显提升识别率
            try { agc = AutomaticGainControl.create(rec.audioSessionId); agc?.enabled = true } catch (_: Exception) {}
        }
        var aecOn = false
        var nsOn = false
        var agcOn = false
        try { aecOn = aec?.enabled == true } catch (_: Exception) {}
        try { nsOn = ns?.enabled == true } catch (_: Exception) {}
        try { agcOn = agc?.enabled == true } catch (_: Exception) {}
        aecReady.set(aecOn)
        Log.i("CallWs", "音频效果 aec=" + aecOn + " ns=" + nsOn + " agc=" + agcOn)
        rec.startRecording()

        micThread = thread(name = "call-ws-mic") {
            val chunk = ByteArray(3200) // 100ms @16k mono 16bit
            var noiseFloor = 350.0      // 环境噪声底（EMA 自适应）
            var gateOpen = false        // 回声门状态：打开后带滞回，词间停顿不断流
            var quietRun = 0
            var bargeStreak = 0         // 抢占式打断：连续高能量帧计数
            var lastBargeAt = 0L        // 上次本地打断时间（做冷却，防连击）
            val preRoll = ArrayDeque<ByteArray>() // 开门前的缓冲（≈2s），补发防止掐掉说话开头
            while (running.get()) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) { Thread.sleep(5); continue }
                // 音量指示（回声门槛也要用，先算）
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
                val s = state.value
                if (s.micMuted) continue
                if (speaking.get()) {
                    // 回声门：AI 说话期间保持上行，用能量门槛挡外放回声（不再整段静音，
                    // 否则用户完全插不上话——2.2.8/2.2.9 实测被打断不了就是这里）。
                    // · 有系统 AEC：回声已被抵消，门槛低，正常音量说话即可开门插嘴；
                    // · 没有 AEC：外放回声很大，门槛抬高到只放"近场大嗓门"，
                    //   混进去的回声由服务端 isSelfEcho 文本过滤兜底。
                    val gate = if (aecReady.get()) noiseFloor * 2.0 + 300.0 else noiseFloor * 3.0 + 2600.0
                    // 抢占式打断：不等 ASR 出字，能量明显超过人声门限就先把播放停掉。
                    // 只在系统 AEC 生效时启用（没有 AEC 时外放回声会把自己打断），
                    // 连续 3 帧（300ms）+ 1.5s 冷却；真内容由服务端 ASR 确认，回声由 isSelfEcho 兜底。
                    if (aecReady.get()) {
                        val nowMs = System.currentTimeMillis()
                        val bargeTh = noiseFloor * 2.5 + 1400.0
                        if (rms >= bargeTh) bargeStreak++ else bargeStreak = 0
                        if (bargeStreak >= 3 && nowMs - lastBargeAt > 1500L) {
                            bargeStreak = 0
                            lastBargeAt = nowMs
                            triggerLocalBarge()
                        }
                    }
                    // 开门时先补发缓冲帧（说话开头不被掐），关门要连续 2s 安静（词间停顿不掉线）。
                    if (!gateOpen) {
                        if (rms >= gate) {
                            gateOpen = true
                            quietRun = 0
                            for (b in preRoll) { try { ws?.send(okio.ByteString.of(*b)) } catch (_: Exception) {} }
                            preRoll.clear()
                        } else {
                            preRoll.addLast(chunk.copyOf(n))
                            if (preRoll.size > 20) preRoll.removeFirst()
                            continue
                        }
                    } else if (rms < gate) {
                        quietRun++
                        if (quietRun > 20) { gateOpen = false; quietRun = 0 }
                    } else {
                        quietRun = 0
                    }
                } else {
                    gateOpen = false
                    quietRun = 0
                    preRoll.clear()
                    noiseFloor = noiseFloor * 0.95 + rms * 0.05
                }
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

    /** 下行音频只入队，由播放线程统一写出（起播前先攒够 PREFILL_BYTES 水位） */
    private fun playPcm(bytes: ByteArray) {
        if (audioTrack == null) return
        if (queuedBytes.get() + bytes.size > QUEUE_MAX_BYTES) return
        if (firstQueuedAt == 0L) firstQueuedAt = System.currentTimeMillis()
        audioQueue.offer(bytes)
        queuedBytes.addAndGet(bytes.size)
    }

    private fun startPlaybackLoop(track: AudioTrack) {
        if (playThread != null) return
        playThread = thread(name = "call-ws-play") {
            while (running.get()) {
                try {
                    if (!playStarted) {
                        val queued = queuedBytes.get()
                        val waited = if (firstQueuedAt > 0L) System.currentTimeMillis() - firstQueuedAt else 0L
                        // 够水位就起播；整句很短永远攒不够时等 400ms 强制起播，别把话吞掉
                        if (queued < PREFILL_BYTES && waited < 400L) { Thread.sleep(10); continue }
                        if (queued <= 0) { Thread.sleep(10); continue }
                        track.play()
                        playStarted = true
                    }
                    val buf = audioQueue.poll(20, TimeUnit.MILLISECONDS) ?: continue
                    queuedBytes.addAndGet(-buf.size)
                    track.write(buf, 0, buf.size)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                }
            }
            try { track.stop() } catch (_: Exception) {}
        }
    }

    /** 打断/挂断：清空待播队列并丢弃 AudioTrack 里积压的音频 */
    private fun flushPlayback() {
        audioQueue.clear()
        queuedBytes.set(0)
        playStarted = false
        firstQueuedAt = 0L
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    private fun stopAudio() {
        try { playThread?.interrupt() } catch (_: Exception) {}
        playThread = null
        audioQueue.clear()
        queuedBytes.set(0)
        playStarted = false
        firstQueuedAt = 0L
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        try { agc?.release() } catch (_: Exception) {}
        aec = null; ns = null; agc = null
        aecReady.set(false)
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
