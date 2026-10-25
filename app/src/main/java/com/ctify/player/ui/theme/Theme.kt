package com.ctify.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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

/** Guava blue, used when the phone has no wallpaper colors (Android 8–11) or they are switched off. */
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

// Sizes and weights only: line height and letter spacing still come from bodyLarge, as with hand-typed sizes.
// Every role takes the chosen family; a serif font only sets the headings, body text stays the phone's.
private fun ctifyType(font: AppFont): Typography {
    val family = font.family()
    val head = family ?: FontFamily.Default
    val body = if (font.variable) head else FontFamily.Default
    val base = Typography()
    fun TextStyle.with(f: FontFamily) = copy(fontFamily = f)
    return Typography(
        displayLarge = base.displayLarge.with(head), displayMedium = base.displayMedium.with(head),
        displaySmall = base.displaySmall.with(head), headlineLarge = base.headlineLarge.with(head),
        headlineMedium = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 28.sp), // screen titles
        headlineSmall = base.headlineSmall.with(head),
        titleLarge = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 20.sp), // sheet titles, hero cards
        titleMedium = TextStyle(fontFamily = head, fontWeight = FontWeight.SemiBold, fontSize = 18.sp), // section headers, card titles
        titleSmall = base.titleSmall.with(body),
        // bodyLarge stays Material's 16sp: list titles and plain text.
        bodyLarge = base.bodyLarge.with(body),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp), // secondary text, settings and sound body
        bodySmall = base.bodySmall.with(body),
        labelLarge = base.labelLarge.with(body), labelMedium = base.labelMedium.with(body),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp), // captions, times, chip labels
    )
}

/** The bundled font as a family; variable fonts get one entry per weight so bold really is bold. */
private fun AppFont.family(): FontFamily? {
    val id = res ?: return null
    if (!variable) return FontFamily(Font(id, FontWeight.Normal))
    return FontFamily(listOf(300, 400, 500, 600, 700, 800).map { w ->
        Font(id, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    })
}

private val CtifyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CtifyTheme(content: @Composable () -> Unit) {
    val source by Appearance.source.collectAsState()
    val pureBlack by Appearance.pureBlack.collectAsState()
    val font by Appearance.font.collectAsState()
    val ctx = LocalContext.current
    var scheme = when {
        source == ThemeSource.DUO -> duoScheme()
        source == ThemeSource.WALLPAPER && Appearance.dynamicSupported -> dynamicDarkColorScheme(ctx)
        else -> CtifyDark
    }
    if (pureBlack) scheme = scheme.pureBlack()
    val type = remember(font) { ctifyType(font) }
    MaterialTheme(colorScheme = scheme, typography = type, shapes = CtifyShapes, content = content)
}

/** Color 1 drives primary, color 2 secondary + tertiary; MaterialKolor keeps the tones contrast-safe. */
@Composable
private fun duoScheme(): ColorScheme {
    val c1 by Appearance.color1.collectAsState()
    val c2 by Appearance.color2.collectAsState()
    val s = rememberDynamicColorScheme(
        seedColor = c1.color, isDark = true,
        primary = c1.color, secondary = c2.color, tertiary = c2.color,
    )
    if (c1 != c2) return s
    // Same swatch twice: color 2 becomes a darker shade, taken from the primary palette's own contrast-safe pair.
    return s.copy(tertiary = s.primaryContainer, onTertiary = s.onPrimaryContainer)
}

private fun ColorScheme.pureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0C0C0C),
)
