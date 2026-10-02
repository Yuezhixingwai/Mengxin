package com.zhiyin.ui.call

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.logic.call.CallWsEngine
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.logic.data.SessionStore
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.components.PersonaAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Base64

/**
 * 实时语音通话页（跟随 App 主题风格）
 * 拨打 → 随机1~10秒接通 → 人设先开口 → 实时对话（可插嘴打断）
 */
@Composable
fun CallScreen(
    personaName: String,
    personaDesc: String,
    personaId: Int,
    onEnd: () -> Unit,
) {
    val context = LocalContext.current
    val st by CallWsEngine.state.collectAsState()
    val colors = MiuixTheme.colorScheme

    var voiceReady by remember { mutableStateOf(CallWsEngine.isVoiceConfigured(context, personaName)) }
    var started by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    fun beginCall() {
        if (started) return
        started = true
        CallWsEngine.start(context, personaName, personaDesc)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginCall() else onEnd()
    }

    LaunchedEffect(voiceReady) {
        if (voiceReady && !started && !permissionRequested) {
            permissionRequested = true
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(Unit) {
        // 先清掉上一通电话残留的 ENDED 状态，否则本页会在几百毫秒内自动退出回聊天页
        CallWsEngine.resetIfEnded()
        if (!voiceReady) showPicker = true
    }

    LaunchedEffect(st.phase) {
        if (st.phase == CallWsEngine.Phase.ENDED) {
            delay(400)
            onEnd()
        }
    }

    val connected = st.phase != CallWsEngine.Phase.IDLE && st.phase != CallWsEngine.Phase.DIALING && st.phase != CallWsEngine.Phase.ENDED
    var secs by remember { mutableStateOf(0) }
    LaunchedEffect(connected) {
        if (connected) {
            secs = 0
            while (true) { delay(1000); secs++ }
        }
    }

    val speakerOn = remember { mutableStateOf(true) }
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    ).value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                personaName,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.weight(0.9f))

            // 头像 + 状态
            Box(contentAlignment = Alignment.Center) {
                if (st.phase == CallWsEngine.Phase.SPEAKING) {
                    Box(
                        Modifier
                            .size((150 + st.level * 36).dp)
                            .background(colors.primary.copy(alpha = 0.12f), CircleShape)
                    )
                }
                Box(
                    Modifier
                        .size(140.dp)
                        .scale(if (st.phase == CallWsEngine.Phase.DIALING || st.phase == CallWsEngine.Phase.GREETING) pulse else 1f)
                        .border(2.dp, colors.dividerLine.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PersonaAvatar(contactId = personaId, name = personaName, size = 132.dp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when {
                    st.phase == CallWsEngine.Phase.DIALING -> "正在等待对方接听…"
                    st.phase == CallWsEngine.Phase.GREETING -> "已接通"
                    st.phase == CallWsEngine.Phase.LISTENING -> "请说话 · 你也可以随时打断对方"
                    st.phase == CallWsEngine.Phase.THINKING -> "对方正在思考…"
                    st.phase == CallWsEngine.Phase.SPEAKING -> "对方正在说话"
                    else -> fmtSecs(secs)
                },
                fontSize = 14.sp,
                color = colors.onSurfaceVariantSummary,
            )
            if (st.subtitle.isNotBlank() && (st.phase == CallWsEngine.Phase.SPEAKING || st.phase == CallWsEngine.Phase.THINKING || st.phase == CallWsEngine.Phase.LISTENING)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    st.subtitle,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    maxLines = 2,
                )
            }
            Spacer(Modifier.weight(1.1f))

            // 未开始通话时的引导按钮（音色弹层可随时关闭，关了从这里再进）
            if (!started) {
                Button(
                    onClick = { showPicker = true },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Text(if (voiceReady) "开始通话" else "选择音色开始通话", color = colors.onPrimary)
                }
                Spacer(Modifier.height(14.dp))
            }

            // 底部控制区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallCtl(
                    icon = if (st.micMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (st.micMuted) "已静音" else "静音",
                ) { if (started) CallWsEngine.toggleMute() }
                CallCtl(
                    icon = Icons.Rounded.GraphicEq,
                    label = "音色",
                ) { showPicker = true }
                CallHangup { if (started) CallWsEngine.hangup() else onEnd() }
                CallCtl(
                    icon = if (speakerOn.value) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    label = if (speakerOn.value) "免提开" else "免提关",
                ) {
                    speakerOn.value = !speakerOn.value
                    CallWsEngine.toggleSpeaker(speakerOn.value)
                }
            }
            Spacer(Modifier.height(30.dp))
        }

        // 音色选择/克隆上传（App 统一底部弹层；可随时关闭，关闭后从"选择音色开始通话"再进）
        if (showPicker) {
            VoicePickerSheet(
                personaName = personaName,
                onDismiss = { showPicker = false },
                onSelected = { voiceId ->
                    CallWsEngine.saveVoice(context, personaName, voiceId)
                    voiceReady = true
                    showPicker = false
                },
            )
        }
    }
}

private fun fmtSecs(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

@Composable
private fun CallCtl(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            modifier = Modifier
                .size(58.dp)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = colors.onSurface, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
    }
}

@Composable
private fun CallHangup(onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = colors.error.copy(alpha = 0.9f),
            modifier = Modifier
                .size(66.dp)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CallEnd,
                    contentDescription = "挂断",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("挂断", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
    }
}

// ==================== 音色选择 / 克隆上传 ====================

@Composable
private fun VoicePickerSheet(
    personaName: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var voices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selected by remember { mutableStateOf(CallWsEngine.currentVoice(context, personaName) ?: "") }
    var loading by remember { mutableStateOf(true) }
    var cloning by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }

    fun refreshVoices() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val token = SessionStore(context).getToken() ?: return@withContext
                    val resp = ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/call/voices", "GET", null, token)
                    val arr = JSONObject(resp).optJSONArray("voices") ?: return@withContext
                    val list = mutableListOf<Pair<String, String>>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(o.optString("id") to o.optString("name"))
                    }
                    voices = list
                } catch (_: Exception) {
                } finally {
                    loading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshVoices() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            cloning = true
            tip = "正在上传样本…"
            val ok = withContext(Dispatchers.IO) {
                try {
                    val cr = context.contentResolver
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
                    if (bytes.size > 8 * 1024 * 1024) { tip = "音频过大（≤8MB）"; return@withContext false }
                    val name = uri.lastPathSegment ?: ""
                    val ext = if (name.endsWith(".wav", true)) "wav" else "mp3"
                    val token = SessionStore(context).getToken() ?: return@withContext false
                    val b64 = Base64.getEncoder().encodeToString(bytes)
                    // 1) 上传样本（与普通语音回复共用同一份样本存储）
                    val upBody = JSONObject().put("audio_base64", b64).put("format", ext)
                    val up = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/tts/voice-sample", upBody.toString(), token)
                    val upJson = JSONObject(up)
                    if (upJson.optBoolean("success") != true) {
                        tip = upJson.optString("error", "样本上传失败")
                        return@withContext false
                    }
                    // 2) 触发 CosyVoice 复刻（一次复刻，之后复用，省消耗）
                    val enroll = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/call/clone-enroll", "{}", token)
                    val ej = JSONObject(enroll)
                    if (ej.optString("voice_id").isEmpty()) {
                        tip = ej.optString("error", "复刻失败")
                        return@withContext false
                    }
                    tip = "复刻成功"
                    true
                } catch (e: Exception) {
                    tip = "失败: ${e.message}"
                    false
                }
            }
            cloning = false
            if (ok) {
                refreshVoices()
                selected = "clone:user"
            }
        }
    }

    LingXinSheet(onDismiss = onDismiss) {
        // 限高（屏幕 62%）+ 内部滚动：音色多也不会撑爆屏幕，且始终能滚到按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Text("为「$personaName」选择通话音色", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "通话使用独立的高音质实时语音模型；不选音色无法开始通话",
                fontSize = 12.sp,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            voices.forEach { (id, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = id }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        fontSize = 15.sp,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(
                        selected = selected == id,
                        onClick = { selected = id },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            // 音色克隆入口（置顶）
            Surface(
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !cloning) { filePicker.launch("audio/*") },
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("克隆我的声音", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "上传一段 MP3 / WAV 音频（10秒以上更清晰，≤8MB），复刻专属音色；只需复刻一次，之后一直复用",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }

            if (cloning) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.height(0.dp))
                    Text("  正在上传并复刻，请稍候…", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
            }

            if (tip.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tip,
                    fontSize = 12.sp,
                    color = if (tip.contains("成功")) colors.primary else colors.error,
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { if (selected.isNotBlank()) onSelected(selected) },
                enabled = selected.isNotBlank() && !cloning,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text(if (selected.isNotBlank()) "使用该音色开始通话" else "请先选择音色", color = colors.onPrimary)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
