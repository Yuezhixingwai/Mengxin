package com.zhiyin.ui.contacts

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.data.AppSession
import com.zhiyin.data.AvatarStore
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.data.PersonaManager
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.EmptyHint
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AddFriendScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenPlaza: () -> Unit,
    onOpenCreate: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    var tab by remember { mutableStateOf("plaza") }
    var mine by remember { mutableStateOf<List<com.zhiyin.data.PersonaLight>>(emptyList()) }
    var favs by remember { mutableStateOf<List<com.zhiyin.data.PersonaLight>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun loadLists() {
        if (loading) return
        loading = true
        scope.launch {
            com.zhiyin.data.PlazaApi.mine().onSuccess { mine = it }
            com.zhiyin.data.PlazaApi.favorites().onSuccess { favs = it }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadLists() }

    fun addPersona(p: com.zhiyin.data.PersonaLight) {
        scope.launch {
            com.zhiyin.data.PlazaApi.addToContacts(p.id).onSuccess {
                appVm.showToast("已添加${p.name}，去会话页聊天吧")
                appVm.loadFriends()
            }.onFailure { appVm.showToast(it.message ?: "添加失败") }
        }
    }

    val doubaoImport = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        appVm.showToast("正在导入…")
        thread {
            try {
                val tempFile = java.io.File(context.cacheDir, "doubao_import_${System.currentTimeMillis()}.json")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { input.copyTo(it) }
                }
                val result = com.zhiyin.logic.net.ApiGateway.uploadSync(
                    com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE + "/api/user/import/doubao",
                    tempFile.absolutePath,
                    AppSession.token(),
                )
                tempFile.delete()
                val json = org.json.JSONObject(result)
                val ok = json.optBoolean("ok", false)
                val message = json.optString("message", "导入完成")
                val detail = StringBuilder()
                json.optJSONObject("results")?.let { r ->
                    r.optJSONArray("success")?.let { a ->
                        if (a.length() > 0) {
                            detail.append("成功导入 ").append(a.length()).append(" 个\n")
                            for (i in 0 until minOf(a.length(), 5)) {
                                detail.append(" · ").append(a.getJSONObject(i).optString("name")).append("\n")
                            }
                        }
                    }
                    r.optJSONArray("skipped")?.let { a ->
                        if (a.length() > 0) detail.append("跳过 ").append(a.length()).append(" 个")
                    }
                    r.optJSONArray("errors")?.let { a ->
                        if (a.length() > 0) detail.append("失败 ").append(a.length()).append(" 个")
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    importResult = Pair(ok, if (ok) "$message\n\n$detail" else message)
                    appVm.loadFriends()
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    importResult = Pair(false, "导入失败: ${e.message ?: ""}")
                }
            }
        }
    }

    val tabKeys = listOf("plaza", "mine", "favs", "import")

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "添加朋友",
                color = colors.surface,
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(
                tabs = listOf("广场", "我发布的", "我收藏的", "导入"),
                selectedTabIndex = tabKeys.indexOf(tab).coerceAtLeast(0),
                onTabSelected = { tab = tabKeys[it] },
                listState = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (tab) {
                "mine" -> {
                    if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else PlazaContactList(mine, appVm, onAdd = ::addPersona, onOpenCreate = onOpenCreate)
                }
                "favs" -> {
                    if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else PlazaContactList(favs, appVm, onAdd = ::addPersona, onOpenCreate = onOpenCreate)
                }
                "import" -> Column {
                    EntryRow(
                        icon = Icons.Rounded.UploadFile,
                        iconTint = colors.primaryVariant,
                        title = "导入豆包人设",
                        summary = "从豆包导出的JSON文件一键导入",
                        onClick = { doubaoImport.launch("application/json") },
                    )
                }
                else -> Column {
                    EntryRow(
                        icon = Icons.Rounded.Explore,
                        iconTint = colors.primary,
                        title = "去人设广场挑选",
                        summary = "热门榜、分类、推荐，发现喜欢的人设",
                        onClick = onOpenPlaza,
                    )
                    EntryRow(
                        icon = Icons.Rounded.Add,
                        iconTint = colors.primary,
                        title = "创建自定义人设",
                        summary = "设定名称、人设、图片，可选择公开分享",
                        onClick = onOpenCreate,
                    )
                }
            }
        }
    }

    importResult?.let { (ok, message) ->
        LingXinDialog(
            onDismiss = { importResult = null },
            title = if (ok) "导入成功" else "导入失败",
            text = message,
            confirmText = "确定",
            dismissText = null,
        )
    }
}

@Composable
private fun EntryRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(iconTint.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(summary, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun PlazaContactList(
    list: List<com.zhiyin.data.PersonaLight>,
    appVm: AppViewModel,
    onAdd: (com.zhiyin.data.PersonaLight) -> Unit,
    onOpenCreate: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    if (list.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("这里还没有人设", fontSize = 16.sp, color = colors.onSurfaceVariantSummary)
            Spacer(Modifier.height(8.dp))
            Text(
                "去创建或发布一个吧",
                color = colors.primary,
                modifier = Modifier.clickable(onClick = onOpenCreate).padding(6.dp),
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        items(list, key = { it.id }) { p ->
            val already = appVm.friends.any { it.name == p.name }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (p.avatarUrl.isNotEmpty()) {
                        com.zhiyin.ui.components.RemoteImage(url = p.avatarUrl, contentDescription = p.name, modifier = Modifier.fillMaxSize(), placeholder = {
                            com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(46.dp), size = 46.dp, shape = RoundedCornerShape(23.dp))
                        })
                    } else {
                        com.zhiyin.ui.DefaultAvatar(modifier = Modifier.size(46.dp), size = 46.dp, shape = RoundedCornerShape(23.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        p.keywords.ifEmpty { p.descriptionLight }.take(60),
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                TextButton(
                    text = if (already) "已添加" else "添加",
                    onClick = { onAdd(p) },
                    enabled = !already,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    minHeight = 36.dp,
                )
            }
        }
    }
}

@Composable
fun FriendSettingsScreen(
    friend: FriendManager.Friend,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onToast: (String) -> Unit,
    onBindWechat: (FriendManager.Friend) -> Unit = {},
    onOpenArtAvatars: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val official = remember { PersonaManager.isOfficial(context, friend.name) }
    val remarkPrefs = remember { context.getSharedPreferences("zhiyin_remark", 0) }
    val savedRemark = remember(friend.id) {
        remarkPrefs.getString("remark_${friend.id}", null)?.takeIf { it.isNotBlank() }
    }
    var name by remember { mutableStateOf(savedRemark ?: friend.name) }
    var persona by remember { mutableStateOf(friend.persona ?: "") }
    var mute by remember { mutableStateOf(friend.mute) }
    var personaExpanded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var wechatBound by remember(friend.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(friend.id) {
        wechatBound = withContext(Dispatchers.IO) {
            try {
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/wechat/bindings", "GET", null, com.zhiyin.data.AppSession.token()
                )
                val arr = org.json.JSONObject(resp).optJSONArray("bindings")
                var found = false
                for (i in 0 until (arr?.length() ?: 0)) {
                    val b = arr!!.getJSONObject(i)
                    if (b.optInt("character_id", -1) == friend.id && "active" == b.optString("status")) {
                        found = true
                        break
                    }
                }
                found
            } catch (_: Exception) {
                null
            }
        }
    }

    val avatarPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val baos = java.io.ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
                        onToast("上传中…")
                        AvatarStore.uploadPersonaAvatar(context, friend.id, baos.toByteArray()) { ok, err ->
                            if (ok) onToast("头像已更新") else onToast("上传失败: $err")
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "好友设置",
                color = colors.surface,
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.clickable { avatarPick.launch("image/*") }) {
                    PersonaAvatar(friend.id, friend.name, 84.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("改", fontSize = 12.sp, color = colors.onPrimary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(friend.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (official) colors.primaryContainer
                    else colors.surfaceContainerHigh,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        if (official) "官方人设" else "自定义人设",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = if (official) colors.onPrimaryContainer
                        else colors.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primaryContainer,
                    onClick = onOpenArtAvatars,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "选插画头像",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onPrimaryContainer,
                        )
                    }
                }
            }

            CardSection(title = "资料") {
                SheetFieldIn(name, { name = it }, "备注名")
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { personaExpanded = !personaExpanded }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI人设描述", fontSize = 16.sp)
                            Text(
                                if (personaExpanded) "点击收起"
                                else if (official) "官方内置人设，点击展开查看" else "已折叠，点击展开查看或修改",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariantSummary,
                            )
                        }
                        Icon(
                            if (personaExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (personaExpanded) "收起" else "展开",
                            tint = colors.onSurfaceVariantSummary,
                        )
                    }
                    AnimatedVisibility(visible = personaExpanded) {
                        if (official) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colors.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp)) {
                                    Text(
                                        "AI人设描述",
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                    Text(
                                        "官方内置人设，内容不可查看与修改",
                                        fontSize = 14.sp,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        } else {
                            TextField(
                                value = persona,
                                onValueChange = { persona = it },
                                label = "AI人设描述（性格、说话方式等）",
                                useLabelAsPlaceholder = true,
                                minLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("消息免打扰", fontSize = 16.sp)
                        Text(
                            "开启后不显示未读角标",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
                        checked = mute,
                        onCheckedChange = { mute = it },
                    )
                }
            }

            Button(
                onClick = {
                    val newName = name.trim()
                    if (newName.isEmpty()) {
                        onToast("名字不能为空")
                        return@Button
                    }
                    val token = AppSession.token()
                    val newPersona = if (official) friend.persona else persona.trim()
                    val rp = context.getSharedPreferences("zhiyin_remark", 0)
                    val orig = rp.getString("orig_${friend.id}", null)
                    val renamed = newName != (savedRemark ?: friend.name)
                    if (renamed) {
                        if (orig == null && savedRemark == null) {
                            rp.edit().putString("orig_${friend.id}", friend.name).apply()
                        }
                        if (newName != (orig ?: friend.name)) {
                            rp.edit().putString("remark_${friend.id}", newName).apply()
                        } else {
                            rp.edit().remove("remark_${friend.id}").apply()
                        }
                    }
                    FriendManager.update(
                        token, friend.id, if (renamed) newName else friend.name,
                        newPersona, friend.avatar,
                        object : com.zhiyin.logic.net.ApiGateway.Callback {
                            override fun onSuccess(response: String) {
                                FriendManager.updateMute(
                                    token, friend.id, mute,
                                    object : com.zhiyin.logic.net.ApiGateway.Callback {
                                        override fun onSuccess(response: String) {
                                            onChanged()
                                            onToast("已保存")
                                        }

                                        override fun onError(error: String?) {
                                            onChanged()
                                            onToast("保存成功(免打扰设置失败)")
                                        }
                                    }
                                )
                            }

                            override fun onError(error: String?) {
                                onToast("保存失败: ${error ?: ""}")
                            }
                        }
                    )
                    onBack()
                },
                cornerRadius = 25.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(50.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("保存修改", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
            }

            CardSection(title = "微信自动回复") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBindWechat(friend) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("绑定微信自动回复", fontSize = 16.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when (wechatBound) {
                                true -> "已绑定 · 微信消息将由「${friend.name}」自动回复"
                                false -> "未绑定 · 绑定后微信消息由该角色自动回复"
                                null -> "绑定后微信消息由该角色自动回复"
                            },
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }

            CardSection(title = "管理") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClear = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = colors.onSurfaceVariantSummary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("清空聊天记录", fontSize = 16.sp)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDelete = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("删除好友", fontSize = 16.sp, color = colors.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
        }
    }

    if (showClear) {
        LingXinDialog(
            onDismiss = { showClear = false },
            title = "清空聊天记录",
            text = "确定要清空与 ${friend.name} 的所有聊天记录吗？",
            confirmText = "清空",
            danger = true,
            onConfirm = {
                showClear = false
                MsgRepo.delete(context, "persona_${friend.name}")
                onToast("已清空聊天记录")
            },
        )
    }

    if (showDelete) {
        LingXinDialog(
            onDismiss = { showDelete = false },
            title = "删除好友",
            text = "确定要删除 ${friend.name} 吗？将同时删除聊天记录。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                showDelete = false
                val token = AppSession.token()
                FriendManager.remove(token, friend.id, object : com.zhiyin.logic.net.ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        MsgRepo.delete(context, "persona_${friend.name}")
                        onChanged()
                        onBack()
                    }

                    override fun onError(error: String?) {
                        onToast("删除失败: ${error ?: ""}")
                    }
                })
            },
        )
    }
}

@Composable
private fun CardSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                fontSize = 12.sp,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            content()
        }
    }
}

@Composable
private fun SheetFieldIn(value: String, onValueChange: (String) -> Unit, hint: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = hint,
        useLabelAsPlaceholder = true,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

@Composable
fun CreateGroupScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onCreated: (String, List<String>) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var groupName by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<Int>()) }
    val selectedNames = remember { mutableStateOf(listOf<String>()) }
    val friends = appVm.friends
    val canCreate = groupName.isNotBlank() && selected.value.isNotEmpty()

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "创建群聊",
                color = colors.surface,
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = "群名称",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
                Text(
                    "已选择 ${selected.value.size} 位好友",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            RubberBandBox(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (friends.isEmpty()) {
                    item { EmptyHint("还没有好友可添加") }
                }
                items(friends, key = { it.id }) { friend ->
                    val checked = friend.id in selected.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSel = selected.value.toMutableSet()
                                val newNames = selectedNames.value.toMutableList()
                                if (checked) {
                                    newSel.remove(friend.id)
                                    newNames.remove(friend.name)
                                } else {
                                    newSel.add(friend.id)
                                    newNames.add(friend.name)
                                }
                                selected.value = newSel
                                selectedNames.value = newNames
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonaAvatar(friend.id, friend.name, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(friend.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Checkbox(
                            state = if (checked) ToggleableState.On else ToggleableState.Off,
                            onClick = {
                                val newSel = selected.value.toMutableSet()
                                val newNames = selectedNames.value.toMutableList()
                                if (checked) {
                                    newSel.remove(friend.id)
                                    newNames.remove(friend.name)
                                } else {
                                    newSel.add(friend.id)
                                    newNames.add(friend.name)
                                }
                                selected.value = newSel
                                selectedNames.value = newNames
                            },
                        )
                    }
                }
            }
            }

            Button(
                onClick = {
                    val gName = groupName.trim()
                    if (gName.isEmpty()) {
                        appVm.showToast("请输入群名称")
                        return@Button
                    }
                    if (selected.value.isEmpty()) {
                        appVm.showToast("请选择至少一位好友")
                        return@Button
                    }
                    com.zhiyin.logic.data.GroupManager.createGroup(
                        appVm.getApplication(),
                        gName,
                        selected.value.toList(),
                        selectedNames.value.toList(),
                    ) { _, _ ->
                        onCreated(gName, selectedNames.value.toList())
                    }
                },
                enabled = canCreate,
                cornerRadius = 25.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(50.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("创建群聊", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
            }
        }
    }
}
