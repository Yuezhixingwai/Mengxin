package com.zhiyin.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ImageCropperDialog(
    path: String,
    frameAspect: Float,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenW = configuration.screenWidthDp.toFloat()
    val screenH = configuration.screenHeightDp.toFloat()
    var frameW = screenW
    var frameH = if (frameAspect > 0f) frameW / frameAspect else screenH
    if (frameH > screenH) {
        frameH = screenH
        frameW = frameH * frameAspect
    }

    val bmp by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeSampledBitmap(path, 1600) }
    }
    var framePx by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(frameW.dp, frameH.dp)
                    .onSizeChanged { framePx = IntSize(it.width, it.height) }
                    .clipToBounds()
                    .pointerInput(bmp, framePx) {
                        val b = bmp ?: return@pointerInput
                        if (framePx.width <= 0 || framePx.height <= 0) return@pointerInput
                        val fitNow = maxOf(
                            framePx.width.toFloat() / b.width,
                            framePx.height.toFloat() / b.height,
                        )
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset += pan
                            val drawnW = b.width * fitNow * scale
                            val drawnH = b.height * fitNow * scale
                            val maxOX = maxOf(0f, (drawnW - framePx.width) / 2f)
                            val maxOY = maxOf(0f, (drawnH - framePx.height) / 2f)
                            offset = Offset(
                                offset.x.coerceIn(-maxOX, maxOX),
                                offset.y.coerceIn(-maxOY, maxOY),
                            )
                        }
                    },
            ) {
                bmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                } ?: Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "取消",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "确认",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            val b = bmp ?: return@clickable
                            if (framePx.width <= 0 || framePx.height <= 0) return@clickable
                            val fitNow = maxOf(
                                framePx.width.toFloat() / b.width,
                                framePx.height.toFloat() / b.height,
                            )
                            val drawnW = b.width * fitNow * scale
                            val drawnH = b.height * fitNow * scale
                            val bx0 = (drawnW / 2f - framePx.width / 2f - offset.x) / (fitNow * scale)
                            val by0 = (drawnH / 2f - framePx.height / 2f - offset.y) / (fitNow * scale)
                            val bw = framePx.width / (fitNow * scale)
                            val bh = framePx.height / (fitNow * scale)
                            val l = bx0.coerceIn(0f, (b.width - bw).coerceAtLeast(0f)).roundToInt()
                            val t = by0.coerceIn(0f, (b.height - bh).coerceAtLeast(0f)).roundToInt()
                            val w = bw.roundToInt().coerceAtMost(b.width - l)
                            val h = bh.roundToInt().coerceAtMost(b.height - t)
                            if (w <= 0 || h <= 0) return@clickable
                            onConfirm(Bitmap.createBitmap(b, l, t, w, h))
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun decodeSampledBitmap(path: String, target: Int): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > target || opts.outHeight / sample > target) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = maxOf(1, sample) })
    } catch (_: Exception) {
        null
    }
}
