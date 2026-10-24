package com.zhiyin.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.service.PhoneAgentService
import com.zhiyin.service.AppLockStore
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.CardContainer
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PhoneAgentScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val status by PhoneAgentService.status.collectAsState()
    var pinConfigured by remember { mutableStateOf(AppLockStore.hasPin(ctx)) }
    var lockedPackages by remember { mutableStateOf(AppLockStore.lockedPackages(ctx)) }
    var aiAllowedPackages by remember { mutableStateOf(AppLockStore.aiAllowedPackages(ctx)) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var appSearch by remember { mutableStateOf("") }
    var lockMessage by remember { mutableStateOf("") }
    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        ctx.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(ctx.packageManager).toString() }
            .distinctBy { it.first }
            .filter { (pkg, _) ->
                pkg != ctx.packageName && pkg != "com.android.settings" &&
                    pkg != "com.android.systemui" && !pkg.contains("launcher", ignoreCase = true)
            }
            .sortedBy { it.second }
    }
    Scaffold(
        containerColor = colors.surface,
        topBar = { SmallTopAppBar(title = "手机助手", navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            CardContainer {
                Column(Modifier.padding(16.dp)) {
                    Text("手机操作权限", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("开启权限后，进入某个人设的聊天 → 会话设置 → 开启「允许人设操作手机」。日常消息仍正常聊天；明确说“帮我打开微信”等手机任务时才操作。也可用“操作：…”明确指定。付款与验证码由你本人完成。",
                        fontSize = 13.sp, color = colors.onSurfaceVariantActions)
                    Spacer(Modifier.height(16.dp))
                    Text("状态：$status", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    if (PhoneAgentService.current == null) {
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                            Text("去开启无障碍权限")
                        }
                    } else {
                        Button(onClick = { PhoneAgentService.stopTask() },
                            modifier = Modifier.fillMaxWidth().height(46.dp)) {
                            Text("停止当前手机任务")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            CardContainer {
                Column(Modifier.padding(16.dp)) {
                    Text("应用锁", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("选中的应用打开时需要输入 PIN。需保持上面的无障碍权限开启；灵心和系统设置不会被锁。",
                        fontSize = 13.sp, color = colors.onSurfaceVariantActions)
                    Spacer(Modifier.height(12.dp))
                    if (!pinConfigured) {
                        TextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                            label = "设置 4–8 位数字 PIN", useLabelAsPlaceholder = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        TextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(8) },
                            label = "再输入一次 PIN", useLabelAsPlaceholder = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (pin.length !in 4..8 || pin != confirmPin) {
                                lockMessage = "两次 PIN 不一致，或长度不足 4 位"
                            } else if (AppLockStore.setPin(ctx, pin)) {
                                pinConfigured = true
                                pin = ""
                                confirmPin = ""
                                lockMessage = "PIN 已设置，可以选择应用"
                            }
                        }, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("设置应用锁 PIN") }
                    } else {
                        Text("已锁定 ${lockedPackages.size} 个应用 · 允许人设上锁 ${aiAllowedPackages.size} 个", fontSize = 14.sp)
                        Text("仅你允许的应用可被已授权的人设上锁；PIN 只保存在本机，人设无法知道。", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        TextField(value = appSearch, onValueChange = { appSearch = it },
                            label = "搜索应用", useLabelAsPlaceholder = true,
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        val shownApps = installedApps.filter { (pkg, name) ->
                            appSearch.isBlank() || name.contains(appSearch, ignoreCase = true) ||
                                pkg.contains(appSearch, ignoreCase = true)
                        }.take(60)
                        shownApps.forEach { (pkg, name) ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                Text(name, fontSize = 15.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("允许人设上锁", modifier = Modifier.weight(1f), fontSize = 12.sp)
                                    Switch(checked = pkg in aiAllowedPackages, onCheckedChange = { checked ->
                                        AppLockStore.setAiAllowed(ctx, pkg, checked)
                                        aiAllowedPackages = AppLockStore.aiAllowedPackages(ctx)
                                    })
                                    Text("当前锁定", fontSize = 12.sp)
                                    Switch(checked = pkg in lockedPackages, onCheckedChange = { checked ->
                                        AppLockStore.setLocked(ctx, pkg, checked)
                                        lockedPackages = AppLockStore.lockedPackages(ctx)
                                        PhoneAgentService.current?.refreshAppLock()
                                    })
                                }
                            }
                        }
                        if (shownApps.isEmpty()) Text("没有找到可锁的应用", fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        TextField(value = currentPin,
                            onValueChange = { currentPin = it.filter(Char::isDigit).take(8) },
                            label = "输入当前 PIN 以关闭应用锁", useLabelAsPlaceholder = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (AppLockStore.clear(ctx, currentPin)) {
                                pinConfigured = false
                                lockedPackages = emptySet()
                                aiAllowedPackages = emptySet()
                                currentPin = ""
                                lockMessage = "应用锁已关闭"
                                PhoneAgentService.current?.refreshAppLock()
                            } else lockMessage = "当前 PIN 错误"
                        }, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("关闭应用锁") }
                    }
                    if (lockMessage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(lockMessage, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
