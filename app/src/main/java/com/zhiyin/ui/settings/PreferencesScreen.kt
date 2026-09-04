package com.zhiyin.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.zhiyin.data.PlazaApi
import com.zhiyin.data.PreferenceApi
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.launch

/** 喜好设置：选择感兴趣的标签，发现页"为你推荐"将据此推荐人设 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = { Text("喜好设置", fontWeight = FontWeight.SemiBold) },
            actions = {
                TextButton(onClick = {
                    scope.launch {
                        PreferenceApi.save(selected).onSuccess {
                            appVm.showToast("已保存，推荐将按喜好更新")
                            onBack()
                        }.onFailure { appVm.showToast(it.message ?: "保存失败") }
                    }
                }) { Text("保存", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("选择你感兴趣的标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "发现页的「为你推荐」会根据这些标签为你推荐人设",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { toggle(tag) },
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("自定义标签", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(20) },
                    placeholder = { Text("输入标签，如：猫咪") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = {
                    val t = input.trim()
                    if (t.isNotEmpty() && !selected.contains(t)) selected = (selected + t).take(20)
                    input = ""
                }) { Text("添加") }
            }
            Spacer(Modifier.height(14.dp))
            if (selected.isNotEmpty()) {
                Text("已选标签", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selected.forEach { tag ->
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
