package com.zhiyin.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zhiyin.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SplashContent(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1600)
        onTimeout()
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val logoScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.55f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "logoScale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(420),
        label = "logoAlpha",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(520, delayMillis = 220),
        label = "textAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "灵心",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
                    .shadow(10.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "灵心",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "懂你的AI陪伴",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
            Spacer(Modifier.height(40.dp))
            BouncingDots()
        }
    }
}

@Composable
private fun BouncingDots() {
    val transition = rememberInfiniteTransition(label = "loadingDots")
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(3) { i ->
            val dy by transition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(380, delayMillis = i * 140, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, dy.roundToInt()) }
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
