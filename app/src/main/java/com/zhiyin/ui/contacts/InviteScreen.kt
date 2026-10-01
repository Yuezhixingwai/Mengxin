package com.zhiyin.ui.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.R
import com.zhiyin.data.AppSession
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 邀请好友页：好友列表 Banner 点进来。
 * 顶部海报图 → 我的专属6位邀请码（可复制）→ 填写他人邀请码（每人只能填一次）。
 * 绑定成功后邀请人与被邀请人各得 5 灵心币（服务端 /api/invite）。
 */
@Composable
fun InviteScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var code by remember { mutableStateOf("") }
    var invited by remember { mutableStateOf(false) }
    var invitedByName by remember { mutableStateOf("") }
    var invitedCount by remember { mutableStateOf(0) }
    var reward by remember { mutableStateOf(5) }
    var loading by remember { mutableStateOf(true) }
    var inputCode by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        val resp = withContext(Dispatchers.IO) {
            try {
                ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/invite/info", "GET", null, AppSession.token()
                )
            } catch (_: Exception) {
                null
            }
        }
        try {
            val json = JSONObject(resp ?: "")
            code = json.optString("code", "")
            invited = json.optBoolean("invited", false)
            invitedByName = json.optString("invited_by_name", "")
            invitedCount = json.optInt("invited_count", 0)
            reward = json.optInt("reward", 5)
        } catch (_: Exception) {
        }
        loading = false
    }

    fun submitInviteCode() {
        val c = inputCode.trim().uppercase()
        if (c.isEmpty()) {
            appVm.showToast("请输入邀请码")
            return
        }
        if (submitting) return
        submitting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val resp = ApiGateway.requestSync(
                        ApiGateway.ZHIYIN_BASE + "/api/invite/bind", "POST",
                        JSONObject().put("code", c).toString(), AppSession.token()
                    )
                    Result.success(JSONObject(resp))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.onSuccess { json ->
                submitting = false
                appVm.showToast(json.optString("message", "绑定成功"))
                invited = true
                invitedCount += 1
                inputCode = ""
            }.onFailure { e ->
                submitting = false
                appVm.showToast(com.zhiyin.data.extractError(e))
            }
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "邀请好友",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))

                    // ===== 顶部海报（与好友列表 Banner 同图） =====
                    Image(
                        painter = painterResource(R.drawable.invite_banner),
                        contentDescription = "邀请好友，双方可得灵心币",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                    )

                    Spacer(Modifier.height(22.dp))

                    // ===== 我的专属邀请码 =====
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp, horizontal = 16.dp),
                        ) {
                            Text(
                                "我的专属邀请码",
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(10.dp))
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(26.dp))
                            } else if (code.isNotEmpty()) {
                                Text(
                                    code,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 8.sp,
                                    textAlign = TextAlign.Center,
                                    color = colors.primary,
                                )
                            } else {
                                Text(
                                    "生成中…",
                                    fontSize = 24.sp,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            if (code.isNotEmpty() && !loading) {
                                Button(
                                    onClick = {
                                        try {
                                            val cm = context
                                                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("invite_code", code))
                                            appVm.showToast("邀请码已复制，去分享吧")
                                        } catch (_: Exception) {
                                            appVm.showToast("复制失败，请手动记下")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text("复制邀请码", color = colors.onPrimary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "新朋友注册时填写你的邀请码，或在你邀请后填写，\n双方各得 ${reward} 灵心币",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                            )
                            if (invitedCount > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "已成功邀请 $invitedCount 位好友",
                                    fontSize = 13.sp,
                                    color = colors.primary,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ===== 填写他人邀请码 =====
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                        ) {
                            Text(
                                "填写邀请码",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (invited) "已经填过啦，每个账号只能填一次"
                                else "好友给你的 6 位邀请码，填完双方都得 ${reward} 灵心币",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(12.dp))
                            if (invited) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (invitedByName.isNotEmpty()) "已绑定好友「$invitedByName」的邀请"
                                        else "已绑定邀请码",
                                        fontSize = 14.sp,
                                        color = colors.primary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextField(
                                        value = inputCode,
                                        onValueChange = { if (it.length <= 8) inputCode = it.uppercase() },
                                        label = "输入 6 位邀请码",
                                        useLabelAsPlaceholder = true,
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = { submitInviteCode() },
                                        enabled = !submitting && inputCode.isNotBlank(),
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                    ) {
                                        if (submitting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                            )
                                        } else {
                                            Text("兑换", color = colors.onPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
