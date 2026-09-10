package com.zhiyin.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SettingsRepo {

    private val _themeId = MutableStateFlow("azure")
    val themeId: StateFlow<String> = _themeId

    fun loadThemeId(ctx: Context) {
        _themeId.value = ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .getString("theme_id", "azure") ?: "azure"
    }

    fun setThemeId(ctx: Context, id: String) {
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .edit().putString("theme_id", id).apply()
        _themeId.value = id
    }

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode

    fun loadDarkMode(ctx: Context) {
        _darkMode.value = ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .getBoolean("dark_mode", false)
    }

    fun setDarkMode(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .edit().putBoolean("dark_mode", enabled).apply()
        _darkMode.value = enabled
    }

    private val _notifyEnabled = MutableStateFlow(true)
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled

    fun loadNotifyEnabled(ctx: Context) {
        _notifyEnabled.value = ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .getBoolean("notify_enabled", true)
    }

    fun setNotifyEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .edit().putBoolean("notify_enabled", enabled).apply()
        _notifyEnabled.value = enabled
    }

    fun thinkingMode(ctx: Context) =
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE).getBoolean("thinking_mode_enabled", false)

    fun setThinkingMode(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE).edit().putBoolean("thinking_mode_enabled", v).apply()

    fun stickerEnabled(ctx: Context) =
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE).getBoolean("sticker_enabled", true)

    fun setStickerEnabled(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE).edit().putBoolean("sticker_enabled", v).apply()

    fun voiceReply(ctx: Context) =
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE).getBoolean("voice_reply_enabled", false)

    fun setVoiceReply(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE).edit().putBoolean("voice_reply_enabled", v).apply()

    fun searchEnabled(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences("zhiyin_search", Context.MODE_PRIVATE)
        return sp.getBoolean("search_enabled", false)
    }

    fun setSearchEnabled(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences("zhiyin_search", Context.MODE_PRIVATE).edit().putBoolean("search_enabled", v).apply()

    fun hasTavilyKey(ctx: Context) =
        ctx.getSharedPreferences("zhiyin_search", Context.MODE_PRIVATE).getBoolean("has_tavily_key", false)

    private val _globalBgEnabled = MutableStateFlow(false)
    val globalBgEnabled: StateFlow<Boolean> = _globalBgEnabled

    private val _globalBgPath = MutableStateFlow("")
    val globalBgPath: StateFlow<String> = _globalBgPath

    private val _globalBgAcrylic = MutableStateFlow(false)
    val globalBgAcrylic: StateFlow<Boolean> = _globalBgAcrylic

    private val _globalBgBlur = MutableStateFlow(36)
    val globalBgBlur: StateFlow<Int> = _globalBgBlur

    fun loadGlobalBg(ctx: Context) {
        val sp = ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
        _globalBgEnabled.value = sp.getBoolean("global_bg_enabled", false)
        _globalBgPath.value = sp.getString("global_bg_path", "") ?: ""
        _globalBgAcrylic.value = sp.getBoolean("global_bg_acrylic", false)
        _globalBgBlur.value = sp.getInt("global_bg_blur", 36)
    }

    fun setGlobalBgEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .edit().putBoolean("global_bg_enabled", enabled).apply()
        _globalBgEnabled.value = enabled
    }

    fun setGlobalBgPath(ctx: Context, path: String) {
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .edit().putString("global_bg_path", path).apply()
        _globalBgPath.value = path
    }

    fun setGlobalBgAcrylic(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .edit().putBoolean("global_bg_acrylic", enabled).apply()
        _globalBgAcrylic.value = enabled
    }

    fun setGlobalBgBlur(ctx: Context, radius: Int) {
        ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
            .edit().putInt("global_bg_blur", radius).apply()
        _globalBgBlur.value = radius
    }
}
