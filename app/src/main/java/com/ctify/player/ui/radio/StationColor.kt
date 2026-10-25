package com.ctify.player.ui.radio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Deep enough that a big white letter stays readable on every one.
private val StationPalette = listOf(
    Color(0xFFD32F2F), Color(0xFFC2185B), Color(0xFF7B1FA2), Color(0xFF512DA8),
    Color(0xFF303F9F), Color(0xFF1976D2), Color(0xFF0277BD), Color(0xFF00838F),
    Color(0xFF00796B), Color(0xFF2E7D32), Color(0xFF827717), Color(0xFFD84315),
)

private val TintBase = Color(0xFF26262C)

/** The color a station without a picture wears, always the same for the same name. */
fun stationColor(name: String): Color =
    StationPalette[Math.floorMod(name.trim().lowercase().hashCode(), StationPalette.size)]

/** The station's color pulled toward dark grey, for list tiles; the skins keep [stationColor]. */
fun stationTint(name: String): Color = lerp(stationColor(name), TintBase, 0.62f)
