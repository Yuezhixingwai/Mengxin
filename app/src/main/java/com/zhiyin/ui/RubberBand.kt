package com.zhiyin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 列表容器：iOS 式回弹 + 下拉刷新。
 *
 * miuix 实现：回弹由 [overScrollVertical] 提供，下拉刷新由 miuix [PullToRefresh] 提供。
 * 对外契约与旧版一致：触发时回调一次 [onRefresh]，内部展示约 1.2s 刷新动画。
 */
@Composable
fun RubberBandBox(
    modifier: Modifier = Modifier,
    refreshEnabled: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val pullRefresh = refreshEnabled && onRefresh != null
    if (pullRefresh) {
        var refreshing by remember { mutableStateOf(false) }
        LaunchedEffect(refreshing) {
            if (refreshing) {
                onRefresh!!()
                delay(1200)
                refreshing = false
            }
        }
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true },
            modifier = modifier,
            pullToRefreshState = rememberPullToRefreshState(),
            color = MiuixTheme.colorScheme.primary,
            refreshTexts = listOf("下拉刷新", "释放立即刷新", "正在刷新…", "刷新成功"),
        ) {
            Box(modifier = Modifier.overScrollVertical()) { content() }
        }
    } else {
        Box(modifier = modifier.overScrollVertical()) { content() }
    }
}
