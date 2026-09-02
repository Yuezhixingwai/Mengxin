package com.zhiyin.ui.components

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhiyin.R
import com.zhiyin.data.PrivacyManager
import kotlinx.coroutines.delay

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        indication = null,
        interactionSource = MutableInteractionSource(),
        onClick = onClick,
    ),
)

fun showBiometricPrompt(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < 28) {
        onError("当前系统不支持生物识别")
        return
    }
    try {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(context)
            .setTitle("解锁灵心")
            .setSubtitle("验证你的身份以继续")
            .setNegativeButton("取消", executor) { _, _ -> }
            .build()
        val signal = CancellationSignal()
        prompt.authenticate(signal, executor, object :
            android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                    onError(errString.toString())
                }
            }
        })
    } catch (_: Exception) {
        onError("生物验证不可用")
    }
}

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val pinLen = remember { PrivacyManager.pinLength() }
    val useBiometric = remember { PrivacyManager.isBiometricEnabled() && PrivacyManager.canBiometric() }
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    fun tryUnlock(code: String) {
        if (PrivacyManager.verify(code)) {
            onUnlocked()
        } else {
            hasError = true
            errorMsg = "密码错误，请重试"
            pin = ""
        }
    }

    fun append(digit: String) {
        if (pin.length >= pinLen) return
        hasError = false
        pin += digit
        if (pin.length == pinLen) tryUnlock(pin)
    }

    LaunchedEffect(Unit) {
        if (useBiometric) {
            delay(350)
            showBiometricPrompt(context, onUnlocked) {
                errorMsg = it
                hasError = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = "灵心",
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.height(14.dp))
        Text("应用已锁定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasError) errorMsg else "输入数字密码解锁",
            style = MaterialTheme.typography.bodySmall,
            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(pinLen) { i ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(30.dp))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(68.dp))
                        "⌫" -> Box(
                            modifier = Modifier
                                .size(68.dp)
                                .noRippleClickable {
                                    if (pin.isNotEmpty()) {
                                        hasError = false
                                        pin = pin.dropLast(1)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Backspace,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> Surface(
                            onClick = { append(key) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(68.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (useBiometric) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .noRippleClickable {
                        showBiometricPrompt(context, onUnlocked) {
                            errorMsg = it
                            hasError = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Fingerprint,
                    contentDescription = "生物识别解锁",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                "生物识别解锁",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
