package com.ctify.player.ui.visualizer

import com.ctify.player.ui.theme.isAppInDarkTheme
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.ctify.player.player.PlayerHub
import com.ctify.player.player.fx.SpectrumAnalyzer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Spectrum pictures the stage can show instead of a skin. */
enum class Visualizer(val label: String) {
    BARS("Bars"), WAVE("Wave"), RING("Ring"), PULSE("Pulse");

    companion object {
        private const val PREFS = "ctify-ui"

        /** The chosen visualizer, or null when a skin is on stage. */
        fun load(ctx: Context): Visualizer? = runCatching {
            valueOf(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("visualizer", null)!!)
        }.getOrNull()

        fun save(ctx: Context, v: Visualizer?) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("visualizer", v?.name).apply()
        }
    }
}

/** A calm, fixed shape for previews and pauses, so nothing is drawn from stale audio. */
private val IDLE = FloatArray(SpectrumAnalyzer.BAR_COUNT) { 0.12f + 0.28f * sin(it / 3.5f).let { s -> s * s } }

/**
 * [v] drawn [side] wide from the live spectrum. Only a playing, non-[still] view reads the
 * spectrum, and it reads it in the draw phase, so new audio frames redraw without recomposing.
 */
@Composable
fun VisualizerView(v: Visualizer, art: Painter, playing: Boolean, still: Boolean, side: Dp) {
    val spectrum: State<FloatArray> =
        if (playing && !still) PlayerHub.spectrum.collectAsState() else remember { mutableStateOf(IDLE) }
    // drawn in white over the dark player; light theme gets near-black so they stay visible
    val ink = if (isAppInDarkTheme()) Color.White else Color(0xFF111111)
    val glow = ink.copy(alpha = 0.55f)
    Box(Modifier.size(side).semantics { contentDescription = "${v.label} visualizer" }, contentAlignment = Alignment.Center) {
        if (v == Visualizer.RING) {
            Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(side * 0.56f).clip(CircleShape))
        }
        Canvas(Modifier.fillMaxSize()) {
            val s = spectrum.value
            when (v) {
                Visualizer.BARS -> bars(s, ink, glow)
                Visualizer.WAVE -> wave(s, ink, glow)
                Visualizer.RING -> ring(s, ink, glow)
                Visualizer.PULSE -> pulse(s, ink, glow)
            }
        }
    }
}

private fun FloatArray.avg(from: Int, to: Int): Float {
    var sum = 0f
    for (i in from until to) sum += this[i]
    return sum / (to - from)
}

private fun DrawScope.bars(s: FloatArray, a: Color, b: Color) {
    val n = s.size
    val slot = size.width / n
    val w = slot * 0.62f
    val mid = size.height / 2
    val brush = Brush.verticalGradient(listOf(b, a, b))
    for (i in 0 until n) {
        val h = (s[i] * size.height * 0.9f).coerceAtLeast(w)
        drawRoundRect(brush, Offset(i * slot + (slot - w) / 2, mid - h / 2), Size(w, h), CornerRadius(w / 2))
    }
}

private fun DrawScope.wave(s: FloatArray, a: Color, b: Color) {
    val n = s.size
    val mid = size.height / 2
    val step = size.width / (n - 1)
    // alternate up and down so the bands read as one swinging line
    fun y(i: Int, k: Float) = mid + (if (i % 2 == 0) -1 else 1) * s[i] * size.height * 0.42f * k
    for ((k, color, width) in listOf(Triple(1f, a, 0.012f), Triple(-0.6f, b.copy(alpha = 0.6f), 0.008f))) {
        val path = Path().apply {
            moveTo(0f, y(0, k))
            for (i in 1 until n) {
                val px = (i - 1) * step
                val x = i * step
                cubicTo(px + step / 2, y(i - 1, k), x - step / 2, y(i, k), x, y(i, k))
            }
        }
        drawPath(path, color, style = Stroke(width = size.minDimension * width, cap = StrokeCap.Round))
    }
}

private fun DrawScope.ring(s: FloatArray, a: Color, b: Color) {
    val r0 = size.minDimension * 0.30f
    val n = s.size * 2
    val w = (2 * PI * r0 / n * 0.55).toFloat()
    for (i in 0 until n) {
        // mirrored halves, so low notes sit at the top and bottom meets smoothly
        val v = s[if (i < s.size) i else n - 1 - i]
        val len = (v * size.minDimension * 0.19f).coerceAtLeast(w)
        rotate(360f * i / n) {
            drawLine(
                if (i % 2 == 0) a else b,
                Offset(center.x, center.y - r0), Offset(center.x, center.y - r0 - len),
                strokeWidth = w, cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.pulse(s: FloatArray, a: Color, b: Color) {
    val n = s.size
    val bass = s.avg(0, 4)
    val mids = s.avg(6, 13)
    val highs = s.avg(14, n)
    val m = size.minDimension
    val blobs = listOf(
        Triple(0f, bass, a), Triple(2.1f, mids, b), Triple(4.2f, highs, a.copy(alpha = 0.8f)),
    )
    for ((angle, e, color) in blobs) {
        val r = m * (0.18f + 0.22f * e)
        val c = Offset(center.x + cos(angle) * m * 0.14f * (1 + e), center.y + sin(angle) * m * 0.14f * (1 + e))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)), c, r), r, c)
    }
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f * bass), Color.Transparent), center, m * 0.2f), m * 0.2f)
}
