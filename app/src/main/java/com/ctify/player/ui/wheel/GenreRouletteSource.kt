package com.ctify.player.ui.wheel

import com.ctify.player.data.MusicRepository
import com.ctify.player.genre.Genre
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.player.PlayerHub
import com.ctify.player.youtube.StreamResolver
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtCategory
import com.ctify.player.youtube.YtPlaylistRef
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A random genre from YouTube Music, a random playlist in it, and its songs as the wheel. */
class GenreRouletteSource(private val repo: MusicRepository) : WheelSource {
    override val id = "genre"
    override val label = "Genre roulette"
    override val noun = "songs"
    override val unavailable: String? get() = NetworkGate.blockReason(Feature.YOUTUBE)?.let { "YouTube is off" }
    override val canDownload get() = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var genres: List<YtCategory>? = null
    private val playlists = HashMap<String, List<YtPlaylistRef>>()
    private val genreOf = HashMap<String, String>()

    override suspend fun pool(): List<WheelItem> {
        // genre pages only, not moods; the lists are kept for the session
        val all = genres ?: YouTubeSource.moodsAndGenres().filter { Genre.of(it.title) != null }.also { genres = it }
        val genre = all.randomOrNull() ?: return emptyList()
        val lists = playlists.getOrPut(genre.browseId + genre.params) { YouTubeSource.categoryPlaylists(genre) }
        val list = lists.randomOrNull() ?: return emptyList()
        return YouTubeSource.playlistSongs(list.id).shuffled().take(40).map { s ->
            genreOf[s.videoId] = genre.title
            WheelItem(s.toTrack().id, s.title, "${s.artist} · ${genre.title}", s.thumbnail, s)
        }
    }

    override suspend fun prepare(winner: WheelItem): Boolean =
        runCatching { StreamResolver.resolve((winner.ref as YtSong).videoId) }.isSuccess

    override fun play(winner: WheelItem, wheel: List<WheelItem>) {
        val songs = listOf(winner) + wheel.filter { it.id != winner.id }.shuffled()
        val tracks = songs.map { (it.ref as YtSong).toTrack() }
        repo.remember(tracks)
        PlayerHub.playQueue(tracks.map { it.id }, 0)
    }

    override fun download(item: WheelItem) {
        val s = item.ref as YtSong
        scope.launch { Downloads.enqueue(listOf(s), genre = genreOf[s.videoId]) }
    }
}
