package com.zhiyin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiyin.data.NotificationApi
import com.zhiyin.data.PersonaLight
import com.zhiyin.data.PlazaApi
import com.zhiyin.ui.DefaultAvatar
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val cardTextShadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 1.2f), blurRadius = 6f)

@Composable
fun PersonaCoverCard(
    p: PersonaLight,
    coverHeight: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (p.coverUrl.isNotEmpty()) {
                RemoteImage(
                    url = p.coverUrl,
                    contentDescription = p.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = { CoverPlaceholder(p.name) },
                    maxDim = 720,
                )
            } else {
                CoverPlaceholder(p.name)
            }
            if (p.hot > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                ) {
                    Text(
                        "🔥 ${p.hot}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    p.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(shadow = cardTextShadow),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = p.slogan.ifEmpty { p.keywords }
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        color = Color.White.copy(alpha = 0.95f),
                        style = MaterialTheme.typography.labelSmall.copy(shadow = cardTextShadow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverPlaceholder(name: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = "无图像",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}

/** 榜单行（01/02/03 网易云式名次） */
@Composable
fun HotRankRow(
    p: PersonaLight,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rankText = if (p.rank > 0) "%02d".format(p.rank) else ""
        Text(
            rankText,
            color = if (p.rank in 1..3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(38.dp),
        )
        if (p.avatarUrl.isNotEmpty()) {
            RemoteImage(
                url = p.avatarUrl,
                contentDescription = p.name,
                modifier = Modifier.size(46.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = {
                    DefaultAvatar(modifier = Modifier.size(46.dp), size = 46.dp, shape = CircleShape)
                },
            )
        } else {
            DefaultAvatar(modifier = Modifier.size(46.dp), size = 46.dp, shape = CircleShape)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (p.keywords.isNotEmpty()) p.keywords else p.descriptionLight,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "🔥 ${p.hot}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    accent: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (accent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun DiscoverScreen(
    appVm: AppViewModel,
    onOpenDetail: (Int) -> Unit,
    onOpenHotList: () -> Unit,
    onOpenMessageCenter: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf(listOf<String>()) }
    var selectedCategory by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("hot") }
    var bannerList by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var recList by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var hotList by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var gridList by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var gridPage by remember { mutableIntStateOf(0) }
    var gridTotal by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var loadingMore by remember { mutableStateOf(false) }
    var unread by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    val pagerState = rememberPagerState(pageCount = { bannerList.size.coerceAtLeast(1) })

    LaunchedEffect(bannerList.size) {
        if (bannerList.size > 1) {
            while (true) {
                delay(4500)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % bannerList.size)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            NotificationApi.unreadCount().onSuccess { unread = it }
            delay(60_000)
        }
    }

    fun loadBase() {
        scope.launch {
            PlazaApi.categories().onSuccess { categories = it }
            PlazaApi.hot(8).onSuccess { hotList = it }
            PlazaApi.recommend(14).onSuccess { list ->
                bannerList = list.take(6)
                recList = list.drop(6)
            }
            loading = false
        }
    }

    fun loadGrid(reset: Boolean) {
        if (loadingMore) return
        loadingMore = true
        val page = if (reset) 1 else gridPage + 1
        scope.launch {
            PlazaApi.plaza(page = page, limit = 20, category = selectedCategory, sort = sort)
                .onSuccess { (total, list) ->
                    gridTotal = total
                    gridPage = page
                    gridList = if (reset) list else (gridList + list).distinctBy { it.id }
                }
                .onFailure { if (reset) appVm.showToast(it.message ?: "加载失败") }
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) { loadBase(); loadGrid(true) }
    LaunchedEffect(selectedCategory, sort) { loadGrid(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f).clickable(onClick = onOpenSearch),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "搜索人设、标签",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            BadgedBox(badge = {
                if (unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") }
            }) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable(onClick = onOpenMessageCenter),
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "消息中心",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        RubberBandBox(
            modifier = Modifier.fillMaxSize(),
            refreshEnabled = true,
            onRefresh = {
                loadBase()
                loadGrid(true)
            },
        ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (bannerList.isNotEmpty()) {
                item {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(190.dp),
                        pageSpacing = 12.dp,
                    ) { page ->
                        val p = bannerList[page % bannerList.size]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onOpenDetail(p.id) }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            if (p.coverUrl.isNotEmpty()) {
                                RemoteImage(
                                    url = p.coverUrl,
                                    contentDescription = p.name,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholder = { CoverPlaceholder(p.name) },
                                )
                            } else CoverPlaceholder(p.name)
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                            ) {
                                Text(p.name, color = Color.White, style = MaterialTheme.typography.titleLarge.copy(shadow = cardTextShadow), fontWeight = FontWeight.Black)
                                if (p.keywords.isNotEmpty()) {
                                    Text(p.keywords, color = Color.White.copy(alpha = 0.95f), style = MaterialTheme.typography.bodySmall.copy(shadow = cardTextShadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (bannerList.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            repeat(bannerList.size) { i ->
                                Box(
                                    Modifier
                                        .padding(horizontal = 3.dp)
                                        .width(if (i == pagerState.currentPage % bannerList.size) 16.dp else 6.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (i == pagerState.currentPage % bannerList.size)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 10.dp),
                    ) {
                        Spacer(Modifier.width(16.dp))
                        CategoryChip("全部", selectedCategory.isEmpty()) { selectedCategory = "" }
                        categories.forEach { c ->
                            CategoryChip(c, selectedCategory == c) { selectedCategory = c }
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                }
            }

            if (hotList.isNotEmpty()) {
                item {
                    SectionTitle("热度榜", accent = true) {
                        Row(
                            Modifier.clickable(onClick = onOpenHotList),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("完整榜单", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column {
                            hotList.take(3).forEachIndexed { i, p ->
                                HotRankRow(p) { onOpenDetail(p.id) }
                                if (i < minOf(3, hotList.size) - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            if (recList.isNotEmpty()) {
                item { SectionTitle("为你推荐", accent = true) }
                item {
                    Text(
                        "根据你的喜好为你挑选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                        items(recList, key = { "rec${it.id}" }) { p ->
                            PersonaCoverCard(p = p, coverHeight = 150, onClick = { onOpenDetail(p.id) }, modifier = Modifier.width(150.dp).padding(end = 10.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selectedCategory.isEmpty()) "全部人设" else selectedCategory,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "共 ${gridTotal} 个" + if (selectedCategory.isNotEmpty()) " · ${selectedCategory}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SortToggle(sort) { s -> sort = s }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onOpenCreate,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("发布", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            val rows = gridList.chunked(2)
            items(rows, key = { r -> r.joinToString("-") { it.id.toString() } }) { row ->
                Row(modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    row.forEach { p ->
                        PersonaCoverCard(
                            p = p,
                            coverHeight = 160,
                            onClick = { onOpenDetail(p.id) },
                            modifier = Modifier.weight(1f).padding(horizontal = 5.dp),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (gridList.size < gridTotal) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { loadGrid(false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "加载更多",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (gridList.isEmpty() && !loadingMore) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("这里还没有人设，快来发布第一个吧", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SortToggle(current: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("hot" to "热度", "new" to "最新").forEach { (v, label) ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (current == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (current == v) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onChange(v) }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotListScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    var list by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        PlazaApi.hot(50).onSuccess { list = it }.onFailure { appVm.showToast(it.message ?: "加载失败") }
        loading = false
    }
    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.TopAppBar(
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            title = { Text("热度榜", fontWeight = FontWeight.SemiBold) },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize()) {
            LazyColumn {
                items(list, key = { it.id }) { p ->
                    HotRankRow(p) { onOpenDetail(p.id) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
            }
        }
    }
}
