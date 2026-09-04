package com.zhiyin.ui.discover

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhiyin.data.ImageUtils
import com.zhiyin.data.PersonaDetail
import com.zhiyin.data.PersonaLight
import com.zhiyin.data.PlazaApi
import com.zhiyin.data.PlazaAuthor
import com.zhiyin.ui.chat.ContentCopy
import com.zhiyin.ui.components.ImageCropperDialog
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.launch

private class PendingImage(val bytes: ByteArray)

private data class EditForm(
    var name: String = "",
    var slogan: String = "",
    var category: String = "",
    var keywords: String = "",
    var light: String = "",
    var full: String = "",
    var pat: String = "",
    var tags: String = "",
    var isPublic: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditScreen(
    appVm: AppViewModel,
    personaId: Int?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(EditForm()) }
    var categories by remember { mutableStateOf(listOf("女人设", "男人设", "情侣", "动漫", "游戏", "其他")) }
    var loading by remember { mutableStateOf(personaId != null) }
    var saving by remember { mutableStateOf(false) }
    var cover by remember { mutableStateOf<PendingImage?>(null) }
    var avatar by remember { mutableStateOf<PendingImage?>(null) }
    var background by remember { mutableStateOf<PendingImage?>(null) }
    var coverPreview by remember { mutableStateOf<String?>(null) }
    var avatarPreview by remember { mutableStateOf<String?>(null) }
    var bgPreview by remember { mutableStateOf<String?>(null) }
    var cropTarget by remember { mutableStateOf("") }
    var cropPath by remember { mutableStateOf<String?>(null) }

    fun cropAspect(field: String): Float = when (field) {
        "cover" -> 16f / 9f
        "avatar" -> 1f
        else -> 16f / 9f
    }

    fun compressFor(field: String, bmp: android.graphics.Bitmap): PendingImage = when (field) {
        "cover" -> PendingImage(ImageUtils.compressJpeg(bmp, ImageUtils.Limits.COVER_MAX_DIM, ImageUtils.Limits.COVER_MAX_BYTES))
        "avatar" -> PendingImage(ImageUtils.compressJpeg(bmp, ImageUtils.Limits.AVATAR_MAX_DIM, ImageUtils.Limits.AVATAR_MAX_BYTES))
        else -> PendingImage(ImageUtils.compressJpeg(bmp, ImageUtils.Limits.BACKGROUND_MAX_DIM, ImageUtils.Limits.BACKGROUND_MAX_BYTES))
    }

    val imagePick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = ContentCopy.copyToCache(ctx, it, "persona_img")
            if (copied != null) cropPath = copied.path
        }
    }

    LaunchedEffect(personaId) {
        PlazaApi.categories().onSuccess { categories = it }
        if (personaId != null) {
            PlazaApi.detail(personaId).onSuccess { d ->
                form = EditForm(
                    name = d.light.name,
                    slogan = d.light.slogan,
                    category = d.light.category,
                    keywords = d.light.keywords,
                    light = d.light.descriptionLight,
                    full = d.descriptionFull,
                    pat = d.pat,
                    tags = d.light.tags.joinToString(" "),
                    isPublic = d.light.isPublic,
                )
                if (d.light.coverUrl.isNotEmpty()) coverPreview = d.light.coverUrl
                if (d.light.avatarUrl.isNotEmpty()) avatarPreview = d.light.avatarUrl
                if (d.light.backgroundUrl.isNotEmpty()) bgPreview = d.light.backgroundUrl
                loading = false
            }.onFailure { appVm.showToast(it.message ?: "加载失败"); loading = false }
        } else {
            loading = false
        }
    }

    fun save() {
        if (form.name.isBlank()) { appVm.showToast("请填写人设名称"); return }
        if (form.light.isBlank()) { appVm.showToast("请填写人设简介"); return }
        if (saving) return
        saving = true
        val tags = form.tags.split(Regex("[\\s,，#]+")).map { it.trim() }.filter { it.isNotEmpty() }.take(20)
        val uploadImages: (Int) -> Unit = { pid ->
            listOf("cover" to cover, "avatar" to avatar, "background" to background).forEach { (field, img) ->
                img?.let { p ->
                    scope.launch { PlazaApi.uploadImage(ctx, pid, field, p.bytes) }
                }
            }
        }
        scope.launch {
            val pid = personaId
            if (pid == null) {
                PlazaApi.create(form.name.trim(), form.slogan.trim(), form.keywords.trim(), form.light.trim(), form.full.trim(), form.pat.trim(), tags, form.category, form.isPublic)
                    .onSuccess { newId ->
                        uploadImages(newId)
                        appVm.showToast("已保存")
                        onSaved()
                    }
                    .onFailure { e ->
                        appVm.showToast(e.message ?: "保存失败")
                        saving = false
                    }
            } else {
                PlazaApi.update(pid, form.name.trim(), form.slogan.trim(), form.keywords.trim(), form.light.trim(), form.full.trim(), form.pat.trim(), tags, form.category, form.isPublic)
                    .onSuccess {
                        uploadImages(pid)
                        appVm.showToast("已保存")
                        onSaved()
                    }
                    .onFailure { e ->
                        appVm.showToast(e.message ?: "保存失败")
                        saving = false
                    }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = { Text(if (personaId == null) "创建人设" else "编辑人设", fontWeight = FontWeight.SemiBold) },
            actions = {
                TextButton(onClick = { save() }, enabled = !saving) {
                    Text(if (saving) "保存中…" else "保存", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("人设图片（可选）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImageSlot("封面大图", coverPreview, "cover") { cropTarget = "cover"; imagePick.launch("image/*") }
                ImageSlot("头像", avatarPreview, "avatar") { cropTarget = "avatar"; imagePick.launch("image/*") }
                ImageSlot("背景图", bgPreview, "background") { cropTarget = "background"; imagePick.launch("image/*") }
            }

            OutlinedTextField(
                value = form.name, onValueChange = { form = form.copy(name = it.take(20)) },
                label = { Text("名称 *（卡片大字）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.slogan, onValueChange = { form = form.copy(slogan = it.take(40)) },
                label = { Text("标语（卡片小字，如：温柔会做饭的姐姐）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.take(6).forEach { c ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (form.category == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { form = form.copy(category = c) },
                    ) {
                        Text(
                            c,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (form.category == c) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.keywords, onValueChange = { form = form.copy(keywords = it.take(100)) },
                label = { Text("关键词（如：傲娇 温柔 学霸，用空格分隔）") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.light, onValueChange = { form = form.copy(light = it.take(2000)) },
                label = { Text("简介 *（一句话介绍）") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            OutlinedTextField(
                value = form.full, onValueChange = { form = form.copy(full = it.take(60000)) },
                label = { Text("完整人设（可选，会压缩上传）") }, modifier = Modifier.fillMaxWidth(), minLines = 5,
            )
            OutlinedTextField(
                value = form.pat, onValueChange = { form = form.copy(pat = it.take(100)) },
                label = { Text("拍一拍文案（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.tags, onValueChange = { form = form.copy(tags = it.take(200)) },
                label = { Text("标签（可选，用空格分隔）") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("公开人设", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (form.isPublic) "公开后所有人可见，可被点赞/收藏/评论" else "仅自己可见，不出现在广场",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.isPublic, onCheckedChange = { form = form.copy(isPublic = it) })
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    cropPath?.let { path ->
        ImageCropperDialog(
            path = path,
            frameAspect = cropAspect(cropTarget),
            onConfirm = { bmp ->
                val pending = compressFor(cropTarget, bmp)
                when (cropTarget) {
                    "cover" -> { cover = pending; coverPreview = path }
                    "avatar" -> { avatar = pending; avatarPreview = path }
                    "background" -> { background = pending; bgPreview = path }
                }
                cropPath = null
            },
            onCancel = { cropPath = null },
        )
    }
}

@Composable
private fun ImageSlot(
    label: String,
    preview: String?,
    tag: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val previewBmp = remember(preview) {
                preview?.let { p ->
                    try {
                        if (p.startsWith("http")) null
                        else BitmapFactory.decodeFile(p)?.asImageBitmap()
                    } catch (e: Exception) { null }
                }
            }
            when {
                preview != null && preview.startsWith("http") -> RemoteImage(
                    url = preview!!, contentDescription = label, modifier = Modifier.fillMaxSize(),
                )
                previewBmp != null -> Image(bitmap = previewBmp!!, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Text("＋", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 作者主页：TA 的公开人设 + 关注 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(
    appVm: AppViewModel,
    authorId: Int,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var author by remember { mutableStateOf<PlazaAuthor?>(null) }
    var list by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var followPersonaId by remember { mutableStateOf<Int?>(null) }

    fun reload() {
        scope.launch {
            PlazaApi.author(authorId).onSuccess { (a, l) ->
                author = a
                list = l
                followPersonaId = l.firstOrNull()?.id
                loading = false
            }.onFailure { appVm.showToast(it.message ?: "加载失败"); loading = false }
        }
    }

    LaunchedEffect(authorId) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = { Text("作者主页", fontWeight = FontWeight.SemiBold) },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (author?.avatar?.isNotEmpty() == true) {
                            RemoteImage(url = author!!.avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), placeholder = {
                                com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(64.dp), size = 64.dp, shape = CircleShape)
                            })
                        } else {
                            com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(64.dp), size = 64.dp, shape = CircleShape)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(author?.nickname ?: "作者", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${author?.followers ?: 0} 粉丝 · ${list.size} 个人设", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (author?.following == true) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            followPersonaId?.let { pid ->
                                scope.launch {
                                    PlazaApi.follow(pid).onSuccess { f ->
                                        author = author?.copy(following = f)
                                        appVm.showToast(if (f) "已关注" else "已取消关注")
                                    }.onFailure { appVm.showToast(it.message ?: "操作失败") }
                                }
                            }
                        },
                    ) {
                        Text(
                            if (author?.following == true) "已关注" else "+ 关注",
                            color = if (author?.following == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                Text("TA 的人设", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            val rows = list.chunked(2)
            items(rows, key = { r -> r.joinToString("-") { it.id.toString() } }) { row ->
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    row.forEach { p ->
                        PersonaCoverCard(p = p, coverHeight = 150, onClick = { onOpenDetail(p.id) }, modifier = Modifier.weight(1f).padding(horizontal = 5.dp))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (list.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("TA 还没有公开人设", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
