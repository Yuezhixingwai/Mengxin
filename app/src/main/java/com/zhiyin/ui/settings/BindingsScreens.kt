package com.zhiyin.ui.settings

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.zhiyin.data.AppSession
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WechatBindingEntry(
    val accountId: String,
    val wechatUserId: String,
    val characterId: Int,
    val updatedAt: String,
)

private fun proxyPost(endpoint: String, body: String): String {
    val conn = URL(ApiGateway.ZHIYIN_BASE + "/api/wechat/proxy/" + endpoint).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("iLink-App-Id", "bot")
        conn.setRequestProperty("iLink-App-ClientVersion", "20404")
        conn.setRequestProperty("X-WECHAT-UIN", ((Math.random() * 4294967295L).toInt()).toString())
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true
        val data = body.toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(data.size)
        conn.outputStream.use { it.write(data) }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code < 200 || code >= 300) throw Exception("HTTP $code")
        return text
    } finally {
        conn.disconnect()
    }
}

private fun proxyGetStatus(qrcode: String): String {
    val q = URLEncoder.encode(qrcode, "UTF-8")
    val conn = URL(ApiGateway.ZHIYIN_BASE + "/api/wechat/proxy/get_qrcode_status?qrcode=" + q).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.setRequestProperty("iLink-App-Id", "bot")
        conn.setRequestProperty("iLink-App-ClientVersion", "20404")
        conn.connectTimeout = 15000
        conn.readTimeout = 35000
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code < 200 || code >= 300) throw Exception("HTTP $code")
        return text
    } finally {
        conn.disconnect()
    }
}

private fun encodeQrBitmap(contents: String, size: Int): Bitmap {
    val hints = HashMap<EncodeHintType, Any>()
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[EncodeHintType.MARGIN] = 1
    val matrix: BitMatrix = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val row = y * size
        for (x in 0 until size) {
            pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

private fun loadBindings(): List<WechatBindingEntry> {
    return try {
        val resp = ApiGateway.requestSync(
            ApiGateway.ZHIYIN_BASE + "/api/wechat/bindings", "GET", null, AppSession.token()
        )
        val arr = JSONObject(resp).optJSONArray("bindings") ?: return emptyList()
        val out = mutableListOf<WechatBindingEntry>()
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            if (b.optString("status", "") != "active") continue
            out.add(
                WechatBindingEntry(
                    accountId = b.optString("account_id", ""),
                    wechatUserId = b.optString("wechat_user_id", ""),
                    characterId = b.optInt("character_id", -1),
                    updatedAt = ApiGateway.toBeijingTime(b.optString("updated_at", ""), "MM-dd HH:mm"),
                )
            )
        }
        out
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
fun BindingsScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onBindWechat: (Int, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    var bindings by remember { mutableStateOf<List<WechatBindingEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var bindCharacter by remember { mutableStateOf<FriendManager.Friend?>(null) }
    var showCharacterPicker by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            bindings = withContext(Dispatchers.IO) { loadBindings() }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "微信 / 外部绑定",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                CardContainer {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("微信绑定", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "扫码绑定微信后，微信消息会由绑定的角色自动回复",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceContainerHigh)
                                .clickable { showCharacterPicker = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "回复角色",
                                fontSize = 14.sp,
                                color = colors.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                bindCharacter?.name ?: "不绑定角色",
                                fontSize = 14.sp,
                                color = colors.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.onSurfaceVariantActions,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onBindWechat(bindCharacter?.id ?: -1, bindCharacter?.name) },
                            cornerRadius = 23.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("扫码绑定微信", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (loading) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        } else if (bindings.isEmpty()) {
                            Text(
                                "暂无已绑定的微信",
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            bindings.forEach { b ->
                                val charName = appVm.friends.find { it.id == b.characterId }?.name
                                BindingInfoRow(
                                    title = "微信 ${b.accountId.takeLast(8)}",
                                    summary = "回复角色：${charName ?: "未指定角色"} · ${b.updatedAt}",
                                    trailing = "运行中",
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                CardContainer {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("外部接入（QQ Bot / 插件）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "QQ 机器人、微信 ClawBot 插件等外部通道可通过统一接口接入，发送消息即由 AI 回复。",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "POST ${ApiGateway.ZHIYIN_BASE}/api/clawbot\n" +
                                "Header: Authorization: Bearer <你的Token>\n" +
                                "Body: {\"content\": \"消息\"}\n" +
                                "返回: {\"reply\": \"AI回复\"}",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariantActions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceContainerHigh)
                                .padding(10.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            text = "复制接口地址",
                            onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("clawbot", ApiGateway.ZHIYIN_BASE + "/api/clawbot"))
                                appVm.showToast("接口地址已复制")
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showCharacterPicker) {
        LingXinSheet(onDismiss = { showCharacterPicker = false }) {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "选择微信回复角色",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                SheetPickRow(
                    title = "不绑定角色",
                    selected = bindCharacter == null,
                    onClick = {
                        bindCharacter = null
                        showCharacterPicker = false
                    },
                )
                appVm.friends.forEach { f ->
                    SheetPickRow(
                        title = f.name,
                        selected = bindCharacter?.id == f.id,
                        onClick = {
                            bindCharacter = f
                            showCharacterPicker = false
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 绑定列表信息行（替代旧 M3 ListItem：标题 + 摘要 + 右侧状态文字）。 */
@Composable
private fun BindingInfoRow(title: String, summary: String, trailing: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(summary, fontSize = 12.sp, color = colors.onSurfaceVariantActions)
        }
        Text(
            trailing,
            fontSize = 12.sp,
            color = colors.primary,
        )
    }
}

/** 底部抽屉里的可选中列表行（替代旧 M3 ListItem 选中态）。 */
@Composable
private fun SheetPickRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (selected) colors.primaryContainer.copy(alpha = 0.4f) else colors.surfaceContainer,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, color = colors.onSurface)
    }
}

@Composable
fun WechatBindScreen(
    appVm: AppViewModel,
    characterId: Int,
    characterName: String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var statusText by remember { mutableStateOf("点击下方按钮生成二维码\n用微信扫码即可绑定") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var qrActive by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    var currentQrcode by remember { mutableStateOf("") }
    var lastToken by remember { mutableStateOf("") }
    var refreshCount by remember { mutableIntStateOf(0) }

    fun requestQr() {
        busy = true
        statusText = "正在获取二维码…"
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("local_token_list", JSONArray().apply { if (lastToken.isNotEmpty()) put(lastToken) })
                }
                val resp = withContext(Dispatchers.IO) {
                    proxyPost("get_bot_qrcode?bot_type=3", body.toString())
                }
                val json = JSONObject(resp)
                currentQrcode = json.getString("qrcode")
                val imgContent = json.getString("qrcode_img_content")
                qrBitmap = withContext(Dispatchers.IO) { encodeQrBitmap(imgContent, 500) }
                statusText = "请用微信扫描上方二维码"
                busy = false
                refreshCount = 0
                qrActive = true
            } catch (e: Exception) {
                statusText = "获取二维码失败: ${e.message ?: ""}"
                busy = false
            }
        }
    }

    fun finishBind() {
        qrActive = false
        done = true
        statusText = "绑定成功，微信自动回复已开启"
        appVm.showToast("微信绑定成功，自动回复已开启")
        scope.launch {
            delay(1200)
            onBack()
        }
    }

    LaunchedEffect(qrActive) {
        if (!qrActive) return@LaunchedEffect
        var attempts = 0
        while (qrActive && attempts < 120) {
            try {
                val resp = withContext(Dispatchers.IO) { proxyGetStatus(currentQrcode) }
                val json = JSONObject(resp)
                when (json.optString("status", "wait")) {
                    "wait" -> attempts += 1
                    "scaned" -> {
                        statusText = "已扫码，请在手机上确认…"
                        attempts += 1
                    }
                    "scaned_but_redirect" -> attempts += 1
                    "expired" -> {
                        refreshCount += 1
                        if (refreshCount > 3) {
                            qrActive = false
                            statusText = "二维码多次过期，请重新生成"
                        } else {
                            statusText = "二维码已过期，正在刷新…"
                            val body = JSONObject().apply {
                                put("local_token_list", JSONArray().apply { if (lastToken.isNotEmpty()) put(lastToken) })
                            }
                            val resp2 = withContext(Dispatchers.IO) { proxyPost("get_bot_qrcode?bot_type=3", body.toString()) }
                            val json2 = JSONObject(resp2)
                            currentQrcode = json2.getString("qrcode")
                            val imgContent = json2.getString("qrcode_img_content")
                            qrBitmap = withContext(Dispatchers.IO) { encodeQrBitmap(imgContent, 500) }
                        }
                    }
                    "confirmed" -> {
                        val botToken = json.optString("bot_token")
                        val botId = json.optString("ilink_bot_id")
                        val baseUrl = json.optString("baseurl")
                        val wxUserId = json.optString("ilink_user_id")
                        if (botId.isEmpty()) {
                            qrActive = false
                            statusText = "登录失败，请重试"
                        } else {
                            lastToken = botToken
                            statusText = "绑定中，正在注册到服务端…"
                            try {
                                val body = JSONObject().apply {
                                    put("accountId", botId)
                                    put("userId", wxUserId)
                                    put("token", botToken)
                                    put("baseUrl", baseUrl)
                                    if (characterId > 0) put("characterId", characterId)
                                }
                                withContext(Dispatchers.IO) {
                                    ApiGateway.requestSync(
                                        ApiGateway.ZHIYIN_BASE + "/api/wechat/register-binding",
                                        "POST", body.toString(), AppSession.token()
                                    )
                                }
                                finishBind()
                            } catch (e: Exception) {
                                qrActive = false
                                statusText = "服务端注册失败: ${e.message ?: ""}"
                            }
                        }
                    }
                    "binded_redirect" -> {
                        qrActive = false
                        statusText = "该微信已绑定过，请重新扫码获取授权"
                    }
                    else -> attempts += 1
                }
            } catch (_: Exception) {
                statusText = "网络波动，重试中…"
            }
            if (qrActive) delay(1000)
        }
        if (qrActive && attempts >= 120) {
            qrActive = false
            statusText = "扫码超时，请重新生成"
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = if (characterName != null) "$characterName - 微信绑定" else "微信绑定",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    statusText,
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "绑定二维码",
                            modifier = Modifier
                                .size(260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } ?: Text(
                        "二维码将显示在这里",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(28.dp))
                if (done) {
                    Text(
                        "微信消息将由 ${characterName ?: "绑定的角色"} 自动回复",
                        fontSize = 13.sp,
                        color = colors.primary,
                    )
                } else {
                    Button(
                        onClick = { requestQr() },
                        enabled = !busy && !qrActive,
                        cornerRadius = 24.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        Text(
                            when {
                                busy -> "生成中…"
                                qrActive -> "等待扫码确认"
                                statusText.startsWith("获取二维码失败") || statusText.startsWith("二维码多次过期") || statusText.startsWith("扫码超时") -> "重新生成"
                                qrBitmap != null -> "重新生成二维码"
                                else -> "生成绑定二维码"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
