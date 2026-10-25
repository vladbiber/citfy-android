package com.ctify.player.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ctify.player.player.PlayerHub
import com.ctify.player.ui.reducedMotion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** Color 1 fading into color 2 with Two colors on, else solid primary: for play button, selected tab, progress, sliders. */
@Composable
fun accentBrush(): Brush {
    val source by Appearance.source.collectAsState()
    val s = MaterialTheme.colorScheme
    return if (source == ThemeSource.DUO) Brush.linearGradient(listOf(s.primary, s.tertiary)) else SolidColor(s.primary)
}

/**
 * Draws the two-color Oil, Liquid or Fade backdrop behind big surfaces (player, Home header); draws nothing otherwise.
 * With a [pulse] (see [rememberMusicPulse]) the colors are boosted and the blobs (or oil swirls) swell with it.
 * Oil needs Android 13 shaders; older phones get Liquid.
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier, pulse: State<Float>? = null) {
    val source by Appearance.source.collectAsState()
    val style by Appearance.gradient.collectAsState()
    if (source != ThemeSource.DUO) return
    val s = MaterialTheme.colorScheme
    // Toned down so text on top stays readable; light mode gets pastel washes.
    val oilStyle = style == GradientStyle.OIL && Build.VERSION.SDK_INT >= 33
    // oil is a picture, not a wash: it needs more body than Liquid to read as a slick
    val alpha = if (isAppInDarkTheme()) (if (oilStyle) 0.72f else 0.38f) else (if (oilStyle) 0.42f else 0.24f)
    val one = if (pulse != null) s.primary.boosted() else s.primary
    val two = if (pulse != null) s.tertiary.boosted() else s.tertiary
    // a bad shader must not crash the app: it just falls back to Liquid
    val oil = if (style == GradientStyle.OIL && Build.VERSION.SDK_INT >= 33) remember { runCatching { OilBrush() }.getOrNull() } else null
    val flow = if (oil != null && !reducedMotion()) {
        rememberInfiniteTransition(label = "oil")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(OIL_LOOP_MS, easing = LinearEasing)), label = "flow")
    } else null
    Canvas(modifier) {
        // read here, in the draw phase, so each beat redraws without recomposing
        val e = pulse?.value ?: 0f
        val al = if (pulse != null) (alpha * 1.4f + 0.18f * e).coerceAtMost(if (oilStyle) 0.92f else 0.75f) else alpha
        if (oil != null && Build.VERSION.SDK_INT >= 33) {
            drawRect(oil.at(size.width, size.height, flow?.value ?: 0.3f, e, al, one, two))
            return@Canvas
        }
        val a = one.copy(alpha = al)
        val b = two.copy(alpha = al)
        if (style == GradientStyle.FADE) {
            drawRect(Brush.linearGradient(listOf(a, b)))
            return@Canvas
        }
        val d = size.maxDimension * (1f + 0.18f * e)
        for ((x, y, r, second) in Blobs) {
            val c = if (second) b else a
            val center = Offset(x * size.width, y * size.height)
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center, r * d), r * d, center)
        }
    }
}

/** More saturated and a little brighter, for the music-reactive background. */
fun Color.boosted(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.45f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceAtLeast(0.55f)
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = alpha)
}

/**
 * Smoothed bass energy (0..1) for the player background, or null when React to music is off or
 * battery saver is on. The spectrum is only read while [playing]; on pause it fades back to 0.
 */
@Composable
fun rememberMusicPulse(playing: Boolean): State<Float>? {
    val react by Appearance.reactToMusic.collectAsState()
    val saver = rememberPowerSave()
    val level = remember { mutableFloatStateOf(0f) }
    val on = react && !saver
    LaunchedEffect(on && playing) {
        if (on && playing) PlayerHub.spectrum.collect { s ->
            val e = bassEnergy(s)
            // quick to swell, slow to settle
            level.floatValue += (e - level.floatValue) * if (e > level.floatValue) 0.5f else 0.12f
        } else while (level.floatValue > 0.01f) {
            withFrameNanos { }
            level.floatValue *= 0.9f
        }
        level.floatValue = 0f
    }
    return if (on) level else null
}

/** The lowest bands, stretched so ordinary music spans most of 0..1 instead of sitting near the top. */
internal fun bassEnergy(spectrum: FloatArray): Float {
    if (spectrum.size < 4) return 0f
    val low = (spectrum[0] + spectrum[1] + spectrum[2] + spectrum[3]) / 4f
    return ((low - 0.35f) / 0.55f).coerceIn(0f, 1f)
}

/** Battery saver, kept current by the system broadcast while on screen. */
@Composable
private fun rememberPowerSave(): Boolean {
    val ctx = LocalContext.current
    val pm = remember { ctx.getSystemService(PowerManager::class.java) }
    var saver by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { saver = pm?.isPowerSaveMode == true }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return saver
}

/** One soft ink blob: center as a fraction of the size, radius as a fraction of the longer side, color 1 or 2. */
private data class Blob(val x: Float, val y: Float, val r: Float, val second: Boolean)

// One fixed layout: the two colors overlap in the middle so they melt into each other.
private val Blobs = listOf(
    Blob(0.15f, 0.10f, 0.75f, false), Blob(0.90f, 0.25f, 0.65f, true), Blob(0.25f, 0.70f, 0.70f, true),
    Blob(0.85f, 0.90f, 0.60f, false), Blob(0.55f, 0.45f, 0.45f, false),
)
