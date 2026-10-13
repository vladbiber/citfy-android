package com.ctify.player.player.fx

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs [EffectChain] on 16-bit PCM inside ExoPlayer. Mono comes out as stereo,
 * any sample rate works because the effects are built for the real rate.
 */
@UnstableApi
class FxAudioProcessor(
    private val fx: Fx,
    private val onSpectrum: (FloatArray) -> Unit = {},
) : BaseAudioProcessor() {

    private var chain: EffectChain? = null
    private var inChannels = 2
    private var rate = 44100
    private var work = FloatArray(0)
    private var analyzer: SpectrumAnalyzer? = null
    private var shorts = ShortArray(0)
    private var lastSpectrum = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inChannels = inputAudioFormat.channelCount
        if (chain == null || rate != inputAudioFormat.sampleRate) {
            rate = inputAudioFormat.sampleRate
            chain = EffectChain(fx, rate.toFloat()).also { it.reset() }
            analyzer = SpectrumAnalyzer(sampleRate = rate)
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val c = chain ?: return
        val inBytes = inputBuffer.remaining()
        val frames = inBytes / (2 * inChannels)
        if (frames == 0) return
        if (work.size < frames * 2) { work = FloatArray(frames * 2); shorts = ShortArray(frames * 2) }
        val src = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val l = src.short / 32768f
            val r = if (inChannels == 2) src.short / 32768f else l
            work[i * 2] = l; work[i * 2 + 1] = r
        }
        if (!c.idle) c.process(work, frames)
        val out = replaceOutputBuffer(frames * 4)
        for (i in 0 until frames * 2) {
            val v = (work[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            shorts[i] = v
            out.putShort(v)
        }
        out.flip()
        val now = System.nanoTime() / 1_000_000
        if (now - lastSpectrum > 70) {
            lastSpectrum = now
            analyzer?.let { onSpectrum(it.analyze(shorts, frames)) }
        }
    }

    override fun onFlush() {
        chain?.reset()
    }

    override fun onReset() {
        chain = null
        analyzer?.let { onSpectrum(it.clear()) }
    }
}

/**
 * ExoPlayer's processor chain with the order slowva uses: speed first (Sonic,
 * pitch follows speed), then every effect, so vinyl and rain are never slowed.
 */
@UnstableApi
class FxProcessorChain(fx: Fx, onSpectrum: (FloatArray) -> Unit) : AudioProcessorChain {
    private val sonic = SonicAudioProcessor()
    private val effects = FxAudioProcessor(fx, onSpectrum)

    override fun getAudioProcessors(): Array<AudioProcessor> = arrayOf(sonic, effects)

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        sonic.setSpeed(playbackParameters.speed)
        sonic.setPitch(playbackParameters.pitch)
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long = sonic.getMediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long = 0
}
