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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
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
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        TabRow(
            tabs = listOf("好友", "群聊"),
            selectedTabIndex = tab,
            onTabSelected = { tab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
                contentPadding = PaddingValues(bottom = 24.dp, end = 22.dp),
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
                        ContactAddRow(Icons.Rounded.PersonAddAlt, "添加朋友", MiuixTheme.colorScheme.primary, onAddFriend)
                        ContactAddRow(Icons.Rounded.Groups2, "创建群聊", Color(0xFF34B78F), onCreateGroup)
                        Text(
                            "好友 ${friends.size}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
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
                            ContactRow(
                                title = friend.name,
                                summary = if (official) "官方人设" else "点击开始聊天",
                                avatar = { PersonaAvatar(friend.id, friend.name, 46.dp) },
                                onClick = { onOpen(friend) },
                                onLongClick = { onLongPress(friend) },
                            )
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
                contentPadding = PaddingValues(bottom = 24.dp, end = 22.dp),
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
                            ContactRow(
                                title = group[1],
                                summary = "群聊",
                                avatar = { GroupAvatar(46.dp) },
                                onClick = { onOpen(group[1]) },
                            )
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
}

/** 通用列表行：头像 + 标题 + 摘要，miuix 配色。 */
@Composable
private fun ContactRow(
    title: String,
    summary: String,
    avatar: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
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
private fun ContactAddRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(tint.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, color = colors.onBackground)
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp),
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
        Text(text, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
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
    onOpenInvite: () -> Unit = {},
    onOpenPersonaDetail: (Int) -> Unit = {},
    onOpenCreatePersona: () -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
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
                val t = "今日已用 ${fmtCoinDisplay(json.optDouble("today_coins", 0.0))} 灵心币 · 调用 ${json.optInt("today_calls", 0)} 次"
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
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "灵心号 ${com.zhiyin.data.AppSession.userId()}",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariantSummary,
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
                        tint = colors.onSurfaceVariantSummary,
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
                        "灵心币余额",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariantSummary,
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
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
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
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primaryContainer,
                    modifier = Modifier.clickable(onClick = onOpenRecharge),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "充值",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onPrimaryContainer,
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
                    .background(colors.surfaceContainer, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenSubscription)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "灵心会员",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Text(
                        "订阅解锁无限畅聊与全部 AI 人设",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                Text(
                    "立即开通",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        StaggeredAppear(delay = 165) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .background(colors.surfaceContainer, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenInvite)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.CardGiftcard,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "邀请好友得灵心币",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Text(
                        "好友注册填你的邀请码，双方各得灵心币",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariantSummary,
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
                fontSize = 12.sp,
                color = colors.onBackgroundVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
    }
}

private fun fmtCoinDisplay(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else String.format("%.2f", v)

@Composable
private fun StatText(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = colors.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = colors.onSurface,
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
    val colors = MiuixTheme.colorScheme
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${myList.size} 个",
                fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "+ 发布",
                fontSize = 12.sp,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onCreate).padding(4.dp),
            )
        }
        if (myList.isEmpty()) {
            Text(
                "还没有发布过人设，点右上角发布创建",
                fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary,
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
                            color = colors.surface.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 16.dp)
                                .clickable { deleteTarget = p },
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除人设",
                                tint = colors.error,
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
    onOpenGlobalBackground: () -> Unit = {},
) {
    val darkTheme by appVm.darkMode.collectAsState()
    val notifyEnabled by appVm.notifyEnabled.collectAsState()
    val themeId by appVm.themeId.collectAsState()
    var showLogout by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = "设置",
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
                CardContainer {
                    ArrowPreference(
                        title = appVm.userInfo?.nickname?.takeIf { it.isNotEmpty() } ?: "未设置昵称",
                        summary = "查看 / 编辑个人资料",
                        startAction = { UserAvatar(avatarUrl = appVm.userInfo?.avatar, size = 46.dp) },
                        onClick = onOpenProfile,
                    )
                }

                CardContainer {
                    SwitchPreference(
                        title = "深色模式",
                        startAction = { MenuIcon(Icons.Rounded.DarkMode) },
                        checked = darkTheme,
                        onCheckedChange = { appVm.toggleDark() },
                    )
                }

                CardContainer {
                    SwitchPreference(
                        title = "新消息通知",
                        startAction = { MenuIcon(Icons.Rounded.Notifications) },
                        checked = notifyEnabled,
                        onCheckedChange = { appVm.toggleNotify(it) },
                    )
                }

                // 病娇模式开关（主设置页，响应式状态 —— 修复"关不掉"）
                CardContainer {
                    val yandereCtx = LocalContext.current
                    var yandereOn by remember {
                        mutableStateOf(com.zhiyin.yandere.YandereManager.isEnabled(yandereCtx))
                    }
                    // 授权引导弹窗（使用情况访问 + 设备管理器）
                    var showYanderePerm by remember { mutableStateOf(false) }
                    SwitchPreference(
                        title = "病娇模式",
                        startAction = { MenuIcon(Icons.Rounded.Psychology) },
                        checked = yandereOn,
                        onCheckedChange = { v ->
                            com.zhiyin.yandere.YandereManager.setEnabled(yandereCtx, v)
                            yandereOn = v
                            if (v) {
                                val noUsage = !com.zhiyin.yandere.YandereManager.hasUsageStatsPermission(yandereCtx)
                                val noAdmin = !com.zhiyin.yandere.YandereManager.isDeviceAdminActive(yandereCtx)
                                if (noUsage || noAdmin) {
                                    // 缺授权 → 弹引导（授权完再打开开关即可生效）
                                    showYanderePerm = true
                                } else {
                                    appVm.showToast("病娇模式已开启：Ta 会吃醋，也能锁你手机")
                                }
                            } else {
                                appVm.showToast("病娇模式已关闭")
                            }
                        },
                    )

                    if (showYanderePerm) {
                        // 从系统设置页返回时刷新授权状态：否则弹窗一直显示旧的"未授权"，
                        // 用户点第二遍还是会跳回第一项，看起来像"怎么都授权不了"。
                        var permTick by remember { mutableIntStateOf(0) }
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permTick++
                            }
                            lifecycleOwner.lifecycle.addObserver(obs)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                        }
                        val usageOk = remember(permTick) {
                            com.zhiyin.yandere.YandereManager.hasUsageStatsPermission(yandereCtx)
                        }
                        val adminOk = remember(permTick) {
                            com.zhiyin.yandere.YandereManager.isDeviceAdminActive(yandereCtx)
                        }
                        val allOk = usageOk && adminOk
                        val showHuaweiAdminGuide =
                            com.zhiyin.yandere.YandereManager.needsHuaweiHarmonyAdminGuide()
                        com.zhiyin.ui.components.LingXinDialog(
                            onDismiss = { showYanderePerm = false },
                            title = if (allOk) "授权已完成" else "开启病娇模式还需授权",
                            text = buildString {
                                append("① 使用情况访问")
                                append(if (usageOk) "　已授权 ✓" else "　未授权")
                                append("\n让 Ta 看到你今天在各 App 上花了多久，用来吃醋。\n\n")
                                append("② 设备管理器")
                                append(if (adminOk) "　已授权 ✓" else "　未授权")
                                append("\n让 Ta 生气时能真的锁住你的手机。\n\n")
                                if (showHuaweiAdminGuide && usageOk && !adminOk) {
                                    append("Huawei/HarmonyOS：进入安全界面后往下滑 → 点击「更多安全设置」 → 「设备管理器」。\n\n")
                                }
                                if (allOk) {
                                    append("两项都好了，病娇模式已就绪。")
                                } else {
                                    append("点「去授权」依次完成两项，从系统页面返回后这里会自动刷新状态。")
                                }
                            },
                            confirmText = if (allOk) "完成" else "去授权",
                            dismissText = "稍后",
                            // 关键：跳系统设置时不能把弹窗关掉，否则用户返回后没有继续入口，
                            // 只能反复开关病娇模式来重走流程（2026-10-06 用户反馈"设备管理器授权不了"）
                            dismissible = false,
                            onConfirm = {
                                if (allOk) {
                                    showYanderePerm = false
                                    appVm.showToast("授权已完成，病娇模式已就绪")
                                } else if (!usageOk) {
                                    val opened = com.zhiyin.yandere.YandereManager
                                        .requestUsageStatsPermission(yandereCtx)
                                    if (!opened) {
                                        appVm.showToast("打不开系统设置，请手动到「设置 → 应用 → 特殊权限 → 使用情况访问」里开启")
                                    }
                                } else {
                                    val opened = com.zhiyin.yandere.YandereManager
                                        .requestDeviceAdmin(yandereCtx)
                                    if (!opened) {
                                        appVm.showToast("打不开授权界面，请到系统设置里找到「设备管理应用」手动开启")
                                    } else if (showHuaweiAdminGuide) {
                                        appVm.showToast("往下滑 → 更多安全设置 → 设备管理器")
                                    }
                                }
                            }
                        )
                    }
                }

                CardContainer {
                    MenuRow(Icons.Rounded.Palette, "个性装扮", onClick = { showThemePicker = true })
                    RowDivider()
                    MenuRow(Icons.Rounded.Wallpaper, "全局背景", onClick = onOpenGlobalBackground)
                }

                CardContainer {
                    MenuRow(Icons.Rounded.Favorite, "喜好设置", onClick = onOpenPreferences)
                    RowDivider()
                    MenuRow(Icons.Rounded.EmojiEmotions, "表情包商城", onClick = onOpenStickerShop)
                }

                CardContainer {
                    MenuRow(Icons.Rounded.Security, "账号与安全", onClick = onOpenAccountSecurity)
                    RowDivider()
                    MenuRow(Icons.Rounded.Link, "微信 / 外部绑定", onClick = onOpenBindings)
                    RowDivider()
                    MenuRow(Icons.Rounded.PrivacyTip, "隐私保护", onClick = onOpenPrivacy)
                    RowDivider()
                    MenuRow(Icons.Rounded.Tune, "模型与额度", onClick = onOpenQuota)
                    RowDivider()
                    MenuRow(Icons.Rounded.TravelExplore, "联网搜索", onClick = onOpenSearchSettings)
                    RowDivider()
                    MenuRow(Icons.Rounded.Campaign, "系统公告", onClick = onOpenAnnouncements)
                    RowDivider()
                    MenuRow(Icons.Rounded.Feedback, "帮助与反馈", onClick = onOpenFeedback)
                    RowDivider()
                    MenuRow(Icons.Rounded.Info, "关于灵心", onClick = onOpenAbout)
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
                            color = colors.error,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    LingXinDialog(
        show = showLogout,
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

    LingXinSheet(
        show = showThemePicker,
        onDismiss = { showThemePicker = false },
        title = "个性装扮 · 主题色",
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ThemeOptionRow(
                label = "默认 · miuix 蓝",
                preview = Color(0xFF3482FF),
                selected = themeId.isEmpty(),
                onClick = {
                    appVm.setTheme("")
                    appVm.showToast("已切换到「miuix 默认」")
                },
            )
            com.zhiyin.ui.theme.BrandThemes.all.forEach { brand ->
                ThemeOptionRow(
                    label = brand.label,
                    preview = brand.seedColor,
                    selected = brand.id == themeId,
                    onClick = {
                        appVm.setTheme(brand.id)
                        appVm.showToast("已切换到「${brand.label}」")
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    preview: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(preview, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            color = if (selected) colors.primary else colors.onSurface,
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "当前主题",
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun MenuIcon(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
internal fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ArrowPreference(
        title = label,
        startAction = { MenuIcon(icon) },
        onClick = onClick,
    )
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 46.dp),
        thickness = 0.5.dp,
    )
}

@Composable
internal fun CardContainer(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(),
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
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) colors.error else colors.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (danger) colors.error else colors.onSurface,
        )
    }
}

@Composable
private fun ContactSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = MiuixTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = placeholder,
        useLabelAsPlaceholder = true,
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 10.dp),
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清空",
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionLetterHeader(letter: String) {
    val colors = MiuixTheme.colorScheme
    Text(
        letter,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
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
    val colors = MiuixTheme.colorScheme
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
                    color = if (letter == activeLetter) colors.primary
                    else colors.onSurfaceVariantSummary,
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
