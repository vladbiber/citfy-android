package com.ctify.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.ctify.player.player.fx.Fx
import com.ctify.player.player.fx.FxAudioProcessor
import com.ctify.player.player.fx.RainPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class FxProcessorTest {

    private fun run(fx: Fx, channels: Int, rate: Int, chunks: Int, gen: (Int) -> Short): ShortArray {
        val p = FxAudioProcessor(fx)
        val out = p.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        assertEquals(2, out.channelCount)
        p.flush()
        val frames = 2048
        val all = ArrayList<Short>()
        var t = 0
        repeat(chunks) {
            val buf = ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { repeat(channels) { buf.putShort(gen(t)) }; t++ }
            buf.flip()
            p.queueInput(buf)
            val o = p.output.order(ByteOrder.LITTLE_ENDIAN)
            while (o.hasRemaining()) all.add(o.short)
        }
        return all.toShortArray()
    }

    private fun rms(a: ShortArray) = sqrt(a.fold(0.0) { acc, v -> acc + v.toDouble() * v } / a.size.coerceAtLeast(1))

    @Test
    fun `effects off leave silence silent and mono becomes stereo`() {
        val out = run(Fx(), 1, 48000, 6) { 0 }
        assertEquals(2048 * 6 * 2, out.size)
        assertTrue(out.all { it.toInt() == 0 })
    }

    @Test
    fun `effects off pass audio through unchanged`() {
        val out = run(Fx(), 2, 44100, 4) { t -> (sin(2 * PI * 440 * t / 44100) * 10000).toInt().toShort() }
        val expect = (sin(2 * PI * 440 * 100 / 44100) * 10000).toInt()
        assertTrue(abs(out[200] - expect) <= 1)
    }

    @Test
    fun `rain fills silence without clipping`() {
        val out = run(Fx().apply { rain = RainPreset.STORM }, 2, 48000, 60) { 0 }
        val level = rms(out)
        assertTrue("rain rms $level", level in 150.0..5000.0)
        assertTrue(out.none { abs(it.toInt()) >= 32767 })
    }

    @Test
    fun `loud audio with every effect never hard clips`() {
        val fx = Fx().apply {
            reverb = 1f; bassGainDb = 12f; vinyl = true; rain = RainPreset.STORM; pan8d = 1f; width = 2f
        }
        val out = run(fx, 2, 44100, 30) { t -> (sin(2 * PI * 80 * t / 44100) * 30000).toInt().toShort() }
        val clipped = out.count { abs(it.toInt()) >= 32767 }
        assertTrue("clipped $clipped of ${out.size}", clipped < out.size / 200)
    }
}
