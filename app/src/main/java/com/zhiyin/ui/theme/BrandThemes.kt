package com.zhiyin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 品牌主题 = 一个种子色。
 *
 * 默认主题（themeId == null）直接使用 miuix 原生配色（lightColorScheme()/darkColorScheme() 的默认值）；
 * 其余品牌主题以各自的种子色通过 miuix 的 Monet 动态取色生成成套的明暗色板。
 */
data class BrandTheme(
    val id: String,
    val label: String,
    val seedColor: Color,
)

object BrandThemes {

    private val Azure = BrandTheme("azure", "静谧蓝", Color(0xFF3E7EE8))
    private val Mint = BrandTheme("mint", "薄荷青", Color(0xFF2FA98C))
    private val Sakura = BrandTheme("sakura", "樱花粉", Color(0xFFE86FA4))
    private val Violet = BrandTheme("violet", "暮光紫", Color(0xFF8B6FE8))
    private val Sunset = BrandTheme("sunset", "暖阳橙", Color(0xFFE8823E))

    val all: List<BrandTheme> = listOf(Azure, Mint, Sakura, Violet, Sunset)

    /** 找不到或传入 null（= miuix 默认主题）时返回 null。 */
    fun byId(id: String?): BrandTheme? = all.find { it.id == id }
}
