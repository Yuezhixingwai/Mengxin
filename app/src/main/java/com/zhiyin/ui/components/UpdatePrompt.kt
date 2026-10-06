package com.zhiyin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhiyin.data.AccountApi
import com.zhiyin.data.AppUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用内更新的全部弹窗（发现新版本 → 下载进度 → 安装引导）。
 *
 * 用法：把「检查到的可更新版本」传给 info，点「以后再说」时会回调 onDismiss。
 * 安装流程与 UI 状态由 [AppUpdater] 统一托管，页面只需传一个 toast。
 */
@Composable
fun UpdatePrompt(
    info: AccountApi.VersionInfo?,
    onDismiss: () -> Unit,
    toast: (String) -> Unit,
    onStartDownload: () -> Unit = {},
    /**
     * 是否渲染「下载进度 / 失败 / 待安装」弹窗。
     * AppUpdater.state 是全局唯一的，所以整个 App 只能有一处渲染这几步，
     * 否则「关于」页和根脚手架会同时弹出来。默认由根脚手架负责。
     */
    showProgressDialogs: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val upState by AppUpdater.state.collectAsState()

    var url by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    /** 开始（或重试）下载：无安装权限时同时把用户送去授权页，下载在后台继续 */
    fun start(u: String, v: String) {
        url = u
        version = v
        if (!AppUpdater.canInstall(context)) {
            AppUpdater.openInstallPermission(context)
            toast("请开启「允许安装未知应用」，安装包将在后台继续下载")
        }
        job?.cancel()
        job = scope.launch {
            val file = AppUpdater.download(context, u, v) ?: return@launch
            if (AppUpdater.install(context, file)) {
                AppUpdater.reset()
            }
            // 拉不起来就保留 Ready 状态，由下面的弹窗引导用户授权后重试
        }
    }

    // ① 发现新版本
    info?.let { uv ->
        LingXinDialog(
            onDismiss = onDismiss,
            title = "发现新版本 ${uv.version}",
            text = uv.changelog.ifEmpty { "修复已知问题，优化使用体验" },
            confirmText = "立即更新",
            dismissText = "以后再说",
            onConfirm = {
                onStartDownload()
                start(uv.apkUrl, uv.version)
            },
        )
    }

    // ② 下载中 / 失败 / 待安装
    if (showProgressDialogs) when (val s = upState) {
        is AppUpdater.State.Downloading -> LingXinDialog(
            onDismiss = {},
            title = "正在下载更新",
            text = if (s.percent > 0) "新版本 v$version 已下载 ${s.percent}%" else "新版本 v$version 下载中…",
            confirmText = "取消",
            dismissText = null,
            dismissible = false,
            onConfirm = {
                job?.cancel()
                AppUpdater.reset()
            },
            content = {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.surfaceContainerHigh)
                ) {
                    if (s.percent > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(s.percent / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.primary)
                        )
                    }
                }
            },
        )

        is AppUpdater.State.Failed -> LingXinDialog(
            onDismiss = { AppUpdater.reset() },
            title = "更新失败",
            text = s.message,
            confirmText = "重试",
            dismissText = "稍后再说",
            onConfirm = {
                if (url.isNotEmpty()) start(url, version) else AppUpdater.reset()
            },
        )

        is AppUpdater.State.Ready -> LingXinDialog(
            // dismissText 按钮走的就是 onDismiss，这里承载"换浏览器下载"的降级动作
            onDismiss = {
                AppUpdater.openInBrowser(context, url)
                AppUpdater.reset()
            },
            title = "准备安装",
            text = "安装包已下载完成，点击「立即安装」完成更新。\n" +
                    "若系统提示需要授权，请先开启「允许安装未知应用」再点一次。",
            confirmText = "立即安装",
            dismissText = "换浏览器下载",
            dismissible = false,
            onConfirm = {
                if (AppUpdater.install(context, s.file)) {
                    AppUpdater.reset()
                } else if (!AppUpdater.canInstall(context)) {
                    AppUpdater.openInstallPermission(context)
                    toast("请开启「允许安装未知应用」后返回再点「立即安装」")
                } else {
                    AppUpdater.openInBrowser(context, url)
                    AppUpdater.reset()
                }
            },
        )

        else -> Unit
    }
}
