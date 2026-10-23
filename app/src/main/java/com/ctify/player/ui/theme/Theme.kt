package com.ctify.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Old fixed palette names, now read from the active color scheme.
val Accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val AccentDark: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val BgBlack: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val SurfaceDark: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val TextPrimary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextMuted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

/** Ctify blue, used when the phone has no wallpaper colors (Android 8–11) or they are switched off. */
private val CtifyDark = darkColorScheme(
    primary = Color(0xFF5B9DFF),
    onPrimary = Color(0xFF0A1428),
    secondary = Color(0xFF7FB8FF),
    onSecondary = Color(0xFF0A1428),
    tertiary = Color(0xFF9775FA),
    primaryContainer = Color(0xFF1F3A66),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondaryContainer = Color(0xFF243552),
    onSecondaryContainer = Color(0xFFD7E6FF),
    tertiaryContainer = Color(0xFF3B2F6E),
    onTertiaryContainer = Color(0xFFE6DEFF),
    outline = Color(0xFF2E3648),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF131722),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C2230),
    onSurfaceVariant = Color(0xFFA9B2C3),
    surfaceContainerLowest = Color(0xFF080A0E),
    surfaceContainerLow = Color(0xFF0F131C),
    surfaceContainer = Color(0xFF0F131C),
    surfaceContainerHigh = Color(0xFF1C2230),
    surfaceContainerHighest = Color(0xFF252C3D),
)

private val CtifyLight = lightColorScheme(
    primary = Color(0xFF2B5FD9),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3F6AB8),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6A4FC8),
    primaryContainer = Color(0xFFD7E6FF),
    onPrimaryContainer = Color(0xFF0A1F4D),
    secondaryContainer = Color(0xFFDDE6F8),
    onSecondaryContainer = Color(0xFF13274A),
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF24145C),
    outline = Color(0xFFC3CAD8),
    background = Color(0xFFF7F9FD),
    onBackground = Color(0xFF12151C),
    surface = Color(0xFFF7F9FD),
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFE4E9F2),
    onSurfaceVariant = Color(0xFF4A5366),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4FA),
    surfaceContainer = Color(0xFFEBEFF7),
    surfaceContainerHigh = Color(0xFFE4E9F2),
    surfaceContainerHighest = Color(0xFFDDE3EE),
)

private val CtifyType = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

/** True when the app is drawn dark, after the user's System/Light/Dark choice. */
@Composable
fun isAppInDarkTheme(): Boolean {
    val mode by Appearance.mode.collectAsState()
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun CtifyTheme(content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme()
    val dynamic by Appearance.dynamic.collectAsState()
    val pureBlack by Appearance.pureBlack.collectAsState()
    val ctx = LocalContext.current
    var scheme = when {
        dynamic && Appearance.dynamicSupported -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> CtifyDark
        else -> CtifyLight
    }
    if (dark && pureBlack) scheme = scheme.pureBlack()
    MaterialTheme(colorScheme = scheme, typography = CtifyType, content = content)
}

private fun ColorScheme.pureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0C0C0C),
)
