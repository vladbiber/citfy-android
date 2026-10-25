package com.ctify.player.ui.songs

import com.ctify.player.data.MusicRepository
import com.ctify.player.data.PlaylistRecency
import com.ctify.player.data.Track
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import com.ctify.player.radio.HeardSong
import com.ctify.player.ui.home.HomePage
import com.ctify.player.ui.youtube.isStreamed
import com.ctify.player.ui.youtube.toYtSong
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the song sheet acts on. A YouTube search result is `Song(ytSong.toTrack())`. */
sealed interface SongTarget {
    /** A library track or a YouTube song. */
    data class Song(val track: Track) : SongTarget

    /** A song known only by name (Last.fm lists, heard on the radio); it is looked up on YouTube when an action needs it. */
    data class Named(val title: String, val artist: String?, val heard: HeardSong? = null) : SongTarget

    companion object {
        fun of(h: HeardSong) = Named(h.title, h.artist, h)
    }
}

/** Where a song was opened from; decides the one remove action and whether Hide artist shows. */
sealed interface SongContext {
    data object Library : SongContext
    data object Favorites : SongContext
    data class Playlist(val name: String) : SongContext
    /** Recently and Most played */
    data object History : SongContext
    /** For you, the recommendation shelves, genre and creator lists */
    data object Recommended : SongContext
    data object Search : SongContext
    data object Heard : SongContext
    /** the wheel, the player */
    data object Other : SongContext
}

/** App-wide messages for the snackbar in CtifyRoot; [undo] adds an Undo button. */
object Snack {
    class Message(val text: String, val undo: (() -> Unit)?)

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    fun show(text: String, undo: (() -> Unit)? = null) {
        _messages.tryEmit(Message(text, undo))
    }
}

/** Opens the song sheet from anywhere; CtifyRoot hosts it. */
object SongSheets {
    class Request(val target: SongTarget, val context: SongContext, val pool: List<Track>)

    val open = MutableStateFlow<Request?>(null)

    /** a Home page to show (Go to artist / genre); HomeScreen consumes it */
    val goTo = MutableStateFlow<HomePage?>(null)

    /** [pool] is the list the song is in, for Select all. */
    fun show(target: SongTarget, context: SongContext, pool: List<Track> = emptyList()) {
        open.value = Request(target, context, pool)
    }
}

/** The songs picked in one list; picking in another list starts over. */
data class Selection(
    val context: SongContext? = null,
    val tracks: List<Track> = emptyList(),
    val pool: List<Track> = emptyList(),
) {
    val active: Boolean get() = tracks.isNotEmpty()

    fun has(id: String) = tracks.any { it.id == id }

    fun isSelecting(c: SongContext) = active && context == c

    fun toggle(track: Track, context: SongContext, pool: List<Track>): Selection {
        if (!isSelecting(context)) return Selection(context, listOf(track), pool)
        val left = if (has(track.id)) tracks.filterNot { it.id == track.id } else tracks + track
        return if (left.isEmpty()) Selection() else copy(tracks = left, pool = pool)
    }

    fun selectAll() = copy(tracks = (tracks + pool).distinctBy { it.id })
}

object Selecting {
    val state = MutableStateFlow(Selection())

    fun toggle(track: Track, context: SongContext, pool: List<Track>) = state.update { it.toggle(track, context, pool) }
    fun selectAll() = state.update { it.selectAll() }
    fun clear() {
        state.value = Selection()
    }
}

/** The organising actions behind the sheet and the selection bar; each one says what it did. */
object SongActions {
    // outlives the sheet that started the work
    private val scope = MainScope()

    private fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"

    fun toggleLike(repo: MusicRepository, track: Track) {
        repo.remember(listOf(track))
        val was = repo.isFavorite(track.id)
        repo.toggleFavorite(track.id)
        if (was) Snack.show("Removed from Liked songs") { if (!repo.isFavorite(track.id)) repo.toggleFavorite(track.id) }
        else Snack.show("Added to Liked songs")
    }

    fun likeAll(repo: MusicRepository, tracks: List<Track>) {
        val add = tracks.filterNot { repo.isFavorite(it.id) }
        repo.remember(add)
        add.forEach { repo.toggleFavorite(it.id) }
        Snack.show(if (add.isEmpty()) "Already in Liked songs" else "${songs(add.size)} added to Liked songs") {
            add.filter { repo.isFavorite(it.id) }.forEach { repo.toggleFavorite(it.id) }
        }
    }

    /** Adds the songs not yet in [name]; never takes any out, so a mis-tap can't lose songs. */
    fun addToPlaylist(repo: MusicRepository, name: String, tracks: List<Track>) {
        val have = repo.playlists.value.firstOrNull { it.name == name }?.trackIds.orEmpty().toSet()
        val add = tracks.filterNot { it.id in have }
        if (add.isEmpty()) return Snack.show(if (tracks.size == 1) "Already in $name" else "All already in $name")
        repo.remember(add)
        add.forEach { repo.addToPlaylist(name, it.id) }
        PlaylistRecency.touch(name)
        Snack.show(if (tracks.size == 1) "Added to $name" else "${songs(add.size)} added to $name") { repo.removeFromPlaylist(name, add.map { it.id }) }
    }

    fun save(repo: MusicRepository, track: Track) {
        repo.saveStreamed(listOf(track))
        Snack.show("Saved to your library · plays online") { repo.removeStreamed(listOf(track.id)) }
    }

    fun download(tracks: List<Track>) {
        val songs = tracks.filter { it.isStreamed() }.map { it.toYtSong() }
        if (songs.isEmpty()) return Snack.show("Already on the phone")
        scope.launch {
            val n = Downloads.enqueue(songs)
            val wait = NetworkGate.blockReason(Feature.YOUTUBE, Traffic.DOWNLOAD)?.let { ". $it" } ?: ""
            Snack.show(if (n == 0) "Already saved or in the queue" else "${songs(n)} added to downloads$wait")
        }
    }

    /** Plays the song with its YouTube mix queued after it. */
    fun songRadio(repo: MusicRepository, track: Track) {
        scope.launch { com.ctify.player.recommend.SongRadio.play(repo, track.toYtSong()) }
    }

    fun hideArtist(name: String) {
        com.ctify.player.recommend.HiddenArtists.hide(name)
        Snack.show("$name won't be recommended") { com.ctify.player.recommend.HiddenArtists.unhide(name) }
    }

    /** The label of the one remove action for [context], or null when there is nothing to remove. */
    fun removeLabel(repo: MusicRepository, context: SongContext, ids: List<String>): String? = when {
        context is SongContext.Playlist -> "Remove from playlist"
        context == SongContext.History -> "Remove from history"
        ids.any { it.startsWith(YtSong.STREAM_PREFIX) && repo.isSaved(it) } -> "Remove from library"
        else -> null
    }

    fun remove(repo: MusicRepository, context: SongContext, ids: List<String>) {
        when {
            context is SongContext.Playlist -> Snack.show("${songs(ids.size)} removed from ${context.name}", repo.removeFromPlaylist(context.name, ids))
            context == SongContext.History -> Snack.show("${songs(ids.size)} removed from history", repo.removeFromHistory(ids))
            else -> {
                val saved = ids.filter { it.startsWith(YtSong.STREAM_PREFIX) && repo.isSaved(it) }
                Snack.show("${songs(saved.size)} removed from your library", repo.removeStreamed(saved))
            }
        }
    }
}
