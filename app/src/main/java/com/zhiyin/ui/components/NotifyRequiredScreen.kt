package com.zhiyin.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 通知是否真的可用：既覆盖「未授予运行时权限」，也覆盖「用户在系统设置里关掉了通知」。 */
fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/** 跳到本应用的通知设置页。部分 ROM 不认 ACTION_APP_NOTIFICATION_SETTINGS，退回应用详情页。 */
fun openNotificationSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

/**
 * 通知权限强制拦截页。
 *
 * 产品要求（2026-11-21）：不开通知就不给用。因为主动消息是核心体验，
 * 没有通知权限时用户根本感知不到对方发过消息，用起来像「她从来不找我」。
 */
@Composable
fun NotifyRequiredScreen(
    onRequest: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("请开启通知权限", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            "灵心需要通知权限，才能在她主动找你的时候及时提醒你。\n关闭通知将无法使用本应用。",
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(34.dp))
        Button(
            onClick = onRequest,
            cornerRadius = 26.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("开启通知", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            text = "暂不使用，退出应用",
            onClick = onExit,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            textStyle = TextStyle(fontSize = 14.sp),
            colors = ButtonDefaults.textButtonColors(
                color = colors.surface,
                textColor = colors.onSurfaceVariantSummary,
            ),
        )
        Spacer(Modifier.weight(1.15f))
        Text(
            "开启后可以随时在系统设置中关闭",
            fontSize = 11.sp,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
