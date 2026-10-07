package com.zhiyin.ui.chat

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.ui.components.ImageCropperDialog
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.components.UserAvatar
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.vm.ChatMsg
import com.zhiyin.ui.vm.GroupChatViewModel
import com.zhiyin.ui.vm.TimeFmt
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
)

@Composable
fun GroupChatScreen(
    groupName: String,
    members: List<String>?,
    onBack: () -> Unit,
) {
    val vm: GroupChatViewModel = viewModel(
        key = "group_$groupName",
        factory = GroupChatViewModel.factory(groupName, members),
    )
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.enter() }

    var input by rememberSaveable { mutableStateOf("") }
    var showPlusPanel by remember { mutableStateOf(false) }
    var showRedpacket by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var longEditorOpen by rememberSaveable { mutableStateOf(false) }
    val isLongInput = input.length >= 80 || input.count { it == '\n' } >= 3
    var actionMsgIndex by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val bgPrefs = remember { context.getSharedPreferences("zhiyin_chat_bg", 0) }
    var chatBgPath by remember { mutableStateOf(bgPrefs.getString("bg_path", "") ?: "") }
    var cropSource by remember { mutableStateOf<String?>(null) }
    val bgPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val copied = ContentCopy.copyToCache(context, uri, "chatbg")
        if (copied != null) cropSource = copied.path
    }
    val bgBmp by produceState<ImageBitmap?>(initialValue = null, chatBgPath) {
        value = if (chatBgPath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                if (File(chatBgPath).exists()) decodeSampledGroup(chatBgPath, 1440)?.asImageBitmap() else null
            }
        } else null
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (vm.messages.size - 1).coerceAtLeast(0),
    )
    var initialCount by remember { mutableStateOf(Int.MAX_VALUE) }
    var lastMsgKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isEmpty()) return@LaunchedEffect
        val key = vm.messages.last().let { it.role + "|" + it.time + "|" + it.content }
        if (initialCount == Int.MAX_VALUE) {
            initialCount = vm.messages.size
            listState.scrollToItem(vm.messages.size - 1)
        } else if (key == lastMsgKey) {
            listState.scrollToItem(vm.messages.size - 1)
        } else {
            listState.animateScrollToItem(vm.messages.size - 1)
        }
        lastMsgKey = key
    }

    val hazeState = remember { HazeState() }
    val topBarTotalHeight = 52.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    var inputBarHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(inputBarHeight) {
        if (inputBarHeight <= 0.dp || vm.messages.isEmpty()) return@LaunchedEffect
        val lastMessageIndex = vm.messages.size - 1
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisibleIndex >= lastMessageIndex - 1) {
            listState.scrollToItem(lastMessageIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        bgBmp?.let {
            Image(
                bitmap = it,
                contentDescription = "聊天背景",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (bgBmp != null) Modifier.padding(top = topBarTotalHeight, bottom = inputBarHeight) else Modifier),
        ) {
        RubberBandBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (bgBmp == null) Modifier.hazeSource(hazeState) else Modifier),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = if (bgBmp == null) topBarTotalHeight else 0.dp,
                bottom = if (bgBmp == null) 10.dp + inputBarHeight else 10.dp,
            ),
        ) {
            items(vm.messages, key = { it.index }) { msg ->
                val prev = vm.messages.getOrNull(msg.index - 1)
                val showTime = prev == null || (msg.time - prev.time) > 5 * 60 * 1000L
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (showTime && msg.time > 0) {
                        Text(
                            TimeFmt.fullTime(msg.time),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    GroupMessageRow(
                        msg = msg,
                        memberNames = vm.group.members.toList(),
                        animateIn = msg.index >= initialCount,
                        onLongPress = { actionMsgIndex = msg.index },
                    )
                }
            }
            item {
                Text(
                    "内容为AI生成，请注意甄别",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                )
            }
        }
        }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (bgBmp == null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.88f),
                            blurRadius = 18.dp,
                            noiseFactor = 0.06f,
                        ),
                    )
                } else {
                    Modifier
                })
                .onSizeChanged { inputBarHeight = with(density) { it.height.toDp() } }
                .navigationBarsPadding()
                .imePadding(),
        ) {
        AnimatedVisibility(
            visible = isLongInput,
            enter = expandVertically(tween(180)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Surface(
                    onClick = { longEditorOpen = true },
                    shape = RoundedCornerShape(50),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已输入${input.length}字",
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Rounded.OpenInFull,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = { showPlusPanel = true }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "更多", tint = MiuixTheme.colorScheme.onSurfaceContainerVariant)
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                label = "和大家聊点什么…",
                useLabelAsPlaceholder = true,
                cornerRadius = 24.dp,
                maxLines = 4,
                insideMargin = DpSize(16.dp, 12.dp),
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        vm.send(input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.size(44.dp),
                backgroundColor = if (input.isNotBlank()) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(20.dp),
                    tint = if (input.isNotBlank()) {
                        MiuixTheme.colorScheme.onPrimary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainerVariant
                    },
                )
            }
        }
        }

        SmallTopAppBar(
            color = Color.Transparent,
            modifier = if (bgBmp == null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.88f),
                        blurRadius = 18.dp,
                        noiseFactor = 0.06f,
                    ),
                )
            } else {
                Modifier
            },
            navigationIcon = { BackButton(onClick = onBack) },
            title = groupName,
            subtitle = "${vm.group.members.size}人",
            subtitleColor = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "群设置")
                }
            },
        )
    }

    if (showPlusPanel) {
        LingXinSheet(onDismiss = { showPlusPanel = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "提及成员",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    TextButton(
                        text = "@所有人",
                        onClick = {
                            input += "@所有人 "
                            showPlusPanel = false
                        },
                        cornerRadius = 50.dp,
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.secondaryContainer,
                            textColor = MiuixTheme.colorScheme.onSecondaryContainer,
                        ),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        minWidth = 0.dp,
                        minHeight = 36.dp,
                    )
                    vm.group.members.forEach { m ->
                        TextButton(
                            text = "@$m",
                            onClick = {
                                input += "@$m "
                                showPlusPanel = false
                            },
                            cornerRadius = 50.dp,
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.secondaryContainer,
                                textColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            ),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            minWidth = 0.dp,
                            minHeight = 36.dp,
                        )
                    }
                }
                com.zhiyin.ui.SheetActionRow(
                    icon = Icons.Rounded.Redeem,
                    label = "发群红包",
                ) {
                    showPlusPanel = false
                    showRedpacket = true
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showRedpacket) {
        GroupRedpacketDialog(
            memberCount = vm.group.members.size,
            onDismiss = { showRedpacket = false },
            onConfirm = { total, count ->
                showRedpacket = false
                vm.sendRedpacket(total, count)
            },
        )
    }

    if (longEditorOpen) {
        LongTextEditor(
            initial = input,
            onDismiss = { longEditorOpen = false },
            onApply = {
                input = it
                longEditorOpen = false
            },
            onSend = { text ->
                longEditorOpen = false
                input = ""
                vm.send(text)
            },
        )
    }

    actionMsgIndex?.let { index ->
        val copyText = vm.messages.getOrNull(index)?.content?.let { c ->
            if (c.startsWith("[")) {
                Regex("^\\[([^\\]]+)\\]\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(c)
                    ?.groupValues?.get(2)?.trim() ?: c
            } else c
        }.orEmpty()
        LingXinSheet(onDismiss = { actionMsgIndex = null }) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (copyText.isNotEmpty()) {
                    com.zhiyin.ui.SheetActionRow(
                        icon = Icons.Rounded.ContentCopy,
                        label = "复制消息",
                    ) {
                        actionMsgIndex = null
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("chat", copyText))
                    }
                }
                com.zhiyin.ui.SheetActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "删除消息",
                    danger = true,
                ) {
                    actionMsgIndex = null
                    vm.deleteMessage(index)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showMenu) {
        com.zhiyin.ui.components.LingXinMenuOverlay(
            items = listOf(
                com.zhiyin.ui.components.MenuItemSpec("设置聊天背景", icon = Icons.Rounded.Wallpaper),
                com.zhiyin.ui.components.MenuItemSpec("恢复默认背景", icon = Icons.Rounded.Refresh),
                com.zhiyin.ui.components.MenuItemSpec("退出群聊", danger = true),
            ),
            onDismiss = { showMenu = false },
            onSelect = { index ->
                showMenu = false
                when (index) {
                    0 -> bgPick.launch("image/*")
                    1 -> {
                        bgPrefs.edit().remove("bg_path").apply()
                        chatBgPath = ""
                    }
                    2 -> showLeave = true
                }
            },
        )
    }

    cropSource?.let { path ->
        ImageCropperDialog(
            path = path,
            frameAspect = context.resources.displayMetrics.let { it.widthPixels.toFloat() / it.heightPixels },
            onConfirm = { bmp ->
                cropSource = null
                thread {
                    try {
                        val f = File(context.filesDir, "chat_bg.jpg")
                        f.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                        bgPrefs.edit().putString("bg_path", f.absolutePath).apply()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            chatBgPath = f.absolutePath
                        }
                    } catch (_: Exception) {
                    }
                }
            },
            onCancel = { cropSource = null },
        )
    }

    if (showLeave) {
        LingXinDialog(
            onDismiss = { showLeave = false },
            title = "退出群聊",
            text = "确定退出群聊「$groupName」？退出后本地消息将删除。",
            confirmText = "退出",
            danger = true,
            onConfirm = {
                showLeave = false
                vm.leaveAndDelete()
                onBack()
            },
        )
    }
}

@Composable
private fun GroupMessageRow(
    msg: ChatMsg,
    memberNames: List<String>,
    animateIn: Boolean = false,
    onLongPress: () -> Unit,
) {
    val mine = msg.role == "user"
    var text = msg.content
    var speakerName: String? = null
    if (!mine) {
        // 表情标记可能出现在说话人之前，不能把 STICKER 当成角色名。
        text = text.replace(Regex("^\\[STICKER:[^\\]]+]\\s*"), "")
        val match = Regex("^[\\[【]([^\\]】]+)[\\]】]\\s*[:：]?\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
        if (match != null) {
            val candidate = match.groupValues[1].trim()
            if (memberNames.contains(candidate)) {
                speakerName = candidate
                text = match.groupValues[2]
            }
        }
        if (speakerName == null) speakerName = memberNames.firstOrNull()
    }
    MessageEntrance(mine = mine, animate = animateIn) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onLongPress),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
            if (!mine) {
                PersonaAvatar(
                    contactId = FriendManager.findIdByName(speakerName),
                    name = speakerName,
                    size = 36.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                if (!mine) {
                    Text(
                        text = speakerName ?: "群成员",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                    )
                }
                if ((mine && text.trim().startsWith("(红包")) || (!mine && text.trim().startsWith("(收款"))) {
                    GroupMoneyBubble(text, received = !mine)
                } else {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = if (mine) 18.dp else 6.dp,
                            topEnd = if (mine) 6.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp,
                        ),
                        color = if (mine) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text,
                            color = if (mine) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .widthIn(max = 264.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            if (mine) {
                Spacer(Modifier.width(8.dp))
                UserAvatar(avatarUrl = null, size = 36.dp)
            }
        }
    }
}

@Composable
private fun GroupMoneyBubble(raw: String, received: Boolean) {
    val inner = raw.trim().removePrefix(if (received) "(收款" else "(红包").removeSuffix(")").trim()
    val countMatch = Regex("(\\d+)个").find(inner)
    val countText = countMatch?.groupValues?.get(1)?.let { "$it 个" } ?: "群红包"
    val amountText = Regex("([0-9]+(?:\\.[0-9]+)?)元").find(inner)
        ?.groupValues?.get(1)?.let { if (received) "¥$it" else "¥$it · $countText" }
        ?: if (received) "已存入钱包" else countText
    val color = if (received) Color(0xFF34B78F) else Color(0xFFF5A623)
    Surface(shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(min = 150.dp)) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color)),
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (received) Icons.Rounded.AccountBalance else Icons.Rounded.Redeem,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (received) "已收款" else "红包", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    amountText,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GroupRedpacketDialog(
    memberCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Double, Int) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var count by remember { mutableStateOf(if (memberCount > 0) memberCount.toString() else "1") }
    var error by remember { mutableStateOf<String?>(null) }
    LingXinDialog(
        onDismiss = onDismiss,
        title = "发红包",
        confirmText = "塞钱进红包",
        onConfirm = {
            val total = amount.toDoubleOrNull()
            if (total == null || total <= 0) {
                error = "请输入正确的金额"
                return@LingXinDialog
            }
            var cnt = count.toIntOrNull() ?: 1
            if (cnt < 1) cnt = 1
            if (memberCount > 0 && cnt > memberCount) cnt = memberCount
            if (total < cnt * 0.01) {
                error = "每个红包至少需要 0.01 元"
                return@LingXinDialog
            }
            onConfirm(Math.round(total * 100) / 100.0, cnt)
        },
    ) {
        Spacer(Modifier.height(16.dp))
        TextField(
            value = amount,
            onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = "总金额（元）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = count,
            onValueChange = { count = it.filter { ch -> ch.isDigit() } },
            label = "个数",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LongTextEditor(
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window
            ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        focusRequester.requestFocus()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = MiuixTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(text = "取消", onClick = onDismiss, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp), minHeight = 40.dp)
                    Text(
                        "长文本编辑",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(text = "完成", onClick = { onApply(text) }, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp), minHeight = 40.dp)
                }
                Text(
                    "${text.length} 字",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = "输入长文本…",
                    useLabelAsPlaceholder = true,
                )
                Button(
                    onClick = { if (text.isNotBlank()) onSend(text.trim()) },
                    enabled = text.isNotBlank(),
                    cornerRadius = 50.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    Text("发送")
                }
            }
        }
    }
}

private fun decodeSampledGroup(path: String, target: Int): android.graphics.Bitmap? {
    return try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > target || opts.outHeight / sample > target) sample *= 2
        android.graphics.BitmapFactory.decodeFile(
            path,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } catch (_: Exception) {
        null
    }
}
