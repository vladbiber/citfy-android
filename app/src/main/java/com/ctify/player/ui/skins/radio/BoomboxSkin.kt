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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ctify.player.ui.skins.TAU
import kotlin.math.sin

/** Hit area of the play key, in fractions of the face. */
internal val BoomboxKey = Rect(0.39f, 0.8f, 0.61f, 0.975f)

/** 80s boombox: chrome handle, tuning dial with a needle, two woofers, LCD over the cassette door. */
internal fun CacheDrawScope.boombox(s: RadioScene): DrawResult {
    val w = size.width; val h = size.height
    fun box(l: Float, t: Float, r: Float, b: Float) = Rect(l * w, t * h, r * w, b * h)
    val px = 1.dp.toPx()
    val body = box(0.01f, 0.13f, 0.99f, 0.955f)
    val dial = box(0.05f, 0.175f, 0.64f, 0.315f)
    val scaleL = dial.left + dial.width * 0.07f; val scaleR = dial.right - dial.width * 0.07f
    val tickBase = dial.top + dial.height * 0.66f
    val needleX = scaleL + (scaleR - scaleL) * (0.04f + 0.92f * s.tuning)
    val left = Speaker(Offset(0.175f * w, 0.63f * h), 0.142f * w)
    val right = Speaker(Offset(0.825f * w, 0.63f * h), 0.142f * w)
    val lcd = box(0.365f, 0.405f, 0.635f, 0.565f)
    val pad = lcd.height * 0.1f
    val row1 = Rect(lcd.left + pad, lcd.top + pad, lcd.right - pad, lcd.top + lcd.height / 2)
    val row2 = Rect(row1.left, row1.bottom, row1.right, lcd.bottom - pad)
    val station = LcdLine(s.measurer, s.station, row1, segment(row1.height * 0.8f), Kit.amber)
    val title = LcdLine(s.measurer, s.title, row2, segment(row2.height * 0.72f), Kit.amber)
    val door = box(0.37f, 0.6f, 0.63f, 0.785f)
    val key = box(0.415f, 0.825f, 0.585f, 0.93f)
    val small = silk(h * 0.045f)

    val back = obtainGraphicsLayer().apply {
        record {
            // handle with its mounts, behind the body
            val hs = h * 0.045f
            val handle = box(0.2f, 0.025f, 0.8f, 0.25f)
            drawRoundRect(Brush.verticalGradient(Kit.chrome, handle.top - hs / 2, handle.top + hs / 2), handle.topLeft, handle.size, CornerRadius(h * 0.07f), style = Stroke(hs))
            for (x in floatArrayOf(0.17f, 0.77f)) drawRoundRect(Color(0xFF1A1B1E), Offset(x * w, 0.085f * h), Size(0.06f * w, 0.07f * h), CornerRadius(h * 0.015f))
            for (x in floatArrayOf(0.08f, 0.84f)) drawRoundRect(Color(0xFF0E0E10), Offset(x * w, 0.94f * h), Size(0.08f * w, 0.035f * h), CornerRadius(h * 0.01f))
            chassis(body, w * 0.045f)
            chromeBar(box(0.03f, 0.345f, 0.97f, 0.354f))

            // tuning dial: FM scale over a short AM row
            recess(dial, h * 0.02f)
            drawRect(Brush.radialGradient(listOf(Kit.amber.copy(alpha = 0.07f), Color.Transparent), dial.center, dial.width * 0.5f), dial.topLeft, dial.size)
            val num = silk(dial.height * 0.17f)
            for (f in 88..108) {
                val x = scaleL + (f - 88) / 20f * (scaleR - scaleL)
                val major = f % 4 == 0
                drawLine(Kit.print.copy(alpha = if (major) 0.9f else 0.5f), Offset(x, tickBase - dial.height * (if (major) 0.2f else 0.11f)), Offset(x, tickBase), px)
                if (major) centered(s.measurer.measure("$f", num), Offset(x, dial.top + dial.height * 0.24f))
            }
            drawLine(Kit.print.copy(alpha = 0.5f), Offset(scaleL, tickBase), Offset(scaleR, tickBase), px)
            for (i in 0..24) {
                val x = scaleL + i / 24f * (scaleR - scaleL)
                drawLine(Kit.printDim, Offset(x, tickBase + dial.height * 0.08f), Offset(x, tickBase + dial.height * (if (i % 4 == 0) 0.18f else 0.13f)), px)
            }
            val tag = silk(dial.height * 0.13f, Kit.printDim)
            drawText(s.measurer.measure("FM", tag), topLeft = Offset(dial.left + dial.width * 0.012f, tickBase - dial.height * 0.2f))
            drawText(s.measurer.measure("AM", tag), topLeft = Offset(dial.left + dial.width * 0.012f, tickBase + dial.height * 0.05f))

            labelledLamp(Offset(0.7f * w, 0.245f * h), h * 0.02f, Kit.live, s.playing, s.measurer.measure("LIVE", small))
            knob(Offset(0.895f * w, 0.245f * h), h * 0.075f, s.accent, -40f + 80f * s.tuning)

            left.back(this); right.back(this)
            lcdBack(lcd, Kit.amber)

            // cassette door: the station's tape, art on the label
            recess(door, door.height * 0.08f)
            val cas = Rect(door.left + door.width * 0.06f, door.top + door.height * 0.1f, door.right - door.width * 0.06f, door.bottom - door.height * 0.06f)
            drawRoundRect(Color(0xFF2E2F35), cas.topLeft, cas.size, CornerRadius(cas.height * 0.06f))
            val label = Rect(cas.left + cas.width * 0.05f, cas.top + cas.height * 0.08f, cas.right - cas.width * 0.05f, cas.top + cas.height * 0.58f)
            drawRect(Kit.paper, label.topLeft, label.size)
            drawRect(s.accent, Offset(label.left, label.bottom - label.height * 0.14f), Size(label.width, label.height * 0.14f))
            val side = label.height * 0.7f
            val artR = Rect(Offset(label.left + label.height * 0.07f, label.top + label.height * 0.07f), Size(side, side))
            artTile(artR, s.art, s.measurer.measure(s.initial, silk(side * 0.55f, Color.White)), s.accent)
            val name = s.measurer.measure(
                s.station, TextStyle(color = Kit.ink, fontSize = (label.height * 0.3f).toSp(), fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium),
                overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (label.right - artR.right - label.height * 0.2f).toInt().coerceAtLeast(1)),
            )
            drawText(name, topLeft = Offset(artR.right + label.height * 0.1f, artR.center.y - name.size.height / 2f))
            val win = Rect(cas.left + cas.width * 0.24f, label.bottom + cas.height * 0.07f, cas.right - cas.width * 0.24f, cas.bottom - cas.height * 0.08f)
            drawRoundRect(Color(0xFF141417), win.topLeft, win.size, CornerRadius(win.height / 2))
            for (cx in floatArrayOf(win.left + win.height * 0.6f, win.right - win.height * 0.6f)) {
                drawCircle(Color(0xFFE9E6DE), win.height * 0.36f, Offset(cx, win.center.y))
                drawCircle(Color(0xFF2B2B2E), win.height * 0.17f, Offset(cx, win.center.y))
            }

            val cap = keyCap(key, s.pressed)
            playGlyph(cap.center, cap.height * 0.22f, s.playing)
            lamp(Offset(0.392f * w, key.center.y), h * 0.016f, s.accent, s.playing)
        }
    }
    val front = obtainGraphicsLayer().apply {
        record {
            left.front(this); right.front(this)
            glass(dial, h * 0.02f)
            glass(lcd, lcd.height * 0.05f)
            drawRoundRect(Color(0xFF101013).copy(alpha = 0.3f), door.topLeft, door.size, CornerRadius(door.height * 0.08f))
            glass(door, door.height * 0.08f)
        }
    }
    return onDrawBehind {
        drawLayer(back)
        val t = s.t()
        left.cone(this, if (s.playing) level(t, 0) else 0f)
        right.cone(this, if (s.playing) level(t, 1) else 0f)
        // the needle quivers a hair while a signal is coming in
        val x = needleX + if (s.playing) sin(TAU * t * 37) * w * 0.0012f else 0f
        drawLine(s.accent.copy(alpha = 0.3f), Offset(x, dial.top + dial.height * 0.08f), Offset(x, dial.bottom - dial.height * 0.08f), 5 * px)
        drawLine(s.accent, Offset(x, dial.top + dial.height * 0.08f), Offset(x, dial.bottom - dial.height * 0.08f), 1.6f * px)
        station.draw(this, t)
        title.draw(this, t)
        drawLayer(front)
    }
}
