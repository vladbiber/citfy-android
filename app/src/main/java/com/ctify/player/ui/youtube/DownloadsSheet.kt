package com.ctify.player.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.Handoff
import com.ctify.player.youtube.download.DownloadEntity
import com.ctify.player.youtube.download.DownloadState
import com.ctify.player.youtube.download.Downloads
import java.text.DateFormat
import java.util.Date

/** The download queue: albums as "7/12", songs with their state, and the hand-off when ours can't work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val items by Downloads.all().collectAsState(initial = emptyList())
    val pausedUntil by Downloads.pausedUntil.collectAsState()
    val pauseReason by Downloads.pauseReason.collectAsState()
    val handoffApp = remember { Handoff.installedApp(ctx) }
    val albums = items.filter { it.groupId != null }.groupBy { it.groupId!! }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Downloads", color = TextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                if (items.any { it.state == DownloadState.FAILED }) TextButton(onClick = { Downloads.retryFailed() }) { Text("Retry failed") }
                if (items.any { it.state == DownloadState.DONE || it.state == DownloadState.SKIPPED }) {
                    TextButton(onClick = { Downloads.clearFinished() }) { Text("Clear done") }
                }
            }
            if (pausedUntil > System.currentTimeMillis()) {
                val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pausedUntil))
                Text("Paused until $at. ${pauseReason ?: ""}", color = Accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
            albums.forEach { (_, songs) ->
                val done = songs.count { it.state == DownloadState.DONE || it.state == DownloadState.SKIPPED }
                val total = songs.first().trackTotal ?: songs.size
                Text(
                    "${songs.first().groupTitle ?: "Album"}: $done/$total",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                )
                LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Accent)
            }
        }
        if (items.isEmpty()) Hint("Nothing here yet. Find a song or an album in YouTube and tap Download.")
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items, key = { it.videoId }) { d ->
                DownloadRow(
                    d,
                    handoff = handoffApp?.second?.takeIf { d.state == DownloadState.FAILED && Handoff.offered(d.errorClass) },
                    onHandoff = { Handoff.send(ctx, d.videoId) },
                    onRemove = { Downloads.remove(d.videoId) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadEntity, handoff: String?, onHandoff: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status(d), color = if (d.state == DownloadState.FAILED) Accent else TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d.state == DownloadState.RUNNING && d.bytesTotal > 0) {
                LinearProgressIndicator(
                    progress = { d.bytesDone.toFloat() / d.bytesTotal },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Accent,
                )
            }
            if (handoff != null) {
                Row(horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = onHandoff, contentPadding = PaddingValues(0.dp)) { Text("Download with $handoff instead") }
                }
            }
        }
        if (d.state != DownloadState.RUNNING) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove ${d.title} from the list", tint = TextSecondary) }
        }
    }
}

private fun status(d: DownloadEntity): String = when (d.state) {
    DownloadState.QUEUED -> "${d.artist} · waiting"
    DownloadState.RUNNING -> if (d.bytesTotal > 0) "${d.artist} · ${d.bytesDone * 100 / d.bytesTotal}%" else "${d.artist} · starting"
    DownloadState.DONE -> "${d.artist} · saved"
    DownloadState.SKIPPED -> "${d.artist} · already in your library"
    DownloadState.FAILED -> "Failed: ${d.error ?: "unknown error"}"
}
