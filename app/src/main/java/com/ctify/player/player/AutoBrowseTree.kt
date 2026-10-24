package com.ctify.player.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.Track
import com.ctify.player.radio.RadioRepository
import com.ctify.player.radio.Station
import kotlinx.coroutines.flow.first

/** Media ids of the Android Auto browse tree: four tabs, playlists one level down, playable leaves "list<SEP>id". */
object AutoIds {
    const val ROOT = "auto.root"
    const val FAVORITES = "auto.favorites"
    const val PLAYLISTS = "auto.playlists"
    const val RADIO = "auto.radio"
    const val SONGS = "auto.songs"
    val TABS = listOf(FAVORITES, PLAYLISTS, RADIO, SONGS)
    private const val PLAYLIST = "auto.playlist:"
    // unit separator: never typed into a playlist name nor part of a track or station id
    private const val SEP = '\u001F'

    fun playlist(name: String) = PLAYLIST + name
    fun playlistName(id: String): String? = if (id.startsWith(PLAYLIST)) id.removePrefix(PLAYLIST) else null
    fun leaf(list: String, itemId: String) = "$list$SEP$itemId"

    /** (list id, track or station id) of a playable leaf; null for folders and any other id. */
    fun parseLeaf(id: String): Pair<String, String>? {
        val i = id.indexOf(SEP)
        if (i <= 0 || i == id.lastIndex) return null
        val list = id.substring(0, i)
        return if (list in TABS || playlistName(list) != null) list to id.substring(i + 1) else null
    }

    fun <T> page(items: List<T>, page: Int, size: Int): List<T> {
        if (page < 0 || size <= 0) return emptyList()
        val from = page.toLong() * size
        if (from >= items.size) return emptyList()
        return items.subList(from.toInt(), minOf(items.size.toLong(), from + size).toInt())
    }
}

/** Builds the Auto browse tree from the library and radio data, and turns a tapped leaf into a [Cmd]. */
class AutoLibrary(private val repository: MusicRepository, private val radio: RadioRepository) {

    val root: MediaItem = folder(AutoIds.ROOT, "Ctify", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    suspend fun children(parentId: String): List<MediaItem>? = when (parentId) {
        AutoIds.ROOT -> AutoIds.TABS.map(::tab)
        AutoIds.PLAYLISTS -> { ensureLoaded(); repository.playlists.value.map { playlistFolder(it.name, it.coverUri) } }
        AutoIds.RADIO -> stations().map(::station)
        else -> tracks(parentId)?.map { song(parentId, it) }
    }

    suspend fun item(id: String): MediaItem? {
        if (id == AutoIds.ROOT) return root
        if (id in AutoIds.TABS) return tab(id)
        AutoIds.playlistName(id)?.let { name ->
            ensureLoaded()
            return repository.playlists.value.firstOrNull { it.name == name }?.let { playlistFolder(it.name, it.coverUri) }
        }
        val (list, itemId) = AutoIds.parseLeaf(id) ?: return null
        if (list == AutoIds.RADIO) return radio.stationById(itemId)?.let(::station)
        ensureLoaded()
        return repository.trackById(itemId)?.let { song(list, it) }
    }

    /** The custom command the app itself sends for this leaf: its list becomes the queue, or the station list the radio list. */
    suspend fun playCommand(id: String): Pair<String, Bundle>? {
        val (list, itemId) = AutoIds.parseLeaf(id) ?: return null
        if (list == AutoIds.RADIO) {
            val stations = stations()
            val index = stations.indexOfFirst { it.uuid == itemId }.takeIf { it >= 0 } ?: return null
            return Cmd.PLAY_STATIONS to Bundle().apply {
                putString(Cmd.ARG_STATIONS, Station.toJson(stations)); putInt(Cmd.ARG_INDEX, index)
            }
        }
        val tracks = tracks(list) ?: return null
        val index = tracks.indexOfFirst { it.id == itemId }.takeIf { it >= 0 } ?: return null
        return Cmd.PLAY_QUEUE to Bundle().apply {
            putStringArray(Cmd.ARG_IDS, tracks.map { it.id }.toTypedArray()); putInt(Cmd.ARG_INDEX, index)
        }
    }

    private suspend fun tracks(list: String): List<Track>? {
        ensureLoaded()
        return when (list) {
            AutoIds.FAVORITES -> repository.favoriteTracks()
            AutoIds.SONGS -> repository.tracks.value
            else -> AutoIds.playlistName(list)?.let(repository::playlistTracks)
        }
    }

    /** Favorite stations first, then recent ones not already listed. */
    private suspend fun stations(): List<Station> = (radio.favorites.first() + radio.recents.first()).distinctBy { it.uuid }

    // shortcut: Auto can connect before the service's own load finishes; a second load waits on the lock and reloads once
    private suspend fun ensureLoaded() {
        if (repository.tracks.value.isEmpty()) repository.load(includeDevice = false)
    }

    private fun tab(id: String) = when (id) {
        AutoIds.FAVORITES -> folder(id, "Favorites", MediaMetadata.MEDIA_TYPE_PLAYLIST)
        AutoIds.PLAYLISTS -> folder(id, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        AutoIds.RADIO -> folder(id, "Radio", MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
        else -> folder(id, "All songs", MediaMetadata.MEDIA_TYPE_PLAYLIST)
    }

    private fun playlistFolder(name: String, cover: String?) = folder(
        AutoIds.playlist(name), name, MediaMetadata.MEDIA_TYPE_PLAYLIST,
        cover ?: repository.playlistTracks(name).firstNotNullOfOrNull { it.coverUri },
    )

    private fun song(list: String, t: Track) = playable(
        AutoIds.leaf(list, t.id), t.title, t.artist, t.coverUri, MediaMetadata.MEDIA_TYPE_MUSIC,
    )

    private fun station(s: Station) = playable(
        AutoIds.leaf(AutoIds.RADIO, s.uuid), s.name, s.detail.ifEmpty { "Live radio" }, s.favicon, MediaMetadata.MEDIA_TYPE_RADIO_STATION,
    )

    private fun folder(id: String, title: String, type: Int, art: String? = null) =
        mediaItem(id, MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(type), art)

    private fun playable(id: String, title: String, subtitle: String, art: String?, type: Int) = mediaItem(
        id, MediaMetadata.Builder().setTitle(title).setArtist(subtitle).setIsBrowsable(false).setIsPlayable(true).setMediaType(type), art,
    )

    private fun mediaItem(id: String, meta: MediaMetadata.Builder, art: String?) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(meta.setArtworkUri(art?.let(Uri::parse)).build())
        .build()
}
