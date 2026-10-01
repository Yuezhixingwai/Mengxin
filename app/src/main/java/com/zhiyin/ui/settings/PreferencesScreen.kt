package com.zhiyin.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.data.PlazaApi
import com.zhiyin.data.PreferenceApi
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        PlazaApi.categories().onSuccess { cats ->
            suggestions = cats + listOf("温柔", "傲娇", "元气", "高冷", "治愈", "古风", "现代", "校园", "职场", "恋爱")
        }
        PreferenceApi.get().onSuccess { selected = it }
        loading = false
    }

    fun toggle(tag: String) {
        selected = if (selected.contains(tag)) selected - tag else (selected + tag).take(20)
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "喜好设置",
                color = colors.surface,
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(
                        text = "保存",
                        onClick = {
                            scope.launch {
                                PreferenceApi.save(selected).onSuccess {
                                    appVm.showToast("已保存，推荐将按喜好更新")
                                    onBack()
                                }.onFailure { appVm.showToast(it.message ?: "保存失败") }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            color = colors.surface,
                            textColor = colors.primary,
                        ),
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        minHeight = 36.dp,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("选择你感兴趣的标签", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "发现页的「为你推荐」会根据这些标签为你推荐人设",
                fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Text("加载中…", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                return@Column
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { tag ->
                    val on = selected.contains(tag)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (on) colors.primary else colors.surfaceContainerHigh,
                        modifier = Modifier.clickable { toggle(tag) },
                    ) {
                        Text(
                            tag,
                            fontSize = 12.sp,
                            color = if (on) colors.onPrimary else colors.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("自定义标签", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = { input = it.take(20) },
                    label = "输入标签，如：猫咪",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                TextButton(
                    text = "添加",
                    onClick = {
                        val t = input.trim()
                        if (t.isNotEmpty() && !selected.contains(t)) selected = (selected + t).take(20)
                        input = ""
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            Spacer(Modifier.height(14.dp))
            if (selected.isNotEmpty()) {
                Text("已选标签", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selected.forEach { tag ->
                        Surface(shape = RoundedCornerShape(16.dp), color = colors.primaryContainer) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text(tag, fontSize = 12.sp, color = colors.onPrimaryContainer)
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp).clickable { toggle(tag) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
