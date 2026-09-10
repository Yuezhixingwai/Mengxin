package com.zhiyin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

fun ColorScheme.globalBackground(containerAlpha: Float): ColorScheme {
    val a = containerAlpha.coerceIn(0f, 1f)
    return copy(
        background = Color.Transparent,
        surface = Color.Transparent,
        surfaceVariant = surfaceVariant.copy(alpha = a),
        surfaceContainerLowest = surfaceContainerLowest.copy(alpha = a),
        surfaceContainerLow = surfaceContainerLow.copy(alpha = a),
        surfaceContainer = surfaceContainer.copy(alpha = a),
        surfaceContainerHigh = surfaceContainerHigh.copy(alpha = a),
        surfaceContainerHighest = surfaceContainerHighest.copy(alpha = a),
    )
}

@Composable
fun LingXinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeId: String? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val brand = BrandThemes.byId(themeId)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> brand.dark
        else -> brand.light
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}