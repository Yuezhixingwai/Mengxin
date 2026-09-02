package com.zhiyin.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import com.zhiyin.logic.chat.ChatEngine
import com.zhiyin.logic.util.StickerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
    personaName: String,
    personaId: Int,
    onSend: (String) -> Unit,
    onSendSticker: (String) -> Unit,
    onSendImage: (String) -> Unit,
    onSendFile: (String, String) -> Unit,
    onSendVoice: (String, Long) -> Unit,
    onTransfer: () -> Unit,
    onRedpacket: () -> Unit,
    onAddCustomSticker: () -> Unit,
    onLocalToast: (String) -> Unit,
    transparentBackground: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var panel by rememberSaveable { mutableStateOf("none") }
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var longEditorOpen by rememberSaveable { mutableStateOf(false) }
    val isLongInput = input.length >= 80 || input.count { it == '\n' } >= 3

    val imagePick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = ContentCopy.copyToCache(context, it, "img")
            if (copied != null) onSendImage(copied.path) else onLocalToast("处理图片失败")
        }
    }

    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (ok && path != null) onSendImage(path)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val dir = java.io.File(context.cacheDir, "camera")
            if (!dir.exists()) dir.mkdirs()
            val f = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
            pendingCameraPath = f.absolutePath
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", f
            )
            try {
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                onLocalToast("无法打开相机: ${e.message}")
            }
        } else {
            onLocalToast("需要相机权限才能拍照")
        }
    }

    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = ContentCopy.copyToCache(context, it, "file")
            if (copied != null) onSendFile(copied.displayName, copied.path) else onLocalToast("处理文件失败")
        }
    }

    val stickerPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = ContentCopy.copyToCache(context, it, "sticker")
            val bmp = copied?.let { c -> android.graphics.BitmapFactory.decodeFile(c.path) }
            if (bmp == null) {
                onLocalToast("添加失败：无法读取图片")
            } else {
                val name = StickerManager.addCustomSticker(context, bmp)
                if (name == null) {
                    onLocalToast("添加失败")
                } else {
                    ChatEngine.backupCustomSticker(context, name)
                    onAddCustomSticker()
                }
            }
        }
    }

    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceMode = true else onLocalToast("需要录音权限")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.surface
            ),
    ) {
        AnimatedVisibility(
            visible = panel == "tools",
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(160)),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ToolButton(Icons.Outlined.Image, "图片") {
                    panel = "none"
                    imagePick.launch("image/*")
                }
                ToolButton(Icons.Rounded.PhotoCamera, "拍摄") {
                    panel = "none"
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val dir = java.io.File(context.cacheDir, "camera")
                        if (!dir.exists()) dir.mkdirs()
                        val f = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
                        pendingCameraPath = f.absolutePath
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", f
                        )
                        try {
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            onLocalToast("无法打开相机: ${e.message}")
                        }
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
                ToolButton(Icons.Outlined.AttachFile, "文件") {
                    panel = "none"
                    filePick.launch("*/*")
                }
                ToolButton(Icons.Rounded.Mic, "语音") {
                    panel = "none"
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        voiceMode = true
                    } else {
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                ToolButton(Icons.Rounded.SwapHoriz, "转账") {
                    panel = "none"
                    onTransfer()
                }
            }
        }

        AnimatedVisibility(
            visible = panel == "sticker",
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(160)),
        ) {
            StickerPanel(
                onPick = { marker ->
                    panel = "none"
                    onSendSticker(marker)
                },
                onAdd = {
                    panel = "none"
                    stickerPick.launch("image/*")
                },
            )
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (voiceMode) {
                HoldToTalk(
                    onCancel = { voiceMode = false; VoiceRecorder.cancel() },
                    onDone = { path, sec ->
                        voiceMode = false
                        onSendVoice(path, sec)
                    },
                    onTooShort = { onLocalToast("录音时间太短") },
                    modifier = Modifier.weight(1f),
                )
            } else {
                IconButton(
                    onClick = { panel = if (panel == "tools") "none" else "tools" },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "更多功能",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 39.dp, max = 140.dp),
                    placeholder = { Text("写点什么…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    maxLines = 4,
                )
                IconButton(
                    onClick = { panel = if (panel == "sticker") "none" else "sticker" },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Outlined.EmojiEmotions,
                        contentDescription = "表情包",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSend(input.trim())
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
                onSend(text)
            },
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StickerPanel(onPick: (String) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val builtIns = remember { StickerManager.getAllStickers() }
    val customs = remember { StickerManager.listCustomStickers(context) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "添加表情包", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(customs) { _, name ->
            val bmp = produceState<ImageBitmap?>(initialValue = null, name) {
                value = withContext(Dispatchers.IO) {
                    decodeSampledFile(StickerManager.getCustomStickerFile(context, name), 216)
                }?.asImageBitmap()
            }
            bmp.value?.let {
                StickerCell(it) { onPick("[CUSTOM_STICKER:$name]") }
            }
        }
        itemsIndexed(builtIns) { _, item ->
            val bmp = produceState<ImageBitmap?>(initialValue = null, item.fileName) {
                value = withContext(Dispatchers.IO) {
                    decodeSampledAsset(context, item.fileName, 216)
                }?.asImageBitmap()
            }
            bmp.value?.let {
                StickerCell(it) { onPick("[STICKER:${item.fileName}]") }
            }
        }
    }
}

@Composable
private fun StickerCell(bmp: ImageBitmap, onClick: () -> Unit) {
    Image(
        bitmap = bmp,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    )
}

private fun decodeSampledFile(f: java.io.File, target: Int): android.graphics.Bitmap? {
    return try {
        if (!f.exists()) return null
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
        var s = 1
        while (o.outWidth / s > target || o.outHeight / s > target) s *= 2
        android.graphics.BitmapFactory.decodeFile(
            f.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
        )
    } catch (_: Exception) {
        null
    }
}

private fun decodeSampledAsset(context: android.content.Context, fileName: String, target: Int): android.graphics.Bitmap? {
    return try {
        val ins = context.assets.open("stickers/$fileName")
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeStream(ins, null, o)
        ins.close()
        var s = 1
        while (o.outWidth / s > target || o.outHeight / s > target) s *= 2
        val ins2 = context.assets.open("stickers/$fileName")
        val bmp = android.graphics.BitmapFactory.decodeStream(
            ins2, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
        )
        ins2.close()
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun HoldToTalk(
    onCancel: () -> Unit,
    onDone: (String, Long) -> Unit,
    onTooShort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(recording) {
        elapsed = 0L
        while (recording) {
            kotlinx.coroutines.delay(500)
            elapsed += 500
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(39.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (recording) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val path = VoiceRecorder.start(context)
                            if (path == null) {
                                onTooShort()
                            } else {
                                recording = true
                                val released = tryAwaitRelease()
                                recording = false
                                val result = VoiceRecorder.stop()
                                if (!released) {
                                    VoiceRecorder.cancel()
                                } else if (result != null) {
                                    onDone(result.first, result.second)
                                } else {
                                    onTooShort()
                                }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recording) {
                    val transition = rememberInfiniteTransition(label = "rec")
                    val alpha by transition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                        label = "dot",
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = alpha),
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${elapsed / 1000.0}s", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (recording) "松开 发送" else "按住 说话",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { onCancel() },
            modifier = Modifier.size(44.dp),
        ) {
            Text("取消", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "${text.length}字",
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
                    placeholder = { Text("输入文本…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
