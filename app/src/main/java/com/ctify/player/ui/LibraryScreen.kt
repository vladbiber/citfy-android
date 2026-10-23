package com.ctify.player.ui

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.Playlist
import com.ctify.player.data.Track
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

internal fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
internal fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 6.dp).semantics { heading() },
    )
}

@Composable
internal fun GridHeader(text: String) {
    Text(
        text, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp).semantics { heading() },
    )
}

/** A playlist as a cover card in a grid. */
@Composable
internal fun PlaylistCard(playlist: Playlist, firstTrack: Track?, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "open", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Playlist ${playlist.name}, ${playlist.trackIds.size} songs"
            }
            .padding(4.dp),
    ) {
        if (playlist.coverUri != null || firstTrack != null) {
            PlaylistArt(playlist, firstTrack, 0.dp, corner = 14.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Accent, modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackIds.size} songs", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    onMore: (String) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    openedPlaylist: String?,
    onOpenPlaylist: (String?) -> Unit,
    section: Int = 0,
    onSection: (Int) -> Unit = {},
    onYouTube: () -> Unit = {},
    onDownloads: () -> Unit = {},
) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    val opened = openedPlaylist?.takeIf { name -> playlists.any { it.name == name } }
    if (opened != null) {
        PlaylistDetail(viewModel, playlists.first { it.name == opened }, ui.currentTrackId, ui.isPlaying, favorites, onMore, onOpenPlaylist)
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
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Filled.Add, "Add music or a playlist", tint = TextPrimary)
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }, containerColor = SurfaceElevated) {
                        DropdownMenuItem(
                            text = { Text("Import a folder", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null, tint = Accent) },
                            onClick = { showAddMenu = false; onImportFolder() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add a song", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.LibraryAdd, null, tint = TextSecondary) },
                            onClick = { showAddMenu = false; onImport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add from YouTube", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                            onClick = { showAddMenu = false; onYouTube() },
                        )
                        DropdownMenuItem(
                            text = { Text("Downloads", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.Download, null, tint = TextSecondary) },
                            onClick = { showAddMenu = false; onDownloads() },
                        )
                        DropdownMenuItem(
                            text = { Text("New playlist", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = TextSecondary) },
                            onClick = { showAddMenu = false; showCreate = true },
                        )
                    }
                }
            }
        }
        full {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Songs", "Favorites", "Playlists").forEachIndexed { i, label ->
                    TabChip(label, section == i) { onSection(i) }
                }
            }
        }

        when (section) {
            0 -> {
                if (tracks.isEmpty()) full { EmptyHint("No music yet. Tap + to import a folder or a song.") }
                tracks.forEach { t ->
                    full {
                        TrackRow(
                            track = t, isCurrent = t.id == ui.currentTrackId,
                            isPlaying = ui.isPlaying && t.id == ui.currentTrackId, isFavorite = t.id in favorites,
                            onClick = { viewModel.playQueue(tracks, tracks.indexOf(t)) },
                            onToggleFavorite = { viewModel.toggleFavorite(t.id) }, onMore = { onMore(t.id) },
                        )
                    }
                }
            }
            1 -> {
                val favs = viewModel.repository.favoriteTracks()
                if (favs.isEmpty()) full { EmptyHint("Tap the heart on a song to save it here.") }
                favs.forEach { t ->
                    full {
                        TrackRow(
                            track = t, isCurrent = t.id == ui.currentTrackId,
                            isPlaying = ui.isPlaying && t.id == ui.currentTrackId, isFavorite = true,
                            onClick = { viewModel.playQueue(favs, favs.indexOf(t)) },
                            onToggleFavorite = { viewModel.toggleFavorite(t.id) }, onMore = { onMore(t.id) },
                        )
                    }
                }
            }
            else -> {
                if (playlists.isEmpty()) full { EmptyHint("No playlists yet. Tap + to create one.") }
                items(playlists, key = { it.name }) { pl ->
                    PlaylistCard(pl, pl.trackIds.firstNotNullOfOrNull { viewModel.repository.trackById(it) }) {
                        onOpenPlaylist(pl.name)
                    }
                }
            }
        }
    }

    if (showCreate) CreatePlaylistDialog(viewModel, onDismiss = { showCreate = false })
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
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
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
    currentId: String?,
    isPlaying: Boolean,
    favorites: Set<String>,
    onMore: (String) -> Unit,
    onOpenPlaylist: (String?) -> Unit,
) {
    val name = playlist.name
    val plTracks = viewModel.repository.playlistTracks(name)
    var showPlMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.repository.setPlaylistCover(name, uri)
    }
    BackHandler { onOpenPlaylist(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        full {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onOpenPlaylist(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library", tint = TextPrimary)
                }
                Spacer(Modifier.weight(1f))
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
                PlaylistArt(playlist, plTracks.firstOrNull(), 180.dp, corner = 18.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    name, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                Text("Playlist · ${plTracks.size} songs", color = TextSecondary, fontSize = 13.sp)
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
                Spacer(Modifier.height(10.dp))
            }
        }
        if (plTracks.isEmpty()) full { EmptyHint("This playlist is empty. Add songs with the menu on any track.") }
        plTracks.forEach { t ->
            full {
                TrackRow(
                    track = t, isCurrent = t.id == currentId, isPlaying = isPlaying && t.id == currentId,
                    isFavorite = t.id in favorites,
                    onClick = { viewModel.playQueue(plTracks, plTracks.indexOf(t)) },
                    onToggleFavorite = { viewModel.toggleFavorite(t.id) },
                    onRemove = { viewModel.repository.removeFromPlaylist(name, t.id) },
                    onMore = { onMore(t.id) },
                )
            }
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
            text = { Text("\"$name\" will be deleted. The songs stay in your library.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.repository.deletePlaylist(name)
                    onOpenPlaylist(null)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSecondary) } },
            containerColor = SurfaceElevated,
        )
    }
}
