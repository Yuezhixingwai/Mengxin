package com.zhiyin

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyin.data.AccountApi
import com.zhiyin.data.AccountApi.RealNameCheck
import com.zhiyin.data.AppSession
import com.zhiyin.data.PaymentPasswordManager
import com.zhiyin.data.PrivacyManager
import com.zhiyin.ui.MainScaffold
import com.zhiyin.ui.SplashContent
import com.zhiyin.ui.auth.AuthScreen
import com.zhiyin.ui.components.AppLockScreen
import com.zhiyin.ui.components.LingXinDialog
import com.zhiyin.ui.components.LingXinToastHost
import com.zhiyin.ui.settings.RealNameVerifyDialog
import com.zhiyin.ui.theme.LingXinTheme
import com.zhiyin.ui.vm.AppViewModel

class MainActivity : ComponentActivity() {

    private val appLocked = mutableStateOf(false)
    private var backgrounded = false

    override fun onStop() {
        super.onStop()
        backgrounded = true
    }

    override fun onResume() {
        super.onResume()
        if (backgrounded && PrivacyManager.isLockEnabled()) {
            appLocked.value = true
        }
        applySecureFlag()
    }

    private fun applySecureFlag() {
        if (PrivacyManager.secureFlagOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        AppSession.init(applicationContext)
        PrivacyManager.init(applicationContext)
        PaymentPasswordManager.init(applicationContext)
        appLocked.value = PrivacyManager.isLockEnabled()
        applySecureFlag()

        setContent {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                val appVm: AppViewModel = viewModel()
                val darkTheme by appVm.darkMode.collectAsState()
                val loggedIn by appVm.loggedIn.collectAsState()
                val themeId by appVm.themeId.collectAsState()

                LaunchedEffect(PrivacyManager.secureFlagOn) { applySecureFlag() }

                LingXinTheme(darkTheme = darkTheme, themeId = themeId) {
                    if (appLocked.value) {
                        AppLockScreen(onUnlocked = {
                            appLocked.value = false
                            backgrounded = false
                        })
                    } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (loggedIn) {
                            MainScaffold(appVm = appVm)
                        } else {
                            AuthScreen(onLoggedIn = {
                                appVm.loadFriends()
                                appVm.refreshUser()
                                appVm.maybeStartNotifyService()
                            })
                        }

                        LingXinToastHost(message = appVm.toastMessage, id = appVm.toastId)

                        var splashVisible by remember { mutableStateOf(true) }
                        AnimatedVisibility(
                            visible = splashVisible,
                            enter = EnterTransition.None,
                            exit = fadeOut(tween(450)),
                        ) {
                            SplashContent(onTimeout = { splashVisible = false })
                        }
                    }

                    PermissionRequester(loggedIn)

                    if (loggedIn) {
                        var announcement by remember { mutableStateOf<AccountApi.Announcement?>(null) }
                        LaunchedEffect(Unit) {
                            announcement = appVm.unreadAnnouncement()
                        }
                        announcement?.let { ann ->
                            LingXinDialog(
                                onDismiss = {
                                    appVm.markAnnouncementRead(ann.id)
                                    announcement = null
                                },
                                title = ann.title,
                                text = ann.content,
                                confirmText = "我知道了",
                                dismissText = null,
                            )
                        }

                        var realNameState by remember { mutableStateOf<RealNameCheck?>(null) }
                        LaunchedEffect(Unit) {
                            realNameState = AccountApi.realNameCheck()
                        }
                        realNameState?.let { state ->
                            if (state.banned) {
                                LingXinDialog(
                                    onDismiss = {},
                                    title = "账号已被禁用",
                                    text = buildString {
                                        append("账号已被禁用")
                                        if (state.banReason.isNotEmpty()) append("\n\n原因：${state.banReason}")
                                        if (state.bannedUntil.isNotEmpty())
                                            append("\n解封时间：${com.zhiyin.logic.net.ApiGateway.toBeijingTime(state.bannedUntil, "yyyy-MM-dd HH:mm:ss")}")
                                    },
                                    confirmText = "确定",
                                    dismissText = null,
                                    dismissible = false,
                                    onConfirm = {
                                        appVm.logout()
                                        realNameState = null
                                    },
                                )
                            } else if (state.required) {
                                RealNameVerifyDialog(
                                    onVerified = { realNameState = null },
                                    onToast = { appVm.showToast(it) },
                                )
                            } else if (state.minorMode) {
                                LingXinDialog(
                                    onDismiss = { realNameState = null },
                                    title = "青少年模式已开启",
                                    text = "您已通过实名认证，年龄14-17岁，已自动开启青少年模式。每日限使用30分钟，请合理分配时间。",
                                    confirmText = "我知道了",
                                    dismissText = null,
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionRequester(loggedIn: Boolean) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        LaunchedEffect(loggedIn) {
            if (!loggedIn) return@LaunchedEffect
            val perms = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )
            if (Build.VERSION.SDK_INT >= 33) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < 29) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            val toRequest = perms.filter {
                ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (toRequest.isNotEmpty()) launcher.launch(toRequest.toTypedArray())
        }
    }
}
