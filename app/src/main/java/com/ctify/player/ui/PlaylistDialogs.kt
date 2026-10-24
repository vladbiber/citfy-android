package com.ctify.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceDark
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.youtube.downloadOne
import com.ctify.player.ui.youtube.isStreamed
import kotlinx.coroutines.launch

/** Bottom sheet to add a single track to an existing playlist, or to a brand-new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(viewModel: PlayerViewModel, trackId: String, onDismiss: () -> Unit, onMoveToTop: (() -> Unit)? = null) {
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val track = viewModel.repository.trackById(trackId)

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Text(
            "Add to playlist",
            color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        if (track != null) {
            Text("${track.title} • ${track.artist}", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(8.dp))

        if (track != null && track.isStreamed()) {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            Row(
                Modifier.fillMaxWidth().clickable { scope.launch { downloadOne(ctx, track) }; onDismiss() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Download, null, tint = Accent)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Download", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Plays online now; keep it on the phone", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        if (track != null && track.isStreamed() && viewModel.repository.isSaved(trackId)) {
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.repository.removeStreamed(trackId); onDismiss() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Remove from library", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    Text("Also takes it out of your playlists and favorites", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().clickable { viewModel.addToQueue(trackId); onDismiss() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Queue, null, tint = Accent)
            Spacer(Modifier.width(12.dp))
            Text("Add to queue", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }

        if (onMoveToTop != null) Row(
            Modifier.fillMaxWidth().clickable { onMoveToTop(); onDismiss() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.VerticalAlignTop, null, tint = Accent)
            Spacer(Modifier.width(12.dp))
            Text("Move to top", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }

        Row(
            Modifier.fillMaxWidth().clickable { showCreate = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlaylistAdd, null, tint = Accent)
            Spacer(Modifier.width(12.dp))
            Text("New playlist", color = Accent, fontWeight = FontWeight.SemiBold)
        }

        playlists.forEach { pl ->
            val already = trackId in pl.trackIds
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (already) viewModel.repository.removeFromPlaylist(pl.name, trackId)
                    else viewModel.repository.addToPlaylist(pl.name, trackId)
                    onDismiss()
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.QueueMusic, null, tint = TextSecondary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pl.name, color = TextPrimary)
                    if (already) Text("Tap to remove", color = TextMuted, fontSize = 11.sp)
                }
                if (already) Icon(Icons.Filled.Check, "in playlist", tint = Accent)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showCreate) {
        CreatePlaylistDialog(viewModel, addTrackId = trackId, onDismiss = { showCreate = false; onDismiss() })
    }
}

/** Dialog to create a playlist; if [addTrackId] is set, the track is added on creation. */
@Composable
fun CreatePlaylistDialog(viewModel: PlayerViewModel, addTrackId: String? = null, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val n = name.trim()
                    viewModel.repository.createPlaylist(n)
                    if (addTrackId != null) viewModel.repository.addToPlaylist(n, addTrackId)
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
