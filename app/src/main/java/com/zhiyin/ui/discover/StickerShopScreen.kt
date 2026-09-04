package com.zhiyin.ui.discover

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiyin.data.StickerPack
import com.zhiyin.data.StickerPackDetail
import com.zhiyin.data.StickerShopApi
import com.zhiyin.logic.util.StickerManager
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun itemsJson(d: StickerPackDetail): String {
    val arr = JSONArray()
    d.items.forEach { (name, desc) ->
        arr.put(JSONObject().put("fileName", name).put("description", desc))
    }
    return arr.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerShopScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onAcquired: () -> Unit,
) {
    val ctx = LocalContext.current
    var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<StickerPackDetail?>(null) }
    var uninstallTarget by remember { mutableStateOf<StickerPack?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            StickerShopApi.packs().onSuccess { packs = it; loading = false }
                .onFailure { appVm.showToast(it.message ?: "加载失败"); loading = false }
        }
    }

    fun acquirePack(packId: Int, existing: StickerPackDetail? = null) {
        scope.launch {
            val d = existing ?: StickerShopApi.detail(packId).getOrNull()
            if (d == null) { appVm.showToast("加载包详情失败"); return@launch }
            StickerShopApi.acquire(packId).onSuccess {
                StickerManager.mergePackMeta(ctx, packId, itemsJson(d))
                StickerManager.cachePackImages(ctx, packId, d.items.map { it.first })
                appVm.showToast("已加入我的表情包")
                onAcquired()
                reload()
            }.onFailure { appVm.showToast(it.message ?: "领取失败") }
        }
    }

    fun uninstallPack(p: StickerPack) {
        scope.launch {
            val d = StickerShopApi.detail(p.id).getOrNull()
            val names = d?.items?.map { it.first } ?: emptyList()
            StickerShopApi.unacquire(p.id).onSuccess {
                StickerManager.removePack(ctx, p.id, names)
                appVm.showToast("已卸载")
                reload()
            }.onFailure { appVm.showToast(it.message ?: "卸载失败") }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = { Text("表情包商城", fontWeight = FontWeight.SemiBold) },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Text("精选表情包，免费领取后即可在聊天中使用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
            }
            items(packs, key = { it.id }) { p ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable {
                        scope.launch {
                            StickerShopApi.detail(p.id).onSuccess { detail = it }
                                .onFailure { appVm.showToast(it.message ?: "加载失败") }
                        }
                    },
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (p.cover.isNotEmpty()) {
                                RemoteImage(
                                    url = p.cover,
                                    contentDescription = p.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    placeholder = {
                                        Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                                    },
                                )
                            } else {
                                Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p.isDefault) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp)) {
                                        Text("默认", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Text(
                                if (p.description.isNotEmpty()) p.description else "${p.itemCount} 张表情",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        when {
                            p.owned && !p.isDefault -> Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { uninstallTarget = p },
                            ) {
                                Text(
                                    "卸载",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                            p.owned -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    "已拥有",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                            else -> Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { acquirePack(p.id) },
                            ) {
                                Text(
                                    "免费领取",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (packs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("暂无表情包", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    detail?.let { d ->
        PackDetailDialog(d, onDismiss = { detail = null }, onAcquire = {
            acquirePack(d.pack.id, d)
            detail = null
        })
    }

    uninstallTarget?.let { p ->
        LingXinDialog(
            onDismiss = { uninstallTarget = null },
            title = "卸载表情包",
            text = "确定卸载「${p.name}」吗？卸载后将从你的表情面板移除（可重新免费领取）。",
            confirmText = "卸载",
            danger = true,
            onConfirm = {
                uninstallTarget = null
                uninstallPack(p)
            },
        )
    }
}

@Composable
private fun PackDetailDialog(
    d: StickerPackDetail,
    onDismiss: () -> Unit,
    onAcquire: () -> Unit,
) {
    val ctx = LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.92f)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(
                    d.pack.description.ifEmpty { "${d.items.size} 张表情 · 免费" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(d.items, key = { it.first }) { (name, _) ->
                        val bmp by produceState<ImageBitmap?>(initialValue = null, name) {
                            value = withContext(Dispatchers.IO) {
                                StickerManager.loadStickerBitmap(ctx, d.pack.id, name)?.asImageBitmap()
                            }
                        }
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp!!,
                                    contentDescription = name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (d.pack.owned) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !d.pack.owned, onClick = onAcquire),
                ) {
                    Text(
                        if (d.pack.owned) "已拥有" else "免费领取",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (d.pack.owned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
