package com.zhiyin.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.zhiyin.data.PersonaLight
import com.zhiyin.data.PlazaApi
import com.zhiyin.ui.DefaultAvatar
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaSearchScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onQueryChange(it.take(30)) },
                    placeholder = { Text("搜索人设名称、关键词", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
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
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("输入名称或关键词搜索人设", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            items(list, key = { it.id }) { p ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = {
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
                    },
                    headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            (p.keywords.ifEmpty { p.descriptionLight }).take(60),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Text("🔥 ${p.hot}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickableItem { onOpenDetail(p.id) },
                )
            }
            if (list.size < total) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp).clickableItem { doSearch(false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (searching) "搜索中…" else "加载更多",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        Text("没有找到相关人设", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
