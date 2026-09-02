package com.zhiyin.ui.moments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AppSession
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.ImageCropperDialog
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinMenuOverlay
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.components.MenuItemSpec
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object MomentsImageCache {
    val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun absolute(url: String): String =
        if (url.startsWith("http")) url else ApiGateway.getBaseUrl() + url
}

@Composable
fun WebImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val abs = remember(url) { MomentsImageCache.absolute(url) }
    val bmp by produceState<ImageBitmap?>(initialValue = MomentsImageCache.cache[abs], abs) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    val data = ApiGateway.getRaw(abs, ctx)
                    if (data != null && data.isNotEmpty()) {
                        BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()?.also {
                            MomentsImageCache.cache[abs] = it
                        }
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (placeholder != null) {
                placeholder()
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
    }
}

data class MomentComment(
    val isAi: Boolean,
    val name: String,
    val content: String,
)

data class MomentPost(
    val id: Int,
    val nickname: String,
    val username: String,
    val avatar: String,
    val content: String,
    val time: String,
    val images: List<String>,
    val likesCount: Int,
    val liked: Boolean,
    val comments: List<MomentComment>,
)

private fun parsePosts(resp: String): List<MomentPost> {
    return try {
        val arr = JSONObject(resp).optJSONArray("posts") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val commentsJson = o.optJSONArray("comments")
            val comments = commentsJson?.let { ca ->
                (0 until ca.length()).map { j ->
                    val c = ca.getJSONObject(j)
                    val isAi = c.optInt("is_ai") == 1
                    MomentComment(
                        isAi = isAi,
                        name = if (isAi) c.optString("ai_name", "AI")
                        else c.optString("nickname", c.optString("username", "用户")),
                        content = c.optString("content", ""),
                    )
                }
            } ?: emptyList()
            MomentPost(
                id = o.optInt("id"),
                nickname = o.optString("nickname", o.optString("username", "用户")),
                username = o.optString("username", ""),
                avatar = o.optString("avatar", ""),
                content = o.optString("content", ""),
                time = ApiGateway.toBeijingTime(o.optString("created_at", ""), "MM-dd HH:mm"),
                images = o.optJSONArray("images")?.let { ia ->
                    (0 until ia.length()).mapNotNull { k -> ia.optString(k, "").takeIf { it.isNotEmpty() } }
                } ?: emptyList(),
                likesCount = o.optInt("likes_count", 0),
                liked = o.optBoolean("liked", false),
                comments = comments,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onCompose: () -> Unit,
) {
    val context = LocalContext.current
    var posts by remember { mutableStateOf<List<MomentPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var commentingPost by remember { mutableStateOf<MomentPost?>(null) }
    var commentReplyTo by remember { mutableStateOf<String?>(null) }
    var deletePost by remember { mutableStateOf<MomentPost?>(null) }
    var showClear by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        ApiGateway.get("/api/moments?limit=50", AppSession.token(), object : ApiGateway.Callback {
            override fun onSuccess(response: String) {
                posts = parsePosts(response)
                loading = false
            }

            override fun onError(error: String?) {
                loading = false
                appVm.showToast("加载失败: ${error ?: ""}")
            }
        })
    }
    LaunchedEffect(Unit) { load() }

    var coverPath by remember {
        mutableStateOf(
            context.getSharedPreferences("moments_bg", 0).getString("cover_path", "") ?: ""
        )
    }
    var coverCropSource by remember { mutableStateOf<String?>(null) }
    val coverPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = com.zhiyin.ui.chat.ContentCopy.copyToCache(context, it, "cover")
            if (copied != null) coverCropSource = copied.path else appVm.showToast("读取图片失败")
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clickable { coverPick.launch("image/*") },
                ) {
                    if (coverPath.isNotEmpty() && File(coverPath).exists()) {
                        val coverBmp by produceState<ImageBitmap?>(initialValue = null, coverPath) {
                            value = withContext(Dispatchers.IO) { decodeSampledFile(coverPath, 1440)?.asImageBitmap() }
                        }
                        coverBmp?.let {
                            Image(
                                bitmap = it,
                                contentDescription = "朋友圈背景",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "点击设置朋友圈背景",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                        )
                    }
                }
            }

            if (posts.isEmpty() && !loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "还没有动态，点右下角发一条吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(posts.size, key = { posts[it].id }) { idx ->
                val post = posts[idx]
                MomentPostCard(
                    post = post,
                    appVm = appVm,
                    onLike = { onLike(post, appVm) { load() } },
                    onComment = {
                        commentReplyTo = null
                        commentingPost = post
                    },
                    onReplyComment = { name ->
                        commentReplyTo = name
                        commentingPost = post
                    },
                    onAiComment = { aiComment(post, appVm) { load() } },
                    onAiBatchComment = { aiBatchComment(post, appVm) { load() } },
                    onDelete = { deletePost = post },
                    onPreview = { previewImage = it },
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
        }

        AnimatedVisibility(
            visible = commentingPost == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MomentFabButton(icon = Icons.Rounded.Edit, contentDescription = "发布") { onCompose() }
                MomentFabButton(icon = Icons.Rounded.MoreVert, contentDescription = "更多") { showMenu = true }
            }
        }

        commentingPost?.let { target ->
            CommentInputBar(
                replyTo = commentReplyTo,
                onSend = { text ->
                    val pid = target.id
                    val body = JSONObject().apply { put("content", text) }
                    ApiGateway.post("/api/moments/$pid/comment", body.toString(), AppSession.token(), object : ApiGateway.Callback {
                        override fun onSuccess(response: String) {
                            appVm.showToast("评论成功")
                            commentingPost = null
                            commentReplyTo = null
                            load()
                        }

                        override fun onError(error: String?) {
                            appVm.showToast("评论失败: ${error ?: ""}")
                        }
                    })
                },
                onCancel = {
                    commentingPost = null
                    commentReplyTo = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }
    }

    if (showMenu) {
        LingXinMenuOverlay(
            items = listOf(
                MenuItemSpec("发布动态", Icons.Rounded.Edit),
                MenuItemSpec("清空朋友圈", Icons.Rounded.DeleteOutline, danger = true),
            ),
            onDismiss = { showMenu = false },
            onSelect = { index ->
                showMenu = false
                if (index == 0) onCompose() else showClear = true
            },
        )
    }

    deletePost?.let { post ->
        LingXinDialog(
            onDismiss = { deletePost = null },
            title = "确认删除",
            text = "确定要删除这条朋友圈吗？",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                deletePost = null
                ApiGateway.delete("/api/moments/${post.id}", AppSession.token(), object : ApiGateway.Callback {
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

    if (showClear) {
        LingXinDialog(
            onDismiss = { showClear = false },
            title = "清空朋友圈",
            text = "确定要清空所有朋友圈吗？此操作会删除你发布的所有帖子、评论和点赞，且无法恢复。",
            confirmText = "清空",
            danger = true,
            onConfirm = {
                showClear = false
                ApiGateway.delete("/api/moments/clear", AppSession.token(), object : ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        appVm.showToast("已清空")
                        load()
                    }

                    override fun onError(error: String?) {
                        appVm.showToast("清空失败: ${error ?: ""}")
                    }
                })
            },
        )
    }

    previewImage?.let { url ->
        LingXinSheet(onDismiss = { previewImage = null }) {
            Column(modifier = Modifier.padding(12.dp)) {
                WebImage(
                    url = url,
                    contentDescription = "图片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    coverCropSource?.let { path ->
        ImageCropperDialog(
            path = path,
            frameAspect = context.resources.displayMetrics.let {
                it.widthPixels.toFloat() / (it.density * 230f)
            },
            onConfirm = { bmp ->
                coverCropSource = null
                thread {
                    try {
                        val file = File(context.filesDir, "moments_cover.jpg")
                        file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 88, out) }
                        context.getSharedPreferences("moments_bg", 0)
                            .edit().putString("cover_path", file.absolutePath).apply()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            coverPath = file.absolutePath
                            appVm.showToast("背景已更换")
                        }
                    } catch (_: Exception) {
                    }
                }
            },
            onCancel = { coverCropSource = null },
        )
    }
}

private fun onLike(post: MomentPost, appVm: AppViewModel, reload: () -> Unit) {
    ApiGateway.post("/api/moments/${post.id}/like", "{}", AppSession.token(), object : ApiGateway.Callback {
        override fun onSuccess(response: String) {
            reload()
        }

        override fun onError(error: String?) {
            appVm.showToast("操作失败: ${error ?: ""}")
        }
    })
}

private fun aiComment(post: MomentPost, appVm: AppViewModel, reload: () -> Unit) {
    val token = AppSession.token()
    FriendManager.getAll(token, object : FriendManager.Callback {
        override fun onResult(list: MutableList<FriendManager.Friend>?) {
            val friends = list ?: emptyList()
            if (friends.isEmpty()) {
                appVm.showToast("通讯录里还没有AI人设，请先添加")
                return
            }
            val pick = friends.random()
            appVm.showToast("让 ${pick.name} 来评论…")
            val body = JSONObject().apply {
                put("ai_name", pick.name)
                put("ai_persona", pick.persona ?: "")
            }
            ApiGateway.post("/api/moments/${post.id}/ai-comment", body.toString(), token, object : ApiGateway.Callback {
                override fun onSuccess(response: String) {
                    appVm.showToast("评论完成")
                    reload()
                }

                override fun onError(error: String?) {
                    appVm.showToast("AI评论失败: ${error ?: ""}")
                }
            })
        }

        override fun onError(err: String?) {
            appVm.showToast("获取通讯录失败: ${err ?: ""}")
        }
    })
}

private fun aiBatchComment(post: MomentPost, appVm: AppViewModel, reload: () -> Unit) {
    val token = AppSession.token()
    FriendManager.getAll(token, object : FriendManager.Callback {
        override fun onResult(list: MutableList<FriendManager.Friend>?) {
            val friends = list ?: emptyList()
            if (friends.isEmpty()) {
                appVm.showToast("通讯录里还没有AI人设，请先添加")
                return
            }
            appVm.showToast("批量评论中…")
            val body = JSONObject().apply {
                put("ai_list", JSONArray().apply {
                    friends.forEach { f ->
                        put(JSONObject().apply {
                            put("name", f.name)
                            put("persona", f.persona ?: "")
                        })
                    }
                })
            }
            ApiGateway.post("/api/moments/${post.id}/ai-batch-comment", body.toString(), token, object : ApiGateway.Callback {
                override fun onSuccess(response: String) {
                    appVm.showToast("完成")
                    reload()
                }

                override fun onError(error: String?) {
                    appVm.showToast("AI评论失败: ${error ?: ""}")
                }
            })
        }

        override fun onError(err: String?) {
            appVm.showToast("获取通讯录失败: ${err ?: ""}")
        }
    })
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MomentPostCard(
    post: MomentPost,
    appVm: AppViewModel,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onReplyComment: (String) -> Unit,
    onAiComment: () -> Unit,
    onAiBatchComment: () -> Unit,
    onDelete: () -> Unit,
    onPreview: (String) -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    var commentsExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onDelete)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        MomentAvatar(post = post, size = 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                post.nickname,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (post.content.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(post.content, style = MaterialTheme.typography.bodyMedium)
            }
            if (post.images.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MomentImageGrid(images = post.images, onPreview = onPreview)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    post.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "赞${if (post.likesCount > 0) "(${post.likesCount})" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (post.liked) Color(0xFFE85D4A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onLike)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                    "评论",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onComment)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                    "AI评",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAiComment)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                    "批量",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAiBatchComment)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            if (post.comments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val shown = if (commentsExpanded) post.comments else post.comments.take(4)
                        shown.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onReplyComment(c.name) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    buildString {
                                        append(c.name)
                                        if (c.isAi) append(" [AI]")
                                        append("：")
                                        append(c.content)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (post.comments.size > 4) {
                            Text(
                                if (commentsExpanded) "收起" else "展开全部${post.comments.size}条评论",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { commentsExpanded = !commentsExpanded }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentAvatar(post: MomentPost, size: androidx.compose.ui.unit.Dp) {
    if (post.avatar.isNotEmpty() && post.avatar.startsWith("/api/user/avatar/")) {
        WebImage(
            url = post.avatar,
            contentDescription = post.nickname,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            placeholder = { com.zhiyin.ui.DefaultAvatar(size = size) },
        )
    } else {
        val friendId = remember(post.nickname) { FriendManager.findIdByName(post.nickname) }
        PersonaAvatar(contactId = friendId, name = post.nickname, size = size)
    }
}

@Composable
private fun MomentImageGrid(images: List<String>, onPreview: (String) -> Unit) {
    val shown = images.take(9)
    val cols = when {
        shown.size == 1 -> 1
        shown.size <= 4 -> 2
        else -> 3
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.chunked(cols).forEach { rowImages ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowImages.forEach { url ->
                    WebImage(
                        url = url,
                        contentDescription = "动态图片",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPreview(url) },
                    )
                }
                repeat(cols - rowImages.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MomentFabButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CommentInputBar(
    replyTo: String?,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(replyTo) { mutableStateOf(if (replyTo != null) "@$replyTo " else "") }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("评论…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(22.dp),
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.FilledIconButton(
                onClick = { if (text.isNotBlank()) onSend(text.trim()) },
                enabled = text.isNotBlank(),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "取消", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun decodeSampledFile(path: String, target: Int): Bitmap? {
    return try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / s > target || o.outHeight / s > target * 2) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = maxOf(1, s) })
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMomentScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onPosted: () -> Unit,
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var uploadedUrls by remember { mutableStateOf(listOf<String>()) }
    var publishing by remember { mutableStateOf(false) }

    val imagePick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = com.zhiyin.ui.chat.ContentCopy.copyToCache(context, it, "moment")
            if (copied != null) {
                appVm.showToast("上传中…")
                ApiGateway.upload("/api/user/upload", copied.path, AppSession.token(), object : ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        val url = try {
                            JSONObject(response).optString("url", "")
                        } catch (_: Exception) {
                            ""
                        }
                        if (url.isNotEmpty()) {
                            uploadedUrls = uploadedUrls + url
                            appVm.showToast("上传成功")
                        } else {
                            appVm.showToast("解析失败")
                        }
                    }

                    override fun onError(error: String?) {
                        appVm.showToast("上传失败: ${error ?: ""}")
                    }
                })
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.TopAppBar(
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            title = { Text("发布动态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            actions = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (content.isBlank() && uploadedUrls.isEmpty()) {
                            appVm.showToast("请输入内容")
                            return@TextButton
                        }
                        publishing = true
                        val body = JSONObject().apply {
                            put("content", content.trim())
                            put("images", JSONArray().apply { uploadedUrls.forEach { put(it) } })
                        }
                        ApiGateway.post("/api/moments", body.toString(), AppSession.token(), object : ApiGateway.Callback {
                            override fun onSuccess(response: String) {
                                appVm.showToast("发布成功")
                                publishing = false
                                onPosted()
                            }

                            override fun onError(error: String?) {
                                appVm.showToast("发布失败: ${error ?: ""}")
                                publishing = false
                            }
                        })
                    },
                    enabled = !publishing,
                ) {
                    Text("发布", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("这一刻的想法…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                minLines = 6,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )

            if (uploadedUrls.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uploadedUrls.take(9).forEachIndexed { index, url ->
                        Box {
                            WebImage(
                                url = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable {
                                        uploadedUrls = uploadedUrls.filterIndexed { i, _ -> i != index }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "删除图片",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clickable { imagePick.launch("image/*") }
                    .padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("添加图片", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
