package com.zhiyin.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

fun Modifier.acrylicSource(state: HazeState): Modifier = hazeSource(state)

fun Modifier.acrylic(
    state: HazeState,
    tint: Color,
    blurRadius: Dp = 36.dp,
    noiseFactor: Float = 0.10f,
): Modifier = hazeEffect(
    state = state,
    style = HazeDefaults.style(
        backgroundColor = tint,
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
    ),
)
