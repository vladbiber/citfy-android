package com.ctify.player.ui.theme

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/** One full flow of the oil; every time term is a whole turn of it, so the loop wraps without a jump. */
internal const val OIL_LOOP_MS = 48_000

// Warped sine bands give the marbling; a rainbow fringe (thin-film colors) sits where the two colors meet.
private const val OIL = """
uniform float2 size;
uniform float time;
uniform float pulse;
uniform float alpha;
layout(color) uniform half4 one;
layout(color) uniform half4 two;

half4 main(float2 fc) {
    float2 p = fc / max(size.x, size.y) * 2.6;
    float a = time * 6.2831853;
    float k = 0.6 + 0.35 * pulse;
    for (int i = 1; i < 5; i++) {
        float f = float(i);
        p += k / f * float2(sin(f * p.y + a + f * 1.7), cos(f * p.x - a + f * 2.3));
    }
    float v = sin(p.x + p.y);
    float3 c = mix(float3(one.rgb), float3(two.rgb), smoothstep(-0.4, 0.4, v));
    float hue = 0.12 * (p.x * 1.3 - p.y) + v;
    float3 film = 0.5 + 0.5 * cos(6.2831853 * (hue + float3(0.0, 0.33, 0.67)));
    c = mix(c, film * 0.85, exp(-v * v * 14.0) * 0.6);
    // patchy coverage: the dark ground shows between the slicks
    float cover = alpha * (0.45 + 0.55 * smoothstep(-0.6, 0.8, sin(p.x * 0.6 - p.y * 0.45 + 1.0)));
    return half4(half3(c * cover), half(cover));
}
"""

/** The oil slick backdrop: one shader, uniforms set at draw time so a frame never recomposes. */
@RequiresApi(33)
internal class OilBrush {
    private val shader = RuntimeShader(OIL)

    // a wrong uniform name throws here, inside the caller's runCatching, instead of mid-draw
    init { at(1f, 1f, 0f, 0f, 0f, Color.Black, Color.Black) }

    fun at(w: Float, h: Float, time: Float, pulse: Float, alpha: Float, one: Color, two: Color): Brush {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("pulse", pulse)
        shader.setFloatUniform("alpha", alpha)
        shader.setColorUniform("one", one.copy(alpha = 1f).toArgb())
        shader.setColorUniform("two", two.copy(alpha = 1f).toArgb())
        return ShaderBrush(shader)
    }
}
