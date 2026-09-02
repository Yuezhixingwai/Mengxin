package com.zhiyin.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class BrandTheme(
    val id: String,
    val label: String,
    val preview: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
)

private fun brandLight(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    tertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    inversePrimary: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = md_light_secondary,
    onSecondary = md_light_onSecondary,
    secondaryContainer = md_light_secondaryContainer,
    onSecondaryContainer = md_light_onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = Color.White,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = md_light_error,
    onError = md_light_onError,
    errorContainer = md_light_errorContainer,
    onErrorContainer = md_light_onErrorContainer,
    background = md_light_background,
    onBackground = md_light_onBackground,
    surface = md_light_surface,
    onSurface = md_light_onSurface,
    surfaceVariant = md_light_surfaceVariant,
    onSurfaceVariant = md_light_onSurfaceVariant,
    outline = md_light_outline,
    outlineVariant = md_light_outlineVariant,
    surfaceContainer = md_light_surfaceContainer,
    surfaceContainerLow = md_light_surfaceContainerLow,
    surfaceContainerHigh = md_light_surfaceContainerHigh,
    inverseSurface = md_light_inverseSurface,
    inverseOnSurface = md_light_inverseOnSurface,
    inversePrimary = inversePrimary,
)

private fun brandDark(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    inversePrimary: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = md_dark_secondary,
    onSecondary = md_dark_onSecondary,
    secondaryContainer = md_dark_secondaryContainer,
    onSecondaryContainer = md_dark_onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = md_dark_error,
    onError = md_dark_onError,
    errorContainer = md_dark_errorContainer,
    onErrorContainer = md_dark_onErrorContainer,
    background = md_dark_background,
    onBackground = md_dark_onBackground,
    surface = md_dark_surface,
    onSurface = md_dark_onSurface,
    surfaceVariant = md_dark_surfaceVariant,
    onSurfaceVariant = md_dark_onSurfaceVariant,
    outline = md_dark_outline,
    outlineVariant = md_dark_outlineVariant,
    surfaceContainer = md_dark_surfaceContainer,
    surfaceContainerLow = md_dark_surfaceContainerLow,
    surfaceContainerHigh = md_dark_surfaceContainerHigh,
    inverseSurface = md_dark_inverseSurface,
    inverseOnSurface = md_dark_inverseOnSurface,
    inversePrimary = inversePrimary,
)

object BrandThemes {

    private val Azure = BrandTheme(
        id = "azure",
        label = "静谧蓝",
        preview = Color(0xFF3E7EE8),
        light = brandLight(
            primary = Color(0xFF3E7EE8),
            primaryContainer = Color(0xFFD6E5FF),
            onPrimaryContainer = Color(0xFF0B2E63),
            tertiary = Color(0xFF39A1DD),
            tertiaryContainer = Color(0xFFD3EBFA),
            onTertiaryContainer = Color(0xFF093043),
            inversePrimary = Color(0xFFA9C8FF),
        ),
        dark = brandDark(
            primary = Color(0xFFA9C8FF),
            onPrimary = Color(0xFF0A305F),
            primaryContainer = Color(0xFF28477E),
            onPrimaryContainer = Color(0xFFD6E5FF),
            tertiary = Color(0xFF8CCDF5),
            onTertiary = Color(0xFF00344A),
            tertiaryContainer = Color(0xFF1D4C66),
            onTertiaryContainer = Color(0xFFD3EBFA),
            inversePrimary = Color(0xFF3E7EE8),
        ),
    )

    private val Mint = BrandTheme(
        id = "mint",
        label = "薄荷青",
        preview = Color(0xFF2FA98C),
        light = brandLight(
            primary = Color(0xFF2FA98C),
            primaryContainer = Color(0xFFCFF2E6),
            onPrimaryContainer = Color(0xFF064233),
            tertiary = Color(0xFF2E8B85),
            tertiaryContainer = Color(0xFFCEEEE9),
            onTertiaryContainer = Color(0xFF063B37),
            inversePrimary = Color(0xFF7FD9C0),
        ),
        dark = brandDark(
            primary = Color(0xFF8FE3CB),
            onPrimary = Color(0xFF00382C),
            primaryContainer = Color(0xFF0E5142),
            onPrimaryContainer = Color(0xFFCFF2E6),
            tertiary = Color(0xFF92E0D5),
            onTertiary = Color(0xFF003731),
            tertiaryContainer = Color(0xFF0F5049),
            onTertiaryContainer = Color(0xFFCEEEE9),
            inversePrimary = Color(0xFF2FA98C),
        ),
    )

    private val Sakura = BrandTheme(
        id = "sakura",
        label = "樱花粉",
        preview = Color(0xFFE86FA4),
        light = brandLight(
            primary = Color(0xFFE86FA4),
            primaryContainer = Color(0xFFFFDDF0),
            onPrimaryContainer = Color(0xFF5C0F35),
            tertiary = Color(0xFFB05FD0),
            tertiaryContainer = Color(0xFFF1DFFA),
            onTertiaryContainer = Color(0xFF3E1160),
            inversePrimary = Color(0xFFFFB1D1),
        ),
        dark = brandDark(
            primary = Color(0xFFFFB1D1),
            onPrimary = Color(0xFF54102F),
            primaryContainer = Color(0xFF8E2A57),
            onPrimaryContainer = Color(0xFFFFDDF0),
            tertiary = Color(0xFFDDB4F2),
            onTertiary = Color(0xFF3E1160),
            tertiaryContainer = Color(0xFF5B2E7E),
            onTertiaryContainer = Color(0xFFF1DFFA),
            inversePrimary = Color(0xFFE86FA4),
        ),
    )

    private val Violet = BrandTheme(
        id = "violet",
        label = "暮光紫",
        preview = Color(0xFF8B6FE8),
        light = brandLight(
            primary = Color(0xFF8B6FE8),
            primaryContainer = Color(0xFFE5DEFF),
            onPrimaryContainer = Color(0xFF2A156E),
            tertiary = Color(0xFF5FA8E8),
            tertiaryContainer = Color(0xFFD8EAFB),
            onTertiaryContainer = Color(0xFF0C3350),
            inversePrimary = Color(0xFFC7B9FF),
        ),
        dark = brandDark(
            primary = Color(0xFFC7B9FF),
            onPrimary = Color(0xFF2E1668),
            primaryContainer = Color(0xFF4B3399),
            onPrimaryContainer = Color(0xFFE5DEFF),
            tertiary = Color(0xFFA3CCF5),
            onTertiary = Color(0xFF0C3350),
            tertiaryContainer = Color(0xFF2A4A6C),
            onTertiaryContainer = Color(0xFFD8EAFB),
            inversePrimary = Color(0xFF8B6FE8),
        ),
    )

    private val Sunset = BrandTheme(
        id = "sunset",
        label = "暖阳橙",
        preview = Color(0xFFE8823E),
        light = brandLight(
            primary = Color(0xFFE8823E),
            primaryContainer = Color(0xFFFFDECC),
            onPrimaryContainer = Color(0xFF5C2604),
            tertiary = Color(0xFFD96A57),
            tertiaryContainer = Color(0xFFFFE2DB),
            onTertiaryContainer = Color(0xFF5C1A10),
            inversePrimary = Color(0xFFFFB68C),
        ),
        dark = brandDark(
            primary = Color(0xFFFFB68C),
            onPrimary = Color(0xFF4F1E00),
            primaryContainer = Color(0xFF9A4A15),
            onPrimaryContainer = Color(0xFFFFDECC),
            tertiary = Color(0xFFFFB4A5),
            onTertiary = Color(0xFF5C1A10),
            tertiaryContainer = Color(0xFF8E3B2B),
            onTertiaryContainer = Color(0xFFFFE2DB),
            inversePrimary = Color(0xFFE8823E),
        ),
    )

    val all: List<BrandTheme> = listOf(Azure, Mint, Sakura, Violet, Sunset)

    fun byId(id: String?): BrandTheme = all.find { it.id == id } ?: Azure
}
