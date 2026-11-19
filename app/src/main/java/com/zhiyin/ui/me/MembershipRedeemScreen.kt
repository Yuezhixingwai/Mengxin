package com.zhiyin.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.R
import com.zhiyin.data.AccountApi
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MembershipRedeemScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onRedeemed: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    showHistory: Boolean = false,
) {
    if (showHistory) {
        MembershipRedeemHistoryScreen(onBack = onBack)
        return
    }
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var memberExpiresAt by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        AccountApi.fetchMembershipStatus().onSuccess {
            memberExpiresAt = it.expiresAt
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            SmallTopAppBar(
                title = "兑换会员",
                color = Color.White,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        RubberBandBox(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Image(
                    painter = painterResource(R.drawable.vip_membership_privileges),
                    contentDescription = "灵心 VIP 会员特权",
                    modifier = Modifier.fillMaxWidth().height(232.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "兑换灵心会员",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (memberExpiresAt.isNotBlank()) {
                        "当前有效期至 ${memberExpiresAt.take(10)}，兑换成功将叠加时长"
                    } else {
                        "输入兑换码即可叠加会员有效期"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(20.dp))
                TextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(24) },
                    label = "兑换码",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (code.isBlank() || submitting) return@Button
                        submitting = true
                        result = ""
                        scope.launch {
                            AccountApi.redeemMembership(code).onSuccess {
                                result = "兑换成功，会员有效期至 ${it.expiresAt}"
                                memberExpiresAt = it.expiresAt
                                appVm.showToast("会员兑换成功")
                                onRedeemed()
                            }.onFailure { result = it.message ?: "兑换失败" }
                            submitting = false
                        }
                    },
                    enabled = code.isNotBlank() && !submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (submitting) "兑换中…" else "立即兑换") }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = "查看兑换记录",
                    onClick = onOpenHistory,
                )
                if (result.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(result, fontSize = 14.sp, color = Color(0xFF4B5563))
                }
            }
        }
    }
}

@Composable
fun MembershipRedeemHistoryScreen(onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    var items by remember { mutableStateOf<List<AccountApi.MembershipRedeemHistoryItem>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        AccountApi.fetchMembershipRedeemHistory()
            .onSuccess { items = it; error = "" }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "兑换记录",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when {
                loading -> Text("加载中…", color = colors.onSurfaceVariantSummary)
                error.isNotEmpty() -> Text(error, color = colors.error)
                items.isEmpty() -> Text("暂无兑换记录", color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(vertical = 24.dp))
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items) { row ->
                            CardContainer {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "兑换 ${row.months} 个月",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            row.redeemedAt.take(16),
                                            fontSize = 12.sp,
                                            color = colors.onSurfaceVariantSummary,
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        thickness = 0.5.dp,
                                        color = colors.dividerLine.copy(alpha = 0.5f),
                                    )
                                    Text(
                                        "兑换码：${row.codeMask}",
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}