package com.zhiyin.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhiyin.data.PaymentPasswordManager

/**
 * 转账/发红包时的支付密码门禁。
 * - 已设置密码：要求输入密码校验，正确才执行 [onContinue]。
 * - 未设置（首次）→ 弹出设置对话框，用户可选择"设置密码"或"免密转账"，
 *   完成后执行 [onContinue]。纯本地装饰性功能。
 */
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { setPin(it) },
            placeholder = { Text("数字密码（至少4位）", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            colors = digitFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it.filter { ch -> ch.isDigit() }.take(8); error = null },
            placeholder = { Text("再次输入确认", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            colors = digitFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Text("设置并支付")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                PaymentPasswordManager.clear()
                onContinue()
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Text("免密转账（不设密码）", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

    PayPasswordSheet(
        title = "验证支付密码",
        onDismiss = null,
    ) {
        Text(
            "请输入支付密码以继续本次转账",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { v ->
                pin = v.filter { it.isDigit() }.take(maxOf(pinLen, 8))
                error = null
            },
            placeholder = { Text("支付密码", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            colors = digitFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Text("确认支付")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun digitFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedBorderColor = Color.Transparent,
)