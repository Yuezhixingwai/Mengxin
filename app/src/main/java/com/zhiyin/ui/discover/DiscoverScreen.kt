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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiyin.data.NotificationApi
import com.zhiyin.data.PersonaLight
import com.zhiyin.data.PlazaApi
import com.zhiyin.ui.BackButton
import com.zhiyin.ui.DefaultAvatar
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.components.RemoteImage
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
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
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    p.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    style = TextStyle(shadow = cardTextShadow),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = p.slogan.ifEmpty { p.keywords }
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
                        style = TextStyle(shadow = cardTextShadow),
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
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = "无图像",
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
fun HotRankRow(
    p: PersonaLight,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
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
            color = if (p.rank in 1..3) colors.primary else colors.onSurfaceContainerVariant,
            fontSize = 20.sp,
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
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (p.keywords.isNotEmpty()) p.keywords else p.descriptionLight,
                fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "🔥 ${p.hot}",
            fontSize = 12.sp,
            color = colors.primary,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    accent: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
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
                    if (accent) colors.primary
                    else colors.surfaceVariant
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
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
    val colors = MiuixTheme.colorScheme
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
    var gridFailed by remember { mutableStateOf(false) }
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
            try {
                coroutineScope {
                    val c = async { PlazaApi.categories() }
                    val h = async { PlazaApi.hot(8) }
                    val r = async { PlazaApi.recommend(14) }
                    c.await().onSuccess { categories = it }
                    h.await().onSuccess { hotList = it }
                    r.await().onSuccess { list ->
                        bannerList = list.take(6)
                        recList = list.drop(6)
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    fun loadGrid(reset: Boolean) {
        if (loadingMore) return
        loadingMore = true
        val page = if (reset) 1 else gridPage + 1
        scope.launch {
            try {
                PlazaApi.plaza(page = page, limit = 20, category = selectedCategory, sort = sort)
                    .onSuccess { (total, list) ->
                        gridTotal = total
                        gridPage = page
                        gridList = if (reset) list else (gridList + list).distinctBy { it.id }
                        gridFailed = false
                    }
                    .onFailure {
                        if (reset) {
                            gridFailed = true
                            gridList = emptyList()
                            gridTotal = 0
                            appVm.showToast(it.message ?: "加载失败")
                        }
                    }
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadBase()
        loadGrid(true)
        var prevCat = selectedCategory
        var prevSort = sort
        snapshotFlow { selectedCategory to sort }.collect { (c, s) ->
            if (c != prevCat || s != prevSort) {
                prevCat = c
                prevSort = s
                loadGrid(true)
            }
        }
    }

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
                color = colors.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.onSurfaceContainerVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "搜索人设、标签",
                        fontSize = 14.sp,
                        color = colors.onSurfaceContainerVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            BadgedBox(badge = {
                if (unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") }
            }) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.clickable(onClick = onOpenMessageCenter),
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "消息中心",
                        tint = colors.onSurface,
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
                gridFailed = false
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
                                .background(colors.surfaceContainerHigh),
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
                                Text(p.name, color = Color.White, fontSize = 20.sp, style = TextStyle(shadow = cardTextShadow), fontWeight = FontWeight.Black)
                                if (p.keywords.isNotEmpty()) {
                                    Text(p.keywords, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, style = TextStyle(shadow = cardTextShadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                                colors.primary
                                            else colors.surfaceVariant
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
                            Text("完整榜单", fontSize = 12.sp, color = colors.onSurfaceContainerVariant)
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceContainerVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column {
                            hotList.take(3).forEachIndexed { i, p ->
                                HotRankRow(p) { onOpenDetail(p.id) }
                                if (i < minOf(3, hotList.size) - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
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
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
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
                            .background(colors.surfaceVariant),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selectedCategory.isEmpty()) "全部人设" else selectedCategory,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                        )
                        Text(
                            "共 ${gridTotal} 个" + if (selectedCategory.isNotEmpty()) " · ${selectedCategory}" else "",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                    SortToggle(sort) { s -> sort = s }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onOpenCreate,
                        shape = RoundedCornerShape(16.dp),
                        color = colors.primary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("发布", fontSize = 12.sp, color = colors.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
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
                                fontSize = 12.sp,
                                color = colors.primary,
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
                    ) {
                        if (gridFailed) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "加载失败，请检查网络后重试",
                                    color = colors.error,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = colors.primary,
                                    modifier = Modifier.clickable {
                                        gridFailed = false
                                        loadBase()
                                        loadGrid(true)
                                    },
                                ) {
                                    Text(
                                        "点击重试",
                                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                                        color = colors.onPrimary,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        } else {
                            Text("这里还没有人设，快来发布第一个吧", color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primary else colors.surfaceContainerHigh,
        modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onClick),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) colors.onPrimary else colors.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SortToggle(current: String, onChange: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("hot" to "热度", "new" to "最新").forEach { (v, label) ->
            Text(
                label,
                fontSize = 12.sp,
                color = if (current == v) colors.primary else colors.onSurfaceContainerVariant,
                fontWeight = if (current == v) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onChange(v) }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun HotListScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var list by remember { mutableStateOf<List<PersonaLight>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey) {
        loading = true
        try {
            PlazaApi.hot(50)
                .onSuccess { list = it; failed = false }
                .onFailure { failed = true; appVm.showToast(it.message ?: "加载失败") }
        } finally {
            loading = false
        }
    }
    Scaffold(
        containerColor = colors.surface,
        topBar = {
            SmallTopAppBar(
                title = "热度榜",
                color = colors.surface,
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "加载失败，请检查网络后重试",
                            color = colors.error,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = colors.primary,
                            modifier = Modifier.clickable { failed = false; reloadKey++ },
                        ) {
                            Text(
                                "点击重试",
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                                color = colors.onPrimary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                } else {
                    Text("暂无数据", color = colors.onSurfaceVariantSummary)
                }
            }
        } else {
            RubberBandBox(modifier = Modifier.fillMaxSize().padding(padding)) {
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
