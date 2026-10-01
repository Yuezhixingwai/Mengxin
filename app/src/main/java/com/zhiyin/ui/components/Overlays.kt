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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LingXinDialog(
    show: Boolean = true,
    onDismiss: () -> Unit,
    title: String,
    text: String? = null,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    danger: Boolean = false,
    dismissible: Boolean = true,
    onConfirm: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = text,
        onDismissRequest = { if (dismissible) onDismiss() },
        content = {
            content()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (dismissText != null) {
                    TextButton(
                        text = dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.width(20.dp))
                }
                TextButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        if (dismissible) onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = if (danger) {
                        ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.error,
                            textColor = MiuixTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.textButtonColorsPrimary()
                    },
                )
            }
        },
    )
}

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
private fun ToastCapsule(message: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .background(colors.onBackground.copy(alpha = 0.92f), RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = colors.background,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            fontSize = 14.sp,
            color = colors.background,
        )
    }
}

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
            Box(modifier = Modifier.padding(top = 64.dp)) {
                ToastCapsule(lastMessage.orEmpty())
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
            Box(modifier = Modifier.padding(top = 64.dp)) {
                ToastCapsule(lastMessage.orEmpty())
            }
        }
    }
}

@Composable
fun LingXinSheet(
    show: Boolean = true,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        content = {
            Box(modifier = Modifier.fillMaxWidth()) { content() }
        },
    )
}

data class MenuItemSpec(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
)

@Composable
fun LingXinMenuOverlay(
    show: Boolean = true,
    items: List<MenuItemSpec>,
    alignEnd: Boolean = true,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120)),
    ) {
        val colors = MiuixTheme.colorScheme
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
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
                    transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f),
                ),
                exit = fadeOut(tween(120)),
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 76.dp, end = if (alignEnd) 16.dp else 0.dp, start = if (alignEnd) 0.dp else 16.dp)
                        .widthIn(min = 176.dp)
                        .background(colors.surfaceContainer, RoundedCornerShape(20.dp)),
                ) {
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
                                    tint = if (item.danger) colors.error else colors.onSurfaceContainerVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                item.label,
                                fontSize = 14.sp,
                                color = if (item.danger) colors.error else colors.onSurface,
                            )
                            if (index < items.lastIndex) {
                            }
                        }
                        if (index < items.lastIndex) {
                            Box(
                                Modifier
                                    .padding(start = 20.dp)
                                    .fillMaxWidth()
                                    .height(0.75.dp)
                                    .background(colors.dividerLine),
                            )
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
        MiuixTheme.colorScheme.primary,
        MiuixTheme.colorScheme.primaryVariant,
    )
)

