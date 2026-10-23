// Adapted from Levyra (GPL-3.0), app/src/main/java/com/luc4n3x/levyra/player/offline/tagging/LevyraM4aTagWriter.kt
package com.ctify.player.youtube.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

data class M4aTags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = artist,
    val trackNumber: Int = 0,
    val trackTotal: Int = 0,
    val year: String = "",
    val genre: String = "",
    val comment: String = "",
    val cover: ByteArray? = null,
)

/** Writes iTunes-style tags into an MP4 audio file by rebuilding moov/udta/meta/ilst. */
object Mp4TagWriter {
    private const val MAX_INPUT_BYTES = 256L * 1024 * 1024

    fun write(input: File, output: File, tags: M4aTags) {
        if (!input.isFile || input.length() <= 0L) throw IOException("tag input missing")
        if (input.length() > MAX_INPUT_BYTES) throw IOException("tag input too large")
        val items = items(tags)
        val source = input.readBytes()
        val boxes = parseBoxes(source, 0, source.size)
        val moov = boxes.firstOrNull { it.type == Atom.MOOV } ?: throw IOException("moov missing")
        val firstMdat = boxes.firstOrNull { it.type == Atom.MDAT }
        val first = rebuildMoov(source, moov, items, 0L)
        // moov before mdat: every chunk offset moves by the size change
        val delta = if (firstMdat != null && moov.start < firstMdat.start) first.size.toLong() - moov.length else 0L
        val finalMoov = if (delta == 0L) first else rebuildMoov(source, moov, items, delta)
        output.parentFile?.mkdirs()
        output.outputStream().buffered().use { out ->
            for (box in boxes) if (box.type == Atom.MOOV) out.write(finalMoov) else out.write(source, box.start, box.length)
        }
    }

    private fun rebuildMoov(source: ByteArray, moov: Box, items: List<ByteArray>, delta: Long): ByteArray {
        val payload = ByteArrayOutputStream(moov.length + 4096)
        var hasUdta = false
        for (child in parseBoxes(source, moov.payloadStart, moov.end)) {
            if (child.type == Atom.UDTA) { payload.write(rebuildUdta(source, child, items, delta)); hasUdta = true }
            else payload.write(patched(source, child, delta))
        }
        if (!hasUdta) payload.write(atom(Atom.UDTA, createMeta(items)))
        return atom(Atom.MOOV, payload.toByteArray())
    }

    private fun rebuildUdta(source: ByteArray, udta: Box, items: List<ByteArray>, delta: Long): ByteArray {
        val payload = ByteArrayOutputStream(udta.length + 4096)
        var hasMeta = false
        for (child in parseBoxes(source, udta.payloadStart, udta.end)) {
            if (child.type == Atom.META) { payload.write(rebuildMeta(source, child, items, delta)); hasMeta = true }
            else payload.write(patched(source, child, delta))
        }
        if (!hasMeta) payload.write(createMeta(items))
        return atom(Atom.UDTA, payload.toByteArray())
    }

    private fun rebuildMeta(source: ByteArray, meta: Box, items: List<ByteArray>, delta: Long): ByteArray {
        val prefixEnd = (meta.payloadStart + 4).coerceAtMost(meta.end)
        val children = parseBoxes(source, prefixEnd, meta.end)
        val payload = ByteArrayOutputStream(meta.length + 4096)
        payload.write(source, meta.payloadStart, prefixEnd - meta.payloadStart)
        if (children.none { it.type == Atom.HDLR }) payload.write(createHdlr())
        var hasIlst = false
        for (child in children) when (child.type) {
            Atom.HDLR -> payload.write(source, child.start, child.length)
            Atom.ILST -> { payload.write(rebuildIlst(source, child, items)); hasIlst = true }
            else -> payload.write(patched(source, child, delta))
        }
        if (!hasIlst) payload.write(atom(Atom.ILST, items.concat()))
        return atom(Atom.META, payload.toByteArray())
    }

    private fun rebuildIlst(source: ByteArray, ilst: Box, items: List<ByteArray>): ByteArray {
        val payload = ByteArrayOutputStream(ilst.length + 4096)
        for (child in parseBoxes(source, ilst.payloadStart, ilst.end)) {
            if (child.type !in Atom.REPLACED) payload.write(source, child.start, child.length)
        }
        items.forEach(payload::write)
        return atom(Atom.ILST, payload.toByteArray())
    }

    private fun patched(source: ByteArray, box: Box, delta: Long): ByteArray {
        if (delta == 0L) return source.copyOfRange(box.start, box.end)
        return when (box.type) {
            Atom.STCO -> patchOffsets(source, box, delta, 4)
            Atom.CO64 -> patchOffsets(source, box, delta, 8)
            in Atom.CONTAINERS -> {
                val payload = ByteArrayOutputStream(box.length)
                for (child in parseBoxes(source, box.payloadStart, box.end)) payload.write(patched(source, child, delta))
                atom(box.type, payload.toByteArray())
            }
            else -> source.copyOfRange(box.start, box.end)
        }
    }

    private fun patchOffsets(source: ByteArray, box: Box, delta: Long, width: Int): ByteArray {
        val start = box.payloadStart
        if (start + 8 > box.end) return source.copyOfRange(box.start, box.end)
        val count = readUInt32(source, start + 4)
        if (start + 8L + count * width > box.end) return source.copyOfRange(box.start, box.end)
        val payload = ByteArrayOutputStream(box.length)
        payload.write(source, start, 8)
        var pos = start + 8
        repeat(count.toInt()) {
            val next = (if (width == 4) readUInt32(source, pos) else readUInt64(source, pos)) + delta
            if (next < 0L || (width == 4 && next > 0xFFFF_FFFFL)) throw IOException("chunk offset out of range")
            if (width == 4) writeUInt32(payload, next) else for (s in 56 downTo 0 step 8) payload.write(((next ushr s) and 0xFF).toInt())
            pos += width
        }
        if (pos < box.end) payload.write(source, pos, box.end - pos)
        return atom(box.type, payload.toByteArray())
    }

    private fun createMeta(items: List<ByteArray>): ByteArray {
        val payload = ByteArrayOutputStream(4096)
        payload.write(ByteArray(4))
        payload.write(createHdlr())
        payload.write(atom(Atom.ILST, items.concat()))
        return atom(Atom.META, payload.toByteArray())
    }

    private fun createHdlr(): ByteArray {
        val payload = ByteArrayOutputStream(33)
        payload.write(ByteArray(8))
        payload.write(Atom.bytes(Atom.MDIR))
        payload.write(ByteArray(12))
        payload.write("appl".toByteArray(StandardCharsets.US_ASCII))
        payload.write(0)
        return atom(Atom.HDLR, payload.toByteArray())
    }

    private fun items(t: M4aTags): List<ByteArray> = buildList {
        t.title.clean()?.let { add(text(Atom.NAM, it)) }
        t.artist.clean()?.let { add(text(Atom.ART, it)) }
        t.album.clean()?.let { add(text(Atom.ALB, it)) }
        t.albumArtist.clean()?.let { add(text(Atom.AART, it)) }
        t.year.clean()?.let { add(text(Atom.DAY, it)) }
        t.genre.clean()?.let { add(text(Atom.GENRE, it)) }
        t.comment.clean(4096)?.let { add(text(Atom.CMT, it)) }
        add(text(Atom.TOO, "Ctify"))
        if (t.trackNumber > 0) {
            val v = ByteArrayOutputStream(8)
            listOf(0, t.trackNumber, t.trackTotal, 0).forEach { writeUInt16(v, it.coerceIn(0, 0xFFFF)) }
            add(data(Atom.TRKN, v.toByteArray(), 0))
        }
        t.cover?.let { c -> artworkType(c)?.let { add(data(Atom.COVR, c, it)) } }
    }

    private fun text(type: Int, s: String) = data(type, s.toByteArray(StandardCharsets.UTF_8), 1)

    private fun data(type: Int, value: ByteArray, dataType: Int): ByteArray {
        val p = ByteArrayOutputStream(value.size + 8)
        writeUInt32(p, dataType.toLong())
        writeUInt32(p, 0)
        p.write(value)
        return atom(type, atom(Atom.DATA, p.toByteArray()))
    }

    private fun artworkType(b: ByteArray): Int? = when {
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> 13
        b.size >= 4 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() -> 14
        else -> null
    }

    private fun parseBoxes(source: ByteArray, start: Int, end: Int): List<Box> {
        if (start < 0 || end < start || end > source.size) throw IOException("invalid range")
        val boxes = ArrayList<Box>()
        var pos = start
        while (pos + 8 <= end) {
            val small = readUInt32(source, pos)
            var header = 8
            val size = when (small) {
                0L -> (end - pos).toLong()
                1L -> { if (pos + 16 > end) throw IOException("invalid large box"); header = 16; readUInt64(source, pos + 8) }
                else -> small
            }
            if (size < header || size > Int.MAX_VALUE || pos + size > end) throw IOException("invalid box size")
            boxes += Box(pos, (pos + size).toInt(), header, readInt32(source, pos + 4))
            pos += size.toInt()
            if (small == 0L) break
        }
        if (pos != end && !source.copyOfRange(pos, end).all { it == 0.toByte() }) throw IOException("unparsed bytes")
        return boxes
    }

    private fun atom(type: Int, payload: ByteArray): ByteArray {
        val size = payload.size + 8L
        if (size > 0xFFFF_FFFFL) throw IOException("atom too large")
        val out = ByteArrayOutputStream(size.toInt())
        writeUInt32(out, size)
        out.write(Atom.bytes(type))
        out.write(payload)
        return out.toByteArray()
    }

    private fun List<ByteArray>.concat(): ByteArray = ByteArrayOutputStream().also { o -> forEach(o::write) }.toByteArray()

    private fun String.clean(max: Int = 1024) = trim().replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() }?.take(max)

    private fun readInt32(s: ByteArray, o: Int) =
        ((s[o].toInt() and 0xFF) shl 24) or ((s[o + 1].toInt() and 0xFF) shl 16) or ((s[o + 2].toInt() and 0xFF) shl 8) or (s[o + 3].toInt() and 0xFF)

    private fun readUInt32(s: ByteArray, o: Int): Long = readInt32(s, o).toLong() and 0xFFFF_FFFFL

    private fun readUInt64(s: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (s[o + i].toLong() and 0xFFL)
        return v
    }

    private fun writeUInt16(out: ByteArrayOutputStream, v: Int) { out.write((v ushr 8) and 0xFF); out.write(v and 0xFF) }

    private fun writeUInt32(out: ByteArrayOutputStream, v: Long) {
        for (s in 24 downTo 0 step 8) out.write(((v ushr s) and 0xFF).toInt())
    }

    private class Box(val start: Int, val end: Int, header: Int, val type: Int) {
        val length = end - start
        val payloadStart = start + header
    }

    private object Atom {
        val MOOV = ascii("moov"); val MDAT = ascii("mdat"); val UDTA = ascii("udta"); val META = ascii("meta")
        val HDLR = ascii("hdlr"); val ILST = ascii("ilst"); val DATA = ascii("data"); val MDIR = ascii("mdir")
        val STCO = ascii("stco"); val CO64 = ascii("co64")
        val NAM = cc(0xA9, 'n', 'a', 'm'); val ART = cc(0xA9, 'A', 'R', 'T'); val ALB = cc(0xA9, 'a', 'l', 'b')
        val DAY = cc(0xA9, 'd', 'a', 'y'); val GENRE = cc(0xA9, 'g', 'e', 'n'); val CMT = cc(0xA9, 'c', 'm', 't')
        val TOO = cc(0xA9, 't', 'o', 'o'); val AART = ascii("aART"); val COVR = ascii("covr"); val TRKN = ascii("trkn")
        val REPLACED = setOf(NAM, ART, ALB, DAY, GENRE, CMT, TOO, AART, COVR, TRKN)
        val CONTAINERS = listOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "udta", "moof", "traf", "mvex").map(::ascii).toSet()

        fun bytes(t: Int) = byteArrayOf((t ushr 24).toByte(), (t ushr 16).toByte(), (t ushr 8).toByte(), t.toByte())
        private fun ascii(v: String) = cc(v[0].code, v[1], v[2], v[3])
        private fun cc(a: Int, b: Char, c: Char, d: Char) = ((a and 0xFF) shl 24) or (b.code shl 16) or (c.code shl 8) or d.code
    }
}
