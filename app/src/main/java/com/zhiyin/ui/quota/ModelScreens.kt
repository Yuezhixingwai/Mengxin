package com.zhiyin.ui.quota

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AppSession
import com.zhiyin.data.VoicePlayer
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.EmptyHint
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zhiyin", 0) }
    var useOfficial by remember { mutableStateOf(prefs.getBoolean("use_official_quota", true)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("模型与额度", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = !useOfficial,
                onClick = {
                    useOfficial = false
                    prefs.edit().putBoolean("use_official_quota", false).apply()
                    appVm.showToast("已切换到自接API")
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("自接API") }
            SegmentedButton(
                selected = useOfficial,
                onClick = {
                    useOfficial = true
                    prefs.edit().putBoolean("use_official_quota", true).apply()
                    appVm.showToast("已切换到官方配额（启辰币）")
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("官方配额") }
        }

        if (useOfficial) {
            OfficialQuotaPanel(appVm = appVm)
        } else {
            SelfApiPanel(appVm = appVm)
        }
    }
}

data class CoinRecord(val type: String, val task: String, val coins: Double, val time: String)

@Composable
fun OfficialQuotaPanel(appVm: AppViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zhiyin", 0) }
    var records by remember { mutableStateOf(listOf<CoinRecord>()) }
    var voices by remember { mutableStateOf(defaultVoices()) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }

    fun loadCoins() {
        thread {
            try {
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/user/coins", "GET", null, AppSession.token()
                )
                val json = JSONObject(resp)
                val recs = mutableListOf<CoinRecord>()
                json.optJSONArray("records")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        recs.add(
                            CoinRecord(
                                type = r.optString("model_type", ""),
                                task = r.optString("task", ""),
                                coins = r.optDouble("coins", 0.0),
                                time = ApiGateway.toBeijingTime(r.optString("created_at", ""), "MM-dd HH:mm"),
                            )
                        )
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    records = recs
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    appVm.showToast("调用记录加载失败: ${e.message ?: ""}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCoins()
        withContext(Dispatchers.IO) {
            try {
                val resp = ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/tts/voices", "GET", null, AppSession.token())
                val arr = JSONObject(resp).optJSONArray("voices")
                if (arr != null && arr.length() > 0) {
                    val list = (0 until arr.length()).map { i ->
                        arr.getJSONObject(i).optString("id", "") to arr.getJSONObject(i).optString("name", "")
                    }.filter { it.first.isNotEmpty() }
                    if (list.isNotEmpty()) voices = list
                }
            } catch (_: Exception) {
            }
        }
    }

    RubberBandBox(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CardContainer {
            ListItem(
                modifier = Modifier.clickable { showVoicePicker = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                headlineContent = { Text("语音回复音色") },
                supportingContent = { Text(currentVoiceName(prefs, voices)) },
                trailingContent = { Text("更换", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
            )
            ListItem(
                modifier = Modifier.clickable {
                    val voice = prefs.getString("tts_voice_global", "mimo_default") ?: "mimo_default"
                    if (previewing) return@clickable
                    previewing = true
                    appVm.showToast("正在生成试听音频…")
                    thread {
                        try {
                            val body = JSONObject().apply {
                                put("text", "你好，我是你的智能助手")
                                put("voice", voice)
                            }
                            val resp = ApiGateway.requestSync(
                                ApiGateway.ZHIYIN_BASE + "/api/tts", "POST", body.toString(), AppSession.token()
                            )
                            val json = JSONObject(resp)
                            val audio = json.optString("audio_data", "")
                            val error = json.optString("error", "")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                previewing = false
                                when {
                                    error.isNotEmpty() -> appVm.showToast("试听失败: $error")
                                    audio.isEmpty() -> appVm.showToast("试听失败：未返回音频")
                                    else -> {
                                        val bytes = android.util.Base64.decode(audio, android.util.Base64.DEFAULT)
                                        val f = File(context.cacheDir, "voice_preview.mp3")
                                        f.outputStream().use { it.write(bytes) }
                                        VoicePlayer.toggle(f.absolutePath)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                previewing = false
                                appVm.showToast("试听失败: ${e.message ?: ""}")
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                headlineContent = { Text("试听当前音色") },
                trailingContent = {
                    if (previewing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("播放", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                },
            )
        }

        CardContainer {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "调用记录",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                if (records.isEmpty()) {
                    Text(
                        "暂无调用记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                records.forEachIndexed { i, r ->
                    val name = modelTypeName(r.type)
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        leadingContent = {
                            Text(
                                "%02d".format(i + 1),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(30.dp),
                            )
                        },
                        headlineContent = { Text("$name · ${r.task}", style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = { Text(r.time, style = MaterialTheme.typography.labelSmall) },
                        trailingContent = {
                            Text(
                                (if (r.coins > 0) "+" else "-") + fmtCoin(kotlin.math.abs(r.coins)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (r.coins > 0) Color(0xFF34B78F) else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
    }

    if (showVoicePicker) {
        LingXinSheet(onDismiss = { showVoicePicker = false }) {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "选择语音回复音色",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(voices.size) { i ->
                        val (id, name) = voices[i]
                        val current = (prefs.getString("tts_voice_global", "mimo_default") ?: "mimo_default") == id
                        ListItem(
                            modifier = Modifier.clickable {
                                prefs.edit().putString("tts_voice_global", id).apply()
                                appVm.showToast("已选择: $name")
                                showVoicePicker = false
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            headlineContent = { Text(name) },
                            trailingContent = {
                                if (current) Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun defaultVoices(): List<Pair<String, String>> = listOf(
    "mimo_default" to "默认音色",
    "冰糖" to "冰糖 - 清甜女声",
    "茉莉" to "茉莉 - 温柔女声",
    "苏打" to "苏打 - 活泼男声",
    "白桦" to "白桦 - 沉稳男声",
    "Mia" to "Mia - 英文女声",
    "Chloe" to "Chloe - 英文女声",
    "Milo" to "Milo - 英文男声",
    "Dean" to "Dean - 英文男声",
)

@Composable
private fun currentVoiceName(prefs: android.content.SharedPreferences, voices: List<Pair<String, String>>): String {
    val current = prefs.getString("tts_voice_global", "mimo_default") ?: "mimo_default"
    return "当前：" + (voices.find { it.first == current }?.second ?: "默认音色")
}

private fun fmtCoin(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format("%.2f", v)

private fun modelTypeName(t: String): String = when (t) {
    "text" -> "文本"
    "tts" -> "语音"
    "asr" -> "识别"
    "image" -> "图片"
    "admin" -> "管理员"
    else -> t
}

data class ApiKeyEntry(
    val id: Int,
    val label: String,
    val type: String,
    val model: String,
    val raw: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfApiPanel(appVm: AppViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zhiyin", 0) }
    var keys by remember { mutableStateOf(listOf<ApiKeyEntry>()) }
    var selections by remember { mutableStateOf(mapOf<String, String>()) }

    var showAdd by remember { mutableStateOf(false) }
    var deleteKey by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var typeSelectorFor by remember { mutableStateOf<String?>(null) }

    fun refreshSelections() {
        val labels = mapOf("text" to "文字", "asr" to "ASR", "tts" to "TTS", "image" to "图片")
        selections = labels.mapValues { (type, label) ->
            val json = prefs.getString("active_${type}_model", "") ?: ""
            if (json.isEmpty()) "$label：未选择"
            else {
                try {
                    val o = JSONObject(json)
                    "$label：${o.optString("label", "")} (${o.optString("model", "")})"
                } catch (_: Exception) {
                    "$label：未选择"
                }
            }
        }
    }

    fun loadKeys() {
        ApiGateway.get("/api/keys", AppSession.token(), object : ApiGateway.Callback {
            override fun onSuccess(response: String) {
                try {
                    val arr = JSONArray(response)
                    val list = (0 until arr.length()).mapNotNull { i ->
                        val k = arr.getJSONObject(i)
                        ApiKeyEntry(
                            id = k.optInt("id"),
                            label = k.optString("label", ""),
                            type = k.optString("type", ""),
                            model = k.optString("model", ""),
                            raw = k.toString(),
                        )
                    }
                    keys = list
                    val validIds = list.map { it.id }.toSet()
                    mapOf("text" to "active_text_model", "asr" to "active_asr_model", "tts" to "active_tts_model", "image" to "active_image_model").forEach { (type, key) ->
                        val json = prefs.getString(key, "") ?: ""
                        if (json.isNotEmpty()) {
                            try {
                                val sel = JSONObject(json)
                                if (sel.optInt("id", -1) !in validIds) prefs.edit().remove(key).apply()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    refreshSelections()
                } catch (_: Exception) {
                }
            }

            override fun onError(error: String?) {}
        })
    }

    LaunchedEffect(Unit) {
        loadKeys()
        refreshSelections()
    }

    RubberBandBox(modifier = Modifier.fillMaxSize()) {
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            CardContainer {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前激活", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    listOf("text", "asr", "tts", "image").forEach { type ->
                        Text(
                            selections[type] ?: "${type}：未选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
        item {
            CardContainer {
                Column {
                    ListItem(
                        modifier = Modifier.clickable { typeSelectorFor = "text" },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        headlineContent = { Text("选择文字模型") },
                        trailingContent = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                    ListItem(
                        modifier = Modifier.clickable { typeSelectorFor = "tts" },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        headlineContent = { Text("选择TTS模型") },
                        trailingContent = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                    ListItem(
                        modifier = Modifier.clickable { typeSelectorFor = "asr" },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        headlineContent = { Text("选择ASR模型") },
                        trailingContent = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                    ListItem(
                        modifier = Modifier.clickable { typeSelectorFor = "image" },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        headlineContent = { Text("选择图片模型") },
                        trailingContent = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
            }
        }
        item {
            Button(
                onClick = { showAdd = true },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加 API Key")
            }
        }
        if (keys.isEmpty()) {
            item { EmptyHint("还没有添加 API Key") }
        }
        items(keys.size) { i ->
            val k = keys[i]
            CardContainer {
                ListItem(
                    modifier = Modifier.clickable {
                        prefs.edit().putString("active_${k.type}_model", k.raw).apply()
                        refreshSelections()
                        appVm.showToast("已激活: ${k.label}")
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                modelTypeName(k.type).take(1),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    },
                    headlineContent = { Text(k.label, style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("${modelTypeName(k.type)} · ${k.model}", style = MaterialTheme.typography.labelSmall) },
                    trailingContent = {
                        IconButton(onClick = { deleteKey = k }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
        }
    }

    if (showAdd) {
        AddKeySheet(
            appVm = appVm,
            onDismiss = { showAdd = false },
            onAdded = {
                showAdd = false
                loadKeys()
            },
        )
    }

    typeSelectorFor?.let { type ->
        val filtered = keys.filter { it.type == type }
        LingXinSheet(onDismiss = { typeSelectorFor = null }) {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "选择${modelTypeName(type)}模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "暂无已添加的${modelTypeName(type)}Key，请先添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                filtered.forEach { k ->
                    ListItem(
                        modifier = Modifier.clickable {
                            prefs.edit().putString("active_${type}_model", k.raw).apply()
                            refreshSelections()
                            appVm.showToast("已选择: ${k.label}")
                            typeSelectorFor = null
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        headlineContent = { Text(k.label) },
                        supportingContent = { Text(k.model, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    deleteKey?.let { k ->
        LingXinDialog(
            onDismiss = { deleteKey = null },
            title = "删除 Key",
            text = "确定要删除「${k.label}」吗？",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                deleteKey = null
                ApiGateway.delete("/api/keys/${k.id}", AppSession.token(), object : ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        appVm.showToast("删除成功")
                        loadKeys()
                    }

                    override fun onError(error: String?) {
                        appVm.showToast("删除失败: ${error ?: ""}")
                    }
                })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddKeySheet(
    appVm: AppViewModel,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    val types = listOf("text" to "文本模型", "asr" to "ASR语音识别", "tts" to "TTS语音合成", "image" to "图片生成")
    var currentType by remember { mutableStateOf("text") }
    var showTypePicker by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var modelOptions by remember { mutableStateOf<List<String>?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var pickingModel by remember { mutableStateOf(false) }

    fun clean(s: String) = s.replace(Regex("[\\u200B-\\u200D\\uFEFF\\u00A0\\u3000]"), "").trim()

    fun fetchModels() {
        val base = clean(baseUrl)
        val key = clean(apiKey)
        when {
            base.isEmpty() || key.isEmpty() -> { appVm.showToast("请先填写API地址和Key"); return }
            !base.startsWith("http://") && !base.startsWith("https://") -> {
                appVm.showToast("API地址必须以 http:// 或 https:// 开头"); return
            }
        }
        loadingModels = true
        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
            val result = try {
                val conn = URL(base.trimEnd('/') + "/models").openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    Either.error(
                        when (code) {
                            401, 403 -> "API Key 无效或权限不足"
                            404 -> "URL 路径不对（404），请确认 API 地址正确"
                            429 -> "请求过于频繁，请稍后重试"
                            else -> "HTTP $code"
                        }
                    )
                } else {
                    val models = JSONObject(body).getJSONArray("data")
                    Either.ok((0 until models.length()).map { models.getJSONObject(it).getString("id") })
                }
            } catch (e: Exception) {
                Either.error(e.message ?: "网络错误")
            }
            withContext(Dispatchers.Main) {
                loadingModels = false
                result.fold(
                    onSuccess = {
                        if (it.isEmpty()) appVm.showToast("服务端未返回模型列表")
                        else {
                            modelOptions = it
                            pickingModel = true
                        }
                    },
                    onFailure = { appVm.showToast("获取失败: $it") },
                )
            }
        }
    }

    LingXinSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "添加 API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton2(
                    text = types.find { it.first == currentType }?.second ?: "选择类型",
                ) { showTypePicker = true }
            }
            Spacer(Modifier.height(8.dp))
            SheetField(label, { label = it }, "名称（如：DeepSeek）")
            SheetField(baseUrl, { baseUrl = it }, "API地址（https://…/v1）")
            SheetField(apiKey, { apiKey = it }, "API Key")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable {
                        if (!loadingModels) fetchModels()
                    },
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.ifEmpty { "点击选择模型" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (model.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (loadingModels) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Button(
                onClick = {
                    if (label.trim().isEmpty() || baseUrl.trim().isEmpty() || apiKey.trim().isEmpty() || model.trim().isEmpty()) {
                        appVm.showToast("请填写完整信息")
                        return@Button
                    }
                    val body = JSONObject().apply {
                        put("label", label.trim())
                        put("base_url", clean(baseUrl))
                        put("api_key", clean(apiKey))
                        put("model", model.trim())
                        put("type", currentType)
                    }
                    ApiGateway.post("/api/keys", body.toString(), AppSession.token(), object : ApiGateway.Callback {
                        override fun onSuccess(response: String) {
                            appVm.showToast("添加成功")
                            onAdded()
                        }

                        override fun onError(error: String?) {
                            appVm.showToast("添加失败: ${error ?: ""}")
                        }
                    })
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(48.dp),
            ) {
                Text("保存")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showTypePicker) {
        LingXinSheet(onDismiss = { showTypePicker = false }) {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                types.forEach { (id, name) ->
                    ListItem(
                        modifier = Modifier.clickable {
                            currentType = id
                            showTypePicker = false
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (id == currentType) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        headlineContent = { Text(name) },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (pickingModel) {
        LingXinSheet(onDismiss = { pickingModel = false }) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "选择模型（${modelOptions?.size ?: 0}）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { pickingModel = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(modelOptions ?: emptyList()) { m ->
                        ListItem(
                            modifier = Modifier.clickable {
                                model = m
                                pickingModel = false
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (m == model) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            headlineContent = { Text(m, style = MaterialTheme.typography.bodyMedium) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private sealed class Either<out E, out V> {
    data class Err<E>(val error: E) : Either<E, Nothing>()
    data class Ok<V>(val value: V) : Either<Nothing, V>()

    companion object {
        fun <V> ok(v: V) = Ok<V>(v)
        fun error(msg: String) = Err(msg)
    }
}

private fun <E, V> Either<E, V>.fold(onSuccess: (V) -> Unit, onFailure: (E) -> Unit) {
    when (this) {
        is Either.Ok -> onSuccess(this.value)
        is Either.Err -> onFailure(this.error)
    }
}

@Composable
private fun TextButton2(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SheetField(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}
