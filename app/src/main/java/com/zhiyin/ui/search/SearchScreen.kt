package com.zhiyin.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.logic.data.PersonaManager
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.EmptyHint
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.GroupAvatar
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.vm.AppViewModel
import com.zhiyin.ui.vm.UConv
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchScreen(
    appVm: AppViewModel,
    conversations: List<UConv>,
    onOpenConversation: (UConv) -> Unit,
    onOpenFriend: (com.zhiyin.logic.data.FriendManager.Friend) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val q = query.trim()
    val matchedConvs = remember(q, conversations) {
        if (q.isEmpty()) emptyList()
        else conversations.filter {
            it.name.contains(q, ignoreCase = true) || it.lastMessage.contains(q, ignoreCase = true)
        }
    }
    val matchedFriends = remember(q, appVm.friends) {
        if (q.isEmpty()) emptyList()
        else appVm.friends.filter {
            it.name.contains(q, ignoreCase = true) || (it.persona ?: "").contains(q, ignoreCase = true)
        }
    }
    val personaHits = remember(q) {
        if (q.isEmpty()) emptyList()
        else PersonaManager.getAll(appVm.getApplication()).filter { it.name.contains(q, ignoreCase = true) }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索会话、好友或人设",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    cornerRadius = 24.dp,
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    insideMargin = DpSize(16.dp, 12.dp),
                )
            }
        },
    ) { padding ->
        RubberBandBox(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            if (q.isEmpty()) {
                item { EmptyHint("输入关键字搜索会话与好友") }
            } else if (matchedConvs.isEmpty() && matchedFriends.isEmpty()) {
                item { EmptyHint("没有找到「$q」相关结果") }
            }
            if (matchedConvs.isNotEmpty()) {
                item {
                    SectionHeader("会话")
                }
                items(matchedConvs, key = { "conv_" + it.key }) { conv ->
                    SearchRow(
                        title = conv.name,
                        summary = conv.lastMessage,
                        avatar = {
                            if (conv.isGroup) GroupAvatar(46.dp)
                            else PersonaAvatar(conv.friendId, conv.name, 46.dp)
                        },
                        onClick = { onOpenConversation(conv) },
                    )
                }
            }
            if (matchedFriends.isNotEmpty()) {
                item { SectionHeader("好友") }
                items(matchedFriends, key = { "friend_" + it.id }) { friend ->
                    val official = appVm.isOfficialPersona(friend.name)
                    SearchRow(
                        title = friend.name,
                        summary = if (official) "官方人设" else (friend.persona ?: ""),
                        avatar = { PersonaAvatar(friend.id, friend.name, 46.dp) },
                        onClick = { onOpenFriend(friend) },
                    )
                }
            }
            if (personaHits.isNotEmpty()) {
                item { SectionHeader("人设库（去添加）") }
                items(personaHits, key = { "persona_" + it.name }) { p ->
                    SearchRow(
                        title = p.name,
                        summary = p.keywords.take(40),
                        avatar = { PersonaAvatar(-1, p.name, 46.dp) },
                        onClick = { appVm.showToast("在「联系人 → 添加朋友」中添加 ${p.name}") },
                    )
                }
            }
        }
        }
    }
}

/** 搜索结果行：头像 + 标题 + 摘要（替代 M3 ListItem）。 */
@Composable
private fun SearchRow(
    title: String,
    summary: String,
    avatar: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                summary,
                fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
    }
    Spacer(Modifier.height(2.dp))
}
