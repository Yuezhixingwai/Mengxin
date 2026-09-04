package com.zhiyin.ui

import android.icu.text.AlphabeticIndex
import android.icu.text.Transliterator
import android.icu.util.ULocale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups2
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.data.GroupManager
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.components.GroupAvatar
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.components.UserAvatar
import com.zhiyin.ui.vm.AppViewModel
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ContactsScreen(
    appVm: AppViewModel,
    onOpenFriendChat: (FriendManager.Friend) -> Unit,
    onOpenFriend: (FriendManager.Friend) -> Unit,
    onOpenFriendSettings: (FriendManager.Friend) -> Unit,
    onAddFriend: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var deleteFor by remember { mutableStateOf<FriendManager.Friend?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 24.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            listOf("好友", "群聊").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            label,
                            fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240)) { it / 4 * dir } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it / 4 * dir } + fadeOut(tween(240)))
            },
            label = "contactsTab",
        ) { t ->
            when (t) {
                0 -> FriendListPage(
                    appVm = appVm,
                    onAddFriend = onAddFriend,
                    onCreateGroup = onCreateGroup,
                    onOpen = onOpenFriend,
                    onLongPress = { deleteFor = it },
                )
                else -> GroupListPage(
                    appVm = appVm,
                    onCreateGroup = onCreateGroup,
                    onOpen = onOpenGroup,
                )
            }
        }
    }

    deleteFor?.let { friend ->
        LingXinDialog(
            onDismiss = { deleteFor = null },
            title = "删除联系人",
            text = "确定删除「${friend.name}」？",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                deleteFor = null
                val token = com.zhiyin.data.AppSession.token()
                FriendManager.remove(token, friend.id, object : com.zhiyin.logic.net.ApiGateway.Callback {
                    override fun onSuccess(response: String) {
                        appVm.loadFriends()
                        appVm.showToast("已删除")
                    }

                    override fun onError(error: String?) {
                        appVm.showToast("删除失败: ${error ?: ""}")
                    }
                })
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendListPage(
    appVm: AppViewModel,
    onAddFriend: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpen: (FriendManager.Friend) -> Unit,
    onLongPress: (FriendManager.Friend) -> Unit,
) {
    val friends = appVm.friends
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()

    val filtered = remember(friends, query) {
        if (!searching) friends
        else friends.filter { f ->
            f.name.contains(query.trim(), ignoreCase = true) ||
                (f.persona ?: "").contains(query.trim(), ignoreCase = true) ||
                PinyinIndex.matchesPinyin(f.name, query)
        }
    }
    val sections = remember(filtered) { groupByLetter(filtered.map { it.name }) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    val sectionIndexMap = remember(sections) {
        val map = mutableMapOf<String, Int>()
        var idx = if (searching) 1 else 2
        for ((letter, list) in sections) {
            map[letter] = idx
            idx += 1 + list.size
        }
        map
    }
    val letters = remember(sections) { sections.map { it.first } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp + BottomNavBarHeight, end = 22.dp),
            ) {
                item(key = "friend_search") {
                    ContactSearchBar(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索好友",
                    )
                }
                if (!searching) {
                    item(key = "friend_actions") {
                            ContactAddRow(Icons.Rounded.PersonAddAlt, "添加朋友", Color(0xFF3E7EE8), onAddFriend)
                            ContactAddRow(Icons.Rounded.Groups2, "创建群聊", Color(0xFF34B78F), onCreateGroup)
                            Text(
                                "好友 ${friends.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "friend_empty") {
                            EmptyHint(
                                if (searching) "没有找到「${query.trim()}」相关好友"
                                else "还没有好友，去添加一个伙伴吧"
                            )
                        }
                    } else {
                        sections.forEach { (letter, indices) ->
                            stickyHeader(key = "friend_header_$letter") {
                                SectionLetterHeader(letter)
                            }
                            itemsIndexed(indices, key = { _, i -> filtered[i].id }) { _, i ->
                                val friend = filtered[i]
                                val official = appVm.isOfficialPersona(friend.name)
                                ListItem(
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onOpen(friend) },
                                        onLongClick = { onLongPress(friend) },
                                    ),
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                    leadingContent = { PersonaAvatar(friend.id, friend.name, 46.dp) },
                                    headlineContent = { Text(friend.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        Text(
                                            if (official) "官方人设" else "点击开始聊天",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (!searching && letters.isNotEmpty()) {
                AlphabetSidebar(
                    letters = letters,
                    activeLetter = activeLetter,
                    onActiveChange = { activeLetter = it },
                    onSelect = { letter ->
                        sectionIndexMap[letter]?.let { idx ->
                            scope.launch { listState.scrollToItem(idx) }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            activeLetter?.let { letter ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                    ) {
                        Text(
                            letter,
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
                }
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupListPage(
    appVm: AppViewModel,
    onCreateGroup: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val groups = remember { GroupManager.getGroupChats(appVm.getApplication()) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()

    val filtered = remember(groups, query) {
        if (!searching) groups
        else groups.filter { g ->
            g[1].contains(query.trim()) || PinyinIndex.matchesPinyin(g[1], query)
        }
    }
    val sections = remember(filtered) { groupByLetter(filtered.map { it[1] }) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    val sectionIndexMap = remember(sections) {
        val map = mutableMapOf<String, Int>()
        var idx = if (searching) 1 else 2
        for ((letter, list) in sections) {
            map[letter] = idx
            idx += 1 + list.size
        }
        map
    }
    val letters = remember(sections) { sections.map { it.first } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp + BottomNavBarHeight, end = 22.dp),
            ) {
                item(key = "group_search") {
                    ContactSearchBar(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索群聊",
                    )
                }
                if (!searching) {
                    item(key = "group_actions") {
                            ContactAddRow(Icons.Rounded.Groups2, "创建群聊", Color(0xFF34B78F), onCreateGroup)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "group_empty") {
                            EmptyHint(
                                if (searching) "没有找到「${query.trim()}」相关群聊"
                                else "暂无群聊"
                            )
                        }
                    } else {
                        sections.forEach { (letter, indices) ->
                            stickyHeader(key = "group_header_$letter") {
                                SectionLetterHeader(letter)
                            }
                            itemsIndexed(indices, key = { _, i -> filtered[i][0] }) { _, i ->
                                val group = filtered[i]
                                ListItem(
                                    modifier = Modifier.clickable { onOpen(group[1]) },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                    leadingContent = { GroupAvatar(46.dp) },
                                    headlineContent = { Text(group[1], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("群聊") },
                                )
                            }
                        }
                    }
                }
            }
            if (!searching && letters.isNotEmpty()) {
                AlphabetSidebar(
                    letters = letters,
                    activeLetter = activeLetter,
                    onActiveChange = { activeLetter = it },
                    onSelect = { letter ->
                        sectionIndexMap[letter]?.let { idx ->
                            scope.launch { listState.scrollToItem(idx) }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            activeLetter?.let { letter ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                    ) {
                        Text(
                            letter,
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
                }
            }
    }
}

@Composable
private fun ContactAddRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(tint.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        },
        headlineContent = { Text(label) },
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp,
    )
}

@Composable
internal fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MeScreen(
    appVm: AppViewModel,
    onOpenSettings: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFavorites: () -> Unit = {},
    onOpenSavedImages: () -> Unit = {},
    onOpenSavedFiles: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onOpenRecharge: () -> Unit = {},
    onOpenPersonaDetail: (Int) -> Unit = {},
    onOpenCreatePersona: () -> Unit = {},
) {
    val userInfo = appVm.userInfo
    var coinBalance by remember { mutableStateOf<Double?>(null) }
    var coinToday by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/user/coins", "GET", null,
                    com.zhiyin.data.AppSession.token()
                )
                val json = JSONObject(resp)
                val b = json.optDouble("balance", 0.0)
                val t = "今日已用 ${fmtCoinDisplay(json.optDouble("today_coins", 0.0))} 启辰币 · 调用 ${json.optInt("today_calls", 0)} 次"
                withContext(Dispatchers.Main) {
                    coinBalance = b
                    coinToday = t
                }
            } catch (_: Exception) {
            }
        }
    }

    RubberBandBox(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StaggeredAppear {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    avatarUrl = userInfo?.avatar,
                    size = 60.dp,
                    fallback = userInfo?.nickname?.takeIf { it.isNotEmpty() } ?: "我",
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        userInfo?.nickname?.takeIf { it.isNotEmpty() }
                            ?: userInfo?.username?.takeIf { it.isNotEmpty() }
                            ?: "未设置昵称",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "灵心号 ${com.zhiyin.data.AppSession.userId()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        StatText("好友", "${appVm.friendCount}")
                        Spacer(Modifier.width(24.dp))
                        StatText("群聊", "${GroupManager.getGroupChats(appVm.getApplication()).size}")
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        StaggeredAppear(delay = 120) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "启辰币余额",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    AnimatedContent(
                        targetState = coinBalance,
                        transitionSpec = {
                            (fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 3 }) togetherWith
                                fadeOut(tween(200))
                        },
                        label = "coinBalance",
                    ) { balance ->
                        Text(
                            balance?.let { "$" + fmtCoinDisplay(it) } ?: "…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    AnimatedVisibility(
                        visible = coinToday.isNotEmpty(),
                        enter = fadeIn(tween(400, delayMillis = 120)) +
                            slideInVertically(tween(400, delayMillis = 120)) { it / 4 },
                        exit = fadeOut(tween(160)),
                    ) {
                        Text(
                            coinToday,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable(onClick = onOpenRecharge),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "充值",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        StaggeredAppear(delay = 150) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenSubscription)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "灵心会员",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "订阅解锁无限畅聊与全部 AI 人设",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "立即开通",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        StaggeredAppear(delay = 170) {
            MyPublishedPersonasSection(
                appVm = appVm,
                onOpenDetail = onOpenPersonaDetail,
                onCreate = onOpenCreatePersona,
            )
        }

        StaggeredAppear(delay = 180) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuickAction(Icons.Rounded.AccountBalanceWallet, "灵心钱包") { onOpenWallet() }
                QuickAction(Icons.Rounded.PhotoLibrary, "我的相册") { onOpenSavedImages() }
                QuickAction(Icons.Rounded.Folder, "我的文件") { onOpenSavedFiles() }
                QuickAction(Icons.Rounded.Star, "我的收藏") { onOpenFavorites() }
            }
        }

        StaggeredAppear(delay = 240) {
            Text(
                "内容为AI生成，请注意甄别",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(BottomNavBarHeight + 24.dp))
    }
    }
}

private fun fmtCoinDisplay(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else String.format("%.2f", v)

@Composable
private fun StatText(label: String, value: String) {    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}


@Composable
private fun MyPublishedPersonasSection(
    appVm: AppViewModel,
    onOpenDetail: (Int) -> Unit,
    onCreate: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var myList by remember { mutableStateOf<List<com.zhiyin.data.PersonaLight>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<com.zhiyin.data.PersonaLight?>(null) }

    fun reload() {
        scope.launch {
            com.zhiyin.data.PlazaApi.mine().onSuccess { myList = it }
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "我发布的人设",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${myList.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "+ 发布",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onCreate).padding(4.dp),
            )
        }
        if (myList.isEmpty()) {
            Text(
                "还没有发布过人设，点右上角发布创建",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(myList, key = { it.id }) { p ->
                    Box {
                        com.zhiyin.ui.discover.PersonaCoverCard(
                            p = p,
                            coverHeight = 150,
                            onClick = { onOpenDetail(p.id) },
                            modifier = Modifier.width(150.dp).padding(end = 10.dp),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 16.dp)
                                .clickable { deleteTarget = p },
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除人设",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(6.dp).size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { p ->
        LingXinDialog(
            onDismiss = { deleteTarget = null },
            title = "删除人设",
            text = "确定删除「${p.name}」吗？广场中相关的点赞、收藏、评论将一并清除，无法恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    com.zhiyin.data.PlazaApi.delete(p.id).onSuccess {
                        appVm.showToast("已删除")
                        reload()
                    }.onFailure { appVm.showToast(it.message ?: "删除失败") }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenAccountSecurity: () -> Unit,
    onOpenBindings: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenFeedback: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenQuota: () -> Unit = {},
    onOpenSearchSettings: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
    onOpenPreferences: () -> Unit = {},
    onOpenStickerShop: () -> Unit = {},
) {
    val darkTheme by appVm.darkMode.collectAsState()
    val notifyEnabled by appVm.notifyEnabled.collectAsState()
    val themeId by appVm.themeId.collectAsState()
    var showLogout by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            title = {
                Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
        )

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenProfile),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                leadingContent = {
                    UserAvatar(avatarUrl = appVm.userInfo?.avatar, size = 52.dp)
                },
                headlineContent = {
                    Text(
                        appVm.userInfo?.nickname?.takeIf { it.isNotEmpty() } ?: "未设置昵称",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                supportingContent = { Text("查看 / 编辑个人资料") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            Spacer(Modifier.height(8.dp))

            CardContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appVm.toggleDark() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("深色模式", modifier = Modifier.weight(1f))
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = { appVm.toggleDark() },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                    )
                }
            }

            CardContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appVm.toggleNotify(!notifyEnabled) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("新消息通知", modifier = Modifier.weight(1f))
                    Switch(
                        checked = notifyEnabled,
                        onCheckedChange = { appVm.toggleNotify(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                    )
                }
            }

            CardContainer {
                MenuRow(Icons.Rounded.Palette, "个性装扮", onClick = { showThemePicker = true })
            }

            CardContainer {
                Column {
                    MenuRow(Icons.Rounded.Favorite, "喜好设置", onClick = onOpenPreferences)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.EmojiEmotions, "表情包商城", onClick = onOpenStickerShop)
                }
            }

            CardContainer {
                Column {
                    MenuRow(Icons.Rounded.Security, "账号与安全", onClick = onOpenAccountSecurity)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.Link, "微信 / 外部绑定", onClick = onOpenBindings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.PrivacyTip, "隐私保护", onClick = onOpenPrivacy)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.Tune, "模型与额度", onClick = onOpenQuota)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.TravelExplore, "联网搜索", onClick = onOpenSearchSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.Campaign, "系统公告", onClick = onOpenAnnouncements)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.Feedback, "帮助与反馈", onClick = onOpenFeedback)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    MenuRow(Icons.Rounded.Info, "关于灵心", onClick = onOpenAbout)
                }
            }

            CardContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogout = true }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "退出登录",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        }
    }

    if (showLogout) {
        LingXinDialog(
            onDismiss = { showLogout = false },
            title = "退出登录",
            text = "确定要退出登录吗？",
            confirmText = "退出",
            danger = true,
            onConfirm = {
                showLogout = false
                appVm.logout()
            },
        )
    }

    if (showThemePicker) {
        LingXinSheet(onDismiss = { showThemePicker = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "个性装扮 · 主题色",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                com.zhiyin.ui.theme.BrandThemes.all.forEach { brand ->
                    val selected = brand.id == themeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appVm.setTheme(brand.id)
                                appVm.showToast("已切换到「${brand.label}」")
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(brand.preview, androidx.compose.foundation.shape.CircleShape),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            brand.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "当前主题",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
internal fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        },
        headlineContent = { Text(label) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
internal fun CardContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        content()
    }
}

@Composable
internal fun StaggeredAppear(delay: Int = 0, content: @Composable () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!visible) visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350, delayMillis = delay)) +
            slideInVertically(tween(350, delayMillis = delay)) { it / 8 },
        exit = fadeOut(tween(120)),
    ) {
        content()
    }
}

@Composable
internal fun SheetActionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
        headlineContent = {
            Text(
                label,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun ContactSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionLetterHeader(letter: String) {
    Text(
        letter,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun AlphabetSidebar(
    letters: List<String>,
    activeLetter: String?,
    onActiveChange: (String?) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var barHeightPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onSizeChanged { barHeightPx = it.height },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .pointerInput(letters, barHeightPx) {
                    if (barHeightPx <= 0) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val letter = letterAt(offset.y, barHeightPx, letters)
                            onActiveChange(letter)
                            letter?.let(onSelect)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val letter = letterAt(change.position.y, barHeightPx, letters)
                            onActiveChange(letter)
                            letter?.let(onSelect)
                        },
                        onDragEnd = { onActiveChange(null) },
                        onDragCancel = { onActiveChange(null) },
                    )
                },
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    letter,
                    fontSize = 10.sp,
                    fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Medium,
                    color = if (letter == activeLetter) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(24.dp)
                        .height(14.dp)
                        .clickable { onSelect(letter) },
                )
            }
        }
    }
}

private fun letterAt(y: Float, totalHeightPx: Int, letters: List<String>): String? {
    if (letters.isEmpty() || totalHeightPx <= 0) return null
    val idx = (y / totalHeightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
    return letters[idx]
}

private fun groupByLetter(names: List<String>): List<Pair<String, List<Int>>> {
    val collator = Collator.getInstance(Locale.CHINA)
    val indexed = names.mapIndexed { i, n -> i to n }
    return indexed.sortedWith(compareBy(collator) { it.second })
        .groupBy { PinyinIndex.letterOf(it.second) }
        .map { (letter, list) -> letter to list.map { it.first } }
        .sortedBy { (letter, _) -> if (letter == "#") '[' else letter[0] }
}

private object PinyinIndex {
    private val alphabeticIndex: AlphabeticIndex.ImmutableIndex<ULocale>? = try {
        AlphabeticIndex<ULocale>(ULocale.CHINESE).buildImmutableIndex()
    } catch (_: Exception) {
        null
    }

    private val hanLatin: Transliterator? = try {
        Transliterator.getInstance("Han-Latin")
    } catch (_: Exception) {
        null
    }

    fun letterOf(name: String): String {
        val c = name.trim().firstOrNull() ?: return "#"
        return when {
            c in 'A'..'Z' || c in 'a'..'z' -> c.uppercaseChar().toString()
            c.isDigit() -> "#"
            alphabeticIndex == null -> "#"
            else -> try {
                val bucket = alphabeticIndex.getBucketIndex(name)
                val label = alphabeticIndex.getBucket(bucket).label?.toString()?.trim()
                if (label != null && label.length == 1 && label[0].isLetter()) label.uppercase() else "#"
            } catch (_: Exception) {
                "#"
            }
        }
    }

    fun matchesPinyin(name: String, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val t = hanLatin ?: return false
        val pinyin = try {
            t.transliterate(name).lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        } catch (_: Exception) {
            return false
        }
        return pinyin.contains(q)
    }
}
