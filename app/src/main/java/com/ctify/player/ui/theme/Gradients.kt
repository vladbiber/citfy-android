package com.ctify.player.ui.theme

import androidx.compose.foundation.Canvas
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

/** Draws the two-color Liquid or Fade backdrop behind big surfaces (player, Home header); draws nothing otherwise. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val source by Appearance.source.collectAsState()
    val style by Appearance.gradient.collectAsState()
    if (source != ThemeSource.DUO) return
    val s = MaterialTheme.colorScheme
    // Toned down so text on top stays readable; light mode gets pastel washes.
    val alpha = if (isAppInDarkTheme()) 0.38f else 0.24f
    val a = s.primary.copy(alpha = alpha)
    val b = s.tertiary.copy(alpha = alpha)
    Canvas(modifier) {
        if (style == GradientStyle.FADE) {
            drawRect(Brush.linearGradient(listOf(a, b)))
            return@Canvas
        }
        val d = size.maxDimension
        for ((x, y, r, second) in Blobs) {
            val c = if (second) b else a
            val center = Offset(x * size.width, y * size.height)
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center, r * d), r * d, center)
        }
    }
}

/** One soft ink blob: center as a fraction of the size, radius as a fraction of the longer side, color 1 or 2. */
private data class Blob(val x: Float, val y: Float, val r: Float, val second: Boolean)

// One fixed layout: the two colors overlap in the middle so they melt into each other.
private val Blobs = listOf(
    Blob(0.15f, 0.10f, 0.75f, false), Blob(0.90f, 0.25f, 0.65f, true), Blob(0.25f, 0.70f, 0.70f, true),
    Blob(0.85f, 0.90f, 0.60f, false), Blob(0.55f, 0.45f, 0.45f, false),
)
