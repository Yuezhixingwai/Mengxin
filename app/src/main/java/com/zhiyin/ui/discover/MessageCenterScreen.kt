package com.zhiyin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhiyin.data.NotificationApi
import com.zhiyin.data.NotifyItem
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.launch

private fun notifyIcon(type: String, tint: Color): Pair<ImageVector, Color> = when (type) {
    "like" -> Icons.Filled.Favorite to Color(0xFFEC4141)
    "comment" -> Icons.Filled.Comment to tint
    "follow" -> Icons.Filled.PersonAdd to tint
    "new_persona" -> Icons.Filled.Star to Color(0xFFFFB300)
    else -> Icons.Filled.Notifications to tint
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCenterScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenPersona: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<NotifyItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    fun load(reset: Boolean) {
        val p = if (reset) 1 else page + 1
        scope.launch {
            NotificationApi.list(p).onSuccess { (t, list) ->
                total = t
                page = p
                items = if (reset) list else (items + list).distinctBy { it.id }
                loading = false
            }.onFailure { appVm.showToast(it.message ?: "加载失败"); loading = false }
        }
    }

    LaunchedEffect(Unit) { load(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            title = { Text("消息中心", fontWeight = FontWeight.SemiBold) },
            actions = {
                TextButton(onClick = {
                    scope.launch {
                        NotificationApi.markRead(all = true).onSuccess {
                            appVm.showToast("全部已读")
                            load(true)
                        }
                    }
                }) { Text("全部已读", color = MaterialTheme.colorScheme.primary) }
            },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { n ->
                val (icon, tint) = notifyIcon(n.type, MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                if (!n.read) NotificationApi.markRead(id = n.id)
                                n.personaId?.let { onOpenPersona(it) }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = n.type, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                n.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                            )
                            if (!n.read) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFEC4141)),
                                )
                            }
                        }
                        Text(
                            n.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        com.zhiyin.logic.net.ApiGateway.toBeijingTime(n.createdAt, "MM-dd HH:mm"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.size < total) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp).clickable { load(false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("加载更多", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("暂无消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
