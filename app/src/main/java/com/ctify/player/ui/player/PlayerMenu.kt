package com.ctify.player.ui.player

import com.ctify.player.ui.songCount
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.CtifyApp
import com.ctify.player.data.Track
import com.ctify.player.player.PlayerHub
import com.ctify.player.radio.Station
import com.ctify.player.radio.StationQuery
import com.ctify.player.radio.StationSort
import com.ctify.player.recommend.RadioMixer
import com.ctify.player.ui.theme.SurfaceDark
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.songs.SheetRow
import com.ctify.player.ui.songs.Snack
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongSheet
import com.ctify.player.ui.songs.SongTarget

/** The player's ⋮ menu for a song: the song sheet with the player's look, sleep timer and playlists added. */
@Composable
fun SongPlayerMenu(track: Track, onOpenPlaylist: (String) -> Unit, onDismiss: () -> Unit, onLook: () -> Unit, onSleep: () -> Unit) =
    SongSheet(SongTarget.Song(track), SongContext.Other, onDismiss, extraTop = {
        PlayerRows(onDismiss, onLook, onSleep)
        PlaylistsFor(track, onOpenPlaylist, onDismiss)
    })

/** Player look and sleep timer, shared by the song and station menus. */
@Composable
private fun PlayerRows(onDismiss: () -> Unit, onLook: () -> Unit, onSleep: () -> Unit) {
    SheetRow(Icons.Filled.Palette, "Player look", "Skins and visualizers") { onDismiss(); onLook() }
    SheetRow(Icons.Filled.Bedtime, "Sleep timer", sleepLeft()?.let { "Pausing $it" } ?: "Fade out and pause later") { onDismiss(); onSleep() }
}

/** "In your playlists" (tap to open) and "Would fit in" (playlists with the same artist, tap to add). */
@Composable
private fun ColumnScope.PlaylistsFor(track: Track, onOpenPlaylist: (String) -> Unit, onDismiss: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as CtifyApp).repository
    val playlists by repo.playlists.collectAsStateWithLifecycle()
    val library by repo.tracks.collectAsStateWithLifecycle()
    val (inIt, rest) = playlists.partition { track.id in it.trackIds }
    // shortcut: "fits" means another song by the same artist is in it; add per-song genres if this feels too narrow
    val key = RadioMixer.artistKey(track.artist)
    val artistOf = library.associate { it.id to RadioMixer.artistKey(it.artist) }
    val fits = if (key.isBlank()) emptyList() else rest.filter { pl -> pl.trackIds.any { artistOf[it] == key } }
    if (inIt.isNotEmpty()) {
        Heading("In your playlists")
        inIt.forEach { pl -> SheetRow(Icons.AutoMirrored.Filled.QueueMusic, pl.name, "${songCount(pl.trackIds.size)}") { onDismiss(); onOpenPlaylist(pl.name) } }
    }
    if (fits.isNotEmpty()) {
        Heading("Would fit in")
        fits.forEach { pl ->
            SheetRow(Icons.AutoMirrored.Filled.PlaylistAdd, pl.name, "Has other songs by ${track.artist}") {
                repo.addToPlaylist(pl.name, track.id)
                onDismiss()
                Snack.show("Added to ${pl.name}") { repo.removeFromPlaylist(pl.name, track.id) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, color = TextMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))

/** The player's ⋮ menu on radio: the station's website, its genre and similar stations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationPlayerMenu(station: Station, onDismiss: () -> Unit, onLook: () -> Unit, onSleep: () -> Unit) {
    val ctx = LocalContext.current
    val radio = (ctx.applicationContext as CtifyApp).radio
    val similar by produceState<List<Station>?>(null, station.uuid) {
        val tags = station.tagList.take(3)
        value = if (tags.isEmpty()) emptyList()
        else radio.search(StationQuery(tags = tags, sort = StationSort.VOTES, limit = 12)).getOrDefault(emptyList())
            .filter { it.uuid != station.uuid && !it.name.equals(station.name, true) }.distinctBy { it.name.lowercase() }.take(8)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                listOfNotNull(station.genre?.label, station.countryCode).joinToString(" · ").ifEmpty { "Radio" },
                color = TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            PlayerRows(onDismiss, onLook, onSleep)
            station.homepage?.takeIf { it.startsWith("http") }?.let { url ->
                SheetRow(Icons.Filled.Language, "Station website", Uri.parse(url).host) {
                    onDismiss()
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            val list = similar
            if (list == null) Heading("Finding similar stations…")
            else if (list.isNotEmpty()) {
                Heading("Similar stations")
                list.forEachIndexed { i, s ->
                    SheetRow(Icons.Filled.Radio, s.name, s.tagList.take(3).joinToString(" · ")) { onDismiss(); PlayerHub.playStations(list, i) }
                }
            }
        }
    }
}
