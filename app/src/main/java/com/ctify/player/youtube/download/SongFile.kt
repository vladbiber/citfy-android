package com.ctify.player.youtube.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import com.ctify.player.youtube.StreamResolver
import com.ctify.player.youtube.YtDecodeException
import com.ctify.player.youtube.YtHttpException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Fetches, remuxes, tags and saves one song. Everything blocks; run it on IO. */
internal class SongFile(private val context: Context, private val dao: DownloadDao) {
    private val work = File(context.filesDir, "yt-downloads").apply { mkdirs() }
    private val covers = File(context.filesDir, "yt-covers").apply { mkdirs() }

    fun partFile(videoId: String) = File(work, "$videoId.part")

    /** Downloads the stream into a .part file, continuing a previous partial one when it's the same file. */
    suspend fun fetch(item: DownloadEntity, stopped: () -> Boolean): File {
        var s = StreamResolver.resolve(item.videoId, forDownload = true)
        if (!s.isM4a) throw YtDecodeException("no AAC format for ${item.videoId}")
        val part = partFile(item.videoId)
        if (part.exists() && (item.partItag != s.itag || (s.contentLength > 0 && item.bytesTotal != s.contentLength))) part.delete()
        var pos = if (part.exists()) part.length() else 0L
        var total = s.contentLength
        var renewed = false
        RandomAccessFile(part, "rw").use { raf ->
            raf.seek(pos)
            val buf = ByteArray(64 * 1024)
            while (total < 0 || pos < total) {
                if (stopped()) throw IOException("stopped")
                val req = NetworkGate.request(s.url, Feature.YOUTUBE, Traffic.DOWNLOAD)
                    .header("Range", "bytes=$pos-${pos + CHUNK - 1}")
                    .apply { s.headers().forEach { (k, v) -> header(k, v) } }
                    .build()
                val got = NetworkGate.client.newCall(req).execute().use { r ->
                    if (r.code == 403 && !renewed) return@use -1L
                    if (r.code != 200 && r.code != 206) throw YtHttpException(r.code, "download HTTP ${r.code} at $pos")
                    r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                    if (r.code == 200 && pos > 0) { pos = 0; raf.setLength(0); raf.seek(0) }
                    var n = 0L
                    r.body.byteStream().use { input ->
                        while (true) {
                            val k = input.read(buf)
                            if (k < 0) break
                            raf.write(buf, 0, k)
                            n += k
                        }
                    }
                    if (r.code == 200) total = n
                    n
                }
                if (got < 0) {
                    // the address expired or was refused: look it up once more and carry on if it's the same file
                    renewed = true
                    val fresh = StreamResolver.resolve(item.videoId, fresh = true, forDownload = true)
                    if (fresh.itag != s.itag) { pos = 0; raf.setLength(0); raf.seek(0); total = fresh.contentLength }
                    s = fresh
                    continue
                }
                pos += got
                dao.progress(item.videoId, pos, total, s.itag)
                if (got == 0L) break
            }
        }
        if (total > 0 && part.length() < total) throw IOException("download ended early at ${part.length()} of $total")
        return part
    }

    /** Copies the AAC track into a plain MP4: drops video, and turns YouTube's fragmented file into a normal one. */
    fun remux(input: File): File {
        val out = File(work, input.nameWithoutExtension + ".m4a")
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            ex.setDataSource(input.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" }
                ?: throw YtDecodeException("the downloaded file has no AAC audio")
            val format = ex.getTrackFormat(track)
            ex.selectTrack(track)
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            val dst = m.addTrack(format)
            m.start()
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 256 * 1024
            val buffer = ByteBuffer.allocate(size.coerceAtLeast(64 * 1024))
            val info = MediaCodec.BufferInfo()
            while (true) {
                val n = ex.readSampleData(buffer, 0)
                if (n < 0) break
                info.set(0, n, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                m.writeSampleData(dst, buffer, info)
                ex.advance()
            }
            m.stop()
        } finally {
            ex.release()
            runCatching { muxer?.release() }
        }
        return out
    }

    fun tag(input: File, tags: M4aTags): File {
        val out = File(work, input.nameWithoutExtension + ".tagged.m4a")
        return runCatching { Mp4TagWriter.write(input, out, tags); out }.getOrElse { out.delete(); input }
    }

    /** Album covers are shared: one fetch per album, reused for every song in it. */
    fun cover(item: DownloadEntity): ByteArray? {
        val shared = item.groupId?.let { File(covers, "album_$it.jpg") }
        shared?.takeIf { it.exists() }?.let { return it.readBytes() }
        val url = item.thumbnail?.let(::bigThumbnail) ?: "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg"
        val bytes = runCatching {
            NetworkGate.client.newCall(NetworkGate.request(url, Feature.YOUTUBE, Traffic.DOWNLOAD).build()).execute().use { r ->
                if (r.isSuccessful) r.body.bytes() else null
            }
        }.getOrNull()?.takeIf { it.size in 1..(4 * 1024 * 1024) } ?: return null
        shared?.writeBytes(bytes)
        return bytes
    }

    fun keepCover(videoId: String, bytes: ByteArray?) {
        if (bytes != null) File(covers, "$videoId.jpg").writeBytes(bytes)
    }

    /** Saves into Music/Ctify/<Artist>/<Album>/ and returns the file's uri. */
    fun save(file: File, item: DownloadEntity): Uri {
        val artist = safe(item.albumArtist ?: item.artist)
        val album = safe(item.album ?: "Singles")
        val name = (item.trackNumber?.let { "%02d ".format(it) } ?: "") + safe(item.title) + ".m4a"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // no MediaStore paths before Android 10: keep it in the app's own music folder
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Ctify/$artist/$album").apply { mkdirs() }
            val dest = File(dir, name)
            file.copyTo(dest, overwrite = true)
            return Uri.fromFile(dest)
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Ctify/$artist/$album/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("Couldn't create the file in Music")
        try {
            resolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException("Couldn't write to Music")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    fun cleanup(videoId: String) {
        work.listFiles()?.filter { it.name.startsWith(videoId) }?.forEach { it.delete() }
    }

    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim().trimEnd('.').take(80).ifBlank { "Unknown" }

    companion object {
        private const val CHUNK = 2L * 1024 * 1024

        /** YouTube Music thumbnails come small; ask for a 544 px square. */
        fun bigThumbnail(url: String): String =
            if (url.contains("googleusercontent.com")) url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj") else url
    }
}

