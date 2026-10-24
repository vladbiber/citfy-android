package com.ctify.player.ui.skins.radio

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Hit area of the play key (the jog dial's centre), in fractions of the face. */
internal val MiniDiscKey = Rect(0.725f, 0.625f, 0.855f, 0.785f)

private const val SEGS = 14

/** Portable MiniDisc: clear cartridge under the lid window, segmented LCD with level meters, jog dial. */
internal fun CacheDrawScope.miniDisc(s: RadioScene): DrawResult {
    val w = size.width; val h = size.height
    fun box(l: Float, t: Float, r: Float, b: Float) = Rect(l * w, t * h, r * w, b * h)
    val px = 1.dp.toPx()
    val body = box(0.015f, 0.02f, 0.985f, 0.955f)
    val window = box(0.055f, 0.11f, 0.6f, 0.9f)
    val cw = 0.47f * w; val ch = cw * 68f / 72f
    val cart = Rect(Offset(window.center.x - cw / 2, window.center.y - ch / 2), Size(cw, ch))
    val dc = Offset(cart.center.x, cart.top + ch * 0.55f); val dr = cw * 0.41f
    val sheen = Brush.sweepGradient(
        listOf(Color.Transparent, Color(0x33FF6FD8), Color(0x3358D6FF), Color(0x33B6FF6A), Color.Transparent, Color(0x22FFFFFF), Color.Transparent),
        dc,
    )
    val lcd = box(0.645f, 0.11f, 0.945f, 0.43f)
    val pad = lcd.height * 0.07f
    val inner = lcd.deflate(pad)
    val row1 = Rect(inner.left, inner.top, inner.right, inner.top + inner.height * 0.34f)
    val row2 = Rect(inner.left, row1.bottom, inner.right, row1.bottom + inner.height * 0.32f)
    val meter = Rect(inner.left, row2.bottom + inner.height * 0.06f, inner.right, inner.bottom)
    val station = LcdLine(s.measurer, s.station, row1, segment(row1.height * 0.78f), Kit.ice)
    val title = LcdLine(s.measurer, s.title, row2, segment(row2.height * 0.72f), Kit.ice)
    val lr = s.measurer.measure("L", segment(meter.height * 0.4f))
    val segL = meter.left + lr.size.width * 1.6f
    val segW = (meter.right - segL) / SEGS
    val jog = Offset(0.79f * w, 0.705f * h); val jogR = 0.125f * w
    val key = Rect(jog, 0.05f * w)
    val small = silk(h * 0.038f)

    val back = obtainGraphicsLayer().apply {
        record {
            chassis(body, w * 0.06f)
            drawText(s.measurer.measure("MINIDISC", silk(h * 0.04f)), topLeft = Offset(window.left, 0.045f * h))
            drawText(s.measurer.measure("FM STEREO", silk(h * 0.032f, Kit.printDim)), topLeft = Offset(lcd.left, 0.05f * h))
            recess(window, w * 0.03f)
            // disc surface; the rainbow sheen and hub turn on top of it
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB9BEC6), Color(0xFF8A9098), Color(0xFF5E636B)), dc, dr), dr, dc)
            drawCircle(Color(0xFFC9CDD3), dr * 0.36f, dc)
            drawCircle(Color.Black.copy(alpha = 0.25f), dr * 0.36f, dc, style = Stroke(px))
            lcdBack(lcd, Kit.ice)
            val tagL = silk(meter.height * 0.36f, Kit.ice.copy(alpha = 0.75f))
            drawText(s.measurer.measure("L", tagL), topLeft = Offset(meter.left, meter.top))
            drawText(s.measurer.measure("R", tagL), topLeft = Offset(meter.left, meter.center.y))
            labelledLamp(Offset(0.665f * w, 0.49f * h), h * 0.018f, Kit.live, s.playing, s.measurer.measure("LIVE", small))
            labelledLamp(Offset(0.83f * w, 0.49f * h), h * 0.018f, s.accent, s.playing, s.measurer.measure("PLAY", small))
            knob(jog, jogR, s.accent, -30f + 60f * s.tuning)
            val cap = keyCap(key, s.pressed, round = 0.5f)
            playGlyph(cap.center, cap.height * 0.2f, s.playing)
        }
    }
    val front = obtainGraphicsLayer().apply {
        record {
            // clear smoke cartridge over the disc
            val cr = CornerRadius(cw * 0.04f)
            drawRoundRect(Color(0xFFB8C4D6).copy(alpha = 0.2f), cart.topLeft, cart.size, cr)
            drawRoundRect(Color.White.copy(alpha = 0.35f), cart.topLeft, cart.size, cr, style = Stroke(px))
            drawRoundRect(Color.White.copy(alpha = 0.12f), cart.topLeft + Offset(cw * 0.03f, cw * 0.03f), Size(cw * 0.94f, ch - cw * 0.06f), cr, style = Stroke(px))
            val label = Rect(cart.left + cw * 0.06f, cart.top + ch * 0.05f, cart.right - cw * 0.06f, cart.top + ch * 0.3f)
            drawRect(Kit.paper, label.topLeft, label.size)
            drawRect(s.accent, Offset(label.left, label.bottom - label.height * 0.1f), Size(label.width, label.height * 0.1f))
            val side = label.height * 0.74f
            val artR = Rect(Offset(label.left + label.height * 0.08f, label.top + label.height * 0.06f), Size(side, side))
            artTile(artR, s.art, s.measurer.measure(s.initial, silk(side * 0.55f, Color.White)), s.accent)
            val name = s.measurer.measure(
                s.station, TextStyle(color = Kit.ink, fontSize = (label.height * 0.24f).toSp(), fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium),
                overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (label.right - artR.right - label.height * 0.2f).toInt().coerceAtLeast(1)),
            )
            drawText(name, topLeft = Offset(artR.right + label.height * 0.1f, artR.center.y - name.size.height / 2f))
            // metal shutter across the lower edge
            val shutter = Rect(cart.left + cw * 0.18f, cart.top + ch * 0.6f, cart.left + cw * 0.64f, cart.bottom - ch * 0.02f)
            drawRect(Brush.horizontalGradient(Kit.chrome, shutter.left, shutter.right), shutter.topLeft, shutter.size)
            drawRect(Color.Black.copy(alpha = 0.3f), shutter.topLeft, shutter.size, style = Stroke(px))
            centered(s.measurer.measure("MD", silk(shutter.height * 0.32f, Color(0xFF5A5C62))), shutter.center)
            glass(window, w * 0.03f)
            glass(lcd, lcd.height * 0.05f)
        }
    }
    return onDrawBehind {
        drawLayer(back)
        val t = s.t()
        rotate(if (s.playing) t * 360f * 6 else 0f, dc) {
            drawCircle(sheen, dr, dc)
            drawCircle(Color(0xFF9DA2AA), dr * 0.18f, dc)
            drawCircle(Color(0xFF3A3C40), dr * 0.05f, dc)
            drawCircle(Color(0xFF3A3C40), dr * 0.035f, dc + Offset(dr * 0.11f, 0f))
        }
        station.draw(this, t)
        title.draw(this, t)
        for (c in 0..1) {
            val lit = if (s.playing) (level(t, c + 3) * SEGS).toInt() else 0
            val y = meter.top + c * meter.height / 2 + meter.height * 0.1f
            for (i in 0 until SEGS) {
                drawRect(
                    Kit.ice.copy(alpha = if (i < lit) 1f else 0.08f),
                    Offset(segL + i * segW, y), Size(segW * 0.7f, meter.height * 0.3f),
                )
            }
        }
        drawLayer(front)
    }
}
