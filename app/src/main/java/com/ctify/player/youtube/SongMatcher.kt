package com.ctify.player.youtube

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/** Finds the YouTube Music song for a title heard on the radio. */
object SongMatcher {
    const val ACCEPT = 0.78

    data class Match(val song: YtSong, val score: Double)

    /** Best first. Take the first one alone when its score is at least [ACCEPT], else offer the top 3. */
    suspend fun candidates(artist: String?, title: String, durationMs: Long = 0L): List<Match> {
        val query = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title).joinToString(" ")
        return rank(artist, title, durationMs, YouTubeSource.search(query))
    }

    fun rank(artist: String?, title: String, durationMs: Long, results: List<YtSong>): List<Match> =
        results.map { Match(it, score(artist, title, durationMs, it)) }.sortedByDescending { it.score }

    fun score(artist: String?, title: String, durationMs: Long, song: YtSong): Double {
        val wantTitle = core(title)
        var s = 0.6 * similarity(wantTitle, core(song.title))
        if (!artist.isNullOrBlank()) {
            val a = words(artist)
            val b = words(song.artist)
            s += 0.3 * if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.size
        } else {
            s += 0.15
        }
        // Topic channels and plain song results are the official audio
        if (song.album != null || !VIDEO_WORDS.containsMatchIn(song.title)) s += 0.1
        for (w in VERSION_WORDS) {
            val inSong = Regex("\\b$w\\b", RegexOption.IGNORE_CASE).containsMatchIn(song.title)
            val inTitle = Regex("\\b$w\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)
            if (inSong && !inTitle) s -= 0.25
        }
        if (durationMs > 0 && song.durationMs > 0 && abs(durationMs - song.durationMs) > 20_000) s -= 0.1
        return s.coerceIn(0.0, 1.0)
    }

    private val VERSION_WORDS = listOf("live", "cover", "remix", "karaoke", "instrumental", "sped up", "slowed", "acoustic", "8d")
    private val VIDEO_WORDS = Regex("official (music )?video|lyric video|\\(video\\)", RegexOption.IGNORE_CASE)

    /** Title without "feat.", "Remastered" and anything in brackets. */
    fun core(s: String): String = plain(s)
        .replace(Regex("[(\\[{][^)\\]}]*[)\\]}]"), " ")
        .replace(Regex("\\b(feat|ft|featuring)\\b.*$"), " ")
        .replace(Regex("\\b(remaster(ed)?( \\d{4})?|\\d{4} remaster(ed)?|radio edit|official audio)\\b"), " ")
        .replace(Regex(" - .*$"), " ")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    private fun words(s: String) = plain(s).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it != "the" && it != "topic" }.toSet()

    /** 1 minus normalized edit distance. */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            cur.copyInto(prev)
        }
        return 1.0 - prev[b.length].toDouble() / max(a.length, b.length)
    }
}
