package com.ctify.player.recommend

import com.ctify.player.youtube.SongMatcher
import com.ctify.player.youtube.YtSong

/**
 * Cleans a song radio the way Levyra's RadioMixer does: no seeds or repeats, one version of each
 * song, at most [MAX_RUN] songs in a row by the same artist, songs not played yet first.
 */
object RadioMixer {
    const val MAX_RUN = 2

    private val VERSION = Regex(
        "\\b(live|remix(ed)?|sped up|speed up|slowed|reverb|nightcore|cover|karaoke|instrumental|acoustic|8d|lyrics?|lyric video)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "Song (Live)", "Song - Sped Up", "Song slowed + reverb" and the video title "Artist - Song
     * (Official Video)" all give "song".
     */
    fun work(title: String): String {
        val parts = title.split(" - ", limit = 2)
        // "Artist - Song" keeps the right side, "Song - Live" the left
        val right = parts.getOrNull(1)?.let(::bare)
        return if (right.isNullOrEmpty()) bare(parts[0]) else right
    }

    private fun bare(s: String) = SongMatcher.core(s).replace(VERSION, " ").replace(Regex("\\s+"), " ").trim()

    fun isVersion(title: String) = VERSION.containsMatchIn(title)

    /** First credited artist, lower case: "A feat. B", "A & B" and "A, B" all give "a". */
    fun artistKey(artist: String): String =
        artist.lowercase().split(Regex(",|&| x | feat\\.? | ft\\.? | and ")).first().removeSuffix(" - topic").trim()

    /** Same title and artist, or the same title where one of them is a cover, live, remix... */
    fun sameWork(a: YtSong, b: YtSong): Boolean {
        val w = work(a.title)
        if (w.isEmpty() || w != work(b.title)) return false
        return artistKey(a.artist) == artistKey(b.artist) || isVersion(a.title) || isVersion(b.title)
    }

    /** Drops [known] songs (seeds, songs already in the list) and their other versions, then orders the rest. */
    fun clean(known: List<YtSong>, songs: List<YtSong>, played: Set<String>): List<YtSong> {
        val ids = known.mapTo(HashSet()) { it.videoId }
        val kept = ArrayList<YtSong>()
        for (s in songs) {
            if (!ids.add(s.videoId)) continue
            if (known.any { sameWork(it, s) } || kept.any { sameWork(it, s) }) continue
            kept += s
        }
        val (fresh, heard) = kept.partition { it.videoId !in played }
        return spread(fresh + heard)
    }

    /** Takes one song from each list in turn, then cleans the result. */
    fun mix(lists: List<List<YtSong>>, played: Set<String>): List<YtSong> {
        val out = ArrayList<YtSong>()
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return clean(emptyList(), out, played)
    }

    /** Keeps the order but pulls a different artist forward when the same one would play [MAX_RUN] + 1 times running. */
    fun spread(songs: List<YtSong>): List<YtSong> {
        val pool = songs.toMutableList()
        val out = ArrayList<YtSong>(songs.size)
        while (pool.isNotEmpty()) {
            val run = out.takeLast(MAX_RUN).map { artistKey(it.artist) }
            val full = run.size == MAX_RUN && run.distinct().size == 1
            val i = if (full) pool.indexOfFirst { artistKey(it.artist) != run[0] } else 0
            out += pool.removeAt(if (i < 0) 0 else i)
        }
        return out
    }
}
