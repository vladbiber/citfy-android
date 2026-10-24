package com.ctify.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.CtifyApp
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.Track
import com.ctify.player.ui.songs.SongActions
import com.ctify.player.ui.songs.Snack
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceDark
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

/** Bottom sheet that adds [tracks] to playlists, e.g. from the selection bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(tracks: List<Track>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Text(
            if (tracks.size == 1) "Add to playlist" else "Add ${tracks.size} songs to a playlist",
            color = TextPrimary, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() },
        )
        PlaylistRows(tracks, onDone = onDismiss)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * "New playlist" and every playlist, checked when it already holds all of [tracks]; a tap adds them,
 * or takes them out when checked. Used inside the song sheet and [AddToPlaylistSheet].
 */
@Composable
fun ColumnScope.PlaylistRows(tracks: List<Track>, onDone: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as CtifyApp).repository
    val playlists by repo.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { showCreate = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlaylistAdd, null, tint = Accent)
        Spacer(Modifier.width(12.dp))
        Text("New playlist", color = Accent, fontWeight = FontWeight.SemiBold)
    }
    playlists.forEach { pl ->
        val already = tracks.all { it.id in pl.trackIds }
        Row(
            Modifier.fillMaxWidth().clickable { SongActions.togglePlaylist(repo, pl.name, tracks); onDone() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.QueueMusic, null, tint = TextSecondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pl.name, color = TextPrimary)
                if (already) Text("Tap to remove", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (already) Icon(Icons.Filled.Check, "in playlist", tint = Accent)
        }
    }
    if (showCreate) CreatePlaylistDialog(repo, addTracks = tracks, onDismiss = { showCreate = false; onDone() })
}

/** Dialog to create a playlist; [addTracks] go into it on creation. */
@Composable
fun CreatePlaylistDialog(repo: MusicRepository, addTracks: List<Track> = emptyList(), onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val n = name.trim()
                    repo.remember(addTracks)
                    repo.createPlaylistWithTracks(n, addTracks.map { it.id })
                    Snack.show(if (addTracks.isEmpty()) "Created $n" else "Added to $n")
                    onDismiss()
                },
            ) { Text("Create", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        title = { Text("New playlist", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text("Playlist name") },
            )
        },
        containerColor = SurfaceElevated,
    )
}

/** Dialog to rename an existing playlist. */
@Composable
fun RenamePlaylistDialog(currentName: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onRename(name); onDismiss() },
            ) { Text("Rename", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        title = { Text("Rename playlist", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text("Playlist name") },
            )
        },
        containerColor = SurfaceElevated,
    )
}
