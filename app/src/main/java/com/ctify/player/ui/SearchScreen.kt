package com.ctify.player.ui

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.HistoryPrefs
import com.ctify.player.data.Track
import com.ctify.player.keys.LocalDeviceProfile
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
import com.ctify.player.ui.songs.Snack
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongRow
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.youtube.CardRow
import com.ctify.player.ui.youtube.LinkPreview
import com.ctify.player.ui.youtube.YouTubeStart
import com.ctify.player.ui.youtube.rememberDownloadStates
import com.ctify.player.ui.youtube.ytStatus
import com.ctify.player.youtube.SpotifyImport
import com.ctify.player.youtube.Suggest
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtIntake
import com.ctify.player.youtube.YtPlaylistRef
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.DownloadState
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

internal enum class Scope(val label: String) { ALL("All"), LIBRARY("Library"), HEARD("Heard"), YOUTUBE("YouTube"), RADIO("Radio") }

/** The filter pills on top of search; heard songs show under All and Library. */
private val PILLS = listOf(Scope.ALL, Scope.LIBRARY, Scope.YOUTUBE, Scope.RADIO)

/** Everything the search filter sheet sets; kept for the session. */
internal data class SearchFilter(val scope: Scope = Scope.ALL, val lib: SongFilter = SongFilter(), val yt: YtFilter = YtFilter()) : Serializable {
    val active get() = copy(scope = Scope.ALL) != SearchFilter()
}

/** The filter button for [SearchResults]: library and YouTube modifiers for the chosen pill; the pills pick where to search. */
@Composable
internal fun SearchFilterButton(f: SearchFilter, onChange: (SearchFilter) -> Unit) =
    FilterButton(f.active, onReset = { onChange(SearchFilter(scope = f.scope)) }) {
        if (f.scope == Scope.ALL || f.scope == Scope.LIBRARY) {
            FilterLabel("Your library")
            SearchSongOptions(f.lib) { onChange(f.copy(lib = it)) }
        }
        if (f.scope == Scope.ALL || f.scope == Scope.YOUTUBE) {
            FilterLabel("YouTube", section = f.scope == Scope.ALL)
            FilterChoice(YtKind.entries, f.yt.kind, { it.label }) { onChange(f.copy(yt = f.yt.copy(kind = it))) }
            FilterSwitch("Hide live versions", f.yt.hideLive) { onChange(f.copy(yt = f.yt.copy(hideLive = it))) }
            FilterSwitch("Hide remixes", f.yt.hideRemixes) { onChange(f.copy(yt = f.yt.copy(hideRemixes = it))) }
            FilterSwitch("Hide covers", f.yt.hideCovers) { onChange(f.copy(yt = f.yt.copy(hideCovers = it))) }
        }
        if (f.scope == Scope.RADIO) FilterLabel("Stations are found by name")
    }

internal fun Track.matches(q: String) = title.contains(q, true) || artist.contains(q, true) || album.contains(q, true)

/** The rounded search box every tab uses. */
@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.typingField().fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

/** Results for [query], narrowed by [filter]: your songs and heard ones first, then YouTube, then radio. Empty sections stay hidden. */
@Composable
internal fun SearchResults(viewModel: PlayerViewModel, query: String, filter: SearchFilter, onOpenList: (String) -> Unit, onPaste: () -> Unit) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val liked by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val radioUi = rememberRadioUi()
    val dlStates = rememberDownloadStates()
    val radio = radioRepo()
    val scope = rememberCoroutineScope()
    val heardAll by radio.heard.collectAsStateWithLifecycle(initialValue = emptyList())
    val where = filter.scope
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
    val wantLibrary = where == Scope.ALL || where == Scope.LIBRARY
    val wantHeard = lib.match == Match.SONGS && (where == Scope.ALL || where == Scope.LIBRARY || where == Scope.HEARD)
    val wantYt = where == Scope.ALL || where == Scope.YOUTUBE
    val wantRadio = (where == Scope.ALL && lib.match == Match.SONGS && kind == YtKind.SONGS) || where == Scope.RADIO
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
        // loading from the first keystroke, so the empty state doesn't flash; old results stay up meanwhile
        ytState = LOADING
        delay(250)
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
        radioState = LOADING
        delay(300)
        radioState = radio.search(StationQuery(name = q, limit = 20)).fold({ stations = it; null }, { it.message ?: "Radio search failed" })
    }

    val showAlbums = wantLibrary && lib.match == Match.ALBUMS && localAlbums.isNotEmpty()
    val showLocal = wantLibrary && lib.match != Match.ALBUMS && local.isNotEmpty()
    val showHeard = wantHeard && heard.isNotEmpty()
    val ytHas = if (kind == YtKind.ALBUMS) ytAlbums.isNotEmpty() else yt.isNotEmpty()
    // in All, a failed or blocked YouTube only shows when there is nothing else to show
    val ytNote = ytState != null && ytState != LOADING
    val showYt = wantYt && (ytHas || ytState == LOADING || (ytNote && where == Scope.YOUTUBE))
    val radioNote = radioState != null && radioState != LOADING
    val showRadio = wantRadio && (stations.isNotEmpty() || radioState == LOADING || (radioNote && where == Scope.RADIO))
    val nothing = !showAlbums && !showLocal && !showHeard && !showYt && !showRadio

    LazyColumn(contentPadding = contentPadding()) {
        if (showAlbums) {
            header("Your albums")
            items(localAlbums, key = { "la" + it.first().album }) { list ->
                CardRow(list.firstNotNullOfOrNull { it.coverUri }, list.first().album, "${list.first().artist} · ${songCount(list.size)}") {
                    viewModel.playQueue(list, 0)
                }
            }
        }

        if (showLocal) {
            header("Your library")
            items(local, key = { "l" + it.id }) { track ->
                SongRow(track, SongContext.Search, local, onPlay = { viewModel.playQueue(local, local.indexOf(track)) }, status = ytStatus(track, dlStates))
            }
        }

        if (showHeard) {
            header("Heard on the radio")
            items(heard, key = { "hs" + it.id }) { HeardRow(it) }
        }

        if (showYt && kind == YtKind.ALBUMS) {
            header("Albums on YouTube")
            status(ytState, hasResults = ytAlbums.isNotEmpty())
            items(ytAlbums, key = { "ya" + it.id }) { a ->
                // opens right here, with Play, Save and Download for the album
                CardRow(a.thumbnail, a.title, a.subtitle) { onOpenList("https://music.youtube.com/playlist?list=${a.id}") }
            }
        }

        if (showYt && kind != YtKind.ALBUMS) {
            header(if (kind == YtKind.VIDEOS) "All YouTube videos" else "YouTube")
            status(ytState, hasResults = yt.isNotEmpty())
            // the YouTube filter keeps the old Add from YouTube buttons
            if (where == Scope.YOUTUBE && yt.isNotEmpty()) item(key = "ytall") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    FilledTonalButton(onClick = {
                        val fresh = yt.filterNot { viewModel.repository.isSaved(it.id) }.map { it.id }
                        viewModel.repository.saveStreamed(yt)
                        Snack.show("${songCount(yt.size)} saved to your library · they play online") { viewModel.repository.removeStreamed(fresh) }
                    }) {
                        Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save all")
                    }
                    FilledTonalButton(onClick = {
                        val songs = ytSongs.filter { filter.yt.keeps(it.title) }
                        scope.launch {
                            val n = Downloads.enqueue(songs)
                            Snack.show(if (n == 0) "Already saved or in the queue" else "${songCount(n)} added to downloads")
                        }
                    }) {
                        Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Download all")
                    }
                }
            }
            items(yt, key = { "y" + it.id }) { track ->
                SongRow(track, SongContext.Search, yt, onPlay = { viewModel.repository.remember(yt); viewModel.playQueue(yt, yt.indexOf(track)) })
            }
        }

        if (showRadio) {
            header("Radio")
            status(radioState, hasResults = stations.isNotEmpty())
            stationRows(stations, radioUi, keyPrefix = "r")
        }

        if (nothing) item(key = "none") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
                Text("Nothing found for \"$q\"", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                (if (ytNote && wantYt) ytState else if (radioNote && wantRadio) radioState else null)?.let {
                    Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    "Can't find it? Paste a YouTube link to a song or playlist.",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp),
                )
                FilledTonalButton(onClick = onPaste, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Paste link")
                }
            }
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

private fun LazyListScope.status(state: String?, hasResults: Boolean) {
    when {
        // a thin bar over results that are being replaced, a spinner when there's nothing yet
        state == LOADING && hasResults -> item {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = Accent)
        }
        state == LOADING -> item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        }
        state != null -> note(state)
    }
}

/** The text on the clipboard, if any. */
private fun clipboardText(ctx: android.content.Context): String? =
    runCatching { ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() }
        .getOrNull()?.takeIf { it.isNotBlank() }

/**
 * The one search screen, over Home: your library, heard songs, YouTube and radio, narrowed by pills.
 * The YouTube pill is what Add from YouTube was; a pasted or shared YouTube link shows what it is.
 */
@Composable
internal fun SearchOverlay(viewModel: PlayerViewModel, start: Scope, onClose: () -> Unit, onOpenDownloads: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(SearchFilter(scope = start)) }
    // an album or playlist opened from the results, shown like a pasted link
    var openList by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val recent = remember { RecentSearches(ctx) }
    val keepSearches by HistoryPrefs.flow(HistoryPrefs.Key.KEEP_SEARCHES).collectAsStateWithLifecycle()
    var recentList by remember(keepSearches) { mutableStateOf(if (keepSearches) recent.all() else emptyList()) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    val downloads by Downloads.all().collectAsState(initial = emptyList())
    val pending = downloads.count { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }

    BackHandler(onBack = onClose)
    BackHandler(enabled = openList != null) { openList = null }
    // keyboard phones: Back first leaves the box, so shortcuts work again
    BackHandler(enabled = fieldFocused && LocalDeviceProfile.current.isKeyboardPhone) { focusManager.clearFocus() }
    LaunchedEffect(start) { searchFilter = searchFilter.copy(scope = start) }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!focusedOnce) { focusedOnce = true; runCatching { focus.requestFocus() } } }
    // a link shared from another app lands here, on the YouTube pill
    val shared by YtIntake.pending.collectAsState()
    LaunchedEffect(shared) {
        shared?.let { query = it; openList = null; searchFilter = searchFilter.copy(scope = Scope.YOUTUBE); YtIntake.pending.value = null; focusManager.clearFocus() }
    }
    // a search counts once the user stopped typing for a while
    LaunchedEffect(query) {
        openList = null
        val q = query.trim()
        if (q.length < 2 || YouTubeSource.isLink(q) || SpotifyImport.isLink(q) || !HistoryPrefs[HistoryPrefs.Key.KEEP_SEARCHES]) return@LaunchedEffect
        delay(2000)
        recent.add(q); recentList = recent.all()
    }
    val paste: () -> Unit = {
        val text = clipboardText(ctx)
        if (text != null && (YouTubeSource.isLink(text) || SpotifyImport.isLink(text))) {
            query = text; focusManager.clearFocus()
        } else Snack.show("Copy a YouTube link first, then tap Paste link")
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search", tint = TextPrimary) }
            SearchField(
                query, { query = it }, "Songs, artists, stations or a link",
                Modifier.weight(1f).focusRequester(focus).onFocusChanged { fieldFocused = it.isFocused },
            )
            SearchFilterButton(searchFilter) { searchFilter = it }
        }
        PillTabs(PILLS.map { it.label }, PILLS.indexOf(searchFilter.scope).coerceAtLeast(0), { searchFilter = searchFilter.copy(scope = PILLS[it]) })
        val q = query.trim()
        val isLink = YouTubeSource.isLink(q)
        // paste, and on the YouTube pill the kind of result and the downloads
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(onClick = paste, label = { Text("Paste link") }, leadingIcon = { Icon(Icons.Filled.ContentPaste, null, Modifier.size(AssistChipDefaults.IconSize)) })
            if (searchFilter.scope == Scope.YOUTUBE) {
                YtKind.entries.forEach { k ->
                    FilterChip(
                        selected = searchFilter.yt.kind == k,
                        onClick = { searchFilter = searchFilter.copy(yt = searchFilter.yt.copy(kind = k)) },
                        label = { Text(if (k == YtKind.VIDEOS) "All videos" else k.label) },
                    )
                }
                AssistChip(
                    onClick = onOpenDownloads,
                    label = { Text(if (pending > 0) "Downloads ($pending)" else "Downloads") },
                    leadingIcon = { Icon(Icons.Filled.Download, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
        // suggestions while the box has focus; one or two letters suggest nothing useful
        var suggestions by remember { mutableStateOf(emptyList<String>()) }
        LaunchedEffect(q, fieldFocused) {
            if (!fieldFocused || q.length < 3 || isLink || NetworkGate.blockReason(Feature.YOUTUBE) != null) { suggestions = emptyList(); return@LaunchedEffect }
            Suggest.cached(q)?.let { suggestions = it; return@LaunchedEffect }
            delay(120)
            suggestions = try { Suggest.of(q) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        }
        if (suggestions.isNotEmpty()) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { s ->
                SuggestionChip(onClick = { query = s; focusManager.clearFocus() }, label = { Text(s, maxLines = 1) })
            }
        }
        val list = openList
        when {
            SpotifyImport.isLink(q) -> Column(Modifier.padding(16.dp)) {
                Text("That's a Spotify link", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text("Guava Player finds its songs on YouTube and saves them as a playlist.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { YtIntake.spotify.value = q; query = "" }, modifier = Modifier.padding(top = 12.dp)) { Text("Import it") }
            }
            isLink -> LinkPreview(viewModel, q, Modifier.fillMaxSize())
            list != null -> LinkPreview(viewModel, list, Modifier.fillMaxSize())
            q.isNotEmpty() -> SearchResults(viewModel, query, searchFilter, onOpenList = { openList = it; focusManager.clearFocus() }, onPaste = paste)
            searchFilter.scope == Scope.YOUTUBE -> YouTubeStart(onPaste = paste, onSpotify = { YtIntake.spotify.value = "" }, onDownloads = onOpenDownloads, pending = pending)
            recentList.isEmpty() -> Text(
                "Search your library, songs heard on the radio, YouTube and stations at once, " +
                    "or paste a YouTube link to a song or playlist.",
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
