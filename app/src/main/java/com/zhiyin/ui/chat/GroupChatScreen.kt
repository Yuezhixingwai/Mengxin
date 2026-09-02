package com.zhiyin.ui.chat

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyin.ui.components.GroupAvatar
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.components.UserAvatar
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.vm.GroupChatViewModel
import com.zhiyin.ui.vm.TimeFmt
import com.zhiyin.ui.vm.ChatMsg

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    var input by rememberSaveable { mutableStateOf("") }
    var showPlusPanel by remember { mutableStateOf(false) }
    var showRedpacket by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var longEditorOpen by rememberSaveable { mutableStateOf(false) }
    val isLongInput = input.length >= 80 || input.count { it == '\n' } >= 3
    var actionMsgIndex by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = {
                Column {
                    Text(
                        groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${vm.group.members.size}人",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "群设置")
                }
            },
        )

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
            items(vm.messages, key = { it.index }) { msg ->
                val prev = vm.messages.getOrNull(msg.index - 1)
                val showTime = prev == null || (msg.time - prev.time) > 5 * 60 * 1000L
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (showTime && msg.time > 0) {
                        Text(
                            TimeFmt.fullTime(msg.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    GroupMessageRow(
                        msg = msg,
                        animateIn = msg.index >= initialCount,
                        onLongPress = { actionMsgIndex = msg.index },
                    )
                }
            }
            item {
                Text(
                    "内容为AI生成，请注意甄别",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                )
            }
        }
        }

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
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    TextButton(
                        onClick = { longEditorOpen = true },
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            "已输入${input.length}字",
                            style = MaterialTheme.typography.labelMedium,
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
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = { showPlusPanel = true }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = { Text("和大家聊点什么…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                maxLines = 4,
            )
            FilledIconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        vm.send(input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
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

    if (showPlusPanel) {
        LingXinSheet(onDismiss = { showPlusPanel = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "提及成员",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            input += "@所有人 "
                            showPlusPanel = false
                        },
                        shape = RoundedCornerShape(50),
                    ) { Text("@所有人") }
                    vm.group.members.forEach { m ->
                        FilledTonalButton(
                            onClick = {
                                input += "@$m "
                                showPlusPanel = false
                            },
                            shape = RoundedCornerShape(50),
                        ) { Text("@$m") }
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
            items = listOf(com.zhiyin.ui.components.MenuItemSpec("退出群聊", danger = true)),
            onDismiss = { showMenu = false },
            onSelect = {
                showMenu = false
                showLeave = true
            },
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
private fun GroupMessageRow(msg: ChatMsg, animateIn: Boolean = false, onLongPress: () -> Unit) {
    val mine = msg.role == "user"
    var speaker = ""
    var text = msg.content
    val match = Regex("^\\[([^\\]]+)\\]\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(msg.content)
    if (!mine && match != null) {
        speaker = match.groupValues[1]
        text = match.groupValues[2]
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
                GroupAvatar(36.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                if (!mine && speaker.isNotEmpty()) {
                    Text(
                        speaker,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (mine) 18.dp else 6.dp,
                        topEnd = if (mine) 6.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp,
                    ),
                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(max = 264.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
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
            onConfirm(Math.round(total * 100) / 100.0, cnt)
        },
    ) {
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
            placeholder = { Text("总金额（元）") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedTextField(
            value = count,
            onValueChange = { count = it.filter { ch -> ch.isDigit() } },
            placeholder = { Text("个数") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Text(
                        "长文本编辑",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onApply(text) }) { Text("完成") }
                }
                Text(
                    "${text.length} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    placeholder = { Text("输入长文本…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Button(
                    onClick = { if (text.isNotBlank()) onSend(text.trim()) },
                    enabled = text.isNotBlank(),
                    shape = RoundedCornerShape(50),
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
