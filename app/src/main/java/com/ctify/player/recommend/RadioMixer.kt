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
        return spread(fresh + heard).map(::tidy)
    }

    private val VIDEO_TAG = Regex(
        "\\s*[(\\[](official[^)\\]]*|music video|lyrics?( video)?|audio|video|visuali[sz]er|hd|4k|mv)[)\\]]",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Mix items are music videos: "Artist - Song (Official Video)" by "ArtistVEVO" becomes "Song" by "Artist",
     * and the 16:9 medium thumbnail replaces the letterboxed 4:3 one so square covers have no black bars.
     */
    fun tidy(s: YtSong): YtSong {
        val artist = s.artist.removeSuffix(" - Topic").replace(Regex("(?i)vevo$"), "").trim().ifEmpty { s.artist }
        val parts = s.title.split(" - ", limit = 2)
        // only split when the left side really is the artist, so "Song - Remastered" stays whole;
        // spaces are ignored because channel names drop them ("RickAstleyVEVO")
        val split = parts.size == 2 && squash(artistKey(parts[0])) == squash(artistKey(artist))
        val title = if (split) parts[1] else s.title
        return s.copy(
            title = title.replace(VIDEO_TAG, "").trim().ifEmpty { s.title },
            artist = if (split) parts[0].trim() else artist,
            thumbnail = s.thumbnail?.replace("/hqdefault.jpg", "/mqdefault.jpg"),
        )
    }

    private fun squash(s: String) = s.filter { it.isLetterOrDigit() }

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
