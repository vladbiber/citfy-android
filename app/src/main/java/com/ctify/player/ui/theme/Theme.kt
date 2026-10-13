package com.ctify.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF5B9DFF)
val AccentDark = Color(0xFF7FB8FF)
val BgBlack = Color(0xFF0B0D12)
val SurfaceDark = Color(0xFF131722)
val SurfaceElevated = Color(0xFF1C2230)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA9B2C3)
val TextMuted = Color(0xFF8A93A6)

private val CtifyColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0A1428),
    secondary = AccentDark,
    onSecondary = Color(0xFF0A1428),
    primaryContainer = Color(0xFF1F3A66),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondaryContainer = Color(0xFF243552),
    onSecondaryContainer = Color(0xFFD7E6FF),
    outline = Color(0xFF2E3648),
    background = BgBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
)

private val CtifyType = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun CtifyTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = CtifyColors, typography = CtifyType, content = content)
}
