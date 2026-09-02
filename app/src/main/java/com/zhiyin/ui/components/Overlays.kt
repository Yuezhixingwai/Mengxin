package com.zhiyin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
fun LingXinDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String? = null,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    danger: Boolean = false,
    dismissible: Boolean = true,
    onConfirm: () -> Unit = {},
    content: @Composable ColumnScopeAlias.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!text.isNullOrEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                    )
                }
                val scope = ColumnScopeAlias
                scope.content()
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (dismissText != null) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                        ) {
                            Text(dismissText)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (danger) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            contentColor = if (danger) MaterialTheme.colorScheme.onError
                            else MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}

object ColumnScopeAlias

class ToastState internal constructor() {
    internal var id by mutableLongStateOf(0L)
    var message by mutableStateOf<String?>(null)
        internal set

    fun show(message: String) {
        id += 1
        this.message = message
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun LingXinToastHost(message: String?, id: Long) {
    var lastMessage by remember { mutableStateOf<String?>(null) }
    if (message != null) lastMessage = message
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it },
        ) {
            Surface(
                modifier = Modifier.padding(top = 64.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        lastMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun LingXinToastHost(state: ToastState) {
    val message = state.message
    var lastMessage by remember { mutableStateOf<String?>(null) }
    if (message != null) lastMessage = message
    LaunchedEffect(state.id) {
        if (state.id > 0) {
            delay(2200)
            state.message = null
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it },
        ) {
            Surface(
                modifier = Modifier.padding(top = 64.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        lastMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun LingXinSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val maxRaisePx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenHeightDp * 0.45f).dp.toPx()
    }
    var dragOffsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            ) {},
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY = (dragOffsetY + dragAmount).coerceIn(-maxRaisePx, 0f)
                                    }
                                }
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(bottom = 12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp, bottom = 6.dp)
                                    .size(width = 36.dp, height = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(2.dp),
                                    )
                                    .align(Alignment.CenterHorizontally),
                            )
                            content()
                        }
                    }
                    if (dragOffsetY < 0f) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(with(LocalDensity.current) { (-dragOffsetY).toDp() })
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                ) {},
                        )
                    }
                }
            }
        }
    }
}

data class MenuItemSpec(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
)

@Composable
fun LingXinMenuOverlay(
    items: List<MenuItemSpec>,
    alignEnd: Boolean = true,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { onDismiss() },
            )
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(if (alignEnd) Alignment.TopEnd else Alignment.TopStart),
                enter = fadeIn(tween(150)) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(180),
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        if (alignEnd) 1f else 0f, 0f
                    ),
                ),
                exit = fadeOut(tween(120)),
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 76.dp, end = if (alignEnd) 16.dp else 0.dp, start = if (alignEnd) 0.dp else 16.dp)
                        .widthIn(min = 176.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(index) }
                                    .padding(horizontal = 20.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (item.icon != null) {
                                    Icon(
                                        item.icon,
                                        contentDescription = null,
                                        tint = if (item.danger) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.danger) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun brandGradient(): Brush = Brush.linearGradient(
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
    )
)
