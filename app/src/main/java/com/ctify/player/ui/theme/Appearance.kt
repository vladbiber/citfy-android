package com.ctify.player.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Where the app colors come from. */
enum class ThemeSource(val label: String) { WALLPAPER("Wallpaper"), CTIFY_BLUE("Ctify Blue"), DUO("Two colors") }

/** How the two colors blend on big surfaces; small spots always fade. */
enum class GradientStyle(val label: String) { LIQUID("Liquid"), FADE("Fade") }

/** The colors a user can pick for the two-color theme. */
enum class Swatch(val label: String, val color: Color) {
    RED("Red", Color(0xFFE53935)),
    ORANGE("Orange", Color(0xFFFB8C00)),
    AMBER("Amber", Color(0xFFFFB300)),
    YELLOW("Yellow", Color(0xFFFDD835)),
    LIME("Lime", Color(0xFFC0CA33)),
    GREEN("Green", Color(0xFF43A047)),
    TEAL("Teal", Color(0xFF00897B)),
    CYAN("Cyan", Color(0xFF00ACC1)),
    BLUE("Blue", Color(0xFF1E88E5)),
    INDIGO("Indigo", Color(0xFF3949AB)),
    PURPLE("Purple", Color(0xFF8E24AA)),
    PINK("Pink", Color(0xFFD81B60)),
}

/** Stored theme source, else the old "Wallpaper colors" switch (on = Wallpaper, off = Ctify Blue). */
internal fun storedSource(stored: String?, dynamicColors: Boolean): ThemeSource =
    runCatching { ThemeSource.valueOf(stored!!) }
        .getOrDefault(if (dynamicColors) ThemeSource.WALLPAPER else ThemeSource.CTIFY_BLUE)

/** Appearance settings, kept next to the skin in the "ctify-ui" prefs. */
object Appearance {
    private lateinit var app: Context

    val mode = MutableStateFlow(ThemeMode.SYSTEM)
    val source = MutableStateFlow(ThemeSource.WALLPAPER)
    val color1 = MutableStateFlow(Swatch.BLUE)
    val color2 = MutableStateFlow(Swatch.PURPLE)
    val gradient = MutableStateFlow(GradientStyle.LIQUID)
    val pureBlack = MutableStateFlow(false)
    val coverColors = MutableStateFlow(false)

    val dynamicSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode.value = runCatching { ThemeMode.valueOf(p.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        source.value = storedSource(p.getString("theme_source", null), p.getBoolean("dynamic_colors", true))
        color1.value = runCatching { Swatch.valueOf(p.getString("color1", null)!!) }.getOrDefault(Swatch.BLUE)
        color2.value = runCatching { Swatch.valueOf(p.getString("color2", null)!!) }.getOrDefault(Swatch.PURPLE)
        gradient.value = runCatching { GradientStyle.valueOf(p.getString("gradient", null)!!) }.getOrDefault(GradientStyle.LIQUID)
        pureBlack.value = p.getBoolean("pure_black", false)
        coverColors.value = p.getBoolean("cover_colors", false)
    }

    fun setMode(v: ThemeMode) { mode.value = v; save() }
    fun setSource(v: ThemeSource) { source.value = v; save() }
    fun setColor1(v: Swatch) { color1.value = v; save() }
    fun setColor2(v: Swatch) { color2.value = v; save() }
    fun setGradient(v: GradientStyle) { gradient.value = v; save() }
    fun setPureBlack(v: Boolean) { pureBlack.value = v; save() }
    fun setCoverColors(v: Boolean) { coverColors.value = v; save() }

    private fun save() {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", mode.value.name)
            .putString("theme_source", source.value.name)
            .putString("color1", color1.value.name)
            .putString("color2", color2.value.name)
            .putString("gradient", gradient.value.name)
            .putBoolean("pure_black", pureBlack.value)
            .putBoolean("cover_colors", coverColors.value)
            .apply()
    }

    private const val PREFS = "ctify-ui"
}
