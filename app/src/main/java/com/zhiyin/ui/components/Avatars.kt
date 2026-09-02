package com.zhiyin.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.zhiyin.data.AvatarStore

@Composable
fun PersonaAvatar(
    contactId: Int,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var bmp by remember(contactId) { mutableStateOf<ImageBitmap?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(contactId) {
        if (contactId > 0) {
            AvatarStore.loadPersonaAvatar(ctx, contactId) { loaded -> bmp = loaded }
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = bmp != null,
            transitionSpec = {
                (fadeIn(tween(500)) + scaleIn(initialScale = 0.85f, animationSpec = tween(500))) togetherWith
                        fadeOut(tween(250))
            },
            label = "personaAvatar",
        ) { loaded ->
            if (loaded) {
                Image(
                    bitmap = bmp!!,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            } else {
                com.zhiyin.ui.DefaultAvatar(size = size)
            }
        }
    }
}

@Composable
fun GroupAvatar(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Groups,
            contentDescription = "群聊",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun UserAvatar(avatarUrl: String?, size: Dp, modifier: Modifier = Modifier, fallback: String = "我") {
    var bmp by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(avatarUrl) {
        AvatarStore.loadUserAvatar(ctx, avatarUrl) { loaded -> bmp = loaded }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!,
                contentDescription = "我的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            com.zhiyin.ui.DefaultAvatar(size = size)
        }
    }
}
