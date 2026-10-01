package com.zhiyin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.data.AppSession
import com.zhiyin.data.PersonaDetail
import com.zhiyin.data.PlazaApi
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.DefaultAvatar
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.components.acrylic
import com.zhiyin.ui.components.acrylicSource
import com.zhiyin.ui.vm.AppViewModel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun DetailStat(modifier: Modifier = Modifier, label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
    }
}

@Composable
private fun CoverImagePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = "无图像",
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PersonaDetailScreen(
    appVm: AppViewModel,
    personaId: Int,
    onBack: () -> Unit,
    onOpenAuthor: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onOpenChat: (String, String, Int) -> Unit = { _, _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var detail by remember { mutableStateOf<PersonaDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var liked by remember { mutableStateOf(false) }
    var faved by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }
    var likesCount by remember { mutableIntStateOf(0) }
    var favsCount by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }

    val myId = AppSession.userId().toIntOrNull() ?: -1

    fun reload() {
        scope.launch {
            PlazaApi.detail(personaId).onSuccess { d ->
                detail = d
                liked = d.light.liked
                faved = d.light.faved
                following = d.author?.following == true
                likesCount = d.light.likesCount
                favsCount = d.light.favsCount
                loading = false
            }.onFailure { e ->
                appVm.showToast(e.message ?: "加载失败")
                loading = false
            }
        }
    }

    LaunchedEffect(personaId) {
        reload()
    }

    val bgHazeState = remember { HazeState() }
    Box(modifier = Modifier.fillMaxSize()) {
    detail?.light?.backgroundUrl?.takeIf { it.isNotEmpty() }?.let { bg ->
        Box(modifier = Modifier.fillMaxSize().acrylicSource(bgHazeState)) {
            RemoteImage(
                url = bg,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .acrylic(
                    state = bgHazeState,
                    tint = colors.surface.copy(alpha = 0.72f),
                ),
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            color = Color.Transparent,
            title = "人设详情",
            titleColor = colors.onBackground,
            navigationIcon = { BackButton(onClick = onBack) },
            actions = {
                if (detail != null && detail!!.light.authorId == myId) {
                    TextButton(
                        text = "编辑",
                        onClick = { onEdit(personaId) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            },
        )

        if (loading || detail == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val d = detail!!

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(215.dp)
                                .background(colors.surfaceContainerHigh),
                        ) {
                            if (d.light.coverUrl.isNotEmpty()) {
                                RemoteImage(
                                    url = d.light.coverUrl,
                                    contentDescription = d.light.name,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholder = { CoverImagePlaceholder() },
                                )
                            } else {
                                CoverImagePlaceholder()
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 14.dp, y = 44.dp)
                            .size(88.dp)
                            .clip(CircleShape)
                            .border(3.dp, colors.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d.light.avatarUrl.isNotEmpty()) {
                            RemoteImage(
                                url = d.light.avatarUrl,
                                contentDescription = d.light.name,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { DefaultAvatar(modifier = Modifier.size(88.dp), size = 88.dp, shape = CircleShape) },
                            )
                        } else {
                            DefaultAvatar(modifier = Modifier.size(88.dp), size = 88.dp, shape = CircleShape)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(58.dp)) }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.light.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (d.light.isOfficial) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.primary,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text("官方", color = colors.onPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    if (d.light.slogan.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            d.light.slogan,
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${d.light.category} · 🔥 ${d.light.hot} 人在用",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariantActions,
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        DetailStat(Modifier.weight(1f), "热度", "🔥 ${d.light.hot}")
                        DetailStat(Modifier.weight(1f), "点赞", "$likesCount")
                        DetailStat(Modifier.weight(1f), "收藏", "$favsCount")
                    }
                }
            }

            item { Spacer(Modifier.height(10.dp)) }

            d.author?.let { au ->
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onOpenAuthor(au.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                if (au.avatar.isNotEmpty()) {
                                    RemoteImage(
                                        url = au.avatar,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholder = { DefaultAvatar(modifier = Modifier.size(40.dp), size = 40.dp, shape = CircleShape) },
                                    )
                                } else {
                                    DefaultAvatar(modifier = Modifier.size(40.dp), size = 40.dp, shape = CircleShape)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(au.nickname, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Text(
                                    "${au.followers} 粉丝 · 点击查看主页",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (following) colors.surfaceVariant else colors.primary,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        PlazaApi.follow(personaId).onSuccess { f ->
                                            following = f
                                            appVm.showToast(if (f) "已关注，TA 发布新人设会通知你" else "已取消关注")
                                        }.onFailure { appVm.showToast(it.message ?: "操作失败") }
                                    }
                                },
                            ) {
                                Text(
                                    if (following) "已关注" else "+ 关注",
                                    color = if (following) colors.onSurfaceContainerVariant else colors.onPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.primary,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !adding) {
                            adding = true
                            scope.launch {
                                PlazaApi.addToContacts(personaId).onSuccess { contact ->
                                    appVm.loadFriends()
                                    appVm.showToast("已添加到通讯录")
                                    if (contact.id > 0) {
                                        onOpenChat(contact.name, contact.persona, contact.id)
                                    }
                                }.onFailure { appVm.showToast(it.message ?: "添加失败") }
                                adding = false
                            }
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = colors.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (adding) "添加中…" else "添加到会话",
                                color = colors.onPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (liked) colors.primaryContainer else colors.surfaceContainer,
                            modifier = Modifier.weight(1f).clickable {
                                scope.launch {
                                    PlazaApi.like(personaId).onSuccess { (lk, cnt) ->
                                        liked = lk
                                        likesCount = cnt
                                    }.onFailure { appVm.showToast(it.message ?: "操作失败") }
                                }
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            ) {
                                Icon(
                                    if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "点赞",
                                    tint = if (liked) colors.primary else colors.onSurfaceContainerVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (liked) "已赞 $likesCount" else "点赞 $likesCount",
                                    fontSize = 12.sp,
                                    color = if (liked) colors.onPrimaryContainer else colors.onSurface,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (faved) colors.primaryContainer else colors.surfaceContainer,
                            modifier = Modifier.weight(1f).clickable {
                                scope.launch {
                                    PlazaApi.favorite(personaId).onSuccess { (fv, cnt) ->
                                        faved = fv
                                        favsCount = cnt
                                        appVm.showToast(if (fv) "已收藏" else "已取消收藏")
                                    }.onFailure { appVm.showToast(it.message ?: "操作失败") }
                                }
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            ) {
                                Icon(
                                    if (faved) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "收藏",
                                    tint = if (faved) Color(0xFFFFB300) else colors.onSurfaceContainerVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (faved) "已藏 $favsCount" else "收藏 $favsCount",
                                    fontSize = 12.sp,
                                    color = if (faved) colors.onPrimaryContainer else colors.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            if (d.light.tags.isNotEmpty()) {
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        d.light.tags.forEach { t ->
                            Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHigh) {
                                Text("# $t", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            if (d.light.descriptionLight.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("简介", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(d.light.descriptionLight, fontSize = 14.sp, color = colors.onSurface, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

}
