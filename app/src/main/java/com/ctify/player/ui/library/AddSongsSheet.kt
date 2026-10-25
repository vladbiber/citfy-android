package com.ctify.player.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.Track
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.ui.AlbumArt
import com.ctify.player.ui.SearchField
import com.ctify.player.ui.SectionHeader
import com.ctify.player.ui.matches
import com.ctify.player.ui.songs.SongActions
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceDark
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.YouTubeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Search your library and YouTube, tick songs, add them all to [playlist] at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(repo: MusicRepository, playlist: String, onDismiss: () -> Unit) {
    val tracks by repo.tracks.collectAsStateWithLifecycle()
    val playlists by repo.playlists.collectAsStateWithLifecycle()
    val inPlaylist = remember(playlists, playlist) { playlists.firstOrNull { it.name == playlist }?.trackIds.orEmpty().toSet() }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val picked = remember { mutableStateListOf<Track>() }
    // with no query: your most recent songs, so the sheet is useful right away
    val local by produceState(emptyList<Track>(), q, tracks) {
        value = withContext(Dispatchers.Default) { (if (q.isEmpty()) tracks.asReversed() else tracks.filter { it.matches(q) }).take(200) }
    }
    var yt by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.length < 2 || NetworkGate.blockReason(Feature.YOUTUBE) != null) { yt = emptyList(); return@LaunchedEffect }
        YouTubeSource.cached<List<com.ctify.player.youtube.YtSong>>(YouTubeSource.SONGS, q)?.let { yt = it.map { s -> s.toTrack() }; return@LaunchedEffect }
        delay(250)
        loading = true
        yt = try { YouTubeSource.search(q).map { it.toTrack() } } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        loading = false
    }
    val toggle: (Track) -> Unit = { t -> if (picked.any { it.id == t.id }) picked.removeAll { it.id == t.id } else picked += t }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Add to $playlist", color = TextPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Button(enabled = picked.isNotEmpty(), onClick = { SongActions.addToPlaylist(repo, playlist, picked.toList()); onDismiss() }) {
                    Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}")
                }
            }
            SearchField(query, { query = it }, "Search your songs and YouTube", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Accent)
            LazyColumn(Modifier.weight(1f)) {
                item { SectionHeader(if (q.isEmpty()) "Recently added" else "Your library", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                if (local.isEmpty()) item { Note(if (q.isEmpty()) "Your library is empty" else "Nothing in your library matches \"$q\"") }
                items(local, key = { "l" + it.id }) { t -> PickRow(t, picked.any { it.id == t.id }, t.id in inPlaylist) { toggle(t) } }
                if (yt.isNotEmpty()) {
                    item { SectionHeader("YouTube", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
                    items(yt, key = { "y" + it.id }) { t -> PickRow(t, picked.any { it.id == t.id }, t.id in inPlaylist) { toggle(t) } }
                }
            }
        }
    }
}

@Composable
private fun PickRow(track: Track, checked: Boolean, already: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked || already, enabled = !already, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(track, 44.dp, shape = MaterialTheme.shapes.small)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (already) "Already in this playlist" else track.artist, color = if (already) TextMuted else TextSecondary,
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked || already, onCheckedChange = null, enabled = !already, colors = CheckboxDefaults.colors(checkedColor = Accent))
    }
}

@Composable
private fun Note(text: String) = Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
}
