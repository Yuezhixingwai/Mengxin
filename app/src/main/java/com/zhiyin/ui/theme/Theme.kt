package com.zhiyin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

fun Colors.globalBackground(containerAlpha: Float): Colors {
    val a = containerAlpha.coerceIn(0f, 1f)
    return copy(
        background = Color.Transparent,
        surface = Color.Transparent,
        surfaceVariant = surfaceVariant.copy(alpha = a),
        surfaceContainer = surfaceContainer.copy(alpha = a),
        surfaceContainerHigh = surfaceContainerHigh.copy(alpha = a),
        surfaceContainerHighest = surfaceContainerHighest.copy(alpha = a),
    )
}

val AppTextStyles: TextStyles = defaultTextStyles().copy(
    main = TextStyle(fontSize = 16.sp),
    paragraph = TextStyle(fontSize = 16.sp, lineHeight = 1.25f.em),
    body1 = TextStyle(fontSize = 15.sp),
    body2 = TextStyle(fontSize = 13.sp),
    button = TextStyle(fontSize = 15.sp),
    footnote1 = TextStyle(fontSize = 12.sp),
    footnote2 = TextStyle(fontSize = 11.sp),
    headline1 = TextStyle(fontSize = 16.sp),
    headline2 = TextStyle(fontSize = 15.sp),
    subtitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    title1 = TextStyle(fontSize = 28.sp),
    title2 = TextStyle(fontSize = 22.sp),
    title3 = TextStyle(fontSize = 18.sp),
    title4 = TextStyle(fontSize = 17.sp),
)

@Composable
fun LingXinTheme(
    darkTheme: Boolean,
    themeId: String?,
    content: @Composable () -> Unit,
) {
    val controller = remember(darkTheme, themeId) {
        val brand = BrandThemes.byId(themeId)
        if (brand == null) {
            ThemeController(if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        } else {
            ThemeController(
                if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = brand.seedColor,
            )
        }
    }
    MiuixTheme(
        controller = controller,
        textStyles = AppTextStyles,
        content = content,
    )
}
