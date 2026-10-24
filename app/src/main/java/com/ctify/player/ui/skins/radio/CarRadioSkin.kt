package com.ctify.player.ui.skins.radio

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Hit area of the play key, in fractions of the face. */
internal val CarKey = Rect(0.735f, 0.6f, 0.95f, 0.88f)

private const val BARS = 7
private const val BAR_SEGS = 6

/** 90s head unit: detachable face in a DIN cage, green LCD with a spectrum, presets 1-6, volume knob. */
internal fun CacheDrawScope.carRadio(s: RadioScene): DrawResult {
    val w = size.width; val h = size.height
    fun box(l: Float, t: Float, r: Float, b: Float) = Rect(l * w, t * h, r * w, b * h)
    val px = 1.dp.toPx()
    val face = box(0.018f, 0.07f, 0.982f, 0.93f)
    val lcd = box(0.235f, 0.14f, 0.72f, 0.56f)
    val inner = lcd.deflate(lcd.height * 0.07f)
    val spec = Rect(inner.right - inner.width * 0.22f, inner.top + inner.height * 0.08f, inner.right, inner.bottom - inner.height * 0.04f)
    val textR = spec.left - inner.width * 0.03f
    val row0 = Rect(inner.left, inner.top, textR, inner.top + inner.height * 0.24f)
    val row1 = Rect(inner.left, row0.bottom, textR, row0.bottom + inner.height * 0.42f)
    val row2 = Rect(inner.left, row1.bottom, textR, inner.bottom)
    val preset = (s.tuning * 6).toInt() + 1
    val status = LcdLine(s.measurer, "FM1 P$preset ST", row0, segment(row0.height * 0.78f), Kit.green)
    val station = LcdLine(s.measurer, s.station, row1, segment(row1.height * 0.8f), Kit.green)
    val title = LcdLine(s.measurer, s.title, row2, segment(row2.height * 0.74f), Kit.green)
    val barW = spec.width / BARS; val segH = spec.height / BAR_SEGS
    val art = Rect(Offset(0.745f * w, lcd.top), Size(lcd.height, lcd.height))
    val presets = box(0.235f, 0.63f, 0.72f, 0.84f)
    val key = Rect(art.left, presets.top, art.right, presets.bottom)
    val small = silk(h * 0.05f)

    val back = obtainGraphicsLayer().apply {
        record {
            // the DIN cage the face clips into
            drawRoundRect(Color(0xFF0D0E10), Offset(0f, h * 0.02f), Size(w, h * 0.96f), CornerRadius(h * 0.03f))
            for (i in 0..1) drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, h * (0.035f + i * 0.93f)), Offset(w, h * (0.035f + i * 0.93f)), px)
            chassis(face, h * 0.07f)
            // seam of the detachable face
            drawRoundRect(Color.Black.copy(alpha = 0.7f), face.topLeft, face.size, CornerRadius(h * 0.07f), style = Stroke(2 * px))
            chromeBar(Rect(presets.left, 0.875f * h, art.right, 0.885f * h))

            val rel = keyCap(box(0.04f, 0.13f, 0.095f, 0.23f), false, 0.3f)
            centered(s.measurer.measure("REL", silk(rel.height * 0.36f, Kit.glyph)), rel.center)
            labelledLamp(Offset(0.13f * w, 0.18f * h), h * 0.024f, Kit.live, s.playing, s.measurer.measure("LIVE", small))
            knob(Offset(0.115f * w, 0.565f * h), 0.2f * h, s.accent, -135f + 200f * s.tuning)
            centered(s.measurer.measure("VOL · PWR", silk(h * 0.05f, Kit.printDim)), Offset(0.115f * w, 0.83f * h))

            lcdBack(lcd, Kit.green)
            recess(art.inflate(h * 0.015f), h * 0.02f)
            artTile(art, s.art, s.measurer.measure(s.initial, silk(art.height * 0.5f, Color.White)), s.accent)

            // presets: the station's slot stays latched with its LED lit
            val gap = 0.012f * w
            val pw = (presets.width - gap * 5) / 6
            val digit = silk(presets.height * 0.38f, Kit.glyph)
            for (i in 0 until 6) {
                val on = i + 1 == preset
                val cap = keyCap(Rect(Offset(presets.left + i * (pw + gap), presets.top), Size(pw, presets.height)), on)
                centered(s.measurer.measure("${i + 1}", digit), Offset(cap.center.x, cap.center.y + cap.height * 0.06f))
                drawRoundRect(
                    if (on) s.accent else Color(0xFF55565C), Offset(cap.center.x - pw * 0.18f, cap.top + cap.height * 0.12f),
                    Size(pw * 0.36f, cap.height * 0.08f), CornerRadius(cap.height * 0.04f),
                )
            }
            val cap = keyCap(key, s.pressed)
            playGlyph(cap.center, cap.height * 0.22f, s.playing)
            lamp(Offset(cap.right - cap.height * 0.16f, cap.top + cap.height * 0.18f), h * 0.016f, s.accent, s.playing)
        }
    }
    val front = obtainGraphicsLayer().apply {
        record {
            glass(lcd, lcd.height * 0.05f)
            glass(art, 0f)
        }
    }
    return onDrawBehind {
        drawLayer(back)
        val t = s.t()
        status.draw(this, 0f)
        station.draw(this, t)
        title.draw(this, t)
        for (i in 0 until BARS) {
            val lit = if (s.playing) (level(t, i) * BAR_SEGS).roundToInt() else 0
            for (j in 0 until BAR_SEGS) {
                drawRect(
                    Kit.green.copy(alpha = if (j < lit) 1f else 0.08f),
                    Offset(spec.left + i * barW + barW * 0.15f, spec.bottom - (j + 1) * segH + segH * 0.18f),
                    Size(barW * 0.7f, segH * 0.64f),
                )
            }
        }
        drawLayer(front)
    }
}
