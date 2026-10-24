package com.ctify.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.material3.rememberDynamicColorScheme

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

// Sizes and weights only: line height and letter spacing still come from bodyLarge, as with hand-typed sizes.
private val CtifyType = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp), // screen titles
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp), // sheet titles, hero cards
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp), // section headers, card titles
    // bodyLarge stays Material's 16sp: list titles and plain text.
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp), // secondary text, settings and sound body
    labelSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp), // captions, times, chip labels
)

private val CtifyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
    val source by Appearance.source.collectAsState()
    val pureBlack by Appearance.pureBlack.collectAsState()
    val ctx = LocalContext.current
    var scheme = when {
        source == ThemeSource.DUO -> duoScheme(dark)
        source == ThemeSource.WALLPAPER && Appearance.dynamicSupported ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> CtifyDark
        else -> CtifyLight
    }
    if (dark && pureBlack) scheme = scheme.pureBlack()
    MaterialTheme(colorScheme = scheme, typography = CtifyType, shapes = CtifyShapes, content = content)
}

/** Color 1 drives primary, color 2 secondary + tertiary; MaterialKolor keeps the tones contrast-safe. */
@Composable
private fun duoScheme(dark: Boolean): ColorScheme {
    val c1 by Appearance.color1.collectAsState()
    val c2 by Appearance.color2.collectAsState()
    val s = rememberDynamicColorScheme(
        seedColor = c1.color, isDark = dark,
        primary = c1.color, secondary = c2.color, tertiary = c2.color,
    )
    if (c1 != c2) return s
    // Same swatch twice: color 2 becomes a darker shade, taken from the primary palette's own contrast-safe pair.
    return if (dark) s.copy(tertiary = s.primaryContainer, onTertiary = s.onPrimaryContainer)
    else s.copy(tertiary = s.onPrimaryContainer, onTertiary = s.primaryContainer)
}

private fun ColorScheme.pureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0C0C0C),
)
