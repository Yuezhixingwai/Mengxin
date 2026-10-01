package com.zhiyin.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.data.PaymentPasswordManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EnsurePayPasswordFlow(
    onContinue: () -> Unit,
) {
    if (PaymentPasswordManager.isSet()) {
        val len = PaymentPasswordManager.pinLength().let { if (it > 0) it else 6 }
        PayPasswordVerifyDialog(
            pinLen = len,
            onVerified = { onContinue() },
        )
    } else {
        PayPasswordSetupDialog(onContinue)
    }
}

@Composable
private fun PayPasswordSetupDialog(
    onContinue: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = MiuixTheme.colorScheme

    fun setPin(v: String) {
        pin = v.filter { it.isDigit() }.take(8)
        error = null
    }

    PayPasswordSheet(
        title = "设置支付密码",
        onDismiss = null,
    ) {
        Text(
            "为了让转账和发红包更真实，请先设置支付密码。\n也可选择免密转账。",
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        TextField(
            value = pin,
            onValueChange = { setPin(it) },
            label = "数字密码（至少4位）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = confirm,
            onValueChange = { confirm = it.filter { ch -> ch.isDigit() }.take(8); error = null },
            label = "再次输入确认",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = colors.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                when {
                    pin.length < 4 -> error = "请输入至少4位数字密码"
                    pin != confirm -> error = "两次输入的密码不一致"
                    else -> {
                        PaymentPasswordManager.set(pin)
                        onContinue()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("设置并支付", color = colors.onPrimary)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            text = "免密转账（不设密码）",
            onClick = {
                PaymentPasswordManager.clear()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PayPasswordVerifyDialog(
    pinLen: Int,
    onVerified: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = MiuixTheme.colorScheme

    PayPasswordSheet(
        title = "验证支付密码",
        onDismiss = null,
    ) {
        Text(
            "请输入支付密码以继续本次转账",
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        TextField(
            value = pin,
            onValueChange = { v ->
                pin = v.filter { it.isDigit() }.take(maxOf(pinLen, 8))
                error = null
            },
            label = "支付密码",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = colors.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (PaymentPasswordManager.verify(pin)) {
                    onVerified()
                } else {
                    pin = ""
                    error = "密码错误，请重试"
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("确认支付", color = colors.onPrimary)
        }
        Spacer(Modifier.height(8.dp))
    }
}
