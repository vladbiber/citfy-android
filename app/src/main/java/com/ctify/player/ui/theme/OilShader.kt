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
    float2 p = fc / max(size.x, size.y) * 3.2;
    float a = time * 6.2831853;
    // domain warp: the slow swirl of a slick spreading on water
    float k = 0.85 + 0.45 * pulse;
    for (int i = 1; i < 6; i++) {
        float f = float(i);
        p += k / f * float2(sin(f * p.y + a + f * 1.7), cos(f * p.x - a + f * 2.3));
    }
    // film thickness -> contour bands; each band is one of the two colors
    float t = sin(p.x * 0.9) * cos(p.y * 0.8) + 0.35 * sin(p.x * 1.7 - p.y * 1.3 + a);
    float phase = t * 3.0;
    float s = sin(phase * 3.1415926);
    // the colours sit deep in the dark film, like oil on wet asphalt; the seams carry the light
    float3 c = mix(float3(one.rgb), float3(two.rgb), smoothstep(-0.25, 0.25, s)) * (0.42 + 0.18 * abs(s));
    // thin-film interference: a rainbow seam where the bands meet
    float3 film = 0.55 + 0.45 * cos(6.2831853 * (phase * 0.6 + float3(0.0, 0.33, 0.67)));
    c = mix(c, film * 0.9, exp(-s * s * 26.0) * 0.9);
    // a soft wet sheen rolling across
    c += 0.14 * pow(max(0.0, sin(p.x * 0.5 + p.y * 0.35 + a)), 10.0);
    // the dark ground shows between slicks
    float cover = alpha * (0.35 + 0.65 * smoothstep(-0.7, 0.7, sin(p.x * 0.5 - p.y * 0.4 + 1.0)));
    return half4(half3(min(c, float3(1.0)) * cover), half(cover));
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
