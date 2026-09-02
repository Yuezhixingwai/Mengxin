package com.zhiyin.ui.roundtable

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AppSession
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinMenuOverlay
import com.zhiyin.ui.components.MenuItemSpec
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.chat.MessageEntrance
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.components.UserAvatar
import com.zhiyin.ui.vm.AppViewModel
import com.zhiyin.ui.vm.TimeFmt
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

data class Meeting(
    val id: Int,
    val title: String,
    val topic: String,
    val status: String,
    val members: List<String>,
    val lastMessage: String?,
)

private fun parseMeetings(resp: String): List<Meeting> {
    return try {
        val arr = JSONObject(resp).optJSONArray("meetings") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val m = arr.getJSONObject(i)
            val last = m.optJSONObject("last_message")
            Meeting(
                id = m.optInt("id"),
                title = m.optString("title", ""),
                topic = m.optString("topic", ""),
                status = m.optString("status", "active"),
                members = m.optJSONArray("ai_members")?.let { a ->
                    (0 until a.length()).map { j -> a.getJSONObject(j).optString("name", "") }
                } ?: emptyList(),
                lastMessage = last?.let { "${it.optString("speaker_name", "")}: ${it.optString("content", "")}" },
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundtableScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenMeeting: (Int, String) -> Unit,
) {
    var meetings by remember { mutableStateOf<List<Meeting>>(emptyList()) }
    var deleteMeeting by remember { mutableStateOf<Meeting?>(null) }

    fun load() {
        ApiGateway.get("/api/roundtable?limit=50", AppSession.token(), object : ApiGateway.Callback {
            override fun onSuccess(response: String) {
                meetings = parseMeetings(response)
            }

            override fun onError(error: String?) {}
        })
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("圆桌会议", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                IconButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = "创建会议")
                }
            },
        )
        if (meetings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有圆桌会议", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onCreate, shape = RoundedCornerShape(50)) {
                        Text("发起一场讨论")
                    }
                }
            }
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(meetings, key = { it.id }) { meeting ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onOpenMeeting(meeting.id, meeting.title) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    meeting.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (meeting.status == "active") "进行中" else "已结束",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (meeting.status == "active") Color(0xFF34B78F)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (meeting.status == "closed") {
                                    IconButton(onClick = { deleteMeeting = meeting }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            if (meeting.topic.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    meeting.topic,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                meeting.members.joinToString("、"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            meeting.lastMessage?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }

    deleteMeeting?.let { m ->
        LingXinDialog(
            onDismiss = { deleteMeeting = null },
            title = "删除会议",
            text = "确定删除「${m.title}」？",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                deleteMeeting = null
                ApiGateway.delete("/api/roundtable/${m.id}", AppSession.token(), object : ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        appVm.showToast("已删除")
                        load()
                    }

                    override fun onError(error: String?) {
                        appVm.showToast("删除失败: ${error ?: ""}")
                    }
                })
            },
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun CreateMeetingScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var creating by remember { mutableStateOf(false) }
    val friends = appVm.friends

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("发起圆桌", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedField(title, { title = it }, "会议标题")
            OutlinedField(topic, { topic = it }, "讨论话题", minLines = 3)
            Spacer(Modifier.height(8.dp))
            Text(
                "选择与会 AI（2-6 位，已选 ${selected.value.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (friends.isEmpty()) {
                Text("请先添加AI好友", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                friends.forEach { f ->
                    val picked = f.name in selected.value
                    FilledTonalButton(
                        onClick = {
                            selected.value = if (picked) selected.value - f.name else selected.value + f.name
                        },
                        shape = RoundedCornerShape(50),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (picked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (picked) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(f.name)
                    }
                }
            }
            androidx.compose.material3.Button(
                onClick = {
                    val t = title.trim()
                    val tp = topic.trim()
                    when {
                        t.isEmpty() -> { appVm.showToast("请填写标题"); return@Button }
                        tp.isEmpty() -> { appVm.showToast("请填写话题"); return@Button }
                        selected.value.size < 2 -> { appVm.showToast("请至少选2个AI"); return@Button }
                        selected.value.size > 6 -> { appVm.showToast("最多6个AI"); return@Button }
                        creating -> return@Button
                    }
                    creating = true
                    val body = JSONObject().apply {
                        put("title", t)
                        put("topic", tp)
                        put("ai_members", JSONArray().apply {
                            friends.filter { it.name in selected.value }.forEach { f ->
                                put(JSONObject().apply {
                                    put("name", f.name)
                                    put("persona", f.persona ?: "讨论者")
                                })
                            }
                        })
                    }
                    ApiGateway.post("/api/roundtable", body.toString(), AppSession.token(), object : ApiGateway.Callback {
                        override fun onSuccess(response: String) {
                            appVm.showToast("创建成功")
                            creating = false
                            onCreated()
                        }

                        override fun onError(error: String?) {
                            appVm.showToast("失败: ${error ?: ""}")
                            creating = false
                        }
                    })
                },
                enabled = !creating,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(50.dp),
            ) {
                Text(if (creating) "创建中…" else "创建会议")
            }
        }
    }
}

@Composable
private fun OutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    minLines: Int = 1,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint) },
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

data class RtMessage(
    val index: Int,
    val role: String,
    val speaker: String,
    val content: String,
    val time: Long,
    val isSystem: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundtableChatScreen(
    appVm: AppViewModel,
    meetingId: Int,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf("圆桌会议") }
    var topic by remember { mutableStateOf("") }
    var members by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("active") }
    var messages by remember { mutableStateOf(listOf<RtMessage>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var summaryText by remember { mutableStateOf<String?>(null) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun loadMessages() {
        ApiGateway.get(
            "/api/roundtable/$meetingId/messages?limit=100",
            AppSession.token(),
            object : ApiGateway.Callback {
                override fun onSuccess(response: String) {
                    val arr = try {
                        JSONObject(response).optJSONArray("messages")
                    } catch (_: Exception) {
                        null
                    } ?: return
                    messages = (0 until arr.length()).map { i ->
                        val m = arr.getJSONObject(i)
                        RtMessage(
                            index = i,
                            role = m.optString("role", ""),
                            speaker = m.optString("speaker_name", ""),
                            content = m.optString("content", ""),
                            time = parseEpochUtc(m.optString("created_at", "")),
                            isSystem = m.optString("role", "") == "system",
                        )
                    }
                }

                override fun onError(error: String?) {}
            }
        )
    }

    fun loadMeeting() {
        ApiGateway.get("/api/roundtable/$meetingId", AppSession.token(), object : ApiGateway.Callback {
            override fun onSuccess(response: String) {
                val m = try {
                    JSONObject(response).optJSONObject("meeting")
                } catch (_: Exception) {
                    null
                } ?: return
                title = m.optString("title", "圆桌会议")
                topic = m.optString("topic", "")
                status = m.optString("status", "active")
                members = m.optJSONArray("ai_members")?.let { a ->
                    (0 until a.length()).map { j -> a.getJSONObject(j).optString("name", "") }
                } ?: emptyList()
            }

            override fun onError(error: String?) {}
        })
    }

    LaunchedEffect(meetingId) {
        loadMeeting()
        loadMessages()
        var cycle = 0
        while (true) {
            delay(4000)
            cycle++
            if (cycle % 5 == 0) loadMeeting()
            if (status == "active") loadMessages() else break
        }
    }

    val listState = rememberLazyListState()
    var initialCount by remember { mutableStateOf(Int.MAX_VALUE) }
    LaunchedEffect(messages.size) {
        if (initialCount == Int.MAX_VALUE && messages.isNotEmpty()) {
            initialCount = messages.size
            listState.scrollToItem(messages.size - 1)
        } else if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun doDebate(aiName: String, type: String) {
        if (status != "active") {
            appVm.showToast("会议已结束")
            return
        }
        appVm.showToast("AI辩论中…")
        val body = JSONObject()
        if (aiName.isNotEmpty()) body.put("ai_name", aiName)
        body.put("debate_type", type)
        ApiGateway.post("/api/roundtable/$meetingId/ai-debate", body.toString(), AppSession.token(), object : ApiGateway.Callback {
            override fun onSuccess(response: String) {
                appVm.showToast("完成")
                loadMessages()
            }

            override fun onError(error: String?) {
                appVm.showToast("辩论失败: ${error ?: ""}")
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (topic.isNotEmpty()) {
                        Text(
                            topic,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "会议操作")
                }
            },
        )

        if (members.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEach { name ->
                    FilledTonalButton(
                        onClick = { doDebate(name, "disagree") },
                        shape = RoundedCornerShape(50),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    ) { Text("$name 反驳", style = MaterialTheme.typography.labelMedium) }
                }
                FilledTonalButton(
                    onClick = { doDebate("", "question") },
                    shape = RoundedCornerShape(50),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                ) { Text("提问", style = MaterialTheme.typography.labelMedium) }
            }
        }

        RubberBandBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            items(messages, key = { it.index }) { msg ->
                val prev = messages.getOrNull(msg.index - 1)
                val showTime = prev == null || (msg.time - prev.time) > 5 * 60 * 1000L
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (showTime && msg.time > 0) {
                        Text(
                            TimeFmt.fullTime(msg.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    if (msg.isSystem) {
                        MessageEntrance(mine = false, animate = msg.index >= initialCount) {
                            Text(
                                "[系统] ${msg.content}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        RoundtableMessageRow(msg, animateIn = msg.index >= initialCount)
                    }
                }
            }
            item {
                Text(
                    "内容为AI生成，请注意甄别",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                )
            }
        }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        if (status == "active") "发表你的观点…" else "会议已结束",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                enabled = status == "active",
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty() || sending) return@FilledIconButton
                    sending = true
                    input = ""
                    val body = JSONObject().apply { put("content", text) }
                    ApiGateway.post(
                        "/api/roundtable/$meetingId/messages", body.toString(), AppSession.token(),
                        object : ApiGateway.Callback {
                            override fun onSuccess(response: String) {
                                sending = false
                                loadMessages()
                            }

                            override fun onError(error: String?) {
                                appVm.showToast("发送失败: ${error ?: ""}")
                                sending = false
                            }
                        }
                    )
                },
                enabled = status == "active" && input.isNotBlank() && !sending,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送", modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showMenu) {
        LingXinMenuOverlay(
            items = listOf(
                MenuItemSpec("生成会议总结", Icons.Rounded.Summarize),
                MenuItemSpec("结束会议", Icons.Rounded.Cancel, danger = true),
                MenuItemSpec("删除会议", Icons.Rounded.DeleteOutline, danger = true),
            ),
            onDismiss = { showMenu = false },
            onSelect = { index ->
                showMenu = false
                when (index) {
                    0 -> {
                        appVm.showToast("生成中…")
                        ApiGateway.post("/api/roundtable/$meetingId/summary", "{}", AppSession.token(), object : ApiGateway.Callback {
                            override fun onSuccess(response: String) {
                                summaryText = try {
                                    JSONObject(response).optString("summary", "暂无")
                                } catch (_: Exception) {
                                    "暂无"
                                }
                            }

                            override fun onError(error: String?) {
                                appVm.showToast("生成失败: ${error ?: ""}")
                            }
                        })
                    }
                    1 -> showCloseDialog = true
                    2 -> showDeleteDialog = true
                }
            },
        )
    }

    summaryText?.let { s ->
        LingXinDialog(
            onDismiss = { summaryText = null },
            title = "会议总结",
            text = s,
            confirmText = "确定",
            dismissText = null,
        )
    }

    if (showCloseDialog) {
        LingXinDialog(
            onDismiss = { showCloseDialog = false },
            title = "结束会议",
            text = "确定结束这场圆桌会议吗？结束后将无法继续发言。",
            confirmText = "结束",
            danger = true,
            onConfirm = {
                showCloseDialog = false
                ApiGateway.post("/api/roundtable/$meetingId/close", "{}", AppSession.token(), object : ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        appVm.showToast("已结束")
                        onBack()
                    }

                    override fun onError(error: String?) {
                        appVm.showToast("操作失败: ${error ?: ""}")
                    }
                })
            },
        )
    }

    if (showDeleteDialog) {
        LingXinDialog(
            onDismiss = { showDeleteDialog = false },
            title = "删除会议",
            text = "确定删除这场圆桌会议吗？",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                showDeleteDialog = false
                ApiGateway.delete("/api/roundtable/$meetingId", AppSession.token(), object : ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        appVm.showToast("已删除")
                        onBack()
                    }

                    override fun onError(error: String?) {
                        appVm.showToast("删除失败: ${error ?: ""}")
                    }
                })
            },
        )
    }
}

@Composable
private fun RoundtableMessageRow(msg: RtMessage, animateIn: Boolean) {
    val mine = msg.role == "user"
    MessageEntrance(mine = mine, animate = animateIn) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
            if (!mine) {
                RoundtableAvatar(msg.speaker)
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                Text(
                    if (mine) "你" else msg.speaker,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp),
                )
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (mine) 18.dp else 6.dp,
                        topEnd = if (mine) 6.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp,
                    ),
                    color = if (mine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.animateContentSize(),
                ) {
                    Text(
                        msg.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mine) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .widthIn(max = 264.dp),
                    )
                }
            }
            if (mine) {
                Spacer(Modifier.width(8.dp))
                UserAvatar(avatarUrl = null, size = 34.dp)
            }
        }
    }
}

@Composable
private fun RoundtableAvatar(speaker: String) {
    val friendId = remember(speaker) { FriendManager.findIdByName(speaker) }
    PersonaAvatar(contactId = friendId, name = speaker, size = 34.dp)
}

private fun parseEpochUtc(iso: String): Long {
    if (iso.isBlank()) return 0L
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(if (iso.length >= 19) iso.substring(0, 19) else iso)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}
