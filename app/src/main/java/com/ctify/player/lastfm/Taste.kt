package com.ctify.player.lastfm

import com.ctify.player.data.MusicRepository
import com.ctify.player.data.Track
import com.ctify.player.genre.Genre
import com.ctify.player.radio.RadioRepository
import com.ctify.player.ui.youtube.ytVideoId
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.flow.first

/** What the listener likes, from listening history, downloads and the radio, as Last.fm tags and artists. */
object Taste {
    /** Last.fm tags for the genre families in [raw], most frequent first. */
    fun genreTags(raw: List<String?>): List<String> =
        raw.mapNotNull { Genre.of(it) }.filter { it != Genre.SPOKEN }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key.tags.first() }

    /** [mine] first, then popular tags of other families, [n] in all. */
    fun genreRow(mine: List<String>, popular: List<String>, n: Int = 12): List<String> {
        val families = mine.mapNotNullTo(HashSet()) { Genre.of(it) }
        val out = mine.take(n).toMutableList()
        for (t in popular) {
            if (out.size >= n) break
            val g = Genre.of(t)
            if (t in out || (g != null && !families.add(g))) continue
            out += t
        }
        return out
    }

    /** Your own genres land three times as often as the rest. */
    fun weight(tag: String, mine: List<String>): Int {
        val g = Genre.of(tag)
        return if (tag in mine || (g != null && mine.any { Genre.of(it) == g })) 3 else 1
    }

    /** Artists of [plays], most played first, each with a cover from one of their songs. */
    fun artists(plays: List<Track>): List<LastFm.Artist> =
        plays.filter { it.artist.isNotBlank() && !it.artist.equals("Unknown artist", true) }
            .groupBy { it.artist.lowercase() }.values.sortedByDescending { it.size }
            .map { songs -> LastFm.Artist(songs.first().artist, songs.firstNotNullOfOrNull { it.coverUri }) }

    /** History: plays count once, most played songs twice more. */
    suspend fun plays(repo: MusicRepository): List<Track> {
        val most = repo.mostPlayed(30).first()
        return repo.recentlyPlayed(100).first() + most + most
    }

    /** Genre tags from what you played (download genres) and what you heard on the radio. */
    suspend fun myGenres(repo: MusicRepository, radio: RadioRepository): List<String> {
        val genreOf = Downloads.all().first().associate { it.videoId to it.genre }
        val played = plays(repo).mapNotNull { t -> t.ytVideoId()?.let(genreOf::get) }
        return genreTags(played + radio.heard.first().map { it.genre })
    }
}
