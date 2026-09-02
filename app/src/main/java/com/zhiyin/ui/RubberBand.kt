package com.zhiyin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun RubberBandBox(
    modifier: Modifier = Modifier,
    refreshEnabled: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val maxDisp = with(LocalDensity.current) { 120.dp.toPx() }
    val refreshThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var refreshing by remember { mutableStateOf(false) }
    var fadingOut by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }
    val fadeAlpha = remember { Animatable(1f) }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(1200)
            refreshing = false
        }
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            wasRefreshing = true
            fadingOut = false
            fadeAlpha.snapTo(1f)
        } else if (wasRefreshing) {
            wasRefreshing = false
            fadingOut = true
            fadeAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            fadingOut = false
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val current = offset
                if (current == 0f) return Offset.Zero
                val dy = available.y
                if (dy * current < 0f) {
                    val consumed = if (abs(dy) >= abs(current)) -current else dy
                    offset = current + consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                val factor = 1f - (abs(offset) / maxDisp).coerceIn(0f, 1f)
                offset += dy * factor
                return Offset(0f, dy)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (refreshEnabled && !refreshing && offset >= refreshThreshold) {
                    refreshing = true
                    onRefresh?.invoke()
                }
                if (offset != 0f) {
                    animate(
                        initialValue = offset,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) { value, _ ->
                        offset = value
                    }
                }
                return available
            }
        }
    }

    val pastThreshold by remember(refreshThreshold) { derivedStateOf { offset >= refreshThreshold } }

    Box(modifier.nestedScroll(connection)) {
        Box(
            modifier = Modifier
                .clipToBounds()
                .graphicsLayer { translationY = offset },
        ) {
            content()
        }
        if (onRefresh != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .graphicsLayer {
                        alpha = when {
                            refreshing -> 1f
                            fadingOut -> fadeAlpha.value
                            offset > 1f -> (offset / refreshThreshold).coerceIn(0f, 1f)
                            else -> 0f
                        }
                        translationY = if (offset > 1f || refreshing || fadingOut) 0f else -12f
                    },
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                if (pastThreshold) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                refreshing -> "正在刷新…"
                                pastThreshold -> "释放立即刷新"
                                else -> "下拉刷新"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
