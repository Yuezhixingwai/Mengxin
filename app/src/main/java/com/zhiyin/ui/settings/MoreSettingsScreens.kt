package com.zhiyin.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.data.AccountApi
import com.zhiyin.data.AppSession
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme


private val FREQ_VALUES = intArrayOf(30, 60, 90, 120, 180, 240, 300, 360, 420, 480)
private val DEPTH_OPTIONS = listOf("fast" to "快速", "basic" to "均衡", "advanced" to "深度")

@Composable
fun SearchSettingsScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
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

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "联网搜索",
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tavily API Key", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (hasKey) "已配置 ${if (maskedKey.isNotEmpty()) "($maskedKey)" else ""}" else "未配置，配置后聊天页可开启联网搜索",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariantActions,
                        )
                        Spacer(Modifier.height(10.dp))
                        TextField(
                            value = tavilyKey,
                            onValueChange = { tavilyKey = it },
                            label = if (maskedKey.isNotEmpty()) "当前: $maskedKey" else "tvly-…",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (tavilyKey.trim().isEmpty()) {
                                    appVm.showToast("请输入 Tavily Key")
                                } else {
                                    doSave(tavilyKey.trim(), null, null, null, null, null)
                                    tavilyKey = ""
                                }
                            },
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                        ) {
                            Text("保存 Key", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "没有 Key？前往 app.tavily.com 免费获取",
                            fontSize = 12.sp,
                            color = colors.primary,
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
                        Text("搜索深度", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Spacer(Modifier.height(10.dp))
                        TabRow(
                            tabs = DEPTH_OPTIONS.map { it.second },
                            selectedTabIndex = DEPTH_OPTIONS.indexOfFirst { it.first == depth }.let { if (it < 0) 1 else it },
                            onTabSelected = { idx ->
                                val id = DEPTH_OPTIONS[idx].first
                                depth = id
                                doSave(null, id, null, null, null, null)
                            },
                        )
                    }
                }

                CardContainer {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row {
                            Text("最大返回条数", fontSize = 16.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
                            Text("$maxResults 条", fontSize = 16.sp, color = colors.primary)
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
                            Text("搜索频率限制", fontSize = 16.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
                            Text(
                                if (frequency >= 60) "${frequency / 60} 分钟" else "$frequency 秒",
                                fontSize = 16.sp,
                                color = colors.primary,
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
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp, color = colors.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
        )
    }
}

@Composable
fun AnnouncementsScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
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

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "系统公告",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无公告", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
            }
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    list.forEach { ann ->
                        CardContainer {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Campaign,
                                        contentDescription = null,
                                        tint = colors.onSurface,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        ann.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                    )
                                }
                                if (ann.content.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(ann.content, fontSize = 14.sp, color = colors.onSurface)
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
