package com.zhiyin.ui.vm

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhiyin.data.AccountApi
import com.zhiyin.data.AppSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    var loading by mutableStateOf(false)
        private set

    var captchaBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var captchaVisible by mutableStateOf(false)
        private set
    private var pendingPhone = ""
    private var pendingAction = ""

    var countdown by mutableStateOf(0)
        private set

    var registerBonus by mutableStateOf(0.0)
        private set

    fun login(username: String, password: String, onToast: (String) -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            loading = true
            AccountApi.login(username, password)
                .onSuccess { data ->
                    AppSession.onLoggedIn(data.token, data.username, data.userId)
                    onSuccess()
                }
                .onFailure { onToast(it.message ?: "登录失败") }
            loading = false
        }
    }

    fun requestSmsCode(phone: String, action: String, onToast: (String) -> Unit) {
        if (!phone.matches(Regex("^1[3-9]\\d{9}$"))) {
            onToast("请输入正确的手机号")
            return
        }
        pendingPhone = phone
        pendingAction = action
        viewModelScope.launch {
            captchaBitmap = AccountApi.captchaBitmap()
            captchaVisible = true
        }
    }

    fun refreshCaptcha() {
        viewModelScope.launch { captchaBitmap = AccountApi.captchaBitmap() }
    }

    fun dismissCaptcha() {
        captchaVisible = false
    }

    fun confirmCaptcha(code: String, onToast: (String) -> Unit, onCaptchaWrong: () -> Unit) {
        if (code.isEmpty()) {
            onToast("请输入验证码")
            return
        }
        viewModelScope.launch {
            AccountApi.sendSmsCode(pendingPhone, pendingAction, code)
                .onSuccess {
                    captchaVisible = false
                    onToast("验证码已发送")
                    startCountdown()
                }
                .onFailure {
                    val msg = it.message ?: "发送失败"
                    if (msg.contains("验证码")) {
                        onToast(msg)
                        onCaptchaWrong()
                        refreshCaptcha()
                    } else {
                        captchaVisible = false
                        onToast("发送失败: $msg")
                    }
                }
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            countdown = 60
            while (countdown > 0) {
                delay(1000)
                countdown -= 1
            }
        }
    }

    fun register(
        phone: String,
        username: String,
        password: String,
        password2: String,
        code: String,
        onToast: (String) -> Unit,
        onSuccess: () -> Unit,
    ) {
        when {
            !phone.matches(Regex("^1[3-9]\\d{9}$")) -> { onToast("请输入正确的手机号"); return }
            username.length < 3 -> { onToast("用户名至少3位"); return }
            password.length < 6 -> { onToast("密码至少6位"); return }
            password != password2 -> { onToast("两次输入的密码不一致"); return }
            code.isEmpty() -> { onToast("请输入短信验证码"); return }
        }
        viewModelScope.launch {
            loading = true
            AccountApi.register(phone, username, password, code)
                .onSuccess { data ->
                    AppSession.onLoggedIn(data.token, data.username, data.userId)
                    onSuccess()
                }
                .onFailure { onToast(it.message ?: "注册失败") }
            loading = false
        }
    }

    fun resetPassword(phone: String, code: String, password: String, onToast: (String) -> Unit, onSuccess: () -> Unit) {
        if (phone.isEmpty() || code.isEmpty()) {
            onToast("请输入手机号和验证码")
            return
        }
        if (password.length < 6) {
            onToast("密码至少6位")
            return
        }
        viewModelScope.launch {
            AccountApi.resetPassword(phone, code, password)
                .onSuccess {
                    onToast("密码重置成功")
                    onSuccess()
                }
                .onFailure { onToast(it.message ?: "重置失败") }
        }
    }
}
