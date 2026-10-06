package com.zhiyin.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups2
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyin.data.AccountApi
import com.zhiyin.data.ImageUtils
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.ui.chat.ChatDetailScreen
import com.zhiyin.ui.chat.GroupChatScreen
import com.zhiyin.ui.components.GroupAvatar
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.PersonaAvatar
import com.zhiyin.ui.components.acrylic
import com.zhiyin.ui.contacts.AddFriendScreen
import com.zhiyin.ui.contacts.CreateGroupScreen
import com.zhiyin.ui.contacts.FriendSettingsScreen
import com.zhiyin.ui.me.ArtAvatarScreen
import com.zhiyin.ui.me.FavoritesScreen
import com.zhiyin.ui.me.RechargeScreen
import com.zhiyin.ui.me.SavedFilesScreen
import com.zhiyin.ui.me.SavedImagesScreen
import com.zhiyin.ui.me.SubscriptionScreen
import com.zhiyin.ui.moments.ComposeMomentScreen
import com.zhiyin.ui.moments.MomentsScreen
import com.zhiyin.ui.quota.QuotaScreen
import com.zhiyin.ui.roundtable.CreateMeetingScreen
import com.zhiyin.ui.roundtable.RoundtableChatScreen
import com.zhiyin.ui.roundtable.RoundtableScreen
import com.zhiyin.ui.search.SearchScreen
import com.zhiyin.ui.settings.AboutScreen
import com.zhiyin.ui.settings.AboutUsScreen
import com.zhiyin.ui.settings.AccountSecurityScreen
import com.zhiyin.ui.settings.AnnouncementsScreen
import com.zhiyin.ui.settings.BindingsScreen
import com.zhiyin.ui.settings.FeedbackScreen
import com.zhiyin.ui.settings.GlobalBackgroundScreen
import com.zhiyin.ui.settings.PrivacyScreen
import com.zhiyin.ui.settings.ProfileEditScreen
import com.zhiyin.ui.settings.SearchSettingsScreen
import com.zhiyin.ui.settings.WalletScreen
import com.zhiyin.ui.settings.WechatBindScreen
import com.zhiyin.ui.theme.globalBackground
import com.zhiyin.ui.vm.AppViewModel
import com.zhiyin.ui.vm.ChatListViewModel
import com.zhiyin.ui.vm.TimeFmt
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.hypot

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    Discover("发现", Icons.Filled.Explore),
    Chats("会话", Icons.Filled.ChatBubble),
    Contacts("联系人", Icons.Filled.Groups),
    Me("我的", Icons.Filled.Person),
}

sealed interface Overlay {
    data class Chat(val name: String, val desc: String, val id: Int) : Overlay
    data class Call(val name: String, val desc: String, val id: Int) : Overlay
    data object Invite : Overlay
    data class Group(val name: String, val members: List<String>?) : Overlay
    data object Settings : Overlay
    data object AddFriend : Overlay
    data class FriendSettings(val friend: FriendManager.Friend) : Overlay
    data object CreateGroup : Overlay
    data object ProfileEdit : Overlay
    data class ArtAvatars(val contactId: Int, val contactName: String?) : Overlay
    data object Wallet : Overlay
    data object Subscription : Overlay
    data object Recharge : Overlay
    data object AccountSecurity : Overlay
    data object Feedback : Overlay
    data object About : Overlay
    data object AboutUs : Overlay
    data object Search : Overlay
    data object Moments : Overlay
    data object MomentCompose : Overlay
    data object Roundtable : Overlay
    data class RoundtableChat(val meetingId: Int, val title: String) : Overlay
    data object CreateMeeting : Overlay
    data object Quota : Overlay
    data object ApiKeys : Overlay
    data object Bindings : Overlay
    data object Privacy : Overlay
    data class WechatBind(val characterId: Int, val characterName: String?) : Overlay
    data object SearchSettings : Overlay
    data object Announcements : Overlay
    data object Favorites : Overlay
    data object SavedImages : Overlay
    data object SavedFiles : Overlay
    data class PersonaDetail(val personaId: Int) : Overlay
    data class PersonaEdit(val personaId: Int?) : Overlay
    data object PersonaSearch : Overlay
    data object HotList : Overlay
    data object MessageCenter : Overlay
    data object StickerShop : Overlay
    data class AuthorPage(val userId: Int) : Overlay
    data object Preferences : Overlay
    data object GlobalBackground : Overlay
}

internal val BottomNavBarHeight: Dp = 64.dp

@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
@Composable
fun MainScaffold(appVm: AppViewModel) {
    val view = LocalView.current
    val darkTheme by appVm.darkMode.collectAsState()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val overlayStack = remember { mutableStateListOf<Overlay>() }
    val overlay = overlayStack.lastOrNull()
    val chatListVm: ChatListViewModel = viewModel(factory = ChatListViewModel.Factory)
    var navForward by remember { mutableStateOf(true) }

    fun push(o: Overlay) {
        overlayStack.add(o)
        navForward = true
    }

    fun pop() {
        if (overlayStack.isNotEmpty()) overlayStack.removeAt(overlayStack.lastIndex)
        navForward = false
    }

    // ===== 语音通话解锁门槛（邀请 2 位新用户）=====
    val scope = rememberCoroutineScope()
    var callGateLoading by remember { mutableStateOf(false) }
    var callGate by remember { mutableStateOf<CallGateInfo?>(null) }

    fun openCallGuarded(name: String, desc: String, id: Int) {
        if (callGateLoading) return
        callGateLoading = true
        scope.launch {
            val resp = withContext(Dispatchers.IO) {
                try {
                    com.zhiyin.logic.net.ApiGateway.requestSync(
                        com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE + "/api/invite/call-gate",
                        "GET", null, com.zhiyin.data.AppSession.token()
                    )
                } catch (_: Exception) {
                    null
                }
            }
            callGateLoading = false
            var allowed = true // 接口异常时放行，不挡正常使用
            var info: CallGateInfo? = null
            try {
                val json = JSONObject(resp ?: "")
                allowed = json.optBoolean("allowed", true)
                if (!allowed) {
                    info = CallGateInfo(
                        validCount = json.optInt("valid_count", 0),
                        required = json.optInt("required", 2),
                        code = json.optString("code", ""),
                    )
                }
            } catch (_: Exception) {
            }
            if (info == null) {
                push(Overlay.Call(name, desc, id))
            } else {
                callGate = info
            }
        }
    }
    // ===== 语音通话解锁门槛 end =====

    LaunchedEffect(overlayStack.size, currentTab) {
        if (overlay == null) chatListVm.refresh()
    }

    BackHandler(enabled = overlay != null) { pop() }

    // ===== 启动自动检查更新：每次冷启动一次；用户点"以后再说"后同一版本不再提醒 =====
    val _upCtx = LocalContext.current
    var updateInfo by remember { mutableStateOf<com.zhiyin.data.AccountApi.VersionInfo?>(null) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        try {
            val v = com.zhiyin.data.AccountApi.versionLatest() ?: return@LaunchedEffect
            val local = _upCtx.packageManager.getPackageInfo(_upCtx.packageName, 0).versionName ?: ""
            if (v.version.isEmpty() || !isNewerAppVersion(v.version, local)) return@LaunchedEffect
            val prefs = _upCtx.getSharedPreferences("zhiyin_update", 0)
            if (prefs.getString("dismissed", null) == v.version) return@LaunchedEffect
            updateInfo = v
        } catch (_: Exception) {
        }
    }
    // 更新弹窗 → 应用内下载 → 直接拉起系统安装（逻辑见 AppUpdater / UpdatePrompt）
    var updateStarted by remember { mutableStateOf(false) }
    com.zhiyin.ui.components.UpdatePrompt(
        info = updateInfo,
        onDismiss = {
            val dismissedVersion = updateInfo?.version
            updateInfo = null
            // 只有"以后再说"才记入忽略列表；点了"立即更新"的取消/失败不该影响下次提醒
            if (!updateStarted && dismissedVersion != null) {
                _upCtx.getSharedPreferences("zhiyin_update", 0).edit()
                    .putString("dismissed", dismissedVersion).apply()
            }
            updateStarted = false
        },
        toast = { appVm.showToast(it) },
        onStartDownload = { updateStarted = true },
    )

    // 语音通话解锁弹窗：邀请 2 位新用户才能使用
    callGate?.let { gate ->
        LingXinDialog(
            onDismiss = { callGate = null },
            title = "语音通话暂未解锁",
            text = "邀请 ${gate.required} 位新用户注册并填写你的邀请码，即可解锁语音通话。" +
                    "（新朋友注册 3 天内有效，双方各得 5 灵心币）\n" +
                    "当前进度 ${gate.validCount}/${gate.required}" +
                    if (gate.code.isNotEmpty()) "\n\n我的邀请码：${gate.code}" else "",
            confirmText = "去邀请",
            dismissText = "下次再说",
            onConfirm = {
                callGate = null
                push(Overlay.Invite)
            },
        )
    }

    val globalBgEnabled by appVm.globalBgEnabled.collectAsState()
    val globalBgPath by appVm.globalBgPath.collectAsState()
    val globalBgAcrylic by appVm.globalBgAcrylic.collectAsState()
    val globalBgBlur by appVm.globalBgBlur.collectAsState()
    val globalBgBitmap by produceState<ImageBitmap?>(initialValue = null, globalBgPath) {
        value = if (globalBgPath.isEmpty()) null else withContext(Dispatchers.IO) {
            val f = java.io.File(globalBgPath)
            if (f.exists()) ImageUtils.decodeSampled(globalBgPath, 1600)?.asImageBitmap() else null
        }
    }
    val globalBgHaze = remember { HazeState() }
    val baseColors = MiuixTheme.colorScheme
    val globalBgReady = globalBgEnabled && globalBgBitmap != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseColors.surface),
    ) {
        if (globalBgReady) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(globalBgHaze)) {
                Image(
                    bitmap = globalBgBitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (globalBgAcrylic) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .acrylic(
                            state = globalBgHaze,
                            tint = baseColors.surface.copy(alpha = 0.35f),
                            blurRadius = globalBgBlur.dp,
                        ),
                )
            }
        }
        AnimatedContent(
            targetState = overlay,
            transitionSpec = {
                if (navForward) {
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
                            fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } +
                                    fadeOut(tween(300)))
                } else {
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } +
                            fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
                                    fadeOut(tween(300)))
                }
            },
            label = "overlay",
        ) { o ->
            val branchChat = o is Overlay.Chat || o is Overlay.Group
            val schemeOverride = if (globalBgReady && !branchChat) {
                baseColors.globalBackground(if (globalBgAcrylic) 0.62f else 0.80f)
            } else {
                baseColors
            }
            top.yukonga.miuix.kmp.theme.MiuixTheme(
                colors = schemeOverride,
                textStyles = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles,
            ) {
                when (o) {
                    is Overlay.Chat -> ChatDetailScreen(
                        personaName = o.name,
                        personaDesc = o.desc,
                        personaId = o.id,
                        onBack = { pop() },
                        onOpenFriendSettings = { friendId ->
                            val friend = appVm.friends.find { it.id == friendId }
                                ?: FriendManager.Friend(friendId, o.name, o.desc, "")
                            push(Overlay.FriendSettings(friend))
                        },
                        onOpenSearchSettings = { push(Overlay.SearchSettings) },
                        onOpenMe = {
                            overlayStack.clear()
                            navForward = false
                            currentTab = 3
                        },
                        onOpenStickerShop = { push(Overlay.StickerShop) },
                        onOpenCall = { openCallGuarded(o.name, o.desc, o.id) },
                    )
                    is Overlay.Call -> com.zhiyin.ui.call.CallScreen(
                        personaName = o.name,
                        personaDesc = o.desc,
                        personaId = o.id,
                        onEnd = { pop() },
                    )
                    Overlay.Invite -> com.zhiyin.ui.contacts.InviteScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    is Overlay.Group -> GroupChatScreen(
                        groupName = o.name,
                        members = o.members,
                        onBack = { pop() },
                    )
                    Overlay.Settings -> SettingsScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenAccountSecurity = { push(Overlay.AccountSecurity) },
                        onOpenBindings = { push(Overlay.Bindings) },
                        onOpenPrivacy = { push(Overlay.Privacy) },
                        onOpenFeedback = { push(Overlay.Feedback) },
                        onOpenAbout = { push(Overlay.About) },
                        onOpenProfile = { push(Overlay.ProfileEdit) },
                        onOpenQuota = { push(Overlay.Quota) },
                        onOpenSearchSettings = { push(Overlay.SearchSettings) },
                        onOpenAnnouncements = { push(Overlay.Announcements) },
                        onOpenPreferences = { push(Overlay.Preferences) },
                        onOpenStickerShop = { push(Overlay.StickerShop) },
                        onOpenGlobalBackground = { push(Overlay.GlobalBackground) },
                    )
                    Overlay.AddFriend -> AddFriendScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenPlaza = {
                            overlayStack.clear()
                            navForward = false
                            currentTab = 0
                        },
                        onOpenCreate = { push(Overlay.PersonaEdit(null)) },
                    )
                    is Overlay.FriendSettings -> FriendSettingsScreen(
                        friend = o.friend,
                        onBack = { pop() },
                        onChanged = { appVm.loadFriends() },
                        onToast = { appVm.showToast(it) },
                        onBindWechat = { push(Overlay.WechatBind(it.id, it.name)) },
                        onOpenArtAvatars = { push(Overlay.ArtAvatars(o.friend.id, o.friend.name)) },
                    )
                    Overlay.CreateGroup -> CreateGroupScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onCreated = { name, members ->
                            overlayStack.removeAt(overlayStack.lastIndex)
                            push(Overlay.Group(name, members))
                        },
                    )
                    Overlay.ProfileEdit -> ProfileEditScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenArtAvatars = { push(Overlay.ArtAvatars(-1, null)) },
                    )
                    is Overlay.ArtAvatars -> ArtAvatarScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        contactId = o.contactId,
                        contactName = o.contactName,
                    )
                    Overlay.Wallet -> WalletScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.Subscription -> SubscriptionScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenRecharge = { push(Overlay.Recharge) },
                    )
                    Overlay.Recharge -> RechargeScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenSubscription = { push(Overlay.Subscription) },
                    )
                    Overlay.AccountSecurity -> AccountSecurityScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.Feedback -> FeedbackScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.About -> AboutScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenAboutUs = { push(Overlay.AboutUs) },
                    )
                    Overlay.AboutUs -> AboutUsScreen(
                        onBack = { pop() },
                    )
                    Overlay.Search -> SearchScreen(
                        appVm = appVm,
                        conversations = chatListVm.conversations,
                        onOpenConversation = { conv ->
                            pop()
                            if (conv.isGroup) push(Overlay.Group(conv.name, null))
                            else push(Overlay.Chat(conv.name, conv.persona, conv.friendId))
                        },
                        onOpenFriend = { friend ->
                            pop()
                            push(Overlay.Chat(friend.name, friend.persona ?: "", friend.id))
                        },
                        onBack = { pop() },
                    )
                    Overlay.Moments -> MomentsScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onCompose = { push(Overlay.MomentCompose) },
                    )
                    Overlay.Roundtable -> RoundtableScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onCreate = { push(Overlay.CreateMeeting) },
                        onOpenMeeting = { id, title ->
                            push(Overlay.RoundtableChat(id, title))
                        },
                    )
                    Overlay.CreateMeeting -> CreateMeetingScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onCreated = {
                            pop()
                        },
                    )
                    is Overlay.RoundtableChat -> RoundtableChatScreen(
                        appVm = appVm,
                        meetingId = o.meetingId,
                        onBack = { pop() },
                    )
                    Overlay.Quota -> QuotaScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.Bindings -> BindingsScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onBindWechat = { charId, charName -> push(Overlay.WechatBind(charId, charName)) },
                    )
                    is Overlay.WechatBind -> WechatBindScreen(
                        appVm = appVm,
                        characterId = o.characterId,
                        characterName = o.characterName,
                        onBack = { pop() },
                    )
                    Overlay.Privacy -> PrivacyScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.ApiKeys -> QuotaScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.SearchSettings -> SearchSettingsScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.Announcements -> AnnouncementsScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.Favorites -> FavoritesScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenChat = { sessionId ->
                            pop()
                            if (sessionId.startsWith("group_")) {
                                push(Overlay.Group(sessionId.removePrefix("group_"), null))
                            } else {
                                val name = sessionId.removePrefix("persona_")
                                val friend = appVm.friends.find { it.name == name }
                                push(Overlay.Chat(name, friend?.persona ?: "", friend?.id ?: FriendManager.findIdByName(name)))
                            }
                        },
                    )
                    Overlay.SavedImages -> SavedImagesScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.SavedFiles -> SavedFilesScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.MomentCompose -> ComposeMomentScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onPosted = {
                            overlayStack.clear()
                            push(Overlay.Moments)
                        },
                    )
                    is Overlay.PersonaDetail -> com.zhiyin.ui.discover.PersonaDetailScreen(
                        appVm = appVm,
                        personaId = o.personaId,
                        onBack = { pop() },
                        onOpenAuthor = { push(Overlay.AuthorPage(it)) },
                        onEdit = { push(Overlay.PersonaEdit(it)) },
                        onOpenChat = { name, desc, id ->
                            overlayStack.clear()
                            navForward = false
                            push(Overlay.Chat(name, desc, id))
                        },
                    )
                    Overlay.PersonaSearch -> com.zhiyin.ui.discover.PersonaSearchScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenDetail = { push(Overlay.PersonaDetail(it)) },
                    )
                    is Overlay.PersonaEdit -> com.zhiyin.ui.discover.PersonaEditScreen(
                        appVm = appVm,
                        personaId = o.personaId,
                        onBack = { pop() },
                        onSaved = { pop() },
                    )
                    Overlay.HotList -> com.zhiyin.ui.discover.HotListScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenDetail = { push(Overlay.PersonaDetail(it)) },
                    )
                    Overlay.MessageCenter -> com.zhiyin.ui.discover.MessageCenterScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onOpenPersona = { push(Overlay.PersonaDetail(it)) },
                    )
                    Overlay.StickerShop -> com.zhiyin.ui.discover.StickerShopScreen(
                        appVm = appVm,
                        onBack = { pop() },
                        onAcquired = { com.zhiyin.logic.util.StickerManager.syncDefaultPackMeta(appVm.getApplication()) },
                    )
                    is Overlay.AuthorPage -> com.zhiyin.ui.discover.AuthorScreen(
                        appVm = appVm,
                        authorId = o.userId,
                        onBack = { pop() },
                        onOpenDetail = { push(Overlay.PersonaDetail(it)) },
                    )
                    Overlay.Preferences -> com.zhiyin.ui.settings.PreferencesScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    Overlay.GlobalBackground -> GlobalBackgroundScreen(
                        appVm = appVm,
                        onBack = { pop() },
                    )
                    null -> MainContent(
                        appVm = appVm,
                        chatListVm = chatListVm,
                        currentTab = currentTab,
                        onTabChange = { currentTab = it },
                        onSearch = { push(Overlay.Search) },
                        onOpenConversation = { conv ->
                            if (conv.isGroup) {
                                push(Overlay.Group(conv.name, null))
                            } else {
                                push(Overlay.Chat(conv.name, conv.persona, conv.friendId))
                            }
                        },
                        onOpenFriend = { friend ->
                            push(Overlay.Chat(friend.name, friend.persona ?: "", friend.id))
                        },
                        onOpenFriendSettings = { friend -> push(Overlay.FriendSettings(friend)) },
                        onOpenFriendChat = { friend -> push(Overlay.Chat(friend.name, friend.persona ?: "", friend.id)) },
                        onAddFriend = { push(Overlay.AddFriend) },
                        onCreateGroup = { push(Overlay.CreateGroup) },
                        onOpenSettings = { push(Overlay.Settings) },
                        onOpenWallet = { push(Overlay.Wallet) },
                        onOpenProfile = { push(Overlay.ProfileEdit) },
                        onOpenMoments = { push(Overlay.Moments) },
                        onOpenRoundtable = { push(Overlay.Roundtable) },
                        onOpenFavorites = { push(Overlay.Favorites) },
                        onOpenSavedImages = { push(Overlay.SavedImages) },
                        onOpenInvite = { push(Overlay.Invite) },
                        onOpenSavedFiles = { push(Overlay.SavedFiles) },
                        onOpenSubscription = { push(Overlay.Subscription) },
                        onOpenRecharge = { push(Overlay.Recharge) },
                        onOpenPersonaDetail = { push(Overlay.PersonaDetail(it)) },
                        onOpenHotList = { push(Overlay.HotList) },
                        onOpenMessageCenter = { push(Overlay.MessageCenter) },
                        onOpenStickerShop = { push(Overlay.StickerShop) },
                        onOpenCreatePersona = { push(Overlay.PersonaEdit(null)) },
                        onOpenPreferences = { push(Overlay.Preferences) },
                        onOpenPlazaSearch = { push(Overlay.PersonaSearch) },
                    )
                }
            }
        }
    }
}

private data class QuickActionItem(
    val title: String,
    val icon: ImageVector,
)

// 语音通话解锁门槛信息（服务端 /api/invite/call-gate 返回）
private data class CallGateInfo(
    val validCount: Int,
    val required: Int,
    val code: String,
)

@Composable
private fun CircularActionMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var rendered by remember { mutableStateOf(visible) }
    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "circular_menu_anim",
    )

    LaunchedEffect(visible) {
        if (visible) rendered = true
    }

    LaunchedEffect(animProgress, visible) {
        if (!visible && animProgress == 0f) {
            rendered = false
        }
    }

    if (!rendered) return

    BackHandler(enabled = true) {
        onDismiss()
    }

    val actions = remember {
        listOf(
            QuickActionItem("添加朋友", Icons.Rounded.PersonAddAlt),
            QuickActionItem("发起群聊", Icons.Rounded.Groups2),
            QuickActionItem("朋友圈", Icons.Rounded.PeopleAlt),
            QuickActionItem("圆桌会议", Icons.Rounded.Forum),
            QuickActionItem("全部已读", Icons.Rounded.CheckCircle),
        )
    }

    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val origin = Offset(size.width - 48.dp.toPx(), size.height - 96.dp.toPx())
                val maxRadius = hypot(size.width, size.height) * 1.15f
                val radius = maxRadius * animProgress
                drawCircle(
                    color = Color.Black.copy(alpha = 0.52f * animProgress),
                    radius = radius,
                    center = origin,
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                actions.take(2).forEachIndexed { index, item ->
                    CircularActionButton(
                        item = item,
                        progress = animProgress,
                        delayFactor = index * 0.06f,
                        onClick = { onSelect(index) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                actions.drop(2).forEachIndexed { index, item ->
                    CircularActionButton(
                        item = item,
                        progress = animProgress,
                        delayFactor = (index + 2) * 0.06f,
                        onClick = { onSelect(index + 2) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = animProgress
                        scaleY = animProgress
                        alpha = animProgress
                    },
                shape = CircleShape,
                color = colors.surfaceVariant,
                shadowElevation = 6.dp,
                onClick = onDismiss,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = colors.onSurfaceContainerVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularActionButton(
    item: QuickActionItem,
    progress: Float,
    delayFactor: Float,
    onClick: () -> Unit,
) {
    val effectiveProgress = ((progress - delayFactor) / (1f - delayFactor)).coerceIn(0f, 1f)
    val scale = 0.2f + 0.8f * effectiveProgress
    val translationY = (1f - effectiveProgress) * 40f
    val colors = MiuixTheme.colorScheme

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = effectiveProgress
                this.translationY = translationY
            }
            .width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = colors.primaryContainer,
            shadowElevation = 6.dp,
            onClick = onClick,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
@Composable
private fun MainContent(
    appVm: AppViewModel,
    chatListVm: ChatListViewModel,
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    onSearch: () -> Unit,
    onOpenConversation: (com.zhiyin.ui.vm.UConv) -> Unit,
    onOpenFriend: (FriendManager.Friend) -> Unit,
    onOpenFriendSettings: (FriendManager.Friend) -> Unit,
    onOpenFriendChat: (FriendManager.Friend) -> Unit,
    onAddFriend: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMoments: () -> Unit,
    onOpenRoundtable: () -> Unit,
    onOpenFavorites: () -> Unit = {},
    onOpenSavedImages: () -> Unit = {},
    onOpenInvite: () -> Unit = {},
    onOpenSavedFiles: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onOpenRecharge: () -> Unit = {},
    onOpenPersonaDetail: (Int) -> Unit = {},
    onOpenHotList: () -> Unit = {},
    onOpenMessageCenter: () -> Unit = {},
    onOpenStickerShop: () -> Unit = {},
    onOpenCreatePersona: () -> Unit = {},
    onOpenPreferences: () -> Unit = {},
    onOpenPlazaSearch: () -> Unit = {},
) {
    var showAddMenu by remember { mutableStateOf(false) }
    var removeConv by remember { mutableStateOf<com.zhiyin.ui.vm.UConv?>(null) }
    val context = LocalContext.current
    val conversations = chatListVm.conversations
    val unreadTotal = conversations.sumOf { if (it.mute) 0 else it.unread }
    val navHazeState = remember { HazeState() }
    val colors = MiuixTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.surface,
            topBar = {
                when (MainTab.entries[currentTab]) {
                    MainTab.Discover -> MainTopAppBar("发现", trailing = { CheckinButton(appVm) })
                    MainTab.Chats -> MainTopAppBar(
                        title = "灵心",
                        onSearch = onSearch,
                        onMore = { showAddMenu = true },
                    )
                    MainTab.Contacts -> MainTopAppBar("联系人")
                    MainTab.Me -> MainTopAppBar("我的")
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = currentTab == 1,
                    enter = fadeIn(tween(200)) + scaleIn(
                        initialScale = 0.6f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                    exit = fadeOut(tween(150)) + scaleOut(
                        targetScale = 0.6f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                    ),
                ) {
                    FloatingActionButton(
                        onClick = { showAddMenu = true },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "新建会话", tint = colors.onPrimary)
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier.hazeEffect(
                        state = navHazeState,
                        style = HazeDefaults.style(
                            backgroundColor = colors.surfaceContainer.copy(alpha = 0.9f),
                            blurRadius = 20.dp,
                            noiseFactor = 0.06f,
                        ),
                    ),
                ) {
                    NavigationBar(
                        color = Color.Transparent,
                        showDivider = false,
                    ) {
                        MainTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = currentTab == index,
                                onClick = { onTabChange(index) },
                                icon = tab.icon,
                                label = tab.label,
                                badge = if (tab == MainTab.Chats && unreadTotal > 0) {
                                    { Badge { Text("$unreadTotal") } }
                                } else null,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .hazeSource(navHazeState),
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 * dir } +
                                fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 3 * dir } +
                                        fadeOut(tween(260)))
                    },
                    label = "tab",
                ) { tab ->
                    when (tab) {
                        0 -> com.zhiyin.ui.discover.DiscoverScreen(
                            appVm = appVm,
                            onOpenDetail = onOpenPersonaDetail,
                            onOpenHotList = onOpenHotList,
                            onOpenMessageCenter = onOpenMessageCenter,
                            onOpenCreate = onOpenCreatePersona,
                            onOpenSearch = onOpenPlazaSearch,
                        )
                        1 -> ChatListScreen(
                            conversations = conversations,
                            onConversation = onOpenConversation,
                            onRefresh = { chatListVm.refresh() },
                            onRemoveConversation = { removeConv = it },
                        )
                        2 -> ContactsScreen(
                            appVm = appVm,
                            onOpenFriendChat = onOpenFriendChat,
                            onOpenFriend = onOpenFriend,
                            onOpenFriendSettings = onOpenFriendSettings,
                            onAddFriend = onAddFriend,
                            onCreateGroup = onCreateGroup,
                            onOpenGroup = { name -> onOpenConversation(com.zhiyin.ui.vm.UConv("group_$name", name, true, -1, "", false, "", 0, 0, true)) },
                        )
                        else -> MeScreen(
                            appVm = appVm,
                            onOpenSettings = onOpenSettings,
                            onOpenWallet = onOpenWallet,
                            onOpenProfile = onOpenProfile,
                            onOpenFavorites = onOpenFavorites,
                            onOpenSavedImages = onOpenSavedImages,
                            onOpenSavedFiles = onOpenSavedFiles,
                            onOpenSubscription = onOpenSubscription,
                            onOpenRecharge = onOpenRecharge,
                            onOpenInvite = onOpenInvite,
                            onOpenPersonaDetail = onOpenPersonaDetail,
                            onOpenCreatePersona = onOpenCreatePersona,
                        )
                    }
                }
            }
        }
    }

    CircularActionMenuOverlay(
        visible = showAddMenu,
        onDismiss = { showAddMenu = false },
        onSelect = { index ->
            showAddMenu = false
            when (index) {
                0 -> onAddFriend()
                1 -> onCreateGroup()
                2 -> onOpenMoments()
                3 -> onOpenRoundtable()
                4 -> {
                    MsgRepo.markAllSessionsRead(context)
                    chatListVm.refresh()
                    appVm.showToast("全部消息已标记已读")
                }
            }
        },
    )

    removeConv?.let { conv ->
        LingXinDialog(
            show = true,
            onDismiss = { removeConv = null },
            title = "删除好友",
            text = "确定删除「${conv.name}」吗？将同时删除聊天记录",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                FriendManager.remove(
                    com.zhiyin.data.AppSession.token(),
                    conv.friendId,
                    object : com.zhiyin.logic.net.ApiGateway.Callback {
                        override fun onSuccess(response: String) {
                            MsgRepo.delete(context, "persona_" + conv.name)
                            appVm.loadFriends()
                            chatListVm.refresh()
                            appVm.showToast("已删除")
                        }

                        override fun onError(error: String?) {
                            appVm.showToast("删除失败: ${error ?: ""}")
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun MainTopAppBar(
    title: String,
    onSearch: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SmallTopAppBar(
        title = title,
        color = MiuixTheme.colorScheme.surface,
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = MiuixTheme.colorScheme.onBackground)
                }
            }
            if (onMore != null) {
                IconButton(onClick = onMore) {
                    Icon(Icons.Filled.Add, contentDescription = "新建", tint = MiuixTheme.colorScheme.onBackground)
                }
            }
            trailing?.invoke()
        },
    )
}

/**
 * 「发现」标题栏右侧的签到按钮：每天可领 3 灵心币。
 * 是否已签到以服务端为准（进入页面查一次，签到后置为已签），重复点击只给提示、不会重复发币。
 */
@Composable
private fun CheckinButton(appVm: AppViewModel) {
    var checkedIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var trigger by remember { mutableIntStateOf(0) }
    val colors = MiuixTheme.colorScheme

    LaunchedEffect(Unit) {
        AccountApi.checkinStatus().onSuccess { checkedIn = it.checkedIn }
    }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        busy = true
        AccountApi.checkin()
            .onSuccess { st ->
                checkedIn = true
                appVm.showToast(st.message.ifEmpty { "签到成功，获得 ${st.reward} 灵心币" })
            }
            .onFailure { appVm.showToast(it.message ?: "签到失败") }
        busy = false
    }

    TextButton(
        text = if (busy) "签到中" else if (checkedIn) "已签到" else "签到",
        onClick = {
            if (checkedIn) appVm.showToast("今天已经签到过了，明天再来～") else trigger++
        },
        enabled = !busy,
        modifier = Modifier.padding(end = 12.dp),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        minHeight = 32.dp,
        colors = if (checkedIn) {
            ButtonDefaults.textButtonColors(
                color = colors.surfaceContainerHigh,
                textColor = colors.onSurfaceVariantSummary,
            )
        } else {
            ButtonDefaults.textButtonColorsPrimary()
        },
    )
}

@Composable
private fun ChatListScreen(
    conversations: List<com.zhiyin.ui.vm.UConv>,
    onConversation: (com.zhiyin.ui.vm.UConv) -> Unit,
    onRefresh: () -> Unit = {},
    onRemoveConversation: (com.zhiyin.ui.vm.UConv) -> Unit = {},
) {
    RubberBandBox(
        modifier = Modifier.fillMaxSize(),
        refreshEnabled = true,
        onRefresh = onRefresh,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
        ) {
            itemsIndexed(conversations, key = { _, c -> c.key }) { index, conv ->
                ConversationRow(
                    conv = conv,
                    onOpen = { onConversation(conv) },
                    onLongPress = if (conv.isGroup) null else ({ onRemoveConversation(conv) }),
                )
                if (index < conversations.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 82.dp),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    conv: com.zhiyin.ui.vm.UConv,
    onOpen: () -> Unit,
    showTime: Boolean = true,
    showUnreadCount: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conv.isGroup) {
            GroupAvatar(size = 50.dp)
        } else {
            PersonaAvatar(contactId = conv.friendId, name = conv.name, size = 50.dp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conv.name,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                conv.lastMessage,
                fontSize = 14.sp,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (showTime && conv.time > 0) {
                Text(
                    TimeFmt.convListTime(conv.time),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantActions,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (showUnreadCount && conv.unread > 0) {
                if (conv.mute) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(8.dp)
                            .background(colors.dividerLine, CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .background(color = colors.primary, shape = RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (conv.unread > 99) "99+" else conv.unread.toString(),
                            fontSize = 11.sp,
                            color = colors.onPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun isNewerAppVersion(remote: String, local: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").removePrefix("V").split(".").map { it.trim().toIntOrNull() ?: 0 }
    val r = parts(remote)
    val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
