package com.zhiyin.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AccountApi
import com.zhiyin.data.AppSession
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject


private val FREQ_VALUES = intArrayOf(30, 60, 90, 120, 180, 240, 300, 360, 420, 480)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSettingsScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("zhiyin_search", 0) }

    var depth by remember { mutableStateOf(sp.getString("search_depth", "basic") ?: "basic") }
    var maxResults by remember { mutableIntStateOf(sp.getInt("max_results", 5)) }
    var includeSummary by remember { mutableStateOf(sp.getBoolean("include_summary", true)) }
    var autoParseUrl by remember { mutableStateOf(sp.getBoolean("auto_parse_url", true)) }
    var frequency by remember { mutableIntStateOf(sp.getInt("search_frequency", 60)) }
    var tavilyKey by remember { mutableStateOf("") }
    var maskedKey by remember { mutableStateOf(sp.getString("tavily_key_masked", "") ?: "") }
    var hasKey by remember { mutableStateOf(sp.getBoolean("has_tavily_key", false)) }

    LaunchedEffect(Unit) {
        val resp = withContext(Dispatchers.IO) {
            try {
                ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/search/config", "GET", null, AppSession.token())
            } catch (_: Exception) {
                null
            }
        }
        if (resp != null) {
            try {
                val json = JSONObject(resp)
                val keyMasked = json.optString("tavily_key", "")
                val serverHasKey = json.optBoolean("has_key", false)
                if (serverHasKey) {
                    sp.edit().putBoolean("has_tavily_key", true)
                        .putString("tavily_key_masked", keyMasked).apply()
                    hasKey = true
                }
                if (keyMasked.isNotEmpty()) maskedKey = keyMasked
            } catch (_: Exception) {
            }
        }
    }

    fun doSave(key: String?, depthNew: String?, maxNew: Int?, summaryNew: Boolean?, autoNew: Boolean?, freqNew: Int?) {
        val body = JSONObject()
        try {
            key?.let { body.put("tavily_key", it) }
            depthNew?.let { body.put("search_depth", it) }
            maxNew?.let { body.put("max_results", it) }
            summaryNew?.let { body.put("include_summary", it) }
            autoNew?.let { body.put("auto_parse_url", it) }
            freqNew?.let { body.put("search_frequency", it) }
        } catch (_: Exception) {
            return
        }
        ApiGateway.post("/api/search/config", body.toString(), AppSession.token(), object : ApiGateway.Callback {
            override fun onSuccess(response: String) {
                val ed = sp.edit()
                key?.let {
                    ed.putBoolean("has_tavily_key", it.isNotEmpty())
                    if (it.isNotEmpty()) {
                        ed.putString("tavily_key_masked", if (it.length > 4) "****" + it.takeLast(4) else "****")
                        maskedKey = if (it.length > 4) "****" + it.takeLast(4) else "****"
                        hasKey = true
                    }
                }
                depthNew?.let { ed.putString("search_depth", it) }
                maxNew?.let { ed.putInt("max_results", it) }
                summaryNew?.let { ed.putBoolean("include_summary", it) }
                autoNew?.let { ed.putBoolean("auto_parse_url", it) }
                freqNew?.let { ed.putInt("search_frequency", it) }
                ed.apply()
                appVm.showToast("设置已保存")
            }

            override fun onError(error: String?) {
                appVm.showToast("保存失败: ${error ?: ""}")
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("联网搜索", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CardContainer {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tavily API Key", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hasKey) "已配置 ${if (maskedKey.isNotEmpty()) "($maskedKey)" else ""}" else "未配置，配置后聊天页可开启联网搜索",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tavilyKey,
                        onValueChange = { tavilyKey = it },
                        placeholder = { Text(if (maskedKey.isNotEmpty()) "当前: $maskedKey" else "tvly-…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            if (tavilyKey.trim().isEmpty()) {
                                appVm.showToast("请输入 Tavily Key")
                            } else {
                                doSave(tavilyKey.trim(), null, null, null, null, null)
                                tavilyKey = ""
                            }
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text("保存 Key")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "没有 Key？前往 app.tavily.com 免费获取",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://app.tavily.com/home")
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        },
                    )
                }
            }

            CardContainer {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("搜索深度", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("fast" to "快速", "basic" to "均衡", "advanced" to "深度").forEachIndexed { i, (id, name) ->
                            SegmentedButton(
                                selected = depth == id,
                                onClick = {
                                    depth = id
                                    doSave(null, id, null, null, null, null)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                            ) { Text(name, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }

            CardContainer {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row {
                        Text("最大返回条数", modifier = Modifier.weight(1f))
                        Text("$maxResults 条", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = maxResults.toFloat(),
                        onValueChange = { maxResults = it.toInt().coerceIn(1, 20) },
                        onValueChangeFinished = { doSave(null, null, maxResults, null, null, null) },
                        valueRange = 1f..20f,
                        steps = 18,
                    )
                }
            }

            CardContainer {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row {
                        Text("搜索频率限制", modifier = Modifier.weight(1f))
                        Text(
                            if (frequency >= 60) "${frequency / 60} 分钟" else "$frequency 秒",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val freqIdx = FREQ_VALUES.indexOfFirst { it >= frequency }.let { if (it < 0) 1 else it }
                    var sliderPos by remember(freqIdx) { mutableFloatStateOf(freqIdx.toFloat()) }
                    Slider(
                        value = sliderPos,
                        onValueChange = { sliderPos = it },
                        onValueChangeFinished = {
                            frequency = FREQ_VALUES[sliderPos.toInt().coerceIn(0, FREQ_VALUES.lastIndex)]
                            doSave(null, null, null, null, null, frequency)
                        },
                        valueRange = 0f..(FREQ_VALUES.size - 1).toFloat(),
                        steps = FREQ_VALUES.size - 2,
                    )
                }
            }

            CardContainer {
                Column {
                    SettingSwitchRow("返回AI摘要", includeSummary) {
                        includeSummary = it
                        doSave(null, null, null, it, null, null)
                    }
                    SettingSwitchRow("自动解析消息中的链接", autoParseUrl) {
                        autoParseUrl = it
                        doSave(null, null, null, null, it, null)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        }
    }
}

@Composable
internal fun SettingSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(appVm: AppViewModel, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var list by remember { mutableStateOf(listOf<AccountApi.Announcement>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/announcements/active", "GET", null, AppSession.token()
                )
                val arr = JSONObject(resp).optJSONArray("announcements")
                if (arr != null) {
                    list = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        AccountApi.Announcement(
                            id = o.optInt("id"),
                            title = o.optString("title", "系统公告"),
                            content = o.optString("content", ""),
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("系统公告", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无公告", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                list.forEach { ann ->
                    CardContainer {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Campaign,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(ann.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                            if (ann.content.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(ann.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
            }
        }
    }
}
