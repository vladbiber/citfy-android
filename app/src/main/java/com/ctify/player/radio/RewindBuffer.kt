package com.ctify.player.radio

import android.content.Context
import com.ctify.player.integration.HttpStatusException
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import okhttp3.Call
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Buffer length setting, 0 = plain live playback. */
object RadioPrefs {
    const val PREFS = "ctify-radio"
    fun bufferMinutes(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("buffer_minutes", 5)
    fun setBufferMinutes(context: Context, minutes: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("buffer_minutes", minutes.coerceIn(0, 15)).apply()
}

/**
 * Records a live stream into a ring file in cacheDir so it can be paused, scrubbed and
 * scratched. ICY metadata blocks are cut out of the audio and kept as (byte offset, title).
 * Offsets are logical: they keep growing while the file wraps at [capacity].
 */
class RewindRecorder(
    private val url: String,
    private val dir: File,
    minutes: Int,
    fallbackKbps: Int,
) {
    val id = "rec${counter.incrementAndGet()}"
    private val file = File(dir, "$id.bin")
    private val raf = RandomAccessFile(file, "rw")
    private val lock = Object()
    private val titles = ArrayList<Pair<Long, String>>()
    private var call: Call? = null
    @Volatile private var closed = false
    @Volatile var failure: IOException? = null
        private set

    /** bytes of audio written so far; the newest byte is at written - 1 */
    @Volatile var written = 0L
        private set
    /** stream bytes per second, from the Icy-Br header or the directory's bitrate */
    @Volatile var byteRate = (fallbackKbps.takeIf { it > 0 } ?: 128) * 125L
        private set
    // sized for the worst case so a high bitrate still gets its minutes
    val capacity = minutes * 60L * maxOf(fallbackKbps, 192) * 125L * 11 / 10

    val windowStart: Long get() = maxOf(0L, written - capacity)
    fun msAt(offset: Long) = offset * 1000 / byteRate
    fun offsetAt(ms: Long) = ms * byteRate / 1000

    fun start() {
        Thread({ runCatching { record() }.onFailure { fail(it) } }, "radio-recorder-$id").start()
    }

    private fun record() {
        val req = NetworkGate.request(url, Feature.RADIO, Traffic.STREAM).header("Icy-MetaData", "1").build()
        val c = NetworkGate.client.newBuilder().readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req)
        call = c
        c.execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "HTTP ${resp.code}")
            resp.header("icy-br")?.substringBefore(',')?.trim()?.toIntOrNull()?.takeIf { it in 16..1411 }?.let { byteRate = it * 125L }
            val metaInt = resp.header("icy-metaint")?.trim()?.toIntOrNull() ?: 0
            val input = resp.body.byteStream()
            val buf = ByteArray(16 * 1024)
            var untilMeta = metaInt
            while (!closed) {
                val want = if (metaInt > 0) minOf(buf.size, untilMeta) else buf.size
                val n = input.read(buf, 0, want)
                if (n < 0) throw IOException("Stream ended")
                append(buf, n)
                if (metaInt > 0) {
                    untilMeta -= n
                    if (untilMeta == 0) {
                        readMeta(input)
                        untilMeta = metaInt
                    }
                }
            }
        }
    }

    private fun readMeta(input: InputStream) {
        val len = input.read().takeIf { it >= 0 }?.times(16) ?: throw IOException("Stream ended")
        if (len == 0) return
        val meta = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = input.read(meta, got, len - got)
            if (n < 0) throw IOException("Stream ended")
            got += n
        }
        val text = String(meta, Charsets.UTF_8).trimEnd('\u0000')
        val title = Regex("StreamTitle='(.*?)';").find(text)?.groupValues?.get(1) ?: return
        synchronized(titles) {
            if (titles.lastOrNull()?.second != title) titles += written to title
            // drop titles that fell out of the window, keep the one still playing at its start
            while (titles.size > 1 && titles[1].first <= windowStart) titles.removeAt(0)
        }
    }

    private fun append(buf: ByteArray, n: Int) {
        var off = 0
        while (off < n) {
            val pos = (written + off) % capacity
            val chunk = minOf(n - off, (capacity - pos).toInt())
            synchronized(raf) {
                raf.seek(pos)
                raf.write(buf, off, chunk)
            }
            off += chunk
        }
        synchronized(lock) {
            written += n
            lock.notifyAll()
        }
    }

    /** Raw ICY title playing at [offset], or null before the first one. */
    fun titleAt(offset: Long): String? = synchronized(titles) { titles.lastOrNull { it.first <= offset }?.second }

    /**
     * Reads audio at logical [offset], waiting at the write head for new bytes.
     * Returns -1 only when closed, throws when the stream failed or [offset] was overwritten.
     */
    fun read(offset: Long, out: ByteArray, at: Int, len: Int): Int {
        synchronized(lock) {
            while (!closed && offset >= written) {
                failure?.let { throw it }
                lock.wait(250)
            }
        }
        if (closed) return -1
        if (offset < windowStart) throw IOException("Fell behind the rewind buffer")
        val avail = (written - offset).coerceAtMost(len.toLong()).toInt()
        val pos = offset % capacity
        val n = minOf(avail, (capacity - pos).toInt())
        synchronized(raf) {
            raf.seek(pos)
            raf.readFully(out, at, n)
        }
        // the writer may have lapped us while we copied
        if (offset < windowStart) throw IOException("Fell behind the rewind buffer")
        return n
    }

    private fun fail(t: Throwable) {
        if (closed) return
        failure = t as? IOException ?: IOException(t.message, t)
        synchronized(lock) { lock.notifyAll() }
    }

    fun close() {
        closed = true
        call?.cancel()
        synchronized(lock) { lock.notifyAll() }
        synchronized(raf) { runCatching { raf.close() } }
        file.delete()
        active.remove(id)
    }

    companion object {
        private val counter = AtomicInteger()
        private val active = ConcurrentHashMap<String, RewindRecorder>()
        const val SCHEME = "ctifybuffer"

        fun create(context: Context, url: String, minutes: Int, kbps: Int): RewindRecorder {
            val dir = File(context.cacheDir, "radio-buffer").apply { mkdirs() }
            return RewindRecorder(url, dir, minutes, kbps).also { active[it.id] = it }
        }

        fun get(id: String): RewindRecorder? = active[id]

        /** Leftovers from a run that was killed. */
        fun clearStale(context: Context) {
            File(context.cacheDir, "radio-buffer").listFiles()?.forEach { f -> if (active.keys.none { f.name.startsWith(it) }) f.delete() }
        }
    }
}
