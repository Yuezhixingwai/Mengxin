package com.zhiyin.ui.me

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiyin.data.LocalArchive
import com.zhiyin.logic.chat.ChatEngine
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.ui.EmptyHint
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.chat.parseBubble
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.vm.AppViewModel
import com.zhiyin.ui.vm.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(LocalArchive.listFavorites(context)) }
    var deleteFor by remember { mutableStateOf<Long?>(null) }
    var detailFor by remember { mutableStateOf<Long?>(null) }

    fun reload() {
        items = LocalArchive.listFavorites(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("我的收藏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                if (items.isNotEmpty()) {
                    TextButtonCompat("清空") {
                        LocalArchive.clearFavorites(context)
                        appVm.showToast("已清空收藏")
                        reload()
                    }
                }
            },
        )
        if (items.isEmpty()) {
            EmptyHint("在聊天中长按消息即可收藏")
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                lazyItems(items, key = { it.id }) { fav ->
                    val friendName = if (fav.sessionId.startsWith("persona_"))
                        fav.sessionId.removePrefix("persona_") else fav.sessionId.removePrefix("group_")
                    val friendId = remember(friendName) { FriendManager.findIdByName(friendName) }
                    val preview = remember(fav.content) { ChatEngine.previewText(fav.content) }
                    ListItem(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { detailFor = fav.id },
                                onLongClick = { deleteFor = fav.id },
                            ),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        leadingContent = {
                            if (fav.sessionId.startsWith("group_")) {
                                com.zhiyin.ui.components.GroupAvatar(44.dp)
                            } else {
                                PersonaAvatar(friendId, friendName, 44.dp)
                            }
                        },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    friendName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    TimeFmt.fullTime(fav.time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        supportingContent = {
                            Text(
                                preview.ifEmpty { "[空消息]" },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            }
        }
    }

    detailFor?.let { id ->
        val fav = items.find { it.id == id }
        if (fav != null) {
            val bubble = remember(fav.content) { parseBubble(fav.content) }
            val display = when (bubble) {
                is com.zhiyin.ui.chat.Bubble.Text -> bubble.text
                is com.zhiyin.ui.chat.Bubble.Think -> "[思考] ${bubble.reasoning}"
                is com.zhiyin.ui.chat.Bubble.Sticker -> "[表情包] ${bubble.fileName}"
                is com.zhiyin.ui.chat.Bubble.Image -> "[图片]"
                is com.zhiyin.ui.chat.Bubble.FileMsg -> "[文件] ${bubble.name}"
                is com.zhiyin.ui.chat.Bubble.Voice -> "[语音] ${bubble.seconds}\""
                is com.zhiyin.ui.chat.Bubble.Pat -> bubble.text
                is com.zhiyin.ui.chat.Bubble.Money -> bubble.amountText
            }
            LingXinSheet(onDismiss = { detailFor = null }) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "收藏的消息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            display,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(16.dp)
                                .height(240.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("favorite", display)
                                )
                                appVm.showToast("已复制")
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f),
                        ) { Text("复制全文") }
                        androidx.compose.material3.Button(
                            onClick = {
                                detailFor = null
                                onOpenChat(fav.sessionId)
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f),
                        ) { Text("查看会话") }
                    }
                }
            }
        }
    }

    deleteFor?.let { id ->
        LingXinDialog(
            onDismiss = { deleteFor = null },
            title = "取消收藏",
            text = "确定移除这条收藏吗？",
            confirmText = "移除",
            danger = true,
            onConfirm = {
                deleteFor = null
                LocalArchive.removeFavorite(context, id)
                reload()
            },
        )
    }
}

@Composable
private fun TextButtonCompat(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SavedImagesScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(LocalArchive.listDownloads(context).filter { it.kind == "image" }) }
    var previewLocation by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("我的相册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        if (items.isEmpty()) {
            EmptyHint("在聊天中长按图片选择「保存图片」即可收藏到这里")
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    val bmp by produceState<ImageBitmap?>(initialValue = null, item.location) {
                        value = withContext(Dispatchers.IO) {
                            LocalArchive.loadSavedImage(context, item.location)?.asImageBitmap()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .combinedClickable(
                                onClick = { previewLocation = item.location },
                                onLongClick = { deleteFor = item.id },
                            ),
                    ) {
                        bmp?.let {
                            Image(
                                bitmap = it,
                                contentDescription = item.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            }
        }
    }

    previewLocation?.let { loc ->
        LingXinSheet(onDismiss = { previewLocation = null }) {
            Column(modifier = Modifier.padding(12.dp)) {
                val bmp by produceState<ImageBitmap?>(initialValue = null, loc) {
                    value = withContext(Dispatchers.IO) {
                        LocalArchive.loadSavedImage(context, loc)?.asImageBitmap()
                    }
                }
                bmp?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    deleteFor?.let { id ->
        val record = items.find { it.id == id }
        LingXinDialog(
            onDismiss = { deleteFor = null },
            title = "移除记录",
            text = "仅从历史列表移除该记录（不删除系统相册中的图片）？",
            confirmText = "移除",
            danger = true,
            onConfirm = {
                deleteFor = null
                if (record != null) LocalArchive.removeDownload(context, record.id)
                items = LocalArchive.listDownloads(context).filter { it.kind == "image" }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SavedFilesScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(LocalArchive.listDownloads(context).filter { it.kind == "file" }) }
    var deleteFor by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("我的文件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        if (items.isEmpty()) {
            EmptyHint("在聊天中长按文件选择「保存文件」即可收藏到这里")
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                lazyItems(items, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { openSaved(context, item.location, item.name) },
                            onLongClick = { deleteFor = item.id },
                        ),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        },
                        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(TimeFmt.fullTime(item.time), style = MaterialTheme.typography.labelSmall) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "打开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
            }
        }
    }

    deleteFor?.let { id ->
        val record = items.find { it.id == id }
        LingXinDialog(
            onDismiss = { deleteFor = null },
            title = "移除记录",
            text = "仅从历史列表移除该记录（不删除已保存的文件）？",
            confirmText = "移除",
            danger = true,
            onConfirm = {
                deleteFor = null
                if (record != null) LocalArchive.removeDownload(context, record.id)
                items = LocalArchive.listDownloads(context).filter { it.kind == "file" }
            },
        )
    }
}

private fun openSaved(context: android.content.Context, location: String, name: String) {
    try {
        val uri: android.net.Uri = if (location.startsWith("content:")) {
            android.net.Uri.parse(location)
        } else {
            androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", java.io.File(location)
            )
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
