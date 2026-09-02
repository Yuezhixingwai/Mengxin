package com.zhiyin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AvatarBg = Color(0xFFC5CFDE)
private val AvatarFg = Color(0xFFFBFDFF)

@Composable
fun DefaultAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape((size.value * 0.28f).dp),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(AvatarBg),
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            drawCircle(color = AvatarFg, radius = r * 0.36f, center = Offset(r, r * 0.68f))
            drawCircle(color = AvatarFg, radius = r * 0.70f, center = Offset(r, r * 2.10f))
        }
    }
}