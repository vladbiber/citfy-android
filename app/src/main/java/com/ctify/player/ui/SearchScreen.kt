package com.ctify.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import com.ctify.player.youtube.Suggest
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.ctify.player.data.HistoryPrefs
import com.ctify.player.data.Track
import com.ctify.player.keys.typingField
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        modifier = modifier.typingField().fillMaxWidth(),
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
    // off the main thread, so typing stays smooth with a big library
    val local by produceState(emptyList<Track>(), q, tracks, lib, liked) {
        value = if (q.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            lib.apply(tracks, q, liked)
                // on the phone first, saved streams after
                .sortedBy { if (it.uri.startsWith("ytstream")) 1 else 0 }
        }
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
        if (q.length < 2 || !wantYt) { ytSongs = emptyList(); ytAlbumsAll = emptyList(); ytState = null; return@LaunchedEffect }
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { ytSongs = emptyList(); ytAlbumsAll = emptyList(); ytState = it; return@LaunchedEffect }
        // a query searched in the last minutes shows at once
        val hit: Any? = when (kind) {
            YtKind.ALBUMS -> YouTubeSource.cached<List<YtPlaylistRef>>(YouTubeSource.ALBUMS, q)?.also { ytAlbumsAll = it }
            YtKind.VIDEOS -> YouTubeSource.cached<List<YtSong>>(YouTubeSource.VIDEOS, q)?.also { ytSongs = it }
            YtKind.SONGS -> YouTubeSource.cached<List<YtSong>>(YouTubeSource.SONGS, q)?.also { ytSongs = it }
        }
        if (hit != null) { ytState = null; return@LaunchedEffect }
        // the old results stay up until the new ones arrive
        delay(250)
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
        if (q.length < 2 || !wantRadio) { stations = emptyList(); radioState = null; return@LaunchedEffect }
        NetworkGate.blockReason(Feature.RADIO)?.let { stations = emptyList(); radioState = it; return@LaunchedEffect }
        delay(300)
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
            status(ytState, ytAlbums.isEmpty() && q.length >= 2, "No albums found on YouTube", hasResults = ytAlbums.isNotEmpty())
            items(ytAlbums, key = { "ya" + it.id }) { a ->
                // opens in the YouTube page, which has Play, Save and Download for the album
                CardRow(a.thumbnail, a.title, a.subtitle) { YtIntake.offer("https://music.youtube.com/playlist?list=${a.id}") }
            }
        }

        if (wantYt && kind != YtKind.ALBUMS) {
            header(if (kind == YtKind.VIDEOS) "All YouTube videos" else "YouTube")
            status(ytState, yt.isEmpty() && q.length >= 2, "No songs found on YouTube", hasResults = yt.isNotEmpty())
            items(yt, key = { "y" + it.id }) { track ->
                SongRow(track, SongContext.Search, yt, onPlay = { viewModel.repository.remember(yt); viewModel.playQueue(yt, yt.indexOf(track)) })
            }
        }

        if (wantRadio) {
            header("Radio")
            status(radioState, stations.isEmpty() && q.length >= 2, "No stations found", hasResults = stations.isNotEmpty())
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

private fun LazyListScope.status(state: String?, empty: Boolean, emptyText: String, hasResults: Boolean = false) {
    when {
        // a thin bar over results that are being replaced, a spinner when there's nothing yet
        state == LOADING && hasResults -> item {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = Accent)
        }
        state == LOADING -> item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        }
        state != null -> note(state)
        empty -> note(emptyText)
    }
}

/** The Search tab: one box for your library, heard songs, YouTube and radio; a pasted YouTube link opens it. */
@Composable
internal fun SearchTab(viewModel: PlayerViewModel, add: AddActions, onOpenSettings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(SearchFilter()) }
    val ctx = LocalContext.current
    val recent = remember { RecentSearches(ctx) }
    val keepSearches by HistoryPrefs.flow(HistoryPrefs.Key.KEEP_SEARCHES).collectAsStateWithLifecycle()
    var recentList by remember(keepSearches) { mutableStateOf(if (keepSearches) recent.all() else emptyList()) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    // Back first leaves the box, so keyboard shortcuts work again on phones with a keyboard
    androidx.activity.compose.BackHandler(enabled = fieldFocused) { focusManager.clearFocus() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!focusedOnce) { focusedOnce = true; runCatching { focus.requestFocus() } } }
    // a search counts once the user stopped typing for a while
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2 || YouTubeSource.isLink(q) || !HistoryPrefs[HistoryPrefs.Key.KEEP_SEARCHES]) return@LaunchedEffect
        delay(2000)
        recent.add(q); recentList = recent.all()
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Search", Modifier.weight(1f))
            AddMenu(add)
            SettingsButton(onOpenSettings)
        }
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, "Songs, artists, albums, stations", Modifier.weight(1f).focusRequester(focus).onFocusChanged { fieldFocused = it.isFocused })
            SearchFilterButton(searchFilter, HOME_SCOPES) { searchFilter = it }
        }
        val q = query.trim()
        // suggestions while the box has focus; a tap searches for one
        var suggestions by remember { mutableStateOf(emptyList<String>()) }
        LaunchedEffect(q, fieldFocused) {
            if (!fieldFocused || q.length < 2 || YouTubeSource.isLink(q) || NetworkGate.blockReason(Feature.YOUTUBE) != null) { suggestions = emptyList(); return@LaunchedEffect }
            Suggest.cached(q)?.let { suggestions = it; return@LaunchedEffect }
            delay(120)
            suggestions = try { Suggest.of(q) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        }
        if (suggestions.isNotEmpty()) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { s ->
                androidx.compose.material3.SuggestionChip(onClick = { query = s; focusManager.clearFocus() }, label = { Text(s, maxLines = 1) })
            }
        }
        when {
            YouTubeSource.isLink(q) -> Column(Modifier.padding(16.dp)) {
                Text("That's a YouTube link", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { YtIntake.offer(q); query = "" }) { Text("Open it") }
            }
            q.isNotEmpty() -> SearchResults(viewModel, query, HOME_SCOPES, searchFilter)
            recentList.isEmpty() -> Text(
                "Search your library, songs heard on the radio, YouTube and stations at once.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(contentPadding = contentPadding()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Recent searches", Modifier.weight(1f))
                        TextButton(onClick = { recent.clear(); recentList = emptyList() }) { Text("Clear") }
                    }
                }
                items(recentList, key = { "rs$it" }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { query = s }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.History, null, tint = TextMuted)
                        Spacer(Modifier.width(16.dp))
                        Text(s, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { recent.remove(s); recentList = recent.all() }) { Icon(Icons.Filled.Close, "Forget $s", tint = TextMuted) }
                    }
                }
            }
        }
    }
}

/** The last searches, newest first, kept on the phone. */
internal class RecentSearches(context: android.content.Context) {
    private val prefs = context.applicationContext.getSharedPreferences("recent_searches", android.content.Context.MODE_PRIVATE)

    fun all(): List<String> = prefs.getString("q", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun add(q: String) = save(listOf(q) + all().filterNot { it.equals(q, true) || q.startsWith(it, true) })

    fun remove(q: String) = save(all() - q)

    fun clear() = save(emptyList())

    private fun save(list: List<String>) = prefs.edit().putString("q", list.take(MAX).joinToString("\n")).apply()

    companion object {
        private const val MAX = 12
        fun clear(context: android.content.Context) = RecentSearches(context).clear()
    }
}
