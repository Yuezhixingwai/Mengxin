package com.zhiyin.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.data.PersonaLight
import com.zhiyin.data.PlazaApi
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.DefaultAvatar
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PersonaSearchScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    fun doSearch(reset: Boolean) {
        val q = query.trim()
        if (q.isEmpty()) {
            list = emptyList()
            total = 0
            searched = false
            return
        }
        val p = if (reset) 1 else page + 1
        searching = true
        scope.launch {
            PlazaApi.plaza(page = p, limit = 20, sort = "hot", q = q)
                .onSuccess { (t, items) ->
                    total = t
                    page = p
                    list = if (reset) items else (list + items).distinctBy { it.id }
                    searched = true
                }
                .onFailure { appVm.showToast(it.message ?: "搜索失败") }
            searching = false
        }
    }

    fun onQueryChange(v: String) {
        query = v
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(350)
            doSearch(true)
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "搜索人设",
                color = colors.surface,
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TextField(
                value = query,
                onValueChange = { onQueryChange(it.take(30)) },
                label = "搜索人设名称、关键词",
                useLabelAsPlaceholder = true,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.onSurfaceContainerVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (searching && list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (!searched || query.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = colors.surfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("输入名称或关键词搜索人设", color = colors.onSurfaceVariantSummary)
                    }
                }
                return@Column
            }

            RubberBandBox(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item {
                        Text(
                            "共 $total 个结果",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    items(list, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDetail(p.id) }
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)), contentAlignment = Alignment.Center) {
                                if (p.avatarUrl.isNotEmpty()) {
                                    RemoteImage(
                                        url = p.avatarUrl,
                                        contentDescription = p.name,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholder = { DefaultAvatar(modifier = Modifier.size(46.dp), size = 46.dp, shape = RoundedCornerShape(23.dp)) },
                                    )
                                } else {
                                    DefaultAvatar(modifier = Modifier.size(46.dp), size = 46.dp, shape = RoundedCornerShape(23.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    (p.keywords.ifEmpty { p.descriptionLight }).take(60),
                                    fontSize = 13.sp,
                                    color = colors.onSurfaceVariantSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("🔥 ${p.hot}", fontSize = 12.sp, color = colors.primary)
                        }
                    }
                    if (list.size < total) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp).clickable { doSearch(false) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (searching) "搜索中…" else "加载更多",
                                    fontSize = 12.sp,
                                    color = colors.primary,
                                )
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                                Text("没有找到相关人设", color = colors.onSurfaceVariantSummary)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
