package com.ctify.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.Track
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.radio.Station
import com.ctify.player.radio.StationQuery
import com.ctify.player.ui.radio.HeardRow
import com.ctify.player.ui.radio.radioRepo
import com.ctify.player.ui.radio.rememberRadioUi
import com.ctify.player.ui.radio.stationRows
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.youtube.CardRow
import com.ctify.player.ui.youtube.rememberDownloadStates
import com.ctify.player.ui.youtube.ytStatus
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongRow
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtIntake
import com.ctify.player.youtube.YtPlaylistRef
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class Scope(val label: String) { ALL("All"), LIBRARY("Library"), HEARD("Heard"), YOUTUBE("YouTube"), RADIO("Radio") }
private enum class Kind(val label: String) { SONGS("Songs"), ALBUMS("Albums"), VIDEOS("All videos") }

/** Home searches everything: your library, songs heard on the radio, YouTube and stations. */
internal val HOME_SCOPES = Scope.entries.toList()

internal fun Track.matches(q: String) = title.contains(q, true) || artist.contains(q, true) || album.contains(q, true)

/** The rounded search box every tab uses. */
@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextMuted) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Filled.Close, "Clear search", tint = TextSecondary)
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceElevated,
            unfocusedContainerColor = SurfaceElevated,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent,
        ),
    )
}

/** Results for [query] from the [scopes] sources, with chips to narrow them: your songs or heard ones first, then YouTube, then radio. */
@Composable
internal fun SearchResults(viewModel: PlayerViewModel, query: String, scopes: List<Scope>) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val radioUi = rememberRadioUi()
    val dlStates = rememberDownloadStates()
    val radio = radioRepo()
    val heardAll by radio.heard.collectAsStateWithLifecycle(initialValue = emptyList())
    var scope by rememberSaveable { mutableStateOf(Scope.ALL) }
    var kind by rememberSaveable { mutableStateOf(Kind.SONGS) }
    var ytSongs by remember { mutableStateOf<List<YtSong>>(emptyList()) }
    val yt = remember(ytSongs) { ytSongs.map { it.toTrack() } }
    var ytAlbums by remember { mutableStateOf<List<YtPlaylistRef>>(emptyList()) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var ytState by remember { mutableStateOf<String?>(null) }
    var radioState by remember { mutableStateOf<String?>(null) }

    val q = query.trim()
    val local = remember(q, tracks) {
        if (q.isEmpty()) emptyList()
        else tracks.filter { it.matches(q) }
            // on the phone first, saved streams after
            .sortedBy { if (it.uri.startsWith("ytstream")) 1 else 0 }
    }
    val localAlbums = remember(q, tracks) {
        if (q.isEmpty()) emptyList()
        else tracks.filter { it.album.contains(q, true) || it.artist.contains(q, true) }
            .groupBy { it.album }.filterKeys { it.isNotBlank() && it != "YouTube" }.values.toList()
    }
    val heard = remember(q, heardAll) {
        if (q.isEmpty()) emptyList() else heardAll.filter { it.title.contains(q, true) || it.artist?.contains(q, true) == true }
    }
    val wantLibrary = Scope.LIBRARY in scopes && (scope == Scope.ALL || scope == Scope.LIBRARY)
    val wantHeard = Scope.HEARD in scopes && kind == Kind.SONGS && (scope == Scope.ALL || scope == Scope.HEARD)
    val wantYt = scope == Scope.ALL || scope == Scope.YOUTUBE
    val wantRadio = (scope == Scope.ALL && kind == Kind.SONGS) || scope == Scope.RADIO
    LaunchedEffect(q, wantYt, kind) {
        ytSongs = emptyList(); ytAlbums = emptyList(); ytState = null
        if (q.length < 2 || !wantYt) return@LaunchedEffect
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { ytState = it; return@LaunchedEffect }
        delay(600)
        ytState = LOADING
        ytState = try {
            when (kind) {
                Kind.ALBUMS -> ytAlbums = YouTubeSource.searchAlbums(q)
                Kind.VIDEOS -> ytSongs = YouTubeSource.searchVideos(q)
                Kind.SONGS -> ytSongs = YouTubeSource.search(q)
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "YouTube search failed"
        }
    }
    LaunchedEffect(q, wantRadio) {
        stations = emptyList(); radioState = null
        if (q.length < 2 || !wantRadio) return@LaunchedEffect
        NetworkGate.blockReason(Feature.RADIO)?.let { radioState = it; return@LaunchedEffect }
        delay(600)
        radioState = LOADING
        radioState = radio.search(StationQuery(name = q, limit = 20)).fold({ stations = it; null }, { it.message ?: "Radio search failed" })
    }

    LazyColumn(contentPadding = contentPadding()) {
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scopes.forEach { s -> FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) }) }
            }
            if (scope != Scope.RADIO) Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
            }
        }

        if (wantLibrary && kind == Kind.ALBUMS) {
            header("Your albums")
            if (localAlbums.isEmpty()) note("No albums in your library match \"$q\"")
            items(localAlbums, key = { "la" + it.first().album }) { list ->
                CardRow(list.firstNotNullOfOrNull { it.coverUri }, list.first().album, "${list.first().artist} · ${list.size} songs") {
                    viewModel.playQueue(list, 0)
                }
            }
        }

        if (wantLibrary && kind == Kind.SONGS) {
            header("Your library")
            if (local.isEmpty()) note("Nothing in your library matches \"$q\"")
            items(local, key = { "l" + it.id }) { track ->
                SongRow(track, SongContext.Search, local, onPlay = { viewModel.playQueue(local, local.indexOf(track)) }, status = ytStatus(track, dlStates))
            }
        }

        if (wantHeard) {
            header("Heard on the radio")
            if (heard.isEmpty()) note("No heard songs match \"$q\"")
            items(heard, key = { "hs" + it.id }) { HeardRow(it) }
        }

        if (wantYt && kind == Kind.ALBUMS) {
            header("Albums on YouTube")
            status(ytState, ytAlbums.isEmpty() && q.length >= 2, "No albums found on YouTube")
            items(ytAlbums, key = { "ya" + it.id }) { a ->
                // opens in the YouTube page, which has Play, Save and Download for the album
                CardRow(a.thumbnail, a.title, a.subtitle) { YtIntake.offer("https://music.youtube.com/playlist?list=${a.id}") }
            }
        }

        if (wantYt && kind != Kind.ALBUMS) {
            header(if (kind == Kind.VIDEOS) "All YouTube videos" else "YouTube")
            status(ytState, yt.isEmpty() && q.length >= 2, "No songs found on YouTube")
            items(yt, key = { "y" + it.id }) { track ->
                SongRow(track, SongContext.Search, yt, onPlay = { viewModel.repository.remember(yt); viewModel.playQueue(yt, yt.indexOf(track)) })
            }
        }

        if (wantRadio) {
            header("Radio")
            status(radioState, stations.isEmpty() && q.length >= 2, "No stations found")
            stationRows(stations, radioUi, keyPrefix = "r")
        }
    }
}

private const val LOADING = "\u0000loading"

private fun LazyListScope.header(text: String) = item(key = "h_$text") {
    SectionHeader(text, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

private fun LazyListScope.note(text: String) = item {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

private fun LazyListScope.status(state: String?, empty: Boolean, emptyText: String) {
    when {
        state == LOADING -> item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        }
        state != null -> note(state)
        empty -> note(emptyText)
    }
}
