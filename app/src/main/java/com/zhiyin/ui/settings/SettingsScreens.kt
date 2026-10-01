package com.zhiyin.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.BuildConfig
import com.zhiyin.R
import com.zhiyin.data.AccountApi
import com.zhiyin.data.AvatarStore
import com.zhiyin.data.PrivacyManager
import com.zhiyin.data.PaymentPasswordManager
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.EmptyHint
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.ImageCropperDialog
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.UserAvatar
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProfileEditScreen(appVm: AppViewModel, onBack: () -> Unit, onOpenArtAvatars: () -> Unit) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val info = appVm.userInfo
    var nickname by remember(info) { mutableStateOf(info?.nickname ?: "") }
    var gender by remember(info) { mutableStateOf(info?.gender ?: "") }
    var birthday by remember(info) { mutableStateOf(if (info?.birthday.isNullOrEmpty()) "" else info!!.birthday) }
    var patMessage by remember {
        mutableStateOf(
            context.getSharedPreferences("zhiyin_pat", 0).getString("user_pat_message", "") ?: ""
        )
    }
    var userProfile by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var avatarCropSource by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        userProfile = AccountApi.fetchUserProfile9005()
    }

    val avatarPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = com.zhiyin.ui.chat.ContentCopy.copyToCache(context, it, "avatar")
            if (copied != null) avatarCropSource = copied.path else appVm.showToast("读取图片失败")
        }
    }

    var showBirthdayPicker by remember { mutableStateOf(false) }
    val years = remember { (1930..2026).map { "${it}年" } }
    val months = remember { (1..12).map { "${it}月" } }
    val parsedBirthday = remember(birthday) { parseBirthday(birthday) }
    var yearIdx by remember(birthday) { mutableIntStateOf((parsedBirthday?.first ?: 2000) - 1930) }
    var monthIdx by remember(birthday) { mutableIntStateOf((parsedBirthday?.second ?: 1) - 1) }
    var dayIdx by remember(birthday) { mutableIntStateOf((parsedBirthday?.third ?: 1) - 1) }
    val dayItems = remember(yearIdx, monthIdx) {
        val maxDay = daysInMonth(1930 + yearIdx, monthIdx + 1)
        (1..maxDay).map { "${it}日" }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "个人资料",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("头像", modifier = Modifier.weight(1f), color = colors.onSurface)
                    Box(modifier = Modifier.clickable { avatarPick.launch("image/*") }) {
                        UserAvatar(avatarUrl = info?.avatar, size = 60.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.primaryContainer,
                        modifier = Modifier.clickable { onOpenArtAvatars() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("选插画头像", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
                        }
                    }
                }

                TextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = "昵称",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("性别", modifier = Modifier.weight(1f), color = colors.onSurface)
                    listOf("" to "未设置", "男" to "男", "女" to "女").forEach { (value, label) ->
                        val selected = gender == value
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) colors.primary else colors.surfaceContainerHigh,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { gender = value },
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) colors.onPrimary else colors.onSurface,
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { showBirthdayPicker = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            birthday.ifEmpty { "生日（选填）" },
                            fontSize = 14.sp,
                            color = if (birthday.isEmpty()) colors.onSurfaceContainerVariant else colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.onSurfaceContainerVariant,
                        )
                    }
                }

                TextField(
                    value = patMessage,
                    onValueChange = { patMessage = it },
                    label = "我的拍一拍文案",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )

                TextField(
                    value = userProfile,
                    onValueChange = { userProfile = it },
                    label = "我的个人设定（仅供 AI 了解你）",
                    useLabelAsPlaceholder = true,
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )

                var sponsorNote by remember { mutableStateOf("") }
                var sponsorQr by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val resp = com.zhiyin.logic.net.ApiGateway.requestSync(
                                com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE + "/api/config/sponsor-config", "GET", null,
                                com.zhiyin.logic.net.ApiGateway.getToken(context)
                            )
                            val json = org.json.JSONObject(resp)
                            sponsorQr = json.optString("qrcode", "")
                            sponsorNote = json.optString("note", "感谢你的支持！")
                        } catch (_: Exception) {
                        }
                    }
                }
                if (sponsorQr.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("赞助支持", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                sponsorNote,
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(12.dp))
                            com.zhiyin.ui.moments.WebImage(
                                url = sponsorQr,
                                contentDescription = "赞助二维码",
                                modifier = Modifier.size(160.dp).clip(RoundedCornerShape(14.dp)),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        saving = true
                        context.getSharedPreferences("zhiyin_pat", 0)
                            .edit().putString("user_pat_message", patMessage.trim()).apply()
                        kotlinx.coroutines.MainScope().launch {
                            AccountApi.saveUserInfo(nickname.trim(), gender, birthday.trim(), userProfile.trim())
                                .onSuccess {
                                    appVm.showToast("保存成功")
                                    appVm.refreshUser()
                                    onBack()
                                }
                                .onFailure { appVm.showToast(it.message ?: "保存失败") }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    cornerRadius = 25.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showBirthdayPicker) {
        BirthdayWheelDialog(
            years = years,
            months = months,
            days = dayItems,
            yearIdx = yearIdx,
            monthIdx = monthIdx,
            dayIdx = dayIdx,
            hasValue = birthday.isNotEmpty(),
            onYear = {
                yearIdx = it
                dayIdx = dayIdx.coerceAtMost(daysInMonth(1930 + yearIdx, monthIdx + 1) - 1)
            },
            onMonth = {
                monthIdx = it
                dayIdx = dayIdx.coerceAtMost(daysInMonth(1930 + yearIdx, monthIdx + 1) - 1)
            },
            onDay = { dayIdx = it },
            onClear = {
                birthday = ""
                showBirthdayPicker = false
            },
            onDismiss = { showBirthdayPicker = false },
            onConfirm = {
                birthday = "%04d-%02d-%02d".format(1930 + yearIdx, monthIdx + 1, dayIdx + 1)
                showBirthdayPicker = false
            },
        )
    }

    avatarCropSource?.let { path ->
        ImageCropperDialog(
            path = path,
            frameAspect = 1f,
            onConfirm = { bmp ->
                avatarCropSource = null
                appVm.showToast("上传中…")
                val baos = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
                AvatarStore.uploadUserAvatar(context, baos.toByteArray()) { ok, err ->
                    if (ok) {
                        appVm.showToast("头像已更新")
                        appVm.refreshUser()
                    } else appVm.showToast("上传失败: $err")
                }
            },
            onCancel = { avatarCropSource = null },
        )
    }
}

@Composable
fun WalletScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    var loading by remember { mutableStateOf(true) }
    var wallet by remember { mutableStateOf<AccountApi.WalletData?>(null) }
    var payEnabled by remember { mutableStateOf(PaymentPasswordManager.isSet()) }
    var payPassSetup by remember { mutableStateOf(false) }
    var coinBalance by remember { mutableStateOf(0.0) }
    var showExchange by remember { mutableStateOf(false) }

    LaunchedEffect(showExchange) {
        AccountApi.wallet()
            .onSuccess { wallet = it }
            .onFailure { appVm.showToast("加载失败: ${it.message}") }
        AccountApi.checkinStatus().onSuccess { coinBalance = it.coins }
        loading = false
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "灵心钱包",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "余额（启辰币）",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "$" + fmtMoney(wallet?.balance ?: 0.0),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                    }

                    val txs = wallet?.transactions ?: emptyList()
                    if (txs.isEmpty()) {
                        EmptyHint("暂无交易记录，去给TA发个转账或红包吧～")
                    } else {
                        CardContainer {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                txs.forEach { tx ->
                                    WalletTxRow(tx)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    CardContainer {
                        IconActionRow(
                            icon = Icons.Rounded.SwapHoriz,
                            title = "兑换",
                            summary = "1 灵心币 = 800 余额 · 1000 余额 = 1 灵心币",
                            onClick = { showExchange = true },
                        )
                    }
                    CardContainer {
                        IconActionRow(
                            icon = Icons.Rounded.Key,
                            title = "支付密码",
                            summary = if (payEnabled) "已设置（转账前需验证）" else "未设置（免密转账）",
                            onClick = { payPassSetup = true },
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (payPassSetup) {
        PayPasswordManageDialog(
            onDismiss = { payPassSetup = false },
            onChanged = { payEnabled = PaymentPasswordManager.isSet() },
        )
    }

    if (showExchange) {
        ExchangeDialog(
            coinBalance = coinBalance,
            walletBalance = wallet?.balance ?: 0.0,
            onDismiss = { showExchange = false },
        )
    }
}

@Composable
private fun ExchangeDialog(
    coinBalance: Double,
    walletBalance: Double,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var direction by remember { mutableStateOf("coin2cash") }
    var amountText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf("") }
    var okDone by remember { mutableStateOf(false) }

    val amount = amountText.toIntOrNull() ?: 0
    val isCoin2Cash = direction == "coin2cash"
    val preview = if (isCoin2Cash) "可得到 ${amount * 800} 钱包余额" else "将扣除 ${amount * 1000} 钱包余额"

    LingXinDialog(
        onDismiss = { if (!busy) onDismiss() },
        title = "兑换",
        confirmText = if (busy) "兑换中…" else "确认兑换",
        onConfirm = {
            if (!busy) {
                if (amount <= 0) {
                    resultMsg = "请输入大于 0 的整数"
                } else {
                    busy = true
                    resultMsg = ""
                    scope.launch {
                        AccountApi.exchange(direction, amount)
                            .onSuccess {
                                okDone = true
                                resultMsg = it.message.ifEmpty { "兑换成功" }
                            }
                            .onFailure {
                                okDone = false
                                resultMsg = it.message ?: "兑换失败"
                            }
                        busy = false
                    }
                }
            }
        },
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("coin2cash" to "灵心币 → 余额", "cash2coin" to "余额 → 灵心币").forEach { (v, label) ->
                val selected = direction == v
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) colors.primary else colors.surfaceContainer,
                    modifier = Modifier.weight(1f).clickable(enabled = !busy) {
                        direction = v
                        resultMsg = ""
                    },
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = if (selected) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        TextField(
            value = amountText,
            onValueChange = { s -> amountText = s.filter { it.isDigit() }.take(6) },
            label = "要兑换的灵心币数量",
            useLabelAsPlaceholder = true,
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (amount > 0) preview else "1 灵心币 = 800 余额 · 1000 余额 = 1 灵心币",
            fontSize = 12.sp,
            color = colors.onSurfaceVariantActions,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "当前：${fmtMoney(coinBalance)} 灵心币 · ${fmtMoney(walletBalance)} 钱包余额",
            fontSize = 12.sp,
            color = colors.onSurfaceVariantActions,
        )
        if (resultMsg.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                resultMsg,
                fontSize = 12.sp,
                color = if (okDone) Color(0xFF34B78F) else colors.error,
            )
        }
    }
}

@Composable
private fun WalletTxRow(tx: AccountApi.WalletTx) {
    val colors = MiuixTheme.colorScheme
    val (title, icon, tint) = when (tx.type) {
        "transfer_out" -> Triple(if (tx.persona.isEmpty()) "转账" else "转账给 ${tx.persona}", Icons.Rounded.SwapHoriz, Color(0xFFF5A623))
        "redpacket_out" -> Triple(if (tx.note.isEmpty()) "发红包" else "发红包 ${tx.note}", Icons.Rounded.Redeem, Color(0xFFE85D4A))
        else -> Triple("收入", Icons.Rounded.Savings, Color(0xFF34B78F))
    }
    val sign = if (tx.type == "transfer_out" || tx.type == "redpacket_out") "-" else "+"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(tx.time, fontSize = 12.sp, color = colors.onSurfaceVariantActions)
        }
        Text(
            "$sign$${fmtMoney(tx.amount)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (sign == "-") colors.onSurface else Color(0xFF34B78F),
        )
    }
}

@Composable
private fun IconActionRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 12.sp, color = colors.onSurfaceVariantActions)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariantActions,
            )
        }
    }
}

private fun fmtMoney(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
    else (Math.round(v * 100) / 100.0).toString()

@Composable
private fun PayPasswordManageDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val enabled = PaymentPasswordManager.isSet()
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LingXinDialog(
        onDismiss = onDismiss,
        title = if (enabled) "修改支付密码" else "设置支付密码",
        text = if (enabled) "支持随时修改或清除支付密码。" else "设置后转账/发红包前需验证支付密码，也可改用免密。",
        confirmText = if (enabled) "保存修改" else "设置",
        dismissText = "取消",
        onConfirm = {
            when {
                enabled && !PaymentPasswordManager.verify(oldPin) -> error = "原密码错误"
                newPin.length < 4 || !newPin.all { it.isDigit() } -> error = "请输入至少4位数字密码"
                newPin != confirmPin -> error = "两次输入的新密码不一致"
                else -> {
                    PaymentPasswordManager.set(newPin)
                    onChanged()
                    onDismiss()
                }
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        if (enabled) {
            TextField(
                value = oldPin,
                onValueChange = { oldPin = it.filter { ch -> ch.isDigit() }.take(8); error = null },
                label = "当前支付密码",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }
        TextField(
            value = newPin,
            onValueChange = { newPin = it.filter { ch -> ch.isDigit() }.take(8); error = null },
            label = "新支付密码（至少4位）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = confirmPin,
            onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() }.take(8); error = null },
            label = "再次输入新密码",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = colors.error, fontSize = 13.sp)
        }
        if (enabled) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "清除密码，改为免密转账",
                onClick = {
                    PaymentPasswordManager.clear()
                    onChanged()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    color = colors.error,
                    textColor = colors.onError,
                ),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun AccountSecurityScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val info = appVm.userInfo

    var realName by remember { mutableStateOf<AccountApi.RealNameStatus?>(null) }
    var rnName by remember { mutableStateOf("") }
    var rnId by remember { mutableStateOf("") }
    var rnBusy by remember { mutableStateOf(false) }

    var rpPhone by remember(info) { mutableStateOf(info?.phone ?: "") }
    var rpCode by remember { mutableStateOf("") }
    var rpPwd by remember { mutableStateOf("") }
    var rpCountdown by remember { mutableStateOf(0) }
    var rpSendBusy by remember { mutableStateOf(false) }
    var rpSubmitBusy by remember { mutableStateOf(false) }

    var cpPhone by remember { mutableStateOf("") }
    var cpCode by remember { mutableStateOf("") }
    var cpCountdown by remember { mutableStateOf(0) }
    var cpSendBusy by remember { mutableStateOf(false) }
    var cpSubmitBusy by remember { mutableStateOf(false) }

    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var newPwd2 by remember { mutableStateOf("") }
    var pwdBusy by remember { mutableStateOf(false) }

    var captchaShow by remember { mutableStateOf(false) }
    var captchaBmp by remember { mutableStateOf<Bitmap?>(null) }
    var captchaInput by remember { mutableStateOf("") }
    var captchaTick by remember { mutableIntStateOf(0) }
    var captchaCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun promptCaptcha(onCode: (String) -> Unit) {
        captchaInput = ""
        captchaBmp = null
        captchaCallback = onCode
        captchaTick += 1
        captchaShow = true
    }

    fun reloadRealName() {
        scope.launch { realName = AccountApi.realNameStatus() }
    }

    LaunchedEffect(Unit) {
        appVm.refreshUser()
        realName = AccountApi.realNameStatus()
    }
    LaunchedEffect(rpCountdown) {
        if (rpCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            rpCountdown -= 1
        }
    }
    LaunchedEffect(cpCountdown) {
        if (cpCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            cpCountdown -= 1
        }
    }
    LaunchedEffect(captchaShow, captchaTick) {
        if (captchaShow) captchaBmp = AccountApi.captchaBitmap()
    }

    val rpBound = !(info?.phone.isNullOrEmpty())

    var subPage by remember { mutableStateOf<SecuritySubPage?>(null) }
    BackHandler(enabled = subPage != null) { subPage = null }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = subPage?.title ?: "账号与安全",
                color = colors.surface,
                navigationIcon = {
                    BackButton(onClick = { if (subPage != null) subPage = null else onBack() })
                },
            )
        },
    ) { padding ->
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = subPage,
                    transitionSpec = {
                        val forward = initialState == null
                        val slideSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                        val fadeSpec = tween<Float>(280, easing = FastOutSlowInEasing)
                        (slideInHorizontally(slideSpec) { if (forward) it else -it / 3 } + fadeIn(fadeSpec)) togetherWith
                            (slideOutHorizontally(slideSpec) { if (forward) -it / 3 else it } + fadeOut(fadeSpec))
                    },
                    label = "securitySub",
                ) { page ->
                    when (page) {
                        SecuritySubPage.RealName -> Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        ) {
                            CardContainer {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("实名认证", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = when {
                                            realName == null -> "加载中…"
                                            realName!!.verified ->
                                                (if (realName!!.age >= 18) "已认证 (成人)" else "已认证 (未成年模式)") +
                                                    if (realName!!.realName.isNotEmpty()) " - " + realName!!.realName else ""
                                            else -> "未认证"
                                        },
                                        fontSize = 14.sp,
                                        color = if (realName?.verified == true) Color(0xFF07C160) else colors.onSurfaceVariantSummary,
                                    )
                                    if (realName != null && !realName!!.verified) {
                                        Spacer(Modifier.height(10.dp))
                                        PwdField(rnName, { rnName = it }, "真实姓名")
                                        PwdField(rnId, { rnId = it }, "身份证号")
                                        Spacer(Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                when {
                                                    rnName.trim().isEmpty() -> appVm.showToast("请输入姓名")
                                                    rnId.trim().isEmpty() -> appVm.showToast("请输入身份证号")
                                                    !rnId.trim().matches(Regex("^\\d{17}[\\dXx]$")) -> appVm.showToast("身份证号格式不正确")
                                                    else -> {
                                                        rnBusy = true
                                                        scope.launch {
                                                            AccountApi.verifyRealName(rnName.trim(), rnId.trim())
                                                                .onSuccess {
                                                                    appVm.showToast("实名认证通过")
                                                                    rnName = ""
                                                                    rnId = ""
                                                                    reloadRealName()
                                                                }
                                                                .onFailure { appVm.showToast(it.message ?: "认证失败") }
                                                            rnBusy = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !rnBusy,
                                            cornerRadius = 23.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(46.dp),
                                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        ) {
                                            if (rnBusy) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text("提交认证", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SecuritySubPage.Password -> Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        ) {
                            CardContainer {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("密码管理", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "通过旧密码修改",
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceVariantActions,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    PwdField(oldPwd, { oldPwd = it }, "旧密码")
                                    PwdField(newPwd, { newPwd = it }, "新密码（至少6位）")
                                    PwdField(newPwd2, { newPwd2 = it }, "确认新密码")
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            when {
                                                oldPwd.isEmpty() -> appVm.showToast("请输入旧密码")
                                                newPwd.isEmpty() -> appVm.showToast("请输入新密码")
                                                newPwd.length < 6 -> appVm.showToast("新密码至少6位")
                                                newPwd != newPwd2 -> appVm.showToast("两次密码不一致")
                                                oldPwd == newPwd -> appVm.showToast("新密码不能与旧密码相同")
                                                else -> promptCaptcha { code ->
                                                    pwdBusy = true
                                                    scope.launch {
                                                        AccountApi.changePasswordByOld(oldPwd, newPwd, code)
                                                            .onSuccess {
                                                                appVm.showToast("密码修改成功")
                                                                oldPwd = ""
                                                                newPwd = ""
                                                                newPwd2 = ""
                                                            }
                                                            .onFailure { appVm.showToast(it.message ?: "修改失败") }
                                                        pwdBusy = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !pwdBusy,
                                        cornerRadius = 23.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    ) {
                                        if (pwdBusy) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("确认修改", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 14.dp),
                                        color = colors.dividerLine.copy(alpha = 0.4f),
                                        thickness = 0.5.dp,
                                    )
                                    Text(
                                        "通过短信验证码重置",
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceVariantActions,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (rpBound) "已绑定手机号 ${info?.phoneMasked.orEmpty()}，输入验证码可重置密码" else "内测账号，未绑定手机号",
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                    if (rpBound) {
                                        Spacer(Modifier.height(10.dp))
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = colors.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                rpPhone.ifEmpty { "手机号" },
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
                                                fontSize = 14.sp,
                                                color = colors.onSurfaceContainerVariant,
                                            )
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextField(
                                                value = rpCode,
                                                onValueChange = { rpCode = it },
                                                label = "短信验证码",
                                                useLabelAsPlaceholder = true,
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            TextButton(
                                                text = if (rpCountdown > 0) "${rpCountdown}s" else "获取验证码",
                                                onClick = {
                                                    promptCaptcha { code ->
                                                        rpSendBusy = true
                                                        scope.launch {
                                                            AccountApi.sendSmsCode(rpPhone, "reset_password", code)
                                                                .onSuccess {
                                                                    appVm.showToast("验证码已发送")
                                                                    rpCountdown = 60
                                                                }
                                                                .onFailure { appVm.showToast(it.message ?: "发送失败") }
                                                            rpSendBusy = false
                                                        }
                                                    }
                                                },
                                                enabled = rpCountdown <= 0 && !rpSendBusy,
                                                modifier = Modifier.height(56.dp),
                                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                            )
                                        }
                                        PwdField(rpPwd, { rpPwd = it }, "新密码（至少6位）")
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                when {
                                                    rpCode.trim().isEmpty() -> appVm.showToast("请输入短信验证码")
                                                    rpPwd.isEmpty() -> appVm.showToast("请输入新密码")
                                                    rpPwd.length < 6 -> appVm.showToast("密码至少6位")
                                                    else -> {
                                                        rpSubmitBusy = true
                                                        scope.launch {
                                                            AccountApi.resetPassword(rpPhone, rpCode.trim(), rpPwd)
                                                                .onSuccess {
                                                                    appVm.showToast("密码重置成功")
                                                                    rpCode = ""
                                                                    rpPwd = ""
                                                                }
                                                                .onFailure { appVm.showToast(it.message ?: "重置失败") }
                                                            rpSubmitBusy = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !rpSubmitBusy,
                                            cornerRadius = 23.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(46.dp),
                                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        ) {
                                            if (rpSubmitBusy) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text("重置密码", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SecuritySubPage.Phone -> Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        ) {
                            CardContainer {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("换绑手机号", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (rpBound) "当前手机号：${info?.phoneMasked.orEmpty()}，请输入新手机号获取验证码" else "内测账号，可绑定手机号",
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    PwdField(cpPhone, { cpPhone = it }, "新手机号")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextField(
                                            value = cpCode,
                                            onValueChange = { cpCode = it },
                                            label = "短信验证码",
                                            useLabelAsPlaceholder = true,
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        TextButton(
                                            text = if (cpCountdown > 0) "${cpCountdown}s" else "获取验证码",
                                            onClick = {
                                                if (!cpPhone.trim().matches(Regex("^1[3-9]\\d{9}$"))) {
                                                    appVm.showToast("请输入正确的手机号")
                                                    return@TextButton
                                                }
                                                promptCaptcha { code ->
                                                    cpSendBusy = true
                                                    scope.launch {
                                                        AccountApi.sendSmsCode(cpPhone.trim(), "bind_phone", code)
                                                            .onSuccess {
                                                                appVm.showToast("验证码已发送")
                                                                cpCountdown = 60
                                                            }
                                                            .onFailure { appVm.showToast(it.message ?: "发送失败") }
                                                        cpSendBusy = false
                                                    }
                                                }
                                            },
                                            enabled = cpCountdown <= 0 && !cpSendBusy,
                                            modifier = Modifier.height(56.dp),
                                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            when {
                                                cpPhone.trim().isEmpty() || cpCode.trim().isEmpty() -> appVm.showToast("请输入手机号和验证码")
                                                else -> {
                                                    cpSubmitBusy = true
                                                    scope.launch {
                                                        AccountApi.changePhone(cpPhone.trim(), cpCode.trim())
                                                            .onSuccess {
                                                                appVm.showToast("手机号已更换为 ${cpPhone.trim()}")
                                                                cpPhone = ""
                                                                cpCode = ""
                                                                appVm.refreshUser()
                                                            }
                                                            .onFailure { appVm.showToast(it.message ?: "换绑失败") }
                                                        cpSubmitBusy = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !cpSubmitBusy,
                                        cornerRadius = 23.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    ) {
                                        if (cpSubmitBusy) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("确认换绑", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                                        }
                                    }
                                }
                            }
                        }
                        null -> Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        ) {
                            CardContainer {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Row {
                                        Text("账号", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            info?.username ?: com.zhiyin.data.AppSession.username(),
                                            fontSize = 14.sp,
                                            color = colors.onSurface,
                                        )
                                    }
                                    if (!info?.phoneMasked.isNullOrEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Row {
                                            Text("手机号", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                                            Spacer(Modifier.width(16.dp))
                                            Text(info!!.phoneMasked, fontSize = 14.sp, color = colors.onSurface)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            CardContainer {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    SecurityEntry(
                                        title = "实名认证",
                                        supporting = when {
                                            realName == null -> "加载中…"
                                            realName!!.verified -> "已认证" + if (realName!!.realName.isNotEmpty()) " · " + realName!!.realName else ""
                                            else -> "未认证，点击前往认证"
                                        },
                                        onClick = { subPage = SecuritySubPage.RealName },
                                    )
                                    RowDivider()
                                    SecurityEntry(
                                        title = "密码管理",
                                        supporting = "旧密码修改 / 短信验证码重置",
                                        onClick = { subPage = SecuritySubPage.Password },
                                    )
                                    RowDivider()
                                    SecurityEntry(
                                        title = "手机绑定",
                                        supporting = if (rpBound) "当前手机号 ${info?.phoneMasked.orEmpty()}" else "未绑定手机号，点击绑定",
                                        onClick = { subPage = SecuritySubPage.Phone },
                                    )
                                }
                            }

                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }

    if (captchaShow) {
        LingXinDialog(
            onDismiss = { captchaShow = false },
            title = "人机验证",
            confirmText = "确认",
            dismissText = "取消",
            onConfirm = {
                val code = captchaInput.trim()
                if (code.isEmpty()) {
                    appVm.showToast("请输入验证码")
                } else {
                    captchaShow = false
                    captchaCallback?.invoke(code)
                }
            },
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .clickable { captchaTick += 1 },
                contentAlignment = Alignment.Center,
            ) {
                if (captchaBmp != null) {
                    Image(
                        bitmap = captchaBmp!!.asImageBitmap(),
                        contentDescription = "验证码，点击刷新",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            PwdField(captchaInput, { captchaInput = it }, "请输入图片中验证码")
        }
    }
}

private enum class SecuritySubPage(val title: String) {
    RealName("实名认证"),
    Password("密码管理"),
    Phone("手机绑定"),
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
    )
}

@Composable
private fun SecurityEntry(title: String, supporting: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                supporting,
                fontSize = 12.sp,
                color = colors.onSurfaceVariantActions,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun PwdField(value: String, onValueChange: (String) -> Unit, label: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

@Composable
fun FeedbackScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    var items by remember { mutableStateOf<List<AccountApi.FeedbackItem>>(emptyList()) }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        loading = true
        kotlinx.coroutines.MainScope().launch {
            items = AccountApi.feedbackList()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "帮助与反馈",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "说说你遇到的问题或建议…",
                    useLabelAsPlaceholder = true,
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
                Button(
                    onClick = {
                        if (content.isBlank()) {
                            appVm.showToast("请输入反馈内容")
                            return@Button
                        }
                        kotlinx.coroutines.MainScope().launch {
                            AccountApi.submitFeedback(content.trim())
                                .onSuccess {
                                    appVm.showToast("反馈已提交，感谢你的建议")
                                    content = ""
                                    reload()
                                }
                                .onFailure { appVm.showToast(it.message ?: "提交失败") }
                        }
                    },
                    cornerRadius = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Text("提交反馈", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "历史反馈",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                } else if (items.isEmpty()) {
                    EmptyHint("暂无历史反馈")
                }
                items.forEach { fb ->
                    CardContainer {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(fb.content, fontSize = 14.sp, color = colors.onSurface)
                            if (fb.reply.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.primaryContainer.copy(alpha = 0.4f),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            "官方回复",
                                            fontSize = 12.sp,
                                            color = colors.primary,
                                        )
                                        Text(fb.reply, fontSize = 13.sp, color = colors.onSurface)
                                    }
                                }
                            }
                            if (fb.time.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(fb.time, fontSize = 12.sp, color = colors.onSurfaceVariantActions)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun AboutScreen(appVm: AppViewModel, onBack: () -> Unit, onOpenAboutUs: () -> Unit) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
    var checking by remember { mutableStateOf(false) }
    var newVersion by remember { mutableStateOf<AccountApi.VersionInfo?>(null) }
    var upToDate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "关于灵心",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(32.dp))
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = "灵心",
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(6.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "灵心",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        "版本号 $versionName",
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "内部版本号 ${BuildConfig.BUILD_BATCH}",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariantActions,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "厦门市月之星外信息技术有限公司 保留所有权利",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantActions,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Text(
                    "版本为内部测试版，不代表最终品质",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantActions,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
                CardContainer {
                    IconActionRow(
                        icon = Icons.Rounded.Business,
                        title = "关于我们",
                        onClick = onOpenAboutUs,
                    )
                }
                CardContainer {
                    IconActionRow(
                        icon = Icons.Rounded.CloudDownload,
                        title = "检查更新",
                        onClick = {
                            checking = true
                            kotlinx.coroutines.MainScope().launch {
                                val latest = AccountApi.versionLatest()
                                checking = false
                                if (latest != null && latest.version.isNotEmpty() && latest.version != versionName) {
                                    newVersion = latest
                                } else {
                                    upToDate = true
                                }
                            }
                        },
                        trailing = {
                            if (checking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.onSurfaceVariantActions,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (newVersion != null) {
        val v = newVersion!!
        LingXinDialog(
            onDismiss = { newVersion = null },
            title = "发现新版本 ${v.version}",
            text = v.changelog.ifEmpty { "修复已知问题，优化使用体验" },
            confirmText = "去下载",
            dismissText = "以后再说",
            onConfirm = {
                newVersion = null
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(v.apkUrl))
                    )
                } catch (_: Exception) {
                    appVm.showToast("无法打开下载链接")
                }
            },
        )
    }
    if (upToDate) {
        LingXinDialog(
            onDismiss = { upToDate = false },
            title = "检查更新",
            text = "当前已是最新版本 v$versionName",
            confirmText = "好的",
            dismissText = null,
            onConfirm = { upToDate = false },
        )
    }
}

@Composable
fun AboutUsScreen(onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "关于我们",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
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
                Spacer(Modifier.height(12.dp))
                CardContainer {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("软件开发者", fontSize = 16.sp, color = colors.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text("厦门市月之星外信息技术有限公司", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "月之星外技术部门",
                        fontSize = 14.sp,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "技术部门",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariantActions,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "营业执照",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantActions,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                val licensePainter = painterResource(R.drawable.license_20260901213327)
                val licenseAspect = licensePainter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 1.4f }
                Image(
                    painter = licensePainter,
                    contentDescription = "营业执照",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .aspectRatio(licenseAspect)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, colors.outline, RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )

                Text(
                    "本软件由厦门市月之星外信息技术有限公司持有",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantActions,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
    }
}

@Composable
fun PrivacyScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    var secureOn by remember { mutableStateOf(PrivacyManager.secureFlagOn) }
    var lockOn by remember { mutableStateOf(PrivacyManager.isLockEnabled()) }
    var bioOn by remember { mutableStateOf(PrivacyManager.isBiometricEnabled()) }
    val bioAvailable = remember { PrivacyManager.canBiometric() }
    var showSetup by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "隐私保护",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
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
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("防截屏与防录屏", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "开启后禁止对本应用截屏与录屏，最近任务中的预览也会被隐藏",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("禁止截屏 / 录屏", modifier = Modifier.weight(1f), fontSize = 16.sp, color = colors.onSurface)
                            Switch(
                                checked = secureOn,
                                onCheckedChange = {
                                    PrivacyManager.setSecureFlag(it)
                                    secureOn = it
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                CardContainer {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("访问验证", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (lockOn) "已开启 · 打开或返回应用时需要验证身份\n开启后无法关闭，卸载并重装应用才会重置"
                            else "开启后打开或返回应用时需要验证身份，防止他人查看你的对话",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("启动验证", modifier = Modifier.weight(1f), fontSize = 16.sp, color = colors.onSurface)
                            Switch(
                                checked = lockOn,
                                onCheckedChange = {
                                    if (lockOn) appVm.showToast("访问验证开启后无法关闭，卸载并重装应用才会重置")
                                    else showSetup = true
                                },
                            )
                        }
                        if (lockOn) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = colors.dividerLine.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("生物识别解锁", fontSize = 16.sp, color = colors.onSurface)
                                    Text(
                                        if (bioAvailable) "指纹 / 面容验证作为快捷解锁方式"
                                        else "当前设备不支持或未录入生物信息",
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceVariantActions,
                                    )
                                }
                                Switch(
                                    checked = bioOn,
                                    enabled = bioAvailable,
                                    onCheckedChange = {
                                        PrivacyManager.setBiometric(it)
                                        bioOn = it
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "注意：访问验证开启后无法关闭，也无法重置密码；忘记密码或无法通过验证时，仅能通过卸载并重装应用来重置。",
                            fontSize = 12.sp,
                            color = colors.error.copy(alpha = 0.8f),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showSetup) {
        LingXinDialog(
            onDismiss = {
                showSetup = false
                newPin = ""
                confirmPin = ""
            },
            title = "设置访问密码",
            confirmText = "开启",
            onConfirm = {
                when {
                    newPin.length < 4 || !newPin.all { it.isDigit() } -> appVm.showToast("请输入至少4位数字密码")
                    newPin != confirmPin -> appVm.showToast("两次输入的密码不一致")
                    else -> {
                        PrivacyManager.enable(newPin, bioAvailable)
                        lockOn = true
                        bioOn = PrivacyManager.isBiometricEnabled()
                        showSetup = false
                        newPin = ""
                        confirmPin = ""
                        appVm.showToast("访问验证已开启")
                    }
                }
            },
        ) {
            Spacer(Modifier.height(12.dp))
            TextField(
                value = newPin,
                onValueChange = { newPin = it.filter { ch -> ch.isDigit() }.take(8) },
                label = "数字密码（至少4位）",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() }.take(8) },
                label = "确认数字密码",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun RealNameVerifyDialog(
    onVerified: () -> Unit,
    onToast: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    LingXinDialog(
        onDismiss = {},
        title = "实名认证",
        text = "根据相关规定，使用前需完成实名认证",
        confirmText = if (submitting) "提交中…" else "提交认证",
        dismissText = null,
        dismissible = false,
        onConfirm = {
            val n = name.trim()
            val id = idNumber.trim()
            when {
                n.isEmpty() || id.isEmpty() -> error = "请填写姓名和身份证号"
                !id.matches(Regex("\\d{17}[\\dXx]")) -> error = "身份证号格式不正确"
                else -> {
                    submitting = true
                    kotlinx.coroutines.MainScope().launch {
                        AccountApi.verifyRealName(n, id)
                            .onSuccess {
                                onToast("实名认证通过")
                                onVerified()
                            }
                            .onFailure {
                                submitting = false
                                error = it.message
                            }
                    }
                }
            }
        },
    ) {
        Spacer(Modifier.height(16.dp))
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "请输入真实姓名",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = idNumber,
            onValueChange = { idNumber = it.uppercase() },
            label = "请输入身份证号",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = colors.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BirthdayWheelDialog(
    years: List<String>,
    months: List<String>,
    days: List<String>,
    yearIdx: Int,
    monthIdx: Int,
    dayIdx: Int,
    hasValue: Boolean,
    onYear: (Int) -> Unit,
    onMonth: (Int) -> Unit,
    onDay: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    LingXinDialog(
        onDismiss = onDismiss,
        title = "选择生日",
        confirmText = "确定",
        onConfirm = onConfirm,
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WheelColumn(years, yearIdx, onYear, Modifier.weight(1f))
            WheelColumn(months, monthIdx, onMonth, Modifier.weight(1f))
            WheelColumn(days, dayIdx, onDay, Modifier.weight(1f))
        }
        if (hasValue) {
            TextButton(
                text = "清除生日",
                onClick = onClear,
                colors = ButtonDefaults.textButtonColors(
                    color = colors.error,
                    textColor = colors.onError,
                ),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                minHeight = 44.dp,
            )
        }
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            listState.centerOn(selectedIndex.coerceIn(0, items.lastIndex), animated = false)
        }
    }

    LaunchedEffect(listState) {
        var lastSettled = -1
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .drop(1)
            .collect {
                val layout = listState.layoutInfo
                if (layout.visibleItemsInfo.isEmpty()) return@collect
                val center = layout.viewportStartOffset + layout.viewportSize.height / 2f
                val nearest = layout.visibleItemsInfo.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2f) - center)
                } ?: return@collect
                if (nearest.index == lastSettled) return@collect
                lastSettled = nearest.index
                val delta = nearest.offset + nearest.size / 2f - center
                scope.launch {
                    listState.animateScrollBy(delta)
                }
                onSelected(nearest.index)
            }
    }

    Box(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 80.dp),
        ) {
            items(items.size) { i ->
                val selected = i == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable {
                            scope.launch {
                                listState.centerOn(i, animated = true)
                                onSelected(i)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        items[i],
                        fontSize = if (selected) 16.sp else 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) colors.primary
                        else colors.onSurfaceContainerVariant,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.Center)
                .background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
        )
    }
}

private suspend fun LazyListState.centerOn(index: Int, animated: Boolean) {
    if (animated) animateScrollToItem(index, 0) else scrollToItem(index, 0)
    val infos = snapshotFlow { layoutInfo.visibleItemsInfo }
        .first { list -> list.any { it.index == index } }
    val info = infos.first { it.index == index }
    val center = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2f
    val delta = info.offset + info.size / 2f - center
    if (animated) animateScrollBy(delta) else scrollBy(delta)
}

private fun parseBirthday(value: String): Triple<Int, Int, Int>? {
    val m = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(value) ?: return null
    return Triple(
        m.groupValues[1].toInt(),
        m.groupValues[2].toInt(),
        m.groupValues[3].toInt(),
    )
}

private fun daysInMonth(year: Int, month: Int): Int {
    val cal = java.util.Calendar.getInstance()
    cal.set(year, month - 1, 1)
    return cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
}
