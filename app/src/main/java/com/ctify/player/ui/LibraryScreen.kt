package com.ctify.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.Playlist
import com.ctify.player.data.Track
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import com.ctify.player.ui.youtube.DownloadPicker
import com.ctify.player.ui.youtube.PlaylistDownloadBar
import com.ctify.player.ui.youtube.pickSong
import com.ctify.player.ui.youtube.toYtSong
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.launch
import com.ctify.player.ui.youtube.isStreamed
import com.ctify.player.ui.youtube.rememberDownloadStates
import com.ctify.player.ui.youtube.ytStatus
import com.ctify.player.ui.songs.Snack
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongRow

internal fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** A playlist as a cover card in a grid. */
@Composable
internal fun PlaylistCard(playlist: Playlist, firstTrack: Track?, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = "open", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Playlist ${playlist.name}, ${songCount(playlist.trackIds.size)}"
            }
            .padding(4.dp),
    ) {
        if (playlist.coverUri != null || firstTrack != null) {
            PlaylistArt(playlist, firstTrack, 0.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small).background(SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Accent, modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${songCount(playlist.trackIds.size)}", color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the + menu offers; Library and Discover share it. */
class AddActions(
    val importFolder: () -> Unit,
    val importFile: () -> Unit,
    val youTube: () -> Unit,
    val spotify: () -> Unit,
    val downloads: () -> Unit,
)

/** The + button and its menu; [onNewPlaylist] adds the playlist entry. */
@Composable
internal fun AddMenu(add: AddActions, onNewPlaylist: (() -> Unit)? = null) {
    var showAddMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showAddMenu = true }) {
            Icon(Icons.Filled.Add, if (onNewPlaylist != null) "Add music or a playlist" else "Add music", tint = TextPrimary)
        }
        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }, containerColor = SurfaceElevated) {
            DropdownMenuItem(
                text = { Text("Import a folder", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, null, tint = Accent) },
                onClick = { showAddMenu = false; add.importFolder() },
            )
            DropdownMenuItem(
                text = { Text("Add a song", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.LibraryAdd, null, tint = TextSecondary) },
                onClick = { showAddMenu = false; add.importFile() },
            )
            DropdownMenuItem(
                text = { Text("Add from YouTube", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                onClick = { showAddMenu = false; add.youTube() },
            )
            DropdownMenuItem(
                text = { Text("Import from Spotify", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.QueueMusic, null, tint = TextSecondary) },
                onClick = { showAddMenu = false; add.spotify() },
            )
            DropdownMenuItem(
                text = { Text("Downloads", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.Download, null, tint = TextSecondary) },
                onClick = { showAddMenu = false; add.downloads() },
            )
            if (onNewPlaylist != null) DropdownMenuItem(
                text = { Text("New playlist", color = TextPrimary) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = TextSecondary) },
                onClick = { showAddMenu = false; onNewPlaylist() },
            )
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    add: AddActions,
    openedPlaylist: String?,
    onOpenPlaylist: (String?) -> Unit,
    section: Int = 0,
    onSection: (Int) -> Unit = {},
) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    // one query for every section and the opened playlist
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()

    val opened = openedPlaylist?.takeIf { name -> playlists.any { it.name == name } }
    if (opened != null) {
        PlaylistDetail(viewModel, playlists.first { it.name == opened }, query, { query = it }, onOpenPlaylist)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        full {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Library", Modifier.weight(1f))
                AddMenu(add, onNewPlaylist = { showCreate = true })
            }
        }
        full { SearchField(query, { query = it }, "Search songs, liked songs and playlists", Modifier.padding(horizontal = 8.dp)) }
        full {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Songs", "Liked songs", "Playlists").forEachIndexed { i, label ->
                    TabChip(label, section == i) { onSection(i) }
                }
            }
        }

        when (section) {
            0 -> {
                val shown = if (q.isEmpty()) tracks else tracks.filter { it.matches(q) }
                if (tracks.isEmpty()) full { EmptyHint("No music yet. Tap + to import a folder or a song.") }
                else if (shown.isEmpty()) full { EmptyHint("No songs match \"$q\"") }
                shown.forEach { t ->
                    full { SongRow(t, SongContext.Library, shown, onPlay = { viewModel.playQueue(shown, shown.indexOf(t)) }) }
                }
            }
            1 -> {
                val allFavs = tracks.filter { it.id in favorites }
                val favs = if (q.isEmpty()) allFavs else allFavs.filter { it.matches(q) }
                // local files are already on the phone; only YouTube streams need saving
                val streamed = allFavs.filter { it.isStreamed() }
                if (streamed.isNotEmpty()) full {
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            scope.launch {
                                val n = Downloads.enqueue(streamed.map { it.toYtSong() })
                                val wait = NetworkGate.blockReason(Feature.YOUTUBE, Traffic.DOWNLOAD)?.let { ". $it" } ?: ""
                                Toast.makeText(ctx, if (n == 0) "Already saved or in the queue" else "$n songs added to downloads$wait", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Download all (${streamed.size})")
                        }
                        TextButton(onClick = { showPicker = true }) { Text("Choose songs") }
                    }
                }
                if (allFavs.isEmpty()) full { EmptyHint("Tap the heart on a song to save it here.") }
                else if (favs.isEmpty()) full { EmptyHint("No liked songs match \"$q\"") }
                favs.forEach { t ->
                    full { SongRow(t, SongContext.Favorites, favs, onPlay = { viewModel.playQueue(favs, favs.indexOf(t)) }) }
                }
            }
            else -> {
                // a playlist matches by name or by any of its songs
                val shown = if (q.isEmpty()) playlists else playlists.filter { pl ->
                    pl.name.contains(q, true) || pl.trackIds.any { viewModel.repository.trackById(it)?.matches(q) == true }
                }
                if (playlists.isEmpty()) full { EmptyHint("No playlists yet. Tap + to create one.") }
                else if (shown.isEmpty()) full { EmptyHint("No playlists match \"$q\"") }
                items(shown, key = { it.name }) { pl ->
                    PlaylistCard(pl, pl.trackIds.firstNotNullOfOrNull { viewModel.repository.trackById(it) }) {
                        onOpenPlaylist(pl.name)
                    }
                }
            }
        }
    }

    if (showCreate) CreatePlaylistDialog(viewModel.repository, onDismiss = { showCreate = false })
    if (showPicker) {
        val streamed = viewModel.repository.favoriteTracks().filter { it.isStreamed() }
        DownloadPicker("Download liked songs", streamed.map { it.pickSong() }, onDismiss = { showPicker = false })
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else TextPrimary,
            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) Accent else SurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PlaylistDetail(
    viewModel: PlayerViewModel,
    playlist: Playlist,
    query: String,
    onQuery: (String) -> Unit,
    onOpenPlaylist: (String?) -> Unit,
) {
    val name = playlist.name
    val plTracks = viewModel.repository.playlistTracks(name)
    val q = query.trim()
    val shown = if (q.isEmpty()) plTracks else plTracks.filter { it.matches(q) }
    val dlStates = rememberDownloadStates()
    val grid = rememberLazyGridState()
    // the dragged row follows the finger: its top when the drag began plus how far it moved
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    fun indexOf(trackId: String) = viewModel.repository.playlists.value.firstOrNull { it.name == name }?.trackIds?.indexOf(trackId) ?: -1
    var showPlMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.repository.setPlaylistCover(name, uri)
    }
    BackHandler { onOpenPlaylist(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        state = grid,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        full {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onOpenPlaylist(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library", tint = TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                if (reordering) TextButton(onClick = { reordering = false }) { Text("Done", color = Accent) }
                Box {
                    IconButton(onClick = { showPlMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "Playlist options", tint = TextSecondary)
                    }
                    DropdownMenu(expanded = showPlMenu, onDismissRequest = { showPlMenu = false }, containerColor = SurfaceElevated) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = TextSecondary) },
                            onClick = { showPlMenu = false; showRename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Change order", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.SwapVert, null, tint = TextSecondary) },
                            onClick = { showPlMenu = false; reordering = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Change cover", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.Image, null, tint = TextSecondary) },
                            onClick = { showPlMenu = false; coverLauncher.launch(arrayOf("image/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showPlMenu = false; showDeleteConfirm = true },
                        )
                    }
                }
            }
        }
        full {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PlaylistArt(playlist, plTracks.firstOrNull(), 180.dp, shape = MaterialTheme.shapes.large)
                Spacer(Modifier.height(14.dp))
                Text(
                    name, color = TextPrimary, style = MaterialTheme.typography.headlineMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                Text("Playlist · ${songCount(plTracks.size)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { viewModel.playQueue(plTracks, 0) }, enabled = plTracks.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                    }
                    FilledTonalButton(
                        onClick = {
                            if (!viewModel.ui.value.shuffle) viewModel.toggleShuffle()
                            viewModel.playQueue(plTracks, plTracks.indices.random())
                        },
                        enabled = plTracks.isNotEmpty(),
                    ) { Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle") }
                }
                PlaylistDownloadBar(playlist, plTracks, dlStates)
                Spacer(Modifier.height(10.dp))
            }
        }
        if (!reordering && plTracks.isNotEmpty()) full {
            SearchField(query, onQuery, "Search this playlist", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (plTracks.isEmpty()) full { EmptyHint("This playlist is empty. Add songs with the menu on any track.") }
        else if (!reordering && shown.isEmpty()) full { EmptyHint("No songs here match \"$q\"") }
        // reordering always shows the whole playlist, so positions are the real ones
        if (reordering) plTracks.forEachIndexed { i, t ->
            val key = "r" + t.id
            item(key = key, span = { GridItemSpan(maxLineSpan) }) {
                val dragging = dragKey == key
                ReorderRow(
                    t, i, plTracks.size,
                    modifier = if (dragging) Modifier.zIndex(1f).graphicsLayer {
                        val now = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset?.y ?: 0
                        translationY = dragStart + dragDelta - now
                    } else Modifier.animateItem(),
                    // shortcut: no auto-scroll at the list edges; drop, scroll, and drag again for long moves
                    handle = Modifier.pointerInput(key) {
                        detectDragGestures(
                            onDragStart = {
                                dragKey = key; dragDelta = 0f
                                dragStart = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset?.y?.toFloat() ?: 0f
                            },
                            onDragEnd = { dragKey = null },
                            onDragCancel = { dragKey = null },
                        ) { change, amount ->
                            change.consume()
                            dragDelta += amount.y
                            val rows = grid.layoutInfo.visibleItemsInfo
                            val me = rows.firstOrNull { it.key == key } ?: return@detectDragGestures
                            val middle = (dragStart + dragDelta + me.size.height / 2).toInt()
                            val over = rows.firstOrNull {
                                it.key != key && (it.key as? String)?.startsWith("r") == true && middle in it.offset.y until it.offset.y + it.size.height
                            } ?: return@detectDragGestures
                            viewModel.repository.moveInPlaylist(name, indexOf(t.id), indexOf((over.key as String).removePrefix("r")))
                        }
                    },
                ) { to -> viewModel.repository.moveInPlaylist(name, indexOf(t.id), to) }
            }
        } else shown.forEach { t ->
            full { SongRow(t, SongContext.Playlist(name), shown, onPlay = { viewModel.playQueue(shown, shown.indexOf(t)) }, status = ytStatus(t, dlStates)) }
        }
    }

    if (showRename) {
        RenamePlaylistDialog(
            currentName = name,
            onRename = { newName -> if (viewModel.repository.renamePlaylist(name, newName)) onOpenPlaylist(newName.trim()) },
            onDismiss = { showRename = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete playlist?", color = TextPrimary) },
            text = {
                val online = plTracks.count { it.isStreamed() }
                Column {
                    Text("\"$name\" will be deleted. The songs stay in your library.", color = TextSecondary)
                    if (online > 0) TextButton(onClick = {
                        showDeleteConfirm = false
                        Snack.show("Deleted \"$name\" and its saved YouTube songs", viewModel.repository.deletePlaylistWithStreams(name))
                        onOpenPlaylist(null)
                    }) { Text("Delete it and its $online saved YouTube songs", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    Snack.show("Deleted \"$name\"", viewModel.repository.deletePlaylist(name))
                    onOpenPlaylist(null)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSecondary) } },
            containerColor = SurfaceElevated,
        )
    }
}

/** One song while a playlist is being reordered: drag the handle, or use the accessibility actions. */
@Composable
private fun ReorderRow(track: Track, index: Int, size: Int, modifier: Modifier, handle: Modifier, onMove: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp).semantics(mergeDescendants = true) {
            customActions = listOfNotNull(
                CustomAccessibilityAction("Move to top") { onMove(0); true }.takeIf { index > 0 },
                CustomAccessibilityAction("Move up") { onMove(index - 1); true }.takeIf { index > 0 },
                CustomAccessibilityAction("Move down") { onMove(index + 1); true }.takeIf { index < size - 1 },
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", color = TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.Filled.DragHandle, "Drag to move ${track.title}", tint = TextSecondary,
            modifier = handle.size(48.dp).padding(12.dp),
        )
    }
}
