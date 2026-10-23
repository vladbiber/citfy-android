package com.ctify.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.Track
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.radio.Station
import com.ctify.player.radio.StationQuery
import com.ctify.player.ui.radio.radioRepo
import com.ctify.player.ui.radio.rememberRadioUi
import com.ctify.player.ui.radio.stationRows
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.YouTubeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private enum class Scope(val label: String) { ALL("All"), LIBRARY("Library"), YOUTUBE("YouTube"), RADIO("Radio") }

/** One search box: your own songs first, then YouTube, then radio stations. */
@Composable
fun SearchScreen(viewModel: PlayerViewModel, onMore: (String) -> Unit) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val radioUi = rememberRadioUi()
    val radio = radioRepo()
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(Scope.ALL) }
    var yt by remember { mutableStateOf<List<Track>>(emptyList()) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var ytState by remember { mutableStateOf<String?>(null) }
    var radioState by remember { mutableStateOf<String?>(null) }

    val q = query.trim()
    val local = remember(q, tracks) {
        if (q.isEmpty()) emptyList()
        else tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
            // on the phone first, saved streams after
            .sortedBy { if (it.uri.startsWith("ytstream")) 1 else 0 }
    }
    val wantYt = scope == Scope.ALL || scope == Scope.YOUTUBE
    val wantRadio = scope == Scope.ALL || scope == Scope.RADIO

    LaunchedEffect(q, wantYt) {
        yt = emptyList(); ytState = null
        if (q.length < 2 || !wantYt) return@LaunchedEffect
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { ytState = it; return@LaunchedEffect }
        delay(600)
        ytState = LOADING
        ytState = try {
            yt = YouTubeSource.search(q).map { it.toTrack() }; null
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
            ScreenTitle("Search", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Songs, artists, albums, stations", color = TextMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, "Clear search", tint = TextSecondary)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
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
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Scope.entries.forEach { s -> FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) }) }
            }
        }

        if (q.isEmpty()) {
            item {
                Text(
                    "Search your songs, YouTube and radio at once. Use the chips to search only one of them.",
                    color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                )
            }
            return@LazyColumn
        }

        if (scope == Scope.ALL || scope == Scope.LIBRARY) {
            header("Your library")
            if (local.isEmpty()) note("Nothing in your library matches \"$q\"")
            items(local, key = { "l" + it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == ui.currentTrackId,
                    isPlaying = ui.isPlaying && track.id == ui.currentTrackId,
                    isFavorite = track.id in favorites,
                    onClick = { viewModel.playQueue(local, local.indexOf(track)) },
                    onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                    onMore = { onMore(track.id) },
                )
            }
        }

        if (wantYt) {
            header("YouTube")
            status(ytState, yt.isEmpty() && q.length >= 2, "No songs found on YouTube")
            items(yt, key = { "y" + it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == ui.currentTrackId,
                    isPlaying = ui.isPlaying && track.id == ui.currentTrackId,
                    isFavorite = track.id in favorites,
                    onClick = { viewModel.repository.remember(yt); viewModel.playQueue(yt, yt.indexOf(track)) },
                    onToggleFavorite = { viewModel.repository.remember(listOf(track)); viewModel.toggleFavorite(track.id) },
                    onMore = { viewModel.repository.remember(listOf(track)); onMore(track.id) },
                )
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
    Text(text, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

private fun LazyListScope.note(text: String) = item {
    Text(text, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
