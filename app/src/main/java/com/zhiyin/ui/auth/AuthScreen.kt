package com.zhiyin.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyin.R
import com.zhiyin.data.AccountApi
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinSheet
import com.zhiyin.ui.vm.AuthViewModel

@Composable
fun AuthScreen(
    onLoggedIn: () -> Unit,
) {
    val vm: AuthViewModel = viewModel()
    var mode by rememberSaveable { mutableStateOf("login") }

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    var regPhone by rememberSaveable { mutableStateOf("") }
    var regUsername by rememberSaveable { mutableStateOf("") }
    var regPassword by rememberSaveable { mutableStateOf("") }
    var regPassword2 by rememberSaveable { mutableStateOf("") }
    var regCode by rememberSaveable { mutableStateOf("") }

    var agreed by rememberSaveable { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    var showForgot by remember { mutableStateOf(false) }
    var agreementType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            kotlinx.coroutines.delay(2200)
            toastMsg = null
        }
    }

    fun requireAgreement(): Boolean {
        if (!agreed) toastMsg = "请先阅读并同意我们的用户协议和隐私政策"
        return agreed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                ),
        )

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))

            AuthEntrance(0) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = "灵心",
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(6.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(18.dp))
            AuthEntrance(90) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "灵心",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "懂你的AI陪伴",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            AuthEntrance(180) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        val forward = targetState == "register"
                        val slideSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                        val fadeSpec = tween<Float>(280, easing = FastOutSlowInEasing)
                        (slideInHorizontally(slideSpec) { if (forward) it / 3 else -it / 3 } + fadeIn(fadeSpec)) togetherWith
                            (slideOutHorizontally(slideSpec) { if (forward) -it / 3 else it / 3 } + fadeOut(fadeSpec))
                    },
                    label = "authMode",
                ) { m ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (m == "login") {
                            AuthField(
                                value = username,
                                onValueChange = { username = it },
                                hint = "用户名 / 手机号",
                                singleLine = true,
                            )
                            Spacer(Modifier.height(14.dp))
                            AuthField(
                                value = password,
                                onValueChange = { password = it },
                                hint = "密码",
                                singleLine = true,
                                isPassword = true,
                            )
                            Spacer(Modifier.height(24.dp))
                            PrimaryButton(text = "登录", loading = vm.loading, enabled = !vm.loading) {
                                if (requireAgreement()) {
                                    vm.login(username.trim(), password.trim(), { toastMsg = it }, onLoggedIn)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "忘记密码？",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showForgot = true },
                            )
                        } else {
                            AuthField(regPhone, { regPhone = it }, "手机号", singleLine = true, keyboardType = KeyboardType.Phone)
                            Spacer(Modifier.height(12.dp))
                            AuthField(regUsername, { regUsername = it }, "用户名（至少3位）", singleLine = true)
                            Spacer(Modifier.height(12.dp))
                            AuthField(regPassword, { regPassword = it }, "密码（至少6位）", singleLine = true, isPassword = true)
                            Spacer(Modifier.height(12.dp))
                            AuthField(regPassword2, { regPassword2 = it }, "确认密码", singleLine = true, isPassword = true)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuthField(regCode, { regCode = it }, "短信验证码", singleLine = true, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                OutlinedPill(
                                    text = if (vm.countdown > 0) "${vm.countdown}s" else "获取验证码",
                                    enabled = vm.countdown <= 0,
                                ) {
                                    vm.requestSmsCode(regPhone.trim(), "register") { toastMsg = it }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            PrimaryButton(text = "注册", loading = vm.loading, enabled = !vm.loading) {
                                if (requireAgreement()) {
                                    vm.register(
                                        regPhone.trim(), regUsername.trim(), regPassword.trim(),
                                        regPassword2.trim(), regCode.trim(), { toastMsg = it }, onLoggedIn
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            AuthEntrance(260) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { agreed = !agreed },
                ) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        "我同意",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "用户协议",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { agreementType = "user-agreement" },
                    )
                    Text(
                        "与",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "隐私政策",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { agreementType = "privacy" },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            AuthEntrance(320) {
                Text(
                    if (mode == "login") "没有账户？注册一个" else "已有账户？去登录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { mode = if (mode == "login") "register" else "login" },
                )
            }

            Spacer(Modifier.height(40.dp))
        }
        }

        if (toastMsg != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            ) {
                Text(
                    toastMsg.orEmpty(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (vm.captchaVisible) {
        LingXinSheet(onDismiss = { vm.dismissCaptcha() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "人机验证",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                var captchaCode by remember { mutableStateOf("") }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clickable { vm.refreshCaptcha() },
                        contentAlignment = Alignment.Center,
                    ) {
                    val bmp = vm.captchaBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
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
                }
                Spacer(Modifier.height(12.dp))
                AuthField(captchaCode, { captchaCode = it }, "请输入图片中的验证码", singleLine = true)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "确 认", loading = false) {
                    vm.confirmCaptcha(
                        captchaCode.trim(),
                        { toastMsg = it },
                        {
                            captchaCode = ""
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showForgot) {
        var rpPhone by remember { mutableStateOf("") }
        var rpCode by remember { mutableStateOf("") }
        var rpPwd by remember { mutableStateOf("") }
        LingXinDialog(
            onDismiss = { showForgot = false },
            title = "重置密码",
            dismissText = "取消",
            confirmText = "确认重置",
            onConfirm = {
                vm.resetPassword(rpPhone.trim(), rpCode.trim(), rpPwd.trim(), { toastMsg = it }) {
                    showForgot = false
                }
            },
        ) {
            Spacer(Modifier.height(16.dp))
            AuthField(rpPhone, { rpPhone = it }, "手机号", singleLine = true, keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthField(rpCode, { rpCode = it }, "验证码", singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                OutlinedPill(
                    text = if (vm.countdown > 0) "${vm.countdown}s" else "获取验证码",
                    enabled = vm.countdown <= 0,
                ) {
                    vm.requestSmsCode(rpPhone.trim(), "reset_password") { toastMsg = it }
                }
            }
            Spacer(Modifier.height(10.dp))
            AuthField(rpPwd, { rpPwd = it }, "新密码（至少6位）", singleLine = true, isPassword = true)
        }
    }

    agreementType?.let { type ->
        var agreementText by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(type) {
            agreementText = AccountApi.fetchAgreement(type)
        }
        LingXinDialog(
            onDismiss = { agreementType = null },
            title = if (type == "privacy") "隐私政策" else "用户服务协议",
            confirmText = "关闭",
            dismissText = null,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                agreementText ?: "加载中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var showPwd by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword && !showPwd) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType
        ),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(
                        if (showPwd) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        ),
    )
}

@Composable
private fun PrimaryButton(text: String, loading: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OutlinedPill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AuthEntrance(delay: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(460, delayMillis = delay, easing = FastOutSlowInEasing),
        label = "authEntrance",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 44f
        },
    ) { content() }
}
