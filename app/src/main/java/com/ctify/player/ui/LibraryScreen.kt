package com.ctify.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.Track
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.theme.SpotifyGreen
import com.ctify.player.ui.theme.SurfaceDark
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    onMore: (String) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    openedPlaylist: String?,
    onOpenPlaylist: (String?) -> Unit,
) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(0) } // 0 Songs, 1 Favorites, 2 Playlists
    var showCreate by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    val opened = openedPlaylist?.takeIf { name -> playlists.any { it.name == name } }
    if (opened != null) {
        val playlist = playlists.first { it.name == opened }
        val plTracks = viewModel.repository.playlistTracks(opened)
        var showPlMenu by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        val coverLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) viewModel.repository.setPlaylistCover(opened, uri) }

        LazyColumn(contentPadding = contentPadding()) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onOpenPlaylist(null) }) {
                        Icon(Icons.Filled.ArrowBack, "back", tint = TextPrimary)
                    }
                    PlaylistArt(playlist, plTracks.firstOrNull(), 44.dp, corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(opened, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showPlMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "playlist options", tint = TextSecondary)
                        }
                        DropdownMenu(
                            expanded = showPlMenu,
                            onDismissRequest = { showPlMenu = false },
                            containerColor = SurfaceElevated,
                        ) {
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
                                text = { Text("Delete playlist", color = Color(0xFFE57373)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFE57373)) },
                                onClick = { showPlMenu = false; showDeleteConfirm = true },
                            )
                        }
                    }
                }
            }
            if (plTracks.isEmpty()) {
                item { EmptyHint("This playlist is empty. Add songs with the menu on any track.") }
            }
            items(plTracks, key = { it.id }) { t ->
                TrackRow(
                    track = t,
                    isCurrent = t.id == ui.currentTrackId,
                    isPlaying = ui.isPlaying && t.id == ui.currentTrackId,
                    isFavorite = t.id in favorites,
                    onClick = { viewModel.playQueue(plTracks, plTracks.indexOf(t)) },
                    onToggleFavorite = { viewModel.toggleFavorite(t.id) },
                    onRemove = { viewModel.repository.removeFromPlaylist(opened, t.id) },
                    onMore = { onMore(t.id) },
                )
            }
        }

        if (showRename) {
            RenamePlaylistDialog(
                currentName = opened,
                onRename = { newName ->
                    if (viewModel.repository.renamePlaylist(opened, newName))
                        onOpenPlaylist(newName.trim())
                },
                onDismiss = { showRename = false },
            )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete playlist?", color = TextPrimary) },
                text = {
                    Text(
                        "\"$opened\" will be deleted. The songs stay in your library.",
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.repository.deletePlaylist(opened)
                        onOpenPlaylist(null)
                    }) { Text("Delete", color = Color(0xFFE57373)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSecondary) }
                },
                containerColor = SurfaceElevated,
            )
        }
        return
    }

    LazyColumn(contentPadding = contentPadding()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your Library", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Filled.Add, "add", tint = TextPrimary)
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        containerColor = SurfaceElevated,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import a folder", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null, tint = SpotifyGreen) },
                            onClick = { showAddMenu = false; onImportFolder() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add a song", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.LibraryAdd, null, tint = TextSecondary) },
                            onClick = { showAddMenu = false; onImport() },
                        )
                        DropdownMenuItem(
                            text = { Text("New playlist", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null, tint = TextSecondary) },
                            onClick = { showAddMenu = false; showCreate = true },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("Songs", section == 0) { section = 0 }
                Chip("Favorites", section == 1) { section = 1 }
                Chip("Playlists", section == 2) { section = 2 }
            }
        }

        when (section) {
            0 -> items(tracks, key = { it.id }) { t ->
                LibTrackRow(viewModel, t, tracks, ui.currentTrackId, ui.isPlaying, favorites) { onMore(t.id) }
            }
            1 -> {
                val favs = viewModel.repository.favoriteTracks()
                if (favs.isEmpty()) item { EmptyHint("Tap the heart on a song to save it here.") }
                items(favs, key = { it.id }) { t ->
                    LibTrackRow(viewModel, t, favs, ui.currentTrackId, ui.isPlaying, favorites) { onMore(t.id) }
                }
            }
            else -> {
                if (playlists.isEmpty()) item { EmptyHint("No playlists yet. Tap + to create one.") }
                items(playlists, key = { it.name }) { pl ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenPlaylist(pl.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val first = pl.trackIds.firstNotNullOfOrNull { viewModel.repository.trackById(it) }
                        if (pl.coverUri != null || first != null) {
                            PlaylistArt(pl, first, 52.dp, corner = 6.dp)
                        } else {
                            Box(Modifier.size(52.dp).background(SurfaceElevated, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.QueueMusic, null, tint = SpotifyGreen)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(pl.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${pl.trackIds.size} songs", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(viewModel, onDismiss = { showCreate = false })
    }
}

@Composable
private fun LibTrackRow(
    viewModel: PlayerViewModel,
    track: Track,
    queue: List<Track>,
    currentId: String?,
    isPlaying: Boolean,
    favorites: Set<String>,
    onMore: () -> Unit,
) {
    TrackRow(
        track = track,
        isCurrent = track.id == currentId,
        isPlaying = isPlaying && track.id == currentId,
        isFavorite = track.id in favorites,
        onClick = { viewModel.playQueue(queue, queue.indexOf(track)) },
        onToggleFavorite = { viewModel.toggleFavorite(track.id) },
        onMore = onMore,
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) SpotifyGreen else SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Color.Black else TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary)
    }
}
