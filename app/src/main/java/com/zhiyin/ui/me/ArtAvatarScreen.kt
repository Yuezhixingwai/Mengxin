package com.zhiyin.ui.me

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AvatarStore
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.vm.AppViewModel
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class AvatarOption(
    val seed: String,
    val style: String,
    val bg: String,
) {
    fun url(): String =
        "https://api.dicebear.com/9.x/$style/png?seed=$seed&size=512&backgroundType=solid&backgroundColor=$bg"
}

private val AVATAR_STYLES = listOf(
    "lorelei", "notionists", "micah", "avataaars", "big-ears",
    "adventurer", "big-smile", "bottts", "croodles", "fun-emoji",
    "miniavs", "open-peeps", "personas", "pixel-art", "thumbs",
)

private val PASTEL_BG = listOf(
    "b6e3f4", "c0aede", "d1d4f9", "ffd5dc", "ffdfbf", "eaddff", "c8f4de", "fce8b3",
)

private fun randomOption(): AvatarOption = AvatarOption(
    seed = UUID.randomUUID().toString().replace("-", "").substring(0, 10),
    style = AVATAR_STYLES.random(),
    bg = PASTEL_BG.random(),
)

@Composable
fun ArtAvatarScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    contactId: Int = -1,
    contactName: String? = null,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    var options by remember { mutableStateOf(List(30) { randomOption() }) }
    var pickFor by remember { mutableStateOf<AvatarOption?>(null) }
    var uploading by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "艺术插画头像",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { options = List(30) { randomOption() } }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "换一批",
                            tint = colors.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options, key = { it.seed + it.style }) { opt ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { pickFor = opt },
                ) {
                    ArtFadeImage(
                        url = opt.url(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    pickFor?.let { opt ->
        LingXinDialog(
            onDismiss = { if (!uploading) pickFor = null },
            title = "设为头像",
            text = if (contactId >= 0) "将该插画设置为「${contactName ?: "TA"}」的头像？"
            else "将该插画设置为你的头像？",
            confirmText = if (uploading) "上传中…" else "设为头像",
            dismissible = !uploading,
            onConfirm = {
                if (uploading) return@LingXinDialog
                uploading = true
                thread {
                    try {
                        var data = try {
                            ApiGateway.getRaw(opt.url(), context)
                        } catch (_: Exception) {
                            null
                        }
                        if (data == null || data.isEmpty()) {
                            Thread.sleep(1200)
                            data = ApiGateway.getRaw(opt.url(), context)
                        }
                        if (data == null || data.isEmpty()) throw Exception("图片下载失败")
                        Handler(Looper.getMainLooper()).post {
                            val onDone: (Boolean, String?) -> Unit = { ok, err ->
                                uploading = false
                                pickFor = null
                                if (ok) {
                                    appVm.showToast("头像已更新")
                                    if (contactId >= 0) appVm.loadFriends() else appVm.refreshUser()
                                    onBack()
                                } else {
                                    appVm.showToast("上传失败: ${err ?: ""}")
                                }
                            }
                            if (contactId >= 0) {
                                AvatarStore.uploadPersonaAvatar(context, contactId, data, onDone)
                            } else {
                                AvatarStore.uploadUserAvatar(context, data, onDone)
                            }
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            uploading = false
                            appVm.showToast("下载失败: ${e.message ?: ""}")
                        }
                    }
                }
            },
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                ArtFadeImage(
                    url = opt.url(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ArtFadeImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, url) {
        value = tryLoad(url, context)
        if (value == null) {
            delay(1200)
            value = tryLoad(url, context)
        }
    }
    var visible by remember(url) { mutableStateOf(false) }
    LaunchedEffect(bmp) { if (bmp != null) visible = true }
    Box(
        modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val img = bmp
        if (img != null) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.94f),
            ) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private suspend fun tryLoad(url: String, context: Context): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            val data = ApiGateway.getRaw(url, context)
            if (data == null || data.isEmpty()) null
            else BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
