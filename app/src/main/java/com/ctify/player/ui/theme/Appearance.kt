package com.ctify.player.ui.theme

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Appearance settings, kept next to the skin in the "ctify-ui" prefs. */
object Appearance {
    private lateinit var app: Context

    val mode = MutableStateFlow(ThemeMode.SYSTEM)
    val dynamic = MutableStateFlow(true)
    val pureBlack = MutableStateFlow(false)
    val coverColors = MutableStateFlow(false)

    val dynamicSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode.value = runCatching { ThemeMode.valueOf(p.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        dynamic.value = p.getBoolean("dynamic_colors", true)
        pureBlack.value = p.getBoolean("pure_black", false)
        coverColors.value = p.getBoolean("cover_colors", false)
    }

    fun setMode(v: ThemeMode) { mode.value = v; save() }
    fun setDynamic(v: Boolean) { dynamic.value = v; save() }
    fun setPureBlack(v: Boolean) { pureBlack.value = v; save() }
    fun setCoverColors(v: Boolean) { coverColors.value = v; save() }

    private fun save() {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", mode.value.name)
            .putBoolean("dynamic_colors", dynamic.value)
            .putBoolean("pure_black", pureBlack.value)
            .putBoolean("cover_colors", coverColors.value)
            .apply()
    }

    private const val PREFS = "ctify-ui"
}
