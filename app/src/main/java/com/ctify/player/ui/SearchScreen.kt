package com.ctify.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import java.io.Serializable

internal enum class Scope(val label: String) { ALL("All"), LIBRARY("Library"), HEARD("Heard"), YOUTUBE("YouTube"), RADIO("Radio") }

/** Everything the search filter sheet sets; kept for the session. */
internal data class SearchFilter(val scope: Scope = Scope.ALL, val lib: SongFilter = SongFilter(), val yt: YtFilter = YtFilter()) : Serializable {
    val active get() = this != SearchFilter()
}

/** The filter button for [SearchResults]: where to search, then library and YouTube modifiers for the scopes that use them. */
@Composable
internal fun SearchFilterButton(f: SearchFilter, scopes: List<Scope>, onChange: (SearchFilter) -> Unit) =
    FilterButton(f.active, onReset = { onChange(SearchFilter()) }) {
        FilterLabel("Search in")
        FilterChoice(scopes, f.scope, { it.label }) { onChange(f.copy(scope = it)) }
        if (f.scope == Scope.ALL || f.scope == Scope.LIBRARY) {
            FilterLabel("Your library", section = true)
            SearchSongOptions(f.lib) { onChange(f.copy(lib = it)) }
        }
        if (f.scope == Scope.ALL || f.scope == Scope.YOUTUBE) {
            FilterLabel("YouTube", section = true)
            FilterChoice(YtKind.entries, f.yt.kind, { it.label }) { onChange(f.copy(yt = f.yt.copy(kind = it))) }
            FilterSwitch("Hide live versions", f.yt.hideLive) { onChange(f.copy(yt = f.yt.copy(hideLive = it))) }
            FilterSwitch("Hide remixes", f.yt.hideRemixes) { onChange(f.copy(yt = f.yt.copy(hideRemixes = it))) }
            FilterSwitch("Hide covers", f.yt.hideCovers) { onChange(f.copy(yt = f.yt.copy(hideCovers = it))) }
        }
    }

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

/** Results for [query] from the [scopes] sources, narrowed by [filter]: your songs or heard ones first, then YouTube, then radio. */
@Composable
internal fun SearchResults(viewModel: PlayerViewModel, query: String, scopes: List<Scope>, filter: SearchFilter) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val liked by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val radioUi = rememberRadioUi()
    val dlStates = rememberDownloadStates()
    val radio = radioRepo()
    val heardAll by radio.heard.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = filter.scope.takeIf { it in scopes } ?: Scope.ALL
    val lib = filter.lib
    val kind = filter.yt.kind
    var ytSongs by remember { mutableStateOf<List<YtSong>>(emptyList()) }
    val yt = remember(ytSongs, filter.yt) { ytSongs.filter { filter.yt.keeps(it.title) }.map { it.toTrack() } }
    var ytAlbumsAll by remember { mutableStateOf<List<YtPlaylistRef>>(emptyList()) }
    val ytAlbums = remember(ytAlbumsAll, filter.yt) { ytAlbumsAll.filter { filter.yt.keeps(it.title) } }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var ytState by remember { mutableStateOf<String?>(null) }
    var radioState by remember { mutableStateOf<String?>(null) }

    val q = query.trim()
    val local = remember(q, tracks, lib, liked) {
        if (q.isEmpty()) emptyList()
        else lib.apply(tracks, q, liked)
            // on the phone first, saved streams after
            .sortedBy { if (it.uri.startsWith("ytstream")) 1 else 0 }
    }
    val localAlbums = remember(local, lib) {
        if (lib.match != Match.ALBUMS) emptyList()
        else local.groupBy { it.album }.filterKeys { it.isNotBlank() && it != "YouTube" }.values.toList()
    }
    val heard = remember(q, heardAll) {
        if (q.isEmpty()) emptyList() else heardAll.filter { it.title.contains(q, true) || it.artist?.contains(q, true) == true }
    }
    val wantLibrary = Scope.LIBRARY in scopes && (scope == Scope.ALL || scope == Scope.LIBRARY)
    val wantHeard = Scope.HEARD in scopes && lib.match == Match.SONGS && (scope == Scope.ALL || scope == Scope.HEARD)
    val wantYt = scope == Scope.ALL || scope == Scope.YOUTUBE
    val wantRadio = (scope == Scope.ALL && lib.match == Match.SONGS && kind == YtKind.SONGS) || scope == Scope.RADIO
    LaunchedEffect(q, wantYt, kind) {
        ytSongs = emptyList(); ytAlbumsAll = emptyList(); ytState = null
        if (q.length < 2 || !wantYt) return@LaunchedEffect
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { ytState = it; return@LaunchedEffect }
        delay(600)
        ytState = LOADING
        ytState = try {
            when (kind) {
                YtKind.ALBUMS -> ytAlbumsAll = YouTubeSource.searchAlbums(q)
                YtKind.VIDEOS -> ytSongs = YouTubeSource.searchVideos(q)
                YtKind.SONGS -> ytSongs = YouTubeSource.search(q)
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
        if (wantLibrary && lib.match == Match.ALBUMS) {
            header("Your albums")
            if (localAlbums.isEmpty()) note("No albums in your library match \"$q\"")
            items(localAlbums, key = { "la" + it.first().album }) { list ->
                CardRow(list.firstNotNullOfOrNull { it.coverUri }, list.first().album, "${list.first().artist} · ${songCount(list.size)}") {
                    viewModel.playQueue(list, 0)
                }
            }
        }

        if (wantLibrary && lib.match != Match.ALBUMS) {
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

        if (wantYt && kind == YtKind.ALBUMS) {
            header("Albums on YouTube")
            status(ytState, ytAlbums.isEmpty() && q.length >= 2, "No albums found on YouTube")
            items(ytAlbums, key = { "ya" + it.id }) { a ->
                // opens in the YouTube page, which has Play, Save and Download for the album
                CardRow(a.thumbnail, a.title, a.subtitle) { YtIntake.offer("https://music.youtube.com/playlist?list=${a.id}") }
            }
        }

        if (wantYt && kind != YtKind.ALBUMS) {
            header(if (kind == YtKind.VIDEOS) "All YouTube videos" else "YouTube")
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
