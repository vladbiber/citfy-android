package com.ctify.player.ui.youtube

import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctify.player.data.MusicRepository
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.SpotifyImport
import com.ctify.player.youtube.YtSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Paste a Spotify playlist or album link; each song is found on YouTube and saved as a Ctify playlist that streams. */
@Composable
fun SpotifyImportDialog(repository: MusicRepository, initialLink: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(initialLink.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var done by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<YtSong>>(emptyList()) }
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val running = job?.isActive == true
    val finished = !running && total > 0 && done == total

    fun start() {
        NetworkGate.blockReason(Feature.SPOTIFY_IMPORT)?.let { error = it; return }
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { error = it; return }
        error = null; found = emptyList(); missing = emptyList(); done = 0; total = 0
        job = scope.launch {
            try {
                current = "Reading the Spotify page"
                val list = SpotifyImport.read(link)
                name = list.name; cover = list.cover; total = list.tracks.size
                if (total == 0) { error = "This playlist looks empty or private"; return@launch }
                for (t in list.tracks) {
                    current = "${t.artist} · ${t.title}"
                    val song = SpotifyImport.match(t)
                    if (song != null) found = found + song else missing = missing + "${t.artist} · ${t.title}"
                    done++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Import failed"
            }
        }
    }

    LaunchedEffect(Unit) { if (SpotifyImport.isLink(link)) start() }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Import from Spotify") },
        text = {
            Column {
                if (total == 0 && !running) {
                    Text("Paste a link to a public Spotify playlist or album. Ctify finds every song on YouTube and saves them as a playlist that plays online.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("open.spotify.com/playlist/…") },
                        trailingIcon = {
                            IconButton(onClick = {
                                ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString()?.let { link = it.trim() }
                            }) { Icon(Icons.Filled.ContentPaste, "Paste link") }
                        },
                    )
                }
                if (running || total > 0) {
                    if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.bodyLarge)
                    if (total > 0) {
                        LinearProgressIndicator(progress = { done / total.toFloat() }, color = Accent, modifier = Modifier.fillMaxWidth())
                        Text("$done of $total checked · ${found.size} found", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    } else LinearProgressIndicator(color = Accent, modifier = Modifier.fillMaxWidth())
                    if (running) Text(current, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (finished && missing.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Not found on YouTube (${missing.size}):", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.heightIn(max = 160.dp)) {
                        items(missing) { Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (finished) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            when {
                finished && found.isNotEmpty() -> TextButton(onClick = {
                    repository.saveAsPlaylist(name.ifBlank { "Spotify import" }, found.map { it.toTrack() }, cover)
                    Toast.makeText(ctx, "Saved ${found.size} songs as \"${name.trim()}\"", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text("Save playlist") }
                !running && total == 0 -> TextButton(onClick = { start() }, enabled = SpotifyImport.isLink(link)) { Text("Import") }
                else -> {}
            }
        },
        dismissButton = {
            // Stop keeps what was found so far
            TextButton(onClick = { if (running) { job?.cancel(); total = done } else onDismiss() }) { Text(if (running) "Stop" else "Close") }
        },
    )
}
