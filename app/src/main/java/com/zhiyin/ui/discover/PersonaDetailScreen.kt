package com.zhiyin.ui.discover

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AppSession
import com.zhiyin.data.PersonaDetail
import com.zhiyin.data.PlazaApi
import com.zhiyin.data.PlazaComment
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.launch

@Composable
private fun CoverImagePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = "无图像",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaDetailScreen(
    appVm: AppViewModel,
    personaId: Int,
    onBack: () -> Unit,
    onOpenAuthor: (Int) -> Unit,
    onEdit: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<PersonaDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var liked by remember { mutableStateOf(false) }
    var faved by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }
    var likesCount by remember { mutableIntStateOf(0) }
    var favsCount by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<PlazaComment>>(emptyList()) }
    var commentsTotal by remember { mutableIntStateOf(0) }
    var commentPage by remember { mutableIntStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

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

    fun loadComments(reset: Boolean) {
        val page = if (reset) 1 else commentPage + 1
        scope.launch {
            PlazaApi.comments(personaId, page).onSuccess { (total, list) ->
                commentsTotal = total
                commentPage = page
                comments = if (reset) list else (comments + list).distinctBy { it.id }
            }
        }
    }

    LaunchedEffect(personaId) {
        reload()
        loadComments(true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    detail?.light?.backgroundUrl?.takeIf { it.isNotEmpty() }?.let { bg ->
        RemoteImage(
            url = bg,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)))
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = { Text("人设详情", fontWeight = FontWeight.SemiBold) },
            actions = {
                if (detail != null && detail!!.light.authorId == myId) {
                    TextButton(onClick = { onEdit(personaId) }) { Text("编辑", color = MaterialTheme.colorScheme.primary) }
                }
            },
        )

        if (loading || detail == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val d = detail!!

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (d.light.avatarUrl.isNotEmpty()) {
                                RemoteImage(
                                    url = d.light.avatarUrl,
                                    contentDescription = d.light.name,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholder = {
                                        com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(72.dp), size = 72.dp, shape = CircleShape)
                                    },
                                )
                            } else {
                                com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(72.dp), size = 72.dp, shape = CircleShape)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.light.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (d.light.isOfficial) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp),
                                    ) {
                                        Text("官方", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            if (d.light.slogan.isNotEmpty()) {
                                Text(
                                    d.light.slogan,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                "${d.light.category} · 🔥 ${d.light.hot} 人在用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    d.author?.let { au ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .clickable { onOpenAuthor(au.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (au.avatar.isNotEmpty()) {
                                    RemoteImage(
                                        url = au.avatar,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholder = { com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(30.dp), size = 30.dp, shape = CircleShape) },
                                    )
                                } else {
                                    com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(30.dp), size = 30.dp, shape = CircleShape)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(au.nickname, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                " · ${au.followers} 粉丝",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (following) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
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
                                    color = if (following) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).clickable(enabled = !adding) {
                                adding = true
                                scope.launch {
                                    PlazaApi.addToContacts(personaId).onSuccess {
                                        appVm.showToast("已添加到会话，去「会话」页聊天吧")
                                        appVm.loadFriends()
                                    }.onFailure { appVm.showToast(it.message ?: "添加失败") }
                                    adding = false
                                }
                            },
                        ) {
                            Text(
                                if (adding) "添加中…" else "添加会话",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable {
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
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "点赞",
                                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("$likesCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable {
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
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    if (faved) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "收藏",
                                    tint = if (faved) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("$favsCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    if (d.light.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            d.light.tags.take(6).forEach { t ->
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Text("# $t", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (d.light.descriptionLight.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Text(d.light.descriptionLight, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("评论 ($commentsTotal)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            items(comments, key = { it.id }) { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c.avatar.isNotEmpty()) {
                            RemoteImage(
                                url = c.avatar,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(32.dp), size = 32.dp, shape = CircleShape) },
                            )
                        } else {
                            com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(32.dp), size = 32.dp, shape = CircleShape)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.nickname, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                com.zhiyin.logic.net.ApiGateway.toBeijingTime(c.createdAt, "MM-dd HH:mm"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(c.content, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (c.mine) {
                        Text(
                            "删除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    PlazaApi.deleteComment(personaId, c.id).onSuccess {
                                        appVm.showToast("已删除")
                                        loadComments(true)
                                    }.onFailure { appVm.showToast(it.message ?: "删除失败") }
                                }
                            }.padding(4.dp),
                        )
                    }
                }
            }
            if (comments.size < commentsTotal) {
                item {
                    Text(
                        "加载更多评论",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { loadComments(false) }
                            .padding(16.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(76.dp)) }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).imePadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = { Text("说点什么…", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = commentText.isNotBlank() && !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        PlazaApi.comment(personaId, commentText.trim()).onSuccess {
                            commentText = ""
                            appVm.showToast("评论成功")
                            loadComments(true)
                            reload()
                        }.onFailure { appVm.showToast(it.message ?: "评论失败") }
                        sending = false
                    }
                },
            ) { Text("发送", fontWeight = FontWeight.SemiBold) }
        }
    }
    }
}
