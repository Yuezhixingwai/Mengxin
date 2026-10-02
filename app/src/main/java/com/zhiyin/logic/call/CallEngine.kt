package com.zhiyin.logic.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.data.SessionStore
import com.zhiyin.logic.net.ApiGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 实时语音通话引擎（与普通聊天语音回复完全独立）：
 *  - 通话 TTS 走 /api/call/tts（CosyVoice），ASR 走 /api/call/asr（qwen3-asr-flash）
 *  - 随机 1~10 秒接通，人设先说第一句
 *  - 句级流水线：LLM 整句返回后按句切分，第 N 句在播的同时第 N+1 句已经在合成，边生成边播
 *  - 插嘴打断：对方说话期间持续监听麦克风，检测到用户开口立即停止播放，
 *    播放预合成的「你先说」短句（通话开始时已提前合成好，零等待），然后继续听用户说
 */
object CallEngine {

    enum class Phase { IDLE, DIALING, GREETING, LISTENING, THINKING, SPEAKING, ENDED }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val seconds: Int = 0,
        val subtitle: String = "",
        val level: Float = 0f,
        val micMuted: Boolean = false,
    )

    val state = kotlinx.coroutines.flow.MutableStateFlow(UiState())

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + Job())
    private var sessionJob: Job? = null
    private val barged = AtomicBoolean(false)
    private val interrupted = AtomicBoolean(false) // 仅打断当前正在播的整句，不结束会话
    private val running = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private var micThread: Thread? = null
    private var player: MediaPlayer? = null
    private var playDone: CompletableDeferred<Boolean>? = null
    private var audioManager: AudioManager? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var ctxRef: Context? = null
    private var personaName: String = ""
    private var personaDesc: String = ""
    private var connectAt: Long = 0L

    // 通话短应答（插嘴时秒回），接通后预合成缓存
    private val ACK_TEXTS = listOf("哎好，你说。", "嗯，你先说。", "欸，你说，我听着呢。")
    private val ackFiles = mutableListOf<File>()

    private const val SAMPLE_RATE = 16000

    // ---------- 对外 ----------
    fun start(context: Context, name: String, desc: String) {
        if (running.get()) return
        running.set(true)
        barged.set(false)
        interrupted.set(false)
        connectAt = 0L
        ctxRef = context.applicationContext
        personaName = name
        personaDesc = desc
        state.value = UiState(phase = Phase.DIALING)
        sessionJob = scope.launch { runSession(context.applicationContext) }
    }

    fun hangup() {
        barged.set(true)
        interrupted.set(true)
        stopPlayer()
        stopMic()
        if (sessionJob?.isActive == true) sessionJob?.cancel()
        running.set(false)
        state.value = state.value.copy(phase = Phase.ENDED)
        writeCallLog()
        cleanupFiles()
    }

    /** 挂断后在聊天页写一条通话记录卡片（不写任何对话文本） */
    private fun writeCallLog() {
        val context = ctxRef ?: return
        if (personaName.isEmpty() || connectAt <= 0L) return
        val secs = ((System.currentTimeMillis() - connectAt) / 1000).toInt().coerceAtLeast(0)
        try {
            MsgRepo.add(context, "persona_$personaName", "user", "[calllog]$secs")
        } catch (_: Exception) {}
    }

    fun toggleMute() {
        val s = state.value
        state.value = s.copy(micMuted = !s.micMuted)
    }

    /** 手动打断：UI 按钮保底，立即停止播放并回到听用户说 */
    fun interruptSpeaking() {
        if (state.value.phase != Phase.SPEAKING) return
        interrupted.set(true)
        stopPlayer()
        pendingUtterances.clear()
        synchronized(bufLock) { speechBuf.clear() }
        state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "好，你先说～")
    }

    fun toggleSpeaker(on: Boolean) {
        try { audioManager?.isSpeakerphoneOn = on } catch (_: Exception) {}
    }

    fun isVoiceConfigured(context: Context, name: String): Boolean {
        return prefs(context).getString(voiceKey(name), null) != null
    }

    fun saveVoice(context: Context, name: String, voiceId: String) {
        prefs(context).edit().putString(voiceKey(name), voiceId).apply()
    }

    fun currentVoice(context: Context, name: String): String? =
        prefs(context).getString(voiceKey(name), null)

    private fun voiceKey(name: String) = "call_voice_persona_$name"
    private fun prefs(context: Context) = context.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)

    // ---------- 主流程 ----------
    private suspend fun runSession(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true

            // 1~10 秒随机接通
            delay(Random.nextLong(1000, 10000))
            if (barged.get()) return
            connectAt = System.currentTimeMillis()
            state.value = state.value.copy(phase = Phase.GREETING, subtitle = "已接通")

            startMic(context)
            prefetchAcks()

            // 人设先说第一句（流式：LLM 边生成边说，不等整段）
            runTurnStreaming(context, "[电话刚刚接通] 请你先开口，像真人接电话一样自然地先说第一句话，简短口语。")
            if (barged.get()) return

            // 主循环：听 → 想 → 边生成边说 → 听…
            while (!barged.get()) {
                state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "")
                val userText = listenForUtterance() ?: continue
                if (barged.get()) return
                pendingUtterances.clear()
                runTurnStreaming(context, userText)
            }
        } catch (e: Exception) {
            if (!barged.get()) {
                state.value = state.value.copy(phase = Phase.ENDED, subtitle = "通话异常: ${e.message}")
                running.set(false)
            }
        }
    }

    // ---------- 聊天 ----------
    /** 通话专用提示词：口语化、活人感、情绪感应，附具体模仿示例 */
    private fun callStylePrompt(): String {
        val base = if (personaDesc.isNotBlank()) personaDesc + "\n\n" else ""
        return base + "【当前是实时语音通话，不是文字聊天】\n" +
            "你正在和用户打电话。保持你的人设性格，但说话方式必须切换成\"打电话的活人\"。\n\n" +
            "一、怎么说话——先看这个例子（一个女生接到喜欢的人的电话）：\n" +
            "用户：\"喂，干嘛呢？\"\n" +
            "你：\"啊？没干嘛呀，刚躺床上刷手机。你怎么突然打来，想我啦？\"\n" +
            "用户：\"嗯…有点。\"\n" +
            "你：\"嘿嘿，那你说说有多想？说来听听嘛。\"\n" +
            "—— 注意：全是\"啊、呀、嗯、嘿嘿、嘛\"这种语气词，会反问、会接对方话头、会撒娇，像真人煲电话粥，而不是念作文。\n\n" +
            "二、硬性规则（违反一条都不行）：\n" +
            "1. 每次只说1~2句，每句不超过18个字。说完就停，把话头递回去，电话里没人听长篇大论。\n" +
            "2. 禁止旁白、禁止动作/表情/心理描写、禁止括号和星号、禁止emoji、禁止书面语。\n" +
            "3. 只输出要说出口的话本身，别的什么都不要。\n\n" +
            "三、情绪感应（通话比文字更重要）：\n" +
            "- 每句话先感受用户此刻的情绪：开心/低落/烦躁/无聊/撒娇/敷衍。\n" +
            "- 对方低落：语气温和，先关心（\"怎么啦，听起来不太开心\"）再逗趣，别急着讲道理。\n" +
            "- 对方兴奋：跟着一起嗨，可以抢话。\n" +
            "- 对方烦躁：少问多哄，直接给情绪价值。\n" +
            "- 对方很久不说话：像真人一样催一下，\"喂？还在吗\"\"怎么不说话啦，睡着了？\"\n\n" +
            "四、活人感细节：\n" +
            "- 可以接半句话、可以\"欸对了\"突然想起什么、可以重复对方的词、可以小小吐槽打趣。\n" +
            "- 偶尔用与情绪匹配的语气词开头，别每句都用同一种。\n" +
            "- 你的人设口癖和称呼方式要保留，但上面这些电话感规则永远优先。"
    }

    private fun buildCallMessages(context: Context, userText: String): JSONArray {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", callStylePrompt()))
        val sid = "persona_$personaName"
        val history = MsgRepo.getAll(context, sid)
        var count = 0
        for (i in history.indices.reversed()) {
            if (count >= 6) break
            val raw = history[i][1]
            val clean = com.zhiyin.logic.chat.ChatEngine.cleanHistoryContent(context, raw) ?: continue
            val role = if (history[i][0] == "ai") "assistant" else "user"
            messages.put(JSONObject().put("role", role).put("content", clean))
            count++
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))
        return messages
    }

    /** 无标点时超过该长度强制切句，尽快开口 */
    private const val FORCE_CUT = 14

    /** 从缓冲里抽一句完整的话（有句读按句读，没有就攒够 FORCE_CUT 字强切） */
    private fun takeCompleteSentence(buf: StringBuilder): String? {
        val t = buf.toString()
        for (i in t.indices) {
            val ch = t[i]
            if (ch == '。' || ch == '！' || ch == '？' || ch == '；' || ch == '\n') {
                val sent = t.substring(0, i + 1).trim()
                buf.delete(0, i + 1)
                return sent.ifEmpty { takeCompleteSentence(buf) }
            }
        }
        if (t.length >= FORCE_CUT) {
            buf.clear()
            return t.trim()
        }
        return null
    }

    /**
     * 流式话轮：LLM SSE 边生成，第一句一到立刻 TTS 播放，不等整段回复。
     * 思考/播放期间用户插话 → 立即中止本话轮。
     */
    private suspend fun runTurnStreaming(context: Context, userText: String) {
        val token = SessionStore(context).getToken()
        if (token == null) {
            state.value = state.value.copy(subtitle = "未登录")
            return
        }
        state.value = state.value.copy(phase = Phase.THINKING, subtitle = userText)
        interrupted.set(false) // 上一次打断的标记复位，否则后续话轮全部哑火
        val sentences = Channel<String>(Channel.UNLIMITED)

        try {
            coroutineScope {
            // 播放协程：按顺序合成+播放抽出来的句子
            val speaker = launch(Dispatchers.IO) {
                for (sent in sentences) {
                    if (barged.get() || interrupted.get()) break
                    val spoken = cleanForSpeech(sent)
                    if (spoken.isEmpty()) continue
                    val f = ttsFile(context, spoken) ?: continue
                    if (barged.get() || interrupted.get()) break
                    state.value = state.value.copy(phase = Phase.SPEAKING, subtitle = spoken)
                    playAndWait(f)
                    if (barged.get() || interrupted.get()) break
                }
            }

            withContext(Dispatchers.IO) {
                val conn = java.net.URL(ApiGateway.ZHIYIN_BASE + "/api/call/chat").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 20000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer " + token)
                val body = JSONObject().put("messages", buildCallMessages(context, userText)).put("stream", 1)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                val buf = StringBuilder()
                reader.forEachLine { line ->
                    if (barged.get() || interrupted.get()) return@forEachLine
                    if (!line.startsWith("data:")) return@forEachLine
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") return@forEachLine
                    val delta = try {
                        val j = JSONObject(data)
                        j.optJSONArray("choices")?.getJSONObject(0)?.optJSONObject("delta")?.optString("content", "") ?: ""
                    } catch (_: Exception) { "" }
                    if (delta.isNotEmpty()) {
                        buf.append(delta)
                        while (true) {
                            val sent = takeCompleteSentence(buf) ?: break
                            sentences.trySend(sent)
                        }
                    }
                }
                val rest = buf.toString().trim()
                if (rest.isNotEmpty()) sentences.trySend(rest)
            }
            sentences.close()
            speaker.join()
            }
        } catch (e: Exception) {
            if (!interrupted.get() && !barged.get()) {
                state.value = state.value.copy(subtitle = "网络异常: ${e.message}")
            }
        }
    }

    /** 剥动作描写/表情/符号，转成适合读出来的口语 */
    private fun cleanForSpeech(text: String): String {
        var t = text
        t = t.replace(Regex("\\*{1,2}[^*]+\\*{1,2}"), "")
        t = t.replace(Regex("\\([^)]*\\)"), "")
        t = t.replace(Regex("（[^）]*）"), "")
        t = t.replace(Regex("【[^】]*】"), "")
        t = t.replace(Regex("\\[STICKER:[^\\]]*\\]"), "")
        t = t.replace(Regex("[\\p{So}\\p{Cs}]"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    // ---------- 句级 TTS 流水线（边播边合成下一句） ----------
    private suspend fun speakUtterance(context: Context, text: String) {
        val sents = splitSentences(cleanForSpeech(text))
        if (sents.isEmpty()) return
        interrupted.set(false)
        state.value = state.value.copy(phase = Phase.SPEAKING)
        coroutineScope {
            var prefetch: kotlinx.coroutines.Deferred<File?>? = null
            for (i in sents.indices) {
                if (barged.get() || interrupted.get()) break
                val cur = prefetch ?: async(Dispatchers.IO) { ttsFile(context, sents[i]) }
                prefetch = if (i + 1 < sents.size) async(Dispatchers.IO) { ttsFile(context, sents[i + 1]) } else null
                val f = cur.await()
                if (barged.get() || interrupted.get()) break
                if (f == null) continue
                state.value = state.value.copy(subtitle = sents[i])
                playAndWait(f)
            }
            prefetch?.cancel()
        }
    }

    private fun splitSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val raw = text.split(Regex("(?<=[。！？!?；;\\n…])"))
        val out = mutableListOf<String>()
        for (s0 in raw) {
            var s = s0.trim()
            if (s.isEmpty()) continue
            // 过长的句子再按逗号切
            while (s.length > 48) {
                var cut = -1
                for (ch in listOf('，', ',', '、', ' ')) {
                    val idx = s.indexOf(ch, s.length / 3)
                    if (idx in 1..48) { cut = idx + 1; break }
                }
                if (cut <= 0) break
                out.add(s.substring(0, cut).trim())
                s = s.substring(cut).trim()
            }
            if (s.isNotEmpty()) out.add(s)
        }
        // 太短的并入前一句
        val merged = mutableListOf<String>()
        for (s in out) {
            if (merged.isNotEmpty() && (merged.last().length + s.length) <= 20) merged[merged.size - 1] = merged.last() + s
            else merged.add(s)
        }
        return merged.filter { it.isNotBlank() }
    }

    private suspend fun ttsFile(context: Context, text: String): File? = withContext(Dispatchers.IO) {
        try {
            val token = SessionStore(context).getToken() ?: return@withContext null
            val voice = prefs(context).getString(voiceKey(personaName), "longwan") ?: "longwan"
            val body = JSONObject().put("text", text).put("voice", voice)
            val resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/call/tts", body.toString(), token)
            val json = JSONObject(resp)
            val audio = json.optString("audio_data", "")
            if (audio.isEmpty()) return@withContext null
            val f = File(context.cacheDir, "call_tts_" + System.nanoTime() + ".mp3")
            FileOutputStream(f).use { it.write(Base64.decode(audio, Base64.DEFAULT)) }
            f
        } catch (e: Exception) {
            null
        }
    }

    private fun playAndWait(f: File) {
        try {
            val done = CompletableDeferred<Boolean>()
            playDone = done
            val p = MediaPlayer()
            // USAGE_VOICE_COMMUNICATION：外放（isSpeakerphoneOn=true 已强制开启）时走通话通路，
            // 系统 AEC 才能拿到回声参考把它消掉；用 USAGE_MEDIA 播放会让 AI 的声音被麦克风录回去。
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener {
                done.complete(true)
            }
            p.prepare()
            p.start()
            player = p
            // 100ms 粒度轮询，插嘴立即退出
            while (!done.isCompleted && !barged.get() && !interrupted.get()) {
                Thread.sleep(60)
            }
            stopPlayer()
        } catch (_: Exception) {
            stopPlayer()
        }
    }

    private fun stopPlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        playDone?.complete(true)
        playDone = null
    }

    private fun prefetchAcks() {
        val context = ctxRef ?: return
        scope.launch(Dispatchers.IO) {
            for (t in ACK_TEXTS) {
                if (barged.get()) return@launch
                val f = ttsFile(context, t)
                if (f != null) ackFiles.add(f)
            }
        }
    }

    private fun playAck() {
        val context = ctxRef ?: return
        if (ackFiles.isEmpty()) return
        val f = ackFiles[Random.nextInt(ackFiles.size)]
        thread {
            try {
                val p = MediaPlayer()
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                p.setDataSource(f.absolutePath)
                p.prepare()
                p.start()
                Thread.sleep(200)
                while (p.isPlaying && !barged.get()) Thread.sleep(60)
                p.release()
            } catch (_: Exception) {}
        }
    }

    // ---------- 麦克风：持续采音 + VAD + 插嘴检测 ----------
    private var noiseFloor = 800.0
    private val speechBuf = mutableListOf<Short>()
    private val bufLock = Object()

    private fun startMic(context: Context) {
        if (micThread != null) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // 优先 VOICE_COMMUNICATION（带回声消除），初始化失败的机型回退 MIC
        var rec = tryInitAudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, minBuf)
        if (rec == null) rec = tryInitAudioRecord(MediaRecorder.AudioSource.MIC, minBuf)
        if (rec == null) return
        audioRecord = rec
        if (rec.state == AudioRecord.STATE_INITIALIZED) {
            // 注意：以前只 create 没 enabled —— 等于没开回声消除，外放时 AI 的声音会被录回去
            try { aec = AcousticEchoCanceler.create(rec.audioSessionId); aec?.enabled = true } catch (_: Exception) {}
            try { ns = NoiseSuppressor.create(rec.audioSessionId); ns?.enabled = true } catch (_: Exception) {}
        }
        rec.startRecording()

        micThread = thread(name = "call-mic") {
            val chunk = ShortArray(1600) // 100ms
            var loudStreak = 0
            var quietStreak = 0
            var speechStarted = false
            var speechFrames = 0
            var totalWait = 0
            while (!barged.get()) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) { Thread.sleep(10); continue }
                val muted = state.value.micMuted
                val rms = if (muted) 0.0 else rmsOf(chunk, n)
                // 噪声底自适应（EMA）
                if (rms < noiseFloor * 1.6) noiseFloor = noiseFloor * 0.97 + rms * 0.03
                val voiceTh = noiseFloor * 2.2 + 320
                val bargeTh = noiseFloor * 3.0 + 700

                if (state.value.phase == Phase.SPEAKING) {
                    // 插嘴检测：连续 300ms 高能量即打断
                    if (rms > bargeTh) loudStreak++ else loudStreak = 0
                    if (loudStreak >= 2) {
                        loudStreak = 0
                        onBargeIn()
                    }
                } else if (state.value.phase == Phase.LISTENING || state.value.phase == Phase.THINKING) {
                    if (!speechStarted) {
                        if (rms > voiceTh) { loudStreak++ } else { loudStreak = 0; totalWait++ }
                        if (loudStreak >= 2) { // 200ms 确认开口
                            speechStarted = true
                            speechFrames = 0
                            quietStreak = 0
                            synchronized(bufLock) { speechBuf.clear() }
                        }
                        if (totalWait > 600) totalWait = 0 // 防溢出
                    } else {
                        synchronized(bufLock) {
                            for (i in 0 until n) speechBuf.add(chunk[i])
                        }
                        speechFrames++
                        if (rms > voiceTh) quietStreak = 0 else quietStreak++
                        // 说完后静音 700ms 收句，或最长 20 秒
                        if ((quietStreak >= 4 && speechFrames >= 2) || speechFrames >= 200) {
                            val data = synchronized(bufLock) { speechBuf.toList() }
                            speechBuf.clear()
                            speechStarted = false
                            if (data.size > 4000) { // ≥0.25s 才处理
                                onUtteranceCaptured(context, data)
                            }
                            quietStreak = 0
                        }
                    }
                    state.value = state.value.copy(level = (rms / 6000.0).coerceIn(0.0, 1.0).toFloat())
                }
            }
            try { rec.stop() } catch (_: Exception) {}
            try { rec.release() } catch (_: Exception) {}
        }
    }

    private fun tryInitAudioRecord(source: Int, minBuf: Int): AudioRecord? {
        return try {
            val rec = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 2, 8192))
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else { rec.release(); null }
        } catch (_: Exception) { null }
    }

    private fun stopMic() {
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        aec = null; ns = null
        micThread = null
        audioRecord = null
    }

    private fun rmsOf(buf: ShortArray, n: Int): Double {
        var acc = 0L
        for (i in 0 until n) acc += buf[i] * buf[i]
        return sqrt(acc.toDouble() / n)
    }

    /** 用户说完一句 → 交给 ASR + LLM，回调由主循环消费 */
    private val pendingUtterances = java.util.concurrent.ConcurrentLinkedQueue<ShortArray>()

    private fun onUtteranceCaptured(context: Context, data: List<Short>) {
        pendingUtterances.add(data.toShortArray())
    }

    /** 从 pending 里取最新一句去 ASR；主循环调用（阻塞直到说完一句或挂断） */
    private suspend fun listenForUtterance(): String? {
        while (!barged.get()) {
            val data = pendingUtterances.poll()
            if (data != null) {
                val text = asr(ctxRef ?: return null, data) ?: return null
                val clean = text.trim()
                if (clean.isNotEmpty() && clean.length >= 1 && hasRealContent(clean)) return clean
                continue
            }
            delay(80)
        }
        return null
    }

    private fun hasRealContent(t: String): Boolean {
        // 过滤纯语气/环境音误触
        val cleaned = t.replace(Regex("[嗯啊呃噢哦欸诶嗯。，,\\s]"), "")
        return cleaned.isNotEmpty()
    }

    private fun onBargeIn() {
        if (state.value.phase != Phase.SPEAKING) return
        interrupted.set(true) // 只打断当前这句，会话继续
        stopPlayer()
        state.value = state.value.copy(phase = Phase.LISTENING, subtitle = "好，你先说～")
        playAck()
        // 丢弃打断瞬间缓冲的音频，避免把自己的声音录进去
        pendingUtterances.clear()
        synchronized(bufLock) { speechBuf.clear() }
    }

    // ---------- ASR ----------
    private fun asr(context: Context, pcm: ShortArray): String? {
        return try {
            val token = SessionStore(context).getToken() ?: return null
            val wav = pcmToWav(pcm.toList(), SAMPLE_RATE)
            val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
            val body = JSONObject().put("audio_base64", b64).put("format", "wav")
            val resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/call/asr", body.toString(), token)
            val json = JSONObject(resp)
            json.optString("text", "").ifEmpty { null }
        } catch (e: Exception) {
            state.value = state.value.copy(subtitle = "识别失败: ${e.message}")
            null
        }
    }

    private fun pcmToWav(pcm: List<Short>, sampleRate: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        for (v in pcm) { baos.write(v.toInt() and 0xFF); baos.write((v.toInt() shr 8) and 0xFF) }
        val pcmBytes = baos.toByteArray()
        val out = ByteArrayOutputStream()
        val totalLen = pcmBytes.size + 36
        fun w16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun w32(v: Int) { w16(v and 0xFFFF); w16((v shr 16) and 0xFFFF) }
        out.write("RIFF".toByteArray()); w32(totalLen); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); w32(16); w16(1); w16(1)
        w32(sampleRate); w32(sampleRate * 2); w16(2); w16(16)
        out.write("data".toByteArray()); w32(pcmBytes.size)
        out.write(pcmBytes)
        return out.toByteArray()
    }

    private fun cleanupFiles() {
        val context = ctxRef ?: return
        thread {
            try {
                context.cacheDir.listFiles()?.forEach {
                    if (it.name.startsWith("call_tts_") && it.name.endsWith(".mp3") && !ackFiles.contains(it)) it.delete()
                }
            } catch (_: Exception) {}
        }
    }
}
