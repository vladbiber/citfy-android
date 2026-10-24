package com.ctify.player.ui.skins.radio

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ctify.player.ui.skins.TAU
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One loop of every radio animation; all rates are whole cycles per loop, so it wraps without a jump. */
internal const val LOOP_MS = 12_000

/** What a radio face draws. Built inside the draw cache, so it lives as long as the cached layers. */
internal class RadioScene(
    val station: String,
    title: String?,
    art: Painter?,
    val accent: Color,
    val playing: Boolean,
    val pressed: Boolean,
    val measurer: TextMeasurer,
    /** loop phase 0..1, read only while drawing so a frame redraws without recomposing */
    val t: () -> Float,
) {
    val title = title?.takeIf { it.isNotBlank() } ?: if (playing) "On air" else "Standby"
    // rememberArtPainter hands out a transparent ColorPainter until the image arrives
    val art = art?.takeUnless { it is ColorPainter && it.color.alpha == 0f }
    /** a stable dial spot (and preset) per station, so the needle doesn't wander between visits */
    val tuning = (station.hashCode() and 0x7fffffff) % 1000 / 1000f
    val initial = station.trim().take(1).uppercase()
}

/** Shared hardware palette: graphite satin plastic, chrome trim, silver keys, realistic LCD backlights. */
internal object Kit {
    val bodyTop = Color(0xFF3B3C42)
    val bodyBottom = Color(0xFF1E1F23)
    val recess = Color(0xFF131417)
    val print = Color(0xFFB9BAC0)
    val printDim = Color(0xFF7D7E85)
    val live = Color(0xFFFF3B2F)
    val paper = Color(0xFFEDE8D6)
    val ink = Color(0xFF2A2925)
    val glyph = Color(0xFF2A2B30)
    val chrome = listOf(Color(0xFF6F7177), Color(0xFFE4E5E8), Color(0xFF9A9CA2), Color(0xFFF2F3F5), Color(0xFF7E8086))
    val chromeSweep = listOf(Color(0xFF8A8C92), Color(0xFFEDEEF1), Color(0xFF7A7C82), Color(0xFFDADBDF), Color(0xFF8A8C92))
    val amber = Color(0xFFFFB443)
    val ice = Color(0xFF9EE6FF)
    val green = Color(0xFF9BFF6E)
}

/** Silk-screen print on the body. */
internal fun Density.silk(px: Float, color: Color = Kit.print) =
    TextStyle(color = color, fontSize = px.toSp(), fontWeight = FontWeight.SemiBold, letterSpacing = (px * 0.14f).toSp())

/** LCD segment text; the colour comes from the backlight at draw time. */
internal fun Density.segment(px: Float) =
    TextStyle(color = Color.White, fontSize = px.toSp(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

/** Marquee offset in px for loop phase [t]: whole laps per loop so the wrap is seamless; 0 when the text fits. */
internal fun marqueeShift(textW: Float, boxW: Float, period: Float, t: Float, loopPx: Float): Float {
    if (textW <= boxW || period <= 0f) return 0f
    val laps = (loopPx / period).roundToInt().coerceAtLeast(1)
    return (t * laps % 1f) * period
}

/** A steady fake programme level 0..1 for channel [i]; there is no live spectrum for radio streams. */
internal fun level(t: Float, i: Int): Float {
    val a = sin(TAU * (t * (19 + 7 * i % 11) + 0.13f * i))
    val b = sin(TAU * (t * (31 + 5 * i % 13) + 0.29f * i))
    return (0.55f + 0.28f * a + 0.17f * b).coerceIn(0f, 1f)
}

/** One LCD row: dim ghost segments under the text, which scrolls when it does not fit. */
internal class LcdLine(measurer: TextMeasurer, text: String, private val box: Rect, style: TextStyle, private val tint: Color) {
    private val layout = measurer.measure(text.uppercase(), style, softWrap = false, maxLines = 1)
    private val ghost: TextLayoutResult
    private val top = box.top + (box.height - layout.size.height) / 2
    private val period = layout.size.width + box.height * 3f
    private val scrolls = layout.size.width > box.width
    private val glow = Shadow(tint.copy(alpha = 0.6f), blurRadius = box.height * 0.22f)

    init {
        val eight = measurer.measure("8", style).size.width.coerceAtLeast(1)
        ghost = measurer.measure("8".repeat((box.width / eight).toInt().coerceAtLeast(1)), style, softWrap = false, maxLines = 1)
    }

    fun draw(d: DrawScope, t: Float) = with(d) {
        drawText(ghost, color = tint.copy(alpha = 0.07f), topLeft = Offset(box.left, top))
        val shift = marqueeShift(layout.size.width.toFloat(), box.width, period, t, box.height * 20f)
        clipRect(box.left, box.top - box.height, box.right, box.bottom + box.height) {
            drawText(layout, color = tint, topLeft = Offset(box.left - shift, top), shadow = glow)
            if (scrolls) drawText(layout, color = tint, topLeft = Offset(box.left - shift + period, top), shadow = glow)
        }
    }
}

/** A speaker: cone that breathes with the level, perforated grille and chrome rim in front. */
internal class Speaker(private val c: Offset, private val r: Float) {
    private val cone = Brush.radialGradient(
        0f to Color(0xFF45464C), 0.3f to Color(0xFF1E1F23), 0.75f to Color(0xFF2C2D32), 1f to Color(0xFF101113),
        center = c - Offset(r * 0.08f, r * 0.1f), radius = r * 0.88f,
    )
    private val cap = Brush.radialGradient(listOf(Color(0xFF6A6B72), Color(0xFF17181B)), c - Offset(r * 0.06f, r * 0.08f), r * 0.24f)
    private val ridge = Stroke(r * 0.012f)
    private val sheet = Brush.linearGradient(listOf(Color(0xFF45464C), Color(0xFF26272B)), c - Offset(r, r), c + Offset(r, r))
    private val rim = Brush.sweepGradient(Kit.chromeSweep, c)
    private val grille = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(c, r * 0.95f))
        val step = r * 0.105f
        var row = 0
        var y = c.y - r
        while (y <= c.y + r) {
            var x = c.x - r + if (row % 2 == 1) step / 2 else 0f
            while (x <= c.x + r) {
                if ((Offset(x, y) - c).getDistance() < r * 0.88f) addOval(Rect(Offset(x, y), step * 0.37f))
                x += step
            }
            y += step * 0.866f; row++
        }
    }

    fun back(d: DrawScope) = with(d) {
        drawCircle(Color.Black.copy(alpha = 0.45f), r * 1.12f, c + Offset(0f, r * 0.05f))
        drawCircle(Brush.verticalGradient(listOf(Kit.bodyBottom, Kit.bodyTop), c.y - r * 1.1f, c.y + r * 1.1f), r * 1.1f, c)
        drawCircle(Color(0xFF0B0B0D), r, c)
        drawCircle(Color(0xFF232428), r * 0.92f, c, style = Stroke(r * 0.07f))
    }

    fun cone(d: DrawScope, level: Float) = with(d) {
        scale(1f + 0.035f * level, c) {
            drawCircle(cone, r * 0.86f, c)
            drawCircle(Color.White.copy(alpha = 0.05f), r * 0.58f, c, style = ridge)
            drawCircle(cap, r * 0.24f, c)
        }
    }

    fun front(d: DrawScope) = with(d) {
        drawPath(grille, sheet)
        drawCircle(rim, r, c, style = Stroke(r * 0.07f))
        drawCircle(Color.Black.copy(alpha = 0.5f), r * 0.965f, c, style = Stroke(1.dp.toPx()))
    }
}

/** Satin plastic body with a drop shadow and a lit top edge. */
internal fun DrawScope.chassis(r: Rect, corner: Float) {
    val cr = CornerRadius(corner)
    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(r.left, r.top + r.height * 0.025f), r.size, cr)
    drawRoundRect(Brush.verticalGradient(listOf(Kit.bodyTop, Kit.bodyBottom), r.top, r.bottom), r.topLeft, r.size, cr)
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.White.copy(alpha = 0.03f)), r.topLeft, r.bottomRight),
        r.topLeft, r.size, cr,
    )
    drawRoundRect(Color.White.copy(alpha = 0.14f), r.topLeft + Offset(1f, 1f), Size(r.width - 2, r.height - 2), cr, style = Stroke(1.dp.toPx()))
    drawRoundRect(Color.Black.copy(alpha = 0.5f), r.topLeft, r.size, cr, style = Stroke(1.dp.toPx()))
}

/** A panel sunk into the body: dark, shadowed at the top, catching light on the lower lip. */
internal fun DrawScope.recess(r: Rect, corner: Float) {
    val cr = CornerRadius(corner)
    drawRoundRect(Kit.recess, r.topLeft, r.size, cr)
    drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), r.top, r.top + min(r.height * 0.3f, 10.dp.toPx())), r.topLeft, r.size, cr)
    drawLine(Color.White.copy(alpha = 0.12f), Offset(r.left + corner, r.bottom), Offset(r.right - corner, r.bottom), 1.dp.toPx())
}

internal fun DrawScope.chromeBar(r: Rect) =
    drawRoundRect(Brush.verticalGradient(Kit.chrome, r.top, r.bottom), r.topLeft, r.size, CornerRadius(r.height / 2))

/** Glass over a window or LCD: one diagonal glare streak and a fine edge. */
internal fun DrawScope.glass(r: Rect, corner: Float) {
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.14f), 0.42f to Color.White.copy(alpha = 0.04f), 0.43f to Color.Transparent,
            start = r.topLeft, end = Offset(r.left + r.width * 0.55f, r.bottom),
        ),
        r.topLeft, r.size, CornerRadius(corner),
    )
    drawRoundRect(Color.White.copy(alpha = 0.10f), r.topLeft, r.size, CornerRadius(corner), style = Stroke(1.dp.toPx()))
}

/** LCD under its glass: black bezel, faintly backlit panel. */
internal fun DrawScope.lcdBack(r: Rect, tint: Color) {
    val bezel = r.inflate(r.height * 0.07f)
    drawRoundRect(Color(0xFF09090B), bezel.topLeft, bezel.size, CornerRadius(r.height * 0.1f))
    drawRoundRect(Brush.verticalGradient(listOf(lerp(Color(0xFF0E1012), tint, 0.1f), lerp(Color(0xFF060708), tint, 0.04f)), r.top, r.bottom), r.topLeft, r.size, CornerRadius(r.height * 0.05f))
    drawRoundRect(Brush.radialGradient(listOf(tint.copy(alpha = 0.09f), Color.Transparent), r.center, r.width * 0.6f), r.topLeft, r.size, CornerRadius(r.height * 0.05f))
}

/** Knurled knob with a chrome cap; the pointer takes the accent. */
internal fun DrawScope.knob(c: Offset, r: Float, accent: Color, angle: Float) {
    drawCircle(Color.Black.copy(alpha = 0.5f), r * 1.05f, c + Offset(0f, r * 0.1f))
    drawCircle(Color(0xFF18191C), r, c)
    for (i in 0 until 48) rotate(i * 7.5f, c) {
        drawLine(Color(0xFF3C3D43), Offset(c.x, c.y - r * 0.98f), Offset(c.x, c.y - r * 0.82f), r * 0.04f)
    }
    drawCircle(Brush.radialGradient(Kit.chrome, c - Offset(r * 0.3f, r * 0.35f), r * 1.1f), r * 0.78f, c)
    drawCircle(Brush.sweepGradient(Kit.chromeSweep, c), r * 0.78f, c, alpha = 0.45f)
    drawCircle(Color.Black.copy(alpha = 0.3f), r * 0.78f, c, style = Stroke(1.dp.toPx()))
    rotate(angle, c) { drawLine(accent, Offset(c.x, c.y - r * 0.68f), Offset(c.x, c.y - r * 0.4f), r * 0.09f, StrokeCap.Round) }
}

/** Silver key with a dark collar; returns the cap (lower while pressed) so callers can print on it. */
internal fun DrawScope.keyCap(r: Rect, pressed: Boolean, round: Float = 0.2f): Rect {
    val cr = CornerRadius(min(r.width, r.height) * round)
    val collar = r.inflate(min(r.width, r.height) * 0.06f)
    drawRoundRect(Color(0xFF0A0A0C), collar.topLeft, collar.size, CornerRadius(cr.x + (collar.width - r.width) / 2))
    val cap = if (pressed) r.translate(0f, r.height * 0.04f) else r
    if (!pressed) drawRoundRect(Color.Black.copy(alpha = 0.6f), r.topLeft + Offset(0f, r.height * 0.06f), r.size, cr)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE9EAED), Color(0xFFC3C5CA), Color(0xFFA2A4AA)), cap.top, cap.bottom), cap.topLeft, cap.size, cr)
    drawRoundRect(Color.White.copy(alpha = if (pressed) 0.25f else 0.7f), cap.topLeft + Offset(1f, 1f), Size(cap.width - 2, cap.height - 2), cr, style = Stroke(1.dp.toPx()))
    drawRoundRect(Color.Black.copy(alpha = 0.35f), cap.topLeft, cap.size, cr, style = Stroke(1.dp.toPx()))
    return cap
}

/** Pause bars while playing (what a tap does), a triangle otherwise. */
internal fun DrawScope.playGlyph(c: Offset, s: Float, playing: Boolean, color: Color = Kit.glyph) {
    if (playing) {
        drawRect(color, c - Offset(s * 0.85f, s), Size(s * 0.6f, s * 2))
        drawRect(color, c + Offset(s * 0.25f, -s), Size(s * 0.6f, s * 2))
    } else drawPath(Path().apply { moveTo(c.x - s * 0.7f, c.y - s); lineTo(c.x + s, c.y); lineTo(c.x - s * 0.7f, c.y + s); close() }, color)
}

/** An LED: lit with a soft halo, or a dark lens. */
internal fun DrawScope.lamp(c: Offset, r: Float, color: Color, on: Boolean) {
    if (on) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
    drawCircle(Color.Black.copy(alpha = 0.6f), r * 1.35f, c)
    drawCircle(if (on) color else lerp(color, Color.Black, 0.75f), r, c)
    drawCircle(Color.White.copy(alpha = if (on) 0.6f else 0.15f), r * 0.35f, c - Offset(r * 0.3f, r * 0.3f))
}

/** An LED with its silk-screen label to the right. */
internal fun DrawScope.labelledLamp(c: Offset, r: Float, color: Color, on: Boolean, label: TextLayoutResult) {
    lamp(c, r, color, on)
    drawText(label, topLeft = Offset(c.x + r * 2.2f, c.y - label.size.height / 2f))
}

internal fun DrawScope.centered(t: TextLayoutResult, c: Offset) =
    drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))

/** Station art fitted into [r]; the station's initial when there is none. */
internal fun DrawScope.artTile(r: Rect, art: Painter?, initial: TextLayoutResult, accent: Color) {
    drawRect(lerp(Color(0xFF26272B), accent, 0.35f), r.topLeft, r.size)
    if (art != null) clipRect(r.left, r.top, r.right, r.bottom) {
        val i = art.intrinsicSize
        val k = if (i.isSpecified && i.width > 0f && i.height > 0f) min(r.width / i.width, r.height / i.height) else 0f
        val sz = if (k > 0f) Size(i.width * k, i.height * k) else r.size
        translate(r.left + (r.width - sz.width) / 2, r.top + (r.height - sz.height) / 2) { with(art) { draw(sz) } }
    } else centered(initial, r.center)
    drawRect(Color.Black.copy(alpha = 0.4f), r.topLeft, r.size, style = Stroke(1.dp.toPx()))
}
