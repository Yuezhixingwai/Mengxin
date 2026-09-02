package com.zhiyin.ui.search

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiyin.logic.data.PersonaManager
import com.zhiyin.ui.EmptyHint
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.GroupAvatar
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.vm.AppViewModel
import com.zhiyin.ui.vm.UConv

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索会话、好友或人设", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            },
        )

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
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
                    ListItem(
                        modifier = Modifier.clickable { onOpenConversation(conv) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        leadingContent = {
                            if (conv.isGroup) GroupAvatar(46.dp)
                            else PersonaAvatar(conv.friendId, conv.name, 46.dp)
                        },
                        headlineContent = { Text(conv.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(conv.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            if (matchedFriends.isNotEmpty()) {
                item { SectionHeader("好友") }
                items(matchedFriends, key = { "friend_" + it.id }) { friend ->
                    val official = appVm.isOfficialPersona(friend.name)
                    ListItem(
                        modifier = Modifier.clickable { onOpenFriend(friend) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        leadingContent = { PersonaAvatar(friend.id, friend.name, 46.dp) },
                        headlineContent = { Text(friend.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                if (official) "官方人设" else (friend.persona ?: ""),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            if (personaHits.isNotEmpty()) {
                item { SectionHeader("人设库（去添加）") }
                items(personaHits, key = { "persona_" + it.name }) { p ->
                    ListItem(
                        modifier = Modifier.clickable { appVm.showToast("在「联系人 → 添加朋友」中添加 ${p.name}") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        leadingContent = { PersonaAvatar(-1, p.name, 46.dp) },
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text(p.keywords.take(40), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
        }
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(2.dp))
}
