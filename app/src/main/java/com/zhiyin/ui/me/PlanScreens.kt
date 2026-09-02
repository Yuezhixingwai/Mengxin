package com.zhiyin.ui.me

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhiyin.R
import com.zhiyin.data.AppSession
import com.zhiyin.logic.net.ApiGateway
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class PaymentMethod(val label: String, val sub: String? = null) {
    WECHAT("微信支付", null),
    ALIPAY("支付宝", null),
    BANKCARD("银行卡", "支持银联 / VISA / Mastercard"),
}

private data class PlanSpec(
    val name: String,
    val price: String,
    val perMonth: String,
    val originalPrice: String? = null,
    val tag: String? = null,
    val features: List<String>,
)

private val monthlyPlan = PlanSpec(
    name = "月度会员",
    price = "¥18",
    perMonth = "/月",
    features = listOf(
        "解锁全部人设与群聊",
        "无限畅聊不限额",
        "专属表情包与装扮",
    ),
)

private val yearlyPlan = PlanSpec(
    name = "年度会员",
    price = "¥128",
    perMonth = "约 ¥10.7/月",
    originalPrice = "¥216",
    tag = "省41%",
    features = listOf(
        "连续包月全部权益",
        "每月赠 500 启辰币",
        "生日专属惊喜礼包",
    ),
)

private data class CoinPackage(
    val price: Double,
    val coins: Int,
    val bonus: Int = 0,
    val tag: String? = null,
) {
    val priceLabel: String get() = "¥" + fmtAmount(price)
}

private val coinPackages = listOf(
    CoinPackage(6.0, 60),
    CoinPackage(30.0, 320, bonus = 20),
    CoinPackage(68.0, 750, bonus = 70, tag = "超值"),
    CoinPackage(128.0, 1500, bonus = 220, tag = "最划算"),
    CoinPackage(328.0, 4000, bonus = 720),
    CoinPackage(648.0, 8000, bonus = 1520),
)

private fun fmtAmount(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) Math.round(v).toString()
    else (Math.round(v * 100) / 100.0).toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenRecharge: () -> Unit,
) {
    var billingPeriod by rememberSaveable { mutableStateOf("yearly") }
    var payment by rememberSaveable { mutableStateOf(PaymentMethod.WECHAT) }

    val plan = if (billingPeriod == "monthly") monthlyPlan else yearlyPlan
    val total = if (billingPeriod == "monthly") "¥18" else "¥128"
    val totalNote = if (billingPeriod == "monthly") "连续包月 · 自动续费，可随时取消"
    else "连续包年 · 自动续费，可随时取消"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("灵心会员", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        RubberBandBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
            ) {
                MemberHeroCard(onOpenRecharge)
                BenefitSection()
                BillingPeriodSwitch(
                    period = billingPeriod,
                    onPeriodChange = { billingPeriod = it },
                )
                SubscriptionPlanCard(plan = plan)
                PaymentMethodSelector(selected = payment, onSelect = { payment = it })
                SubscriptionNotes(appVm)
            }
        }
        PayBottomBar(total = total, note = totalNote, buttonText = "立即订阅") {
            appVm.showToast("订阅支付功能开发中，敬请期待")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenSubscription: () -> Unit,
) {
    var balance by remember { mutableStateOf<Double?>(null) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(3) }
    var customAmount by rememberSaveable { mutableStateOf("") }
    var payment by rememberSaveable { mutableStateOf(PaymentMethod.WECHAT) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/user/coins", "GET", null, AppSession.token()
                )
                val b = JSONObject(resp).optDouble("balance", 0.0)
                withContext(Dispatchers.Main) { balance = b }
            } catch (_: Exception) {
            }
        }
    }

    val customValue = customAmount.toDoubleOrNull()
    val total = if (customValue != null && customValue > 0) "¥" + fmtAmount(customValue)
    else coinPackages.getOrNull(selectedIndex)?.priceLabel ?: "¥0"
    val totalNote = if (customValue != null && customValue > 0) "自定义充值 · 1元=10启辰币"
    else "1元=10启辰币 · 充值后立即到账"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("充值启辰币", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        RubberBandBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
            ) {
                RechargeMemberBanner(onOpenSubscription)
                BalanceCard(balance)
                RechargePackagesSection(
                    selectedIndex = selectedIndex,
                    onSelectPackage = {
                        selectedIndex = it
                        customAmount = ""
                    },
                    customAmount = customAmount,
                    onCustomChange = {
                        customAmount = it.filter { ch -> ch.isDigit() || ch == '.' }
                        if (it.isNotBlank()) selectedIndex = -1
                    },
                )
                PaymentMethodSelector(selected = payment, onSelect = { payment = it })
                RechargeNotes()
            }
        }
        PayBottomBar(total = total, note = totalNote, buttonText = "立即支付") {
            appVm.showToast("充值支付功能开发中，敬请期待")
        }
    }
}

@Composable
private fun MemberHeroCard(onOpenRecharge: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                "灵心会员",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "解锁全部 AI 人设 · 无限畅聊 · 专属权益",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRecharge),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.MonetizationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "充值启辰币",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "会员赠送额度用完后可随时补充",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BenefitSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "会员权益",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        CardContainer {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                BenefitRow(1, "解锁全部 AI 人设", "官方与自定义人设、群聊角色随心切换")
                BenefitRow(2, "无限畅聊", "消息额度不再受限，长聊不中断")
                BenefitRow(3, "专属表情包与装扮", "会员限定表情、聊天背景与主题色")
                BenefitRow(4, "启辰币礼遇", "年费会员每月额外赠送启辰币")
                BenefitRow(5, "优先响应通道", "高峰时段优先排队，专属客服支持")
            }
        }
    }
}

@Composable
private fun BenefitRow(index: Int, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(index),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(30.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BillingPeriodSwitch(period: String, onPeriodChange: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SegmentedButton(
            selected = period == "monthly",
            onClick = { onPeriodChange("monthly") },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                Text(
                    "连续包月",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (period == "monthly") FontWeight.SemiBold else FontWeight.Normal,
                )
                Text("¥18/月", style = MaterialTheme.typography.labelSmall)
            }
        }
        SegmentedButton(
            selected = period == "yearly",
            onClick = { onPeriodChange("yearly") },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                Text(
                    "连续包年 · 推荐",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (period == "yearly") FontWeight.SemiBold else FontWeight.Normal,
                )
                Text("¥128/年 · 省41%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SubscriptionPlanCard(plan: PlanSpec) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        plan.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            plan.price,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (plan.originalPrice != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                plan.originalPrice,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            plan.perMonth,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                plan.tag?.let { tag ->
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFE85D4A)) {
                        Text(
                            tag,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            plan.features.forEach { f ->
                Row(
                    modifier = Modifier.padding(vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(f, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionNotes(appVm: AppViewModel) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            "· 会员服务为虚拟商品，订阅成功后立即生效\n" +
                "· 连续包月 / 包年到期前将自动续费，可随时取消\n" +
                "· 支持微信支付、支付宝及银联 / VISA / Mastercard 银行卡",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { appVm.showToast("自动续费说明：功能开发中") }) {
                Text("自动续费说明", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { appVm.showToast("会员服务协议：功能开发中") }) {
                Text("会员服务协议", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { appVm.showToast("恢复购买：功能开发中") }) {
                Text("恢复购买", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RechargeMemberBanner(onOpenSubscription: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(onClick = onOpenSubscription),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "开通会员更划算",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "年费会员每月额外赠 500 启辰币",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BalanceCard(balance: Double?) {
    CardContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                "启辰币余额",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                balance?.let { "$" + fmtAmount(it) } ?: "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "充值后实时到账",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RechargePackagesSection(
    selectedIndex: Int,
    onSelectPackage: (Int) -> Unit,
    customAmount: String,
    onCustomChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "选择充值金额",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        coinPackages.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { pkg ->
                    val idx = coinPackages.indexOf(pkg)
                    CoinPackageCard(
                        pkg = pkg,
                        selected = idx == selectedIndex,
                        onClick = { onSelectPackage(idx) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        OutlinedTextField(
            value = customAmount,
            onValueChange = onCustomChange,
            placeholder = { Text("自定义金额（¥1 起充）", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            prefix = { Text("¥", color = MaterialTheme.colorScheme.onSurface) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun CoinPackageCard(
    pkg: CoinPackage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    pkg.priceLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${pkg.coins} 启辰币",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pkg.bonus > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "赠 ${pkg.bonus} 币",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF34B78F),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        pkg.tag?.let { tag ->
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, bottomEnd = 10.dp),
                color = Color(0xFFE85D4A),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    tag,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun RechargeNotes() {
    Text(
        "· 启辰币为虚拟商品，充值成功后立即到账，不支持退款\n" +
            "· 1元 = 10启辰币，多充多赠\n" +
            "· 未成年人请在监护人指导下理性消费",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
internal fun PaymentMethodSelector(
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "支付方式",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        CardContainer {
            Column {
                PaymentMethodRow(PaymentMethod.WECHAT, selected, onSelect)
                HorizontalDivider(
                    modifier = Modifier.padding(start = 82.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                )
                PaymentMethodRow(PaymentMethod.ALIPAY, selected, onSelect)
                HorizontalDivider(
                    modifier = Modifier.padding(start = 82.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                )
                PaymentMethodRow(PaymentMethod.BANKCARD, selected, onSelect)
            }
        }
    }
}

@Composable
private fun PaymentMethodRow(
    method: PaymentMethod,
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(method) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 54.dp, height = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (method) {
                PaymentMethod.WECHAT -> Image(
                    painter = painterResource(R.drawable.pay_wechat),
                    contentDescription = "微信支付",
                    modifier = Modifier.size(30.dp),
                )
                PaymentMethod.ALIPAY -> Image(
                    painter = painterResource(R.drawable.pay_alipay),
                    contentDescription = "支付宝",
                    modifier = Modifier.size(30.dp),
                )
                PaymentMethod.BANKCARD -> BankCardMark(width = 54.dp, height = 34.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(method.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            method.sub?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RadioButton(
            selected = selected == method,
            onClick = { onSelect(method) },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun BankCardMark(width: Dp, height: Dp) {
    Row(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(height * 0.22f))
            .background(Color.White)
            .border(0.5.dp, Color(0x33000000), RoundedCornerShape(height * 0.22f))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.pay_unionpay),
            contentDescription = "银联",
            modifier = Modifier
                .weight(1f, fill = false)
                .height(14.dp),
        )
        Image(
            painter = painterResource(R.drawable.pay_visa),
            contentDescription = "VISA",
            modifier = Modifier
                .weight(1.4f, fill = false)
                .height(10.dp),
        )
        Image(
            painter = painterResource(R.drawable.pay_mastercard),
            contentDescription = "Mastercard",
            modifier = Modifier
                .weight(1f, fill = false)
                .height(16.dp),
        )
    }
}

@Composable
private fun PayBottomBar(total: String, note: String, buttonText: String, onPay: () -> Unit) {
    Surface(
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "合计",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    total,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onPay,
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp),
            ) {
                Text(buttonText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
