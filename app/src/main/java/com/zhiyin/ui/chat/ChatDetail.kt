package com.zhiyin.ui.chat

import android.content.Intent
import android.graphics.Bitmap
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyin.data.AppSession
import com.zhiyin.data.VoicePlayer
import com.zhiyin.logic.chat.ChatEngine
import com.zhiyin.logic.data.PersonaManager
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.logic.util.StickerManager
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.EnsurePayPasswordFlow
import com.zhiyin.ui.components.ImageCropperDialog
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.components.UserAvatar
import com.zhiyin.ui.vm.ChatMsg
import com.zhiyin.ui.vm.ChatViewModel
import com.zhiyin.ui.vm.TimeFmt
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

sealed interface Bubble {
    data class Text(val text: String, val error: Boolean = false) : Bubble
    data class Think(val reasoning: String, val sourcesJson: String) : Bubble
    data class Sticker(val fileName: String, val custom: Boolean, val extra: String) : Bubble
    data class Image(val path: String) : Bubble
    data class FileMsg(val name: String, val url: String?) : Bubble
    data class Voice(val path: String, val seconds: Long) : Bubble
    data class Pat(val text: String) : Bubble
    data class Money(val kind: Kind, val amountText: String, val note: String, val amount: Double, val packetId: Long = 0) : Bubble

    enum class Kind { TRANSFER, REDPACKET, RECEIPT }
}

fun parseBubble(raw: String): Bubble {
    val c = raw.trim()
    return when {
        c.startsWith("[voice]") -> {
            val body = c.removePrefix("[voice]")
            val parts = body.split("|")
            Bubble.Voice(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L)
        }
        c.indexOf("[STICKER:") >= 0 || c.indexOf("[CUSTOM_STICKER:") >= 0 -> {
            val plainIdx = c.indexOf("[STICKER:")
            val customIdx = c.indexOf("[CUSTOM_STICKER:")
            val isCustom = customIdx >= 0 && (plainIdx < 0 || customIdx < plainIdx)
            val marker = if (isCustom) "[CUSTOM_STICKER:" else "[STICKER:"
            val name = c.substringAfter(marker, "").substringBefore("]").trim()
            val extra = c.replace(Regex("\\[(CUSTOM_)?STICKER:[^\\]]*\\]"), "").trim()
            Bubble.Sticker(name, custom = isCustom, extra = extra)
        }
        c.startsWith("[image]") -> Bubble.Image(c.removePrefix("[image]"))
        c.startsWith("[file]") -> {
            val body = c.removePrefix("[file]")
            val idx = body.indexOf("||")
            if (idx > 0) Bubble.FileMsg(body.substring(0, idx), body.substring(idx + 2))
            else Bubble.FileMsg(body, null)
        }
        c.startsWith("[pat]") -> Bubble.Pat(c.removePrefix("[pat]"))
        c.startsWith("(转账 ") || c.startsWith("(红包") || c.startsWith("(收款 ") -> parseMoney(c)
        c.startsWith("[错误]") -> Bubble.Text(c, error = true)
        c.contains(ChatEngine.MARK_THINK) || c.contains(ChatEngine.MARK_SRC) -> Bubble.Think(
            reasoning = c.substringAfter(ChatEngine.MARK_THINK, "").substringBefore(ChatEngine.MARK_SRC).trim(),
            sourcesJson = c.substringAfter(ChatEngine.MARK_SRC, "").trim(),
        )
        else -> Bubble.Text(c)
    }
}

private fun parseMoney(c: String): Bubble.Money {
    val kind = when {
        c.startsWith("(转账 ") -> Bubble.Kind.TRANSFER
        c.startsWith("(红包") -> Bubble.Kind.REDPACKET
        else -> Bubble.Kind.RECEIPT
    }
    var inner = c
        .removePrefix("(转账 ").removePrefix("(红包 ").removePrefix("(收款 ")
        .trim().removeSuffix(")").removeSuffix("）").trim()
    var packetId = 0L
    val hashIdx = inner.indexOf("|#")
    if (hashIdx >= 0) {
        packetId = inner.substring(hashIdx + 2).trim().toLongOrNull() ?: 0L
        inner = inner.substring(0, hashIdx)
    }
    val note = inner.substringAfter("|", "").trim()
    val amount = inner.substringBefore("|").trim().removeSuffix("元").trim().toDoubleOrNull() ?: 0.0
    return Bubble.Money(kind, c, note, amount, packetId)
}

private fun moneyFmt(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) Math.round(v).toString()
    else (Math.round(v * 100) / 100.0).toString()

private fun copyableTextOf(bubble: Bubble): String = when (bubble) {
    is Bubble.Text -> bubble.text
    is Bubble.Think -> bubble.reasoning
    is Bubble.Sticker -> bubble.extra
    is Bubble.Pat -> bubble.text
    else -> ""
}

private sealed interface PreviewTarget {
    data class FileImage(val path: String) : PreviewTarget
    data class StickerImage(val fileName: String, val custom: Boolean) : PreviewTarget
}


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
)
@Composable
fun ChatDetailScreen(
    personaName: String,
    personaDesc: String,
    personaId: Int,
    onBack: () -> Unit,
    onOpenFriendSettings: (Int) -> Unit,
    onOpenSearchSettings: () -> Unit = {},
    onOpenMe: () -> Unit = {},
) {
    val vm: ChatViewModel = viewModel(
        key = "chat_$personaName",
        factory = ChatViewModel.factory(personaName, personaDesc, personaId),
    )
    val context = LocalContext.current

    var localToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(localToast) {
        if (localToast != null) {
            kotlinx.coroutines.delay(2000)
            localToast = null
        }
    }

    val remarkPrefs = remember { context.getSharedPreferences("zhiyin_remark", 0) }
    val displayName = remember(personaId, personaName) {
        if (personaId >= 0) remarkPrefs.getString("orig_$personaId", null)?.takeIf { it.isNotBlank() } ?: personaName
        else personaName
    }
    val remarkName = remember(personaId) {
        if (personaId >= 0) remarkPrefs.getString("remark_$personaId", null)?.takeIf { it.isNotBlank() } else null
    }
    val headerSubtitle = remarkName ?: displayName

    var showChatSettings by remember { mutableStateOf(false) }
    var actionMsgIndex by remember { mutableStateOf<Int?>(null) }
    var showTransfer by remember { mutableStateOf(false) }
    var showRedpacket by remember { mutableStateOf(false) }
    var showPayGuard by remember { mutableStateOf<PayGuardAction?>(null) }
    var moneyDetail by remember { mutableStateOf<Pair<Bubble.Money, Long>?>(null) }
    var previewTarget by remember { mutableStateOf<PreviewTarget?>(null) }

    val bgPrefs = remember { context.getSharedPreferences("zhiyin_chat_bg", 0) }
    var chatBgPath by remember { mutableStateOf(bgPrefs.getString("bg_path", "") ?: "") }
    var cropSource by remember { mutableStateOf<String?>(null) }
    val bgPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val copied = ContentCopy.copyToCache(context, uri, "chatbg")
        if (copied != null) cropSource = copied.path else localToast = "读取图片失败"
    }
    val bgBmp by produceState<ImageBitmap?>(initialValue = null, chatBgPath) {
        value = if (chatBgPath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                if (File(chatBgPath).exists()) decodeSampled(chatBgPath, 1440)?.asImageBitmap() else null
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
    LaunchedEffect(Unit) { vm.enter() }

    var startDragX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val hazeState = remember { HazeState() }
    val topBarTotalHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
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
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        startDragX += dragAmount
                    },
                    onDragEnd = {
                        if (startDragX > 120.dp.toPx()) onBack()
                        startDragX = 0f
                    },
                    onDragCancel = { startDragX = 0f },
                )
            }
            .background(MaterialTheme.colorScheme.surface),
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
                val bubble = remember(msg.index, msg.content) { parseBubble(msg.content) }
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
                    MessageRow(
                        msg = msg,
                        bubble = bubble,
                        personaName = personaName,
                        personaId = personaId,
                        animateIn = msg.index >= initialCount,
                        onLongPress = { actionMsgIndex = msg.index },
                        onPat = { vm.pat() },
                        onOpenFriendSettings = { onOpenFriendSettings(personaId) },
                        onOpenMe = onOpenMe,
                        onOpenMoney = { m, t -> moneyDetail = m to t },
                        onPreviewImage = { previewTarget = it },
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
        }

        ChatInputBar(
            personaName = personaName,
            personaId = personaId,
            onSend = { text -> vm.send(text) },
            onSendSticker = { marker -> vm.sendSticker(marker) },
            onSendImage = { path -> vm.sendImage(path) },
            onSendFile = { name, path -> vm.sendFile(name, path) },
            onSendVoice = { path, sec -> vm.sendVoice(path, sec) },
            onTransfer = { showTransfer = true },
            onRedpacket = { showRedpacket = true },
            onAddCustomSticker = { localToast = "表情包已添加" },
            onLocalToast = { localToast = it },
            transparentBackground = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (bgBmp == null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
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
        )

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
            modifier = if (bgBmp == null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        blurRadius = 18.dp,
                        noiseFactor = 0.06f,
                    ),
                )
            } else {
                Modifier
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            title = {
                Column {
                    Text(
                        personaName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (vm.typing) "正在输入…" else headerSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (vm.typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                IconButton(onClick = { localToast = "语音通话功能开发中" }) {
                    Icon(Icons.Rounded.Call, contentDescription = "语音通话")
                }
                IconButton(onClick = { showChatSettings = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "会话设置")
                }
            },
        )
    }

    previewTarget?.let { target ->
        Dialog(
            onDismissRequest = { previewTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val fullBmp by produceState<ImageBitmap?>(initialValue = null, target) {
                value = withContext(Dispatchers.IO) {
                    when (target) {
                        is PreviewTarget.FileImage -> decodeSampled(target.path, 2048)?.asImageBitmap()
                        is PreviewTarget.StickerImage ->
                            decodeStickerFull(context, target.fileName, target.custom)?.asImageBitmap()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { previewTarget = null })
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (fullBmp != null) {
                    Image(
                        bitmap = fullBmp!!,
                        contentDescription = "预览",
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                }
            }
        }
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
                            localToast = "聊天背景已更换"
                        }
                    } catch (_: Exception) {
                    }
                }
            },
            onCancel = { cropSource = null },
        )
    }

    if (showChatSettings) {
        LingXinSheet(onDismiss = { showChatSettings = false }) {
            ChatSettingsSheetContent(
                vm = vm,
                hasChatBg = chatBgPath.isNotEmpty(),
                onSetBackground = {
                    showChatSettings = false
                    bgPick.launch("image/*")
                },
                onClearBackground = {
                    showChatSettings = false
                    bgPrefs.edit().remove("bg_path").apply()
                    chatBgPath = ""
                    localToast = "已恢复默认背景"
                },
                onDismiss = { showChatSettings = false },
                onOpenFriendSettings = {
                    showChatSettings = false
                    onOpenFriendSettings(personaId)
                },
                onOpenSearchSettings = {
                    showChatSettings = false
                    onOpenSearchSettings()
                },
                onLocalToast = { localToast = it },
            )
        }
    }

    actionMsgIndex?.let { index ->
        val msg = vm.messages.getOrNull(index)
        val bubble = msg?.let { parseBubble(it.content) }
        val canRegen = msg?.role == "ai" &&
            ((bubble is Bubble.Text && !bubble.error) || bubble is Bubble.Think)
        val copyText = bubble?.let { copyableTextOf(it) }.orEmpty()
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
                        localToast = "已复制"
                    }
                }
                if (canRegen) {
                    com.zhiyin.ui.SheetActionRow(
                        icon = Icons.Rounded.Refresh,
                        label = "重新生成",
                    ) {
                        actionMsgIndex = null
                        vm.regenerate(index)
                    }
                }
                if (msg != null && bubble !is Bubble.Pat) {
                    com.zhiyin.ui.SheetActionRow(
                        icon = Icons.Rounded.Star,
                        label = "收藏消息",
                    ) {
                        actionMsgIndex = null
                        com.zhiyin.data.LocalArchive.addFavorite(
                            context, vm.sessionId, msg.role, msg.content, msg.time
                        )
                        localToast = "已收藏"
                    }
                }
                if (bubble is Bubble.Image) {
                    com.zhiyin.ui.SheetActionRow(
                        icon = Icons.Rounded.Download,
                        label = "保存图片",
                    ) {
                        actionMsgIndex = null
                        com.zhiyin.data.LocalArchive.saveImageToGallery(context, bubble.path) { saved ->
                            localToast = if (saved != null) "已保存到相册" else "保存失败"
                        }
                    }
                }
                if (bubble is Bubble.FileMsg && bubble.url != null) {
                    com.zhiyin.ui.SheetActionRow(
                        icon = Icons.Rounded.Download,
                        label = "保存文件",
                    ) {
                        actionMsgIndex = null
                        com.zhiyin.data.LocalArchive.saveFile(context, bubble.name, bubble.url) { saved ->
                            localToast = if (saved != null) "已保存到下载" else "保存失败"
                        }
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

    if (showTransfer) {
        AmountInputDialog(
            title = "转账给 $personaName",
            hint = "金额（元）",
            confirmText = "转账",
            onDismiss = { showTransfer = false },
            onConfirm = { amount, note ->
                showTransfer = false
                showPayGuard = PayGuardAction(amount = amount, note = note, isRedpacket = false)
            },
        )
    }
    if (showRedpacket) {
        AmountInputDialog(
            title = "发红包给 $personaName",
            hint = "总金额（元）",
            confirmText = "塞钱进红包",
            onDismiss = { showRedpacket = false },
            onConfirm = { amount, note ->
                showRedpacket = false
                showPayGuard = PayGuardAction(amount = amount, note = note, isRedpacket = true)
            },
        )
    }

    showPayGuard?.let { guard ->
        EnsurePayPasswordFlow(
            onContinue = {
                showPayGuard = null
                if (guard.isRedpacket) vm.sendRedpacket(guard.amount, guard.note)
                else vm.sendTransfer(guard.amount, guard.note)
            },
        )
    }

    moneyDetail?.let { (money, time) ->
        val myName = remember {
            context.getSharedPreferences("zhiyin", 0).getString("user_nickname", null)?.takeIf { it.isNotBlank() }
                ?: com.zhiyin.data.AppSession.username().takeIf { it.isNotBlank() }
                ?: "我"
        }
        MoneyDetailDialog(
            money = money,
            payerName = myName,
            peerName = personaName,
            time = time,
            onDismiss = { moneyDetail = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: ChatMsg,
    bubble: Bubble,
    personaName: String,
    personaId: Int,
    animateIn: Boolean = false,
    onLongPress: () -> Unit,
    onPat: () -> Unit,
    onOpenFriendSettings: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenMoney: (Bubble.Money, Long) -> Unit = { _, _ -> },
    onPreviewImage: (PreviewTarget) -> Unit = {},
) {
    val mine = msg.role == "user"
    val isPat = bubble is Bubble.Pat

    MessageEntrance(mine = mine, animate = animateIn) {
        when {
            isPat -> {
                Text(
                    (bubble as Bubble.Pat).text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            ) {
                if (!mine) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .pointerInput(personaId) {
                                detectTapGestures(
                                    onTap = { onOpenFriendSettings() },
                                    onDoubleTap = { onPat() },
                                )
                            },
                    ) {
                        PersonaAvatar(contactId = personaId, name = personaName, size = 36.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 264.dp),
                ) {
                    BubbleContent(
                        bubble = bubble,
                        mine = mine,
                        personaId = personaId,
                        personaName = personaName,
                        onLongPress = onLongPress,
                        onOpenMoney = { m -> onOpenMoney(m, msg.time) },
                        onPreviewImage = onPreviewImage,
                    )
                }
                if (mine) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = { onOpenMe() }) },
                    ) {
                        UserAvatar(avatarUrl = null, size = 36.dp)
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleContent(
    bubble: Bubble,
    mine: Boolean,
    personaId: Int,
    personaName: String,
    onLongPress: () -> Unit,
    onOpenMoney: (Bubble.Money) -> Unit = {},
    onPreviewImage: (PreviewTarget) -> Unit = {},
) {
    val bubbleColor =
        if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val bubbleTextColor =
        if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    when (bubble) {
        is Bubble.Text -> {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (mine) 18.dp else 6.dp,
                    topEnd = if (mine) 6.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
                color = if (bubble.error) MaterialTheme.colorScheme.errorContainer else bubbleColor,
                modifier = Modifier
                    .animateContentSize()
                    .combinedClickable(onClick = {}, onLongClick = onLongPress),
            ) {
                Text(
                    bubble.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (bubble.error) MaterialTheme.colorScheme.onErrorContainer else bubbleTextColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        is Bubble.Think -> ThinkBubble(bubble, mine, onLongPress)
        is Bubble.Sticker -> StickerBubble(bubble, mine, onLongPress, onPreviewImage)
        is Bubble.Image -> ImageBubble(bubble, onLongPress, onPreviewImage)
        is Bubble.FileMsg -> FileBubble(bubble, onLongPress)
        is Bubble.Voice -> VoiceBubble(bubble, mine, onLongPress)
        is Bubble.Money -> MoneyBubble(
            bubble = bubble,
            mine = mine,
            onClick = { onOpenMoney(bubble) },
            onLongPress = onLongPress,
        )
        is Bubble.Pat -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThinkBubble(bubble: Bubble.Think, mine: Boolean, onLongPress: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sources = remember(bubble.sourcesJson) { parseSources(bubble.sourcesJson) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier
            .animateContentSize()
            .clickable { expanded = !expanded }
            .combinedClickable(onClick = { expanded = !expanded }, onLongClick = onLongPress),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "收起" else buildString {
                        append("思考过程")
                        if (sources.isNotEmpty()) append(" · ${sources.size} 条引用")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                if (bubble.reasoning.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        bubble.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sources.forEach { src ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• ${src.first}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun parseSources(json: String): List<Pair<String, String>> {
    if (json.isEmpty()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Pair(o.optString("title", ""), o.optString("url", ""))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerBubble(
    bubble: Bubble.Sticker,
    mine: Boolean,
    onLongPress: () -> Unit,
    onPreview: (PreviewTarget) -> Unit = {},
) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, bubble.fileName) {
        value = withContext(Dispatchers.IO) {
            if (bubble.custom) StickerManager.getCustomStickerByName(context, bubble.fileName)
            else StickerManager.getStickerByName(context, bubble.fileName)
        }?.asImageBitmap()
    }
    Column {
        bmp?.let {
            Image(
                bitmap = it,
                contentDescription = "表情包",
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { onPreview(PreviewTarget.StickerImage(bubble.fileName, bubble.custom)) },
                        onLongClick = onLongPress,
                    ),
            )
        } ?: Box(
            modifier = Modifier
                .size(110.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
        )
        if (bubble.extra.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (mine) 18.dp else 6.dp,
                    topEnd = if (mine) 6.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
            ) {
                Text(
                    bubble.extra,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBubble(
    bubble: Bubble.Image,
    onLongPress: () -> Unit,
    onPreview: (PreviewTarget) -> Unit = {},
) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, bubble.path) {
        value = withContext(Dispatchers.IO) {
            decodeSampled(bubble.path, 720)
        }?.asImageBitmap()
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.combinedClickable(
            onClick = { onPreview(PreviewTarget.FileImage(bubble.path)) },
            onLongClick = onLongPress,
        ),
    ) {
        bmp?.let {
            Image(
                bitmap = it,
                contentDescription = "图片消息",
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.FillWidth,
            )
        } ?: Box(
            modifier = Modifier
                .size(width = 160.dp, height = 120.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

private fun decodeStickerFull(context: android.content.Context, fileName: String, custom: Boolean): android.graphics.Bitmap? {
    return try {
        if (custom) {
            android.graphics.BitmapFactory.decodeFile(
                StickerManager.getCustomStickerFile(context, fileName).absolutePath
            )
        } else {
            context.assets.open("stickers/$fileName").use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun decodeSampled(path: String, target: Int): android.graphics.Bitmap? {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBubble(bubble: Bubble.FileMsg, onLongPress: () -> Unit) {
    val context = LocalContext.current
    var downloading by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .combinedClickable(onClick = { }, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = bubble.url != null && !downloading) {
                    val url = bubble.url ?: return@clickable
                    downloading = true
                    thread {
                        try {
                            val data = ApiGateway.getRaw(url, context)
                            if (data != null && data.isNotEmpty()) {
                                val dir = File(context.cacheDir, "downloads")
                                if (!dir.exists()) dir.mkdirs()
                                val f = File(dir, bubble.name)
                                f.outputStream().use { it.write(data) }
                                openFileWithProvider(context, f)
                            }
                        } catch (_: Exception) {
                        }
                        downloading = false
                    }
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    bubble.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (downloading) "下载中…" else if (bubble.url != null) "点击打开" else "文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun openFileWithProvider(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceBubble(bubble: Bubble.Voice, mine: Boolean, onLongPress: () -> Unit) {
    val playing by VoicePlayer.playingPath.collectAsState()
    val isPlaying = playing == bubble.path
    Surface(
        shape = RoundedCornerShape(
            topStart = if (mine) 18.dp else 6.dp,
            topEnd = if (mine) 6.dp else 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp,
        ),
        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .widthIn(min = 96.dp)
            .combinedClickable(
                onClick = { VoicePlayer.toggle(bubble.path) },
                onLongClick = onLongPress,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "播放语音",
                tint = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${bubble.seconds}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoneyBubble(
    bubble: Bubble.Money,
    mine: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val color = when (bubble.kind) {
        Bubble.Kind.TRANSFER -> Color(0xFFF5A623)
        Bubble.Kind.REDPACKET -> Color(0xFFE85D4A)
        Bubble.Kind.RECEIPT -> Color(0xFF34B78F)
    }
    val headline = when (bubble.kind) {
        Bubble.Kind.TRANSFER -> "转账"
        Bubble.Kind.REDPACKET -> "红包"
        Bubble.Kind.RECEIPT -> "已收款"
    }
    val icon = when (bubble.kind) {
        Bubble.Kind.TRANSFER -> Icons.Rounded.AccountBalanceWallet
        Bubble.Kind.REDPACKET -> Icons.Rounded.Redeem
        Bubble.Kind.RECEIPT -> Icons.Rounded.AccountBalance
    }
    val subline = bubble.note.ifEmpty {
        if (bubble.amount > 0) "¥" + moneyFmt(bubble.amount) else bubble.amountText
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .widthIn(min = 150.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
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
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    subline,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class RedpacketClaim(val persona: String, val amount: Double, val time: String)

@Composable
private fun MoneyDetailDialog(
    money: Bubble.Money,
    payerName: String,
    peerName: String,
    time: Long,
    onDismiss: () -> Unit,
) {
    val color = when (money.kind) {
        Bubble.Kind.TRANSFER -> Color(0xFFF5A623)
        Bubble.Kind.REDPACKET -> Color(0xFFE85D4A)
        Bubble.Kind.RECEIPT -> Color(0xFF34B78F)
    }
    val label = when (money.kind) {
        Bubble.Kind.TRANSFER -> "灵心转账"
        Bubble.Kind.REDPACKET -> "灵心红包"
        Bubble.Kind.RECEIPT -> "灵心钱包"
    }
    val status = when (money.kind) {
        Bubble.Kind.TRANSFER -> "对方已收款，已存入对方钱包"
        Bubble.Kind.REDPACKET -> "红包已被领取"
        Bubble.Kind.RECEIPT -> "已存入对方钱包"
    }
    var claims by remember(money.packetId) { mutableStateOf<List<RedpacketClaim>?>(null) }
    LaunchedEffect(money.packetId) {
        if (money.kind == Bubble.Kind.REDPACKET && money.packetId > 0) {
            ApiGateway.get(
                "/api/wallet/redpacket/" + money.packetId,
                com.zhiyin.data.AppSession.token(),
                object : ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        claims = try {
                            val arr = JSONObject(response).optJSONArray("claims")
                            (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                                val o = arr!!.getJSONObject(i)
                                RedpacketClaim(
                                    persona = o.optString("persona_name", ""),
                                    amount = o.optDouble("amount", 0.0),
                                    time = ApiGateway.toBeijingTime(o.optString("created_at", ""), "MM-dd HH:mm"),
                                )
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    override fun onError(error: String?) {
                        claims = emptyList()
                    }
                },
            )
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color))
                        )
                        .padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "¥" + moneyFmt(money.amount),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (money.note.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            money.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34B78F),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "付款方：$payerName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "收款方：$peerName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (time > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "时间：" + TimeFmt.fullTime(time),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    claims?.takeIf { it.isNotEmpty() }?.forEach { c ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildString {
                                    append(c.persona.ifEmpty { "对方" })
                                    append(" 领取了 ¥")
                                    append(moneyFmt(c.amount))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (c.time.isNotEmpty()) {
                                Text(
                                    c.time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 10.dp),
                ) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun ChatSettingsSheetContent(
    vm: ChatViewModel,
    hasChatBg: Boolean,
    onSetBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFriendSettings: () -> Unit,
    onOpenSearchSettings: () -> Unit,
    onLocalToast: (String) -> Unit,
) {
    val context = LocalContext.current
    var thinking by remember {
        mutableStateOf(com.zhiyin.data.SettingsRepo.thinkingMode(context))
    }
    var sticker by remember {
        mutableStateOf(com.zhiyin.data.SettingsRepo.stickerEnabled(context))
    }
    var voiceReply by remember {
        mutableStateOf(com.zhiyin.data.SettingsRepo.voiceReply(context))
    }
    var searchEnabled by remember {
        mutableStateOf(com.zhiyin.data.SettingsRepo.searchEnabled(context))
    }
    var hasSearchKey by remember {
        mutableStateOf(com.zhiyin.data.SettingsRepo.hasTavilyKey(context))
    }
    var showKeyPrompt by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    val official = remember {
        PersonaManager.isOfficial(context, vm.personaName)
    }

    LaunchedEffect(Unit) {
        if (!hasSearchKey) {
            val resp = withContext(Dispatchers.IO) {
                try {
                    ApiGateway.requestSync(
                        ApiGateway.ZHIYIN_BASE + "/api/search/config", "GET", null, AppSession.token()
                    )
                } catch (_: Exception) {
                    null
                }
            }
            if (resp != null) {
                try {
                    val json = JSONObject(resp)
                    if (json.optBoolean("has_key", false)) {
                        val masked = json.optString("tavily_key", "")
                        context.getSharedPreferences("zhiyin_search", 0).edit()
                            .putBoolean("has_tavily_key", true)
                            .putString("tavily_key_masked", masked)
                            .apply()
                        hasSearchKey = true
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "会话设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        SheetSwitchRow("思考模式", thinking, enabled = true) {
            thinking = it
            com.zhiyin.data.SettingsRepo.setThinkingMode(context, it)
            if (it) onLocalToast("思考模式已开启（需自备 API Key 才能显示思考过程）")
        }
        SheetSwitchRow("表情包", sticker) {
            sticker = it
            com.zhiyin.data.SettingsRepo.setStickerEnabled(context, it)
        }
        SheetSwitchRow("语音回复", voiceReply) {
            voiceReply = it
            com.zhiyin.data.SettingsRepo.setVoiceReply(context, it)
            onLocalToast(if (it) "语音回复已开启" else "语音回复已关闭")
        }
        SheetSwitchRow("联网搜索", searchEnabled && hasSearchKey) {
            if (!hasSearchKey) {
                showKeyPrompt = true
            } else {
                searchEnabled = it
                com.zhiyin.data.SettingsRepo.setSearchEnabled(context, it)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp,
        )
        com.zhiyin.ui.SheetActionRow(
            icon = Icons.Rounded.Wallpaper,
            label = if (hasChatBg) "更换聊天背景" else "设置聊天背景",
        ) {
            onSetBackground()
        }
        if (hasChatBg) {
            com.zhiyin.ui.SheetActionRow(
                icon = Icons.Rounded.Refresh,
                label = "恢复默认背景",
            ) {
                onClearBackground()
            }
        }
        com.zhiyin.ui.SheetActionRow(
            icon = Icons.Rounded.Settings,
            label = if (official) "好友设置" else "好友设置（人设编辑）",
        ) {
            onDismiss()
            onOpenFriendSettings()
        }
        com.zhiyin.ui.SheetActionRow(
            icon = Icons.Rounded.DeleteOutline,
            label = "清空聊天记录",
            danger = true,
        ) {
            showClear = true
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showKeyPrompt) {
        LingXinDialog(
            onDismiss = { showKeyPrompt = false },
            title = "提示",
            text = "联网搜索需要先配置 Tavily API Key，是否前往设置页配置？",
            confirmText = "去配置",
            dismissText = "取消",
            onConfirm = {
                showKeyPrompt = false
                onDismiss()
                onOpenSearchSettings()
            },
        )
    }

    if (showClear) {
        LingXinDialog(
            onDismiss = { showClear = false },
            title = "清空聊天记录",
            text = "确定要清空与 ${vm.personaName} 的所有聊天记录吗？",
            confirmText = "清空",
            danger = true,
            onConfirm = {
                showClear = false
                vm.clearChat()
                onDismiss()
            },
        )
    }
}

@Composable
private fun SheetSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChecked,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
fun AmountInputDialog(
    title: String,
    hint: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LingXinDialog(
        onDismiss = onDismiss,
        title = title,
        confirmText = confirmText,
        onConfirm = {
            val v = amount.toDoubleOrNull()
            if (v == null || v <= 0) {
                error = "请输入正确的金额"
            } else {
                onConfirm(Math.round(v * 100) / 100.0, note.trim())
            }
        },
    ) {
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
            placeholder = { Text(hint) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("备注（选填）") },
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

private data class PayGuardAction(
    val amount: Double,
    val note: String,
    val isRedpacket: Boolean,
)
