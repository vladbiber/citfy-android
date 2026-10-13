package com.ctify.player.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.ctify.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes a file once and writes it back as a reversed WAV for ExoPlayer. */
class ReverseDecoder(private val ctx: Context) {

    private val dir get() = File(ctx.cacheDir, "reverse").apply { mkdirs() }

    suspend fun reversed(track: Track): Result<File> = withContext(Dispatchers.IO) {
        val out = File(dir, "${track.id.hashCode().toUInt()}.wav")
        if (out.length() > 44 + 4096) {
            out.setLastModified(System.currentTimeMillis())
            return@withContext Result.success(out)
        }
        val raw = File(dir, "decode.tmp")
        runCatching {
            val (rate, channels) = decode(track.uri, raw)
            writeReversed(raw, out, rate, channels)
            raw.delete()
            prune()
            out
        }.onFailure { raw.delete(); out.delete() }
    }

    private fun decode(uri: String, raw: File): Pair<Int, Int> {
        val ex = MediaExtractor()
        try {
            if (uri.startsWith("file:///android_asset/")) {
                ctx.assets.openFd(uri.removePrefix("file:///android_asset/")).use {
                    ex.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            } else {
                ex.setDataSource(ctx, Uri.parse(uri), null)
            }
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("no audio track")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            RandomAccessFile(raw, "rw").use { f ->
                f.setLength(0)
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        o >= 0 -> {
                            val buf = codec.getOutputBuffer(o)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            if (floatPcm) {
                                val fb = buf.order(ByteOrder.nativeOrder()).asFloatBuffer()
                                val bb = ByteBuffer.allocate(fb.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
                                while (fb.hasRemaining()) bb.putShort((fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                                f.write(bb.array(), 0, bb.position())
                            } else {
                                while (buf.hasRemaining()) {
                                    val n = minOf(chunk.size, buf.remaining())
                                    buf.get(chunk, 0, n)
                                    f.write(chunk, 0, n)
                                }
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            }
            codec.stop(); codec.release()
            return rate to channels
        } finally {
            ex.release()
        }
    }

    private fun writeReversed(raw: File, out: File, rate: Int, channels: Int) {
        val frameBytes = 2 * channels
        val total = raw.length() / frameBytes
        RandomAccessFile(raw, "r").use { src ->
            RandomAccessFile(out, "rw").use { dst ->
                dst.setLength(0)
                dst.write(wavHeader(total * frameBytes, rate, channels))
                val framesPerChunk = 16 * 1024
                val inBuf = ByteArray(framesPerChunk * frameBytes)
                val outBuf = ByteArray(inBuf.size)
                var end = total
                while (end > 0) {
                    val start = (end - framesPerChunk).coerceAtLeast(0)
                    val n = (end - start).toInt()
                    src.seek(start * frameBytes)
                    src.readFully(inBuf, 0, n * frameBytes)
                    for (i in 0 until n) {
                        System.arraycopy(inBuf, (n - 1 - i) * frameBytes, outBuf, i * frameBytes, frameBytes)
                    }
                    dst.write(outBuf, 0, n * frameBytes)
                    end = start
                }
            }
        }
    }

    private fun wavHeader(dataBytes: Long, rate: Int, channels: Int): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + dataBytes).toInt()); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(channels.toShort())
        b.putInt(rate); b.putInt(rate * channels * 2); b.putShort((channels * 2).toShort()); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataBytes.toInt())
        return b.array()
    }

    /** about 10 MB per stereo minute, so only the last few tracks stay */
    private fun prune() {
        dir.listFiles { f -> f.name.endsWith(".wav") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(4)
            ?.forEach { it.delete() }
    }
}
