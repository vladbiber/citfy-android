package com.ctify.player.recommend

import com.ctify.player.data.MusicRepository
import com.ctify.player.player.PlayerHub
import com.ctify.player.youtube.SongMatcher
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtSong
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/** Plays a song known only by name (Last.fm, a local file) from YouTube, with its song radio queued after it. */
object SongRadio {
    // per session; an empty list means no confident match
    private val found = ConcurrentHashMap<String, List<YtSong>>()

    /** The YouTube song for [artist] and [title], only when the match is confident. Network errors give null and are retried next time. */
    suspend fun find(artist: String, title: String, durationMs: Long = 0L): YtSong? {
        val key = "${artist.lowercase()}|${title.lowercase()}"
        found[key]?.let { return it.firstOrNull() }
        val song = try {
            SongMatcher.candidates(artist, title, durationMs).firstOrNull()?.takeIf { it.score >= SongMatcher.ACCEPT }?.song
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        found[key] = listOfNotNull(song)
        return song
    }

    /** Starts [song] at once, then queues its cleaned YouTube mix behind it. */
    suspend fun play(repo: MusicRepository, song: YtSong) {
        val first = song.toTrack()
        repo.remember(listOf(first))
        PlayerHub.playQueue(listOf(first.id), 0)
        val mix = try {
            YouTubeSource.radio(song.videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val rest = RadioMixer.clean(listOf(song), mix, emptySet()).take(25).map { it.toTrack() }
        repo.remember(rest)
        rest.forEach { PlayerHub.addToQueue(it.id) }
    }
}
