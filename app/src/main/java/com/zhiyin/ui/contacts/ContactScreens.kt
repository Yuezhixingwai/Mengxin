package com.zhiyin.ui.contacts

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AppSession
import com.zhiyin.data.AvatarStore
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.data.PersonaManager
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.EmptyHint
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val personas = remember { PersonaManager.getAll(context) }
    var showCustom by remember { mutableStateOf(false) }
    var addedNames by remember { mutableStateOf(setOf<String>()) }
    var importResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("添加朋友", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
            item {
                ListItem(
                    modifier = Modifier.clickable { showCustom = true },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    headlineContent = { Text("创建自定义人设") },
                    supportingContent = { Text("为 TA 编写专属人设描述") },
                )
                ListItem(
                    modifier = Modifier.clickable { doubaoImport.launch("application/json") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    },
                    headlineContent = { Text("导入豆包人设") },
                    supportingContent = { Text("从豆包导出的JSON文件一键导入") },
                )
            }
            item {
                Text(
                    "官方人设",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            if (personas.isEmpty()) {
                item { EmptyHint("人设库为空") }
            }
            items(personas, key = { it.name }) { persona ->
                val already = persona.name in addedNames || appVm.friends.any { it.name == persona.name }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = { PersonaAvatar(-1, persona.name, 46.dp) },
                    headlineContent = { Text(persona.name) },
                    supportingContent = {
                        Text(
                            persona.keywords.ifEmpty { persona.description }.take(60),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Button(
                            onClick = {
                                val token = AppSession.token()
                                FriendManager.add(token, persona.name, persona.description, "", object : com.zhiyin.logic.net.ApiGateway.Callback {
                                    override fun onSuccess(response: String) {
                                        appVm.loadFriends { friends ->
                                            if (friends.any { it.name == persona.name }) {
                                                addedNames = addedNames + persona.name
                                                appVm.showToast("已添加 ${persona.name}")
                                            } else {
                                                appVm.showToast("添加失败")
                                            }
                                        }
                                    }

                                    override fun onError(error: String?) {
                                        appVm.showToast("添加失败: ${error ?: ""}")
                                    }
                                })
                            },
                            enabled = !already,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text(if (already) "已添加" else "添加")
                        }
                    },
                )
            }
        }
        }
    }

    if (showCustom) {
        var name by remember { mutableStateOf("") }
        var persona by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        LingXinDialog(
            onDismiss = { showCustom = false },
            title = "自定义人设",
            confirmText = "添加",
            onConfirm = {
                val n = name.trim()
                if (n.isEmpty()) error = "请输入名字"
                else {
                    showCustom = false
                    val token = AppSession.token()
                    FriendManager.add(token, n, persona.trim(), "", object : com.zhiyin.logic.net.ApiGateway.Callback {
                        override fun onSuccess(response: String) {
                            appVm.loadFriends()
                            appVm.showToast("已添加 $n")
                        }

                        override fun onError(error: String?) {
                            appVm.showToast("添加失败: ${error ?: ""}")
                        }
                    })
                }
            },
        ) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("名字") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                placeholder = { Text("人设描述（性格、说话方式等）") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val official = remember { PersonaManager.isOfficial(context, friend.name) }
    val remarkPrefs = remember { context.getSharedPreferences("zhiyin_remark", 0) }
    val savedRemark = remember(friend.id) {
        remarkPrefs.getString("remark_${friend.id}", null)?.takeIf { it.isNotBlank() }
    }
    var name by remember { mutableStateOf(savedRemark ?: friend.name) }
    var persona by remember { mutableStateOf(friend.persona ?: "") }
    var mute by remember { mutableStateOf(friend.mute) }
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("好友设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("改", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(friend.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (official) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        if (official) "官方人设" else "自定义人设",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (official) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onOpenArtAvatars,
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(
                        Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("选插画头像", style = MaterialTheme.typography.labelLarge)
                }
            }

            CardSection(title = "资料") {
                SheetFieldIn(name, { name = it }, "备注名")
                if (official) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp)) {
                            Text(
                                "AI人设描述",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "官方内置人设，内容不可查看与修改",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = persona,
                        onValueChange = { persona = it },
                        placeholder = { Text("AI人设描述（性格、说话方式等）") },
                        minLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("消息免打扰", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开启后不显示未读角标",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = mute,
                        onCheckedChange = { mute = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
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
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(50.dp),
            ) {
                Text("保存修改")
            }

            CardSection(title = "微信自动回复") {
                ListItem(
                    modifier = Modifier.clickable { onBindWechat(friend) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("绑定微信自动回复") },
                    supportingContent = {
                        Text(
                            when (wechatBound) {
                                true -> "已绑定 · 微信消息将由「${friend.name}」自动回复"
                                false -> "未绑定 · 绑定后微信消息由该角色自动回复"
                                null -> "绑定后微信消息由该角色自动回复"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            CardSection(title = "管理") {
                ListItem(
                    modifier = Modifier.clickable { showClear = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    headlineContent = { Text("清空聊天记录", style = MaterialTheme.typography.bodyLarge) },
                )
                ListItem(
                    modifier = Modifier.clickable { showDelete = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    headlineContent = { Text("删除好友", color = MaterialTheme.colorScheme.error) },
                )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            content()
        }
    }
}

@Composable
private fun SheetFieldIn(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onCreated: (String, List<String>) -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<Int>()) }
    val selectedNames = remember { mutableStateOf(listOf<String>()) }
    val friends = appVm.friends

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("创建群聊", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                placeholder = { Text("群名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
            Text(
                "已选择 ${selected.value.size} 位好友",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ListItem(
                    modifier = Modifier.clickable {
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
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = { PersonaAvatar(friend.id, friend.name, 44.dp) },
                    headlineContent = { Text(friend.name) },
                    trailingContent = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { _ ->
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
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        )
                    },
                )
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
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(50.dp),
        ) {
            Text("创建群聊")
        }
    }
}
