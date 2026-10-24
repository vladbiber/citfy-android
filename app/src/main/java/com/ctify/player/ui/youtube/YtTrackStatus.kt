package com.ctify.player.ui.youtube

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ctify.player.data.Playlist
import com.ctify.player.data.Track
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.YtAlbum
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.DownloadState
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.launch

/** The YouTube video behind a streamed (yts_) or downloaded (yt_) track. */
fun Track.ytVideoId(): String? = when {
    id.startsWith(YtSong.STREAM_PREFIX) -> id.removePrefix(YtSong.STREAM_PREFIX)
    id.startsWith("yt_") -> id.removePrefix("yt_")
    else -> null
}

fun Track.isStreamed() = id.startsWith(YtSong.STREAM_PREFIX)

fun Track.toYtSong(trackNumber: Int? = null) = YtSong(
    videoId = ytVideoId() ?: id,
    title = title,
    artist = artist,
    album = album.takeIf { it != "YouTube" },
    durationMs = durationMs,
    thumbnail = coverUri,
    trackNumber = trackNumber,
)

@Composable
fun rememberDownloadStates(): Map<String, DownloadState> {
    val all by Downloads.all().collectAsState(initial = emptyList())
    return remember(all) { all.associate { it.videoId to it.state } }
}

/** "Online", "Downloaded", "Downloading…" for YouTube songs; null for everything else. */
fun ytStatus(track: Track, states: Map<String, DownloadState>): Pair<String, ImageVector>? {
    val v = track.ytVideoId() ?: return null
    if (!track.isStreamed()) return "Downloaded" to Icons.Filled.DownloadDone
    return when (states[v]) {
        DownloadState.RUNNING -> "Downloading" to Icons.Filled.Downloading
        DownloadState.QUEUED -> "Waiting to download" to Icons.Filled.Schedule
        DownloadState.FAILED -> "Download failed" to Icons.Filled.ErrorOutline
        else -> "Online" to Icons.Filled.Cloud
    }
}

/** Queues one streamed song for download and says what happened. */
suspend fun downloadOne(ctx: android.content.Context, track: Track) {
    val n = Downloads.enqueue(listOf(track.toYtSong()))
    Toast.makeText(ctx, if (n == 0) "Already saved or in the queue" else "Added to downloads", Toast.LENGTH_SHORT).show()
}

/** Under a playlist's title: how much of it is on the phone, and a button for the rest. */
@Composable
fun PlaylistDownloadBar(playlist: Playlist, tracks: List<Track>, states: Map<String, DownloadState>) {
    val yt = tracks.filter { it.ytVideoId() != null }
    if (yt.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val done = yt.count { !it.isStreamed() }
    val missing = yt.filter { it.isStreamed() && states[it.ytVideoId()] != DownloadState.RUNNING && states[it.ytVideoId()] != DownloadState.QUEUED }
    val busy = yt.count { it.isStreamed() && (states[it.ytVideoId()] == DownloadState.RUNNING || states[it.ytVideoId()] == DownloadState.QUEUED) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 10.dp)) {
        Text(
            when {
                done == yt.size -> "All ${yt.size} songs downloaded"
                busy > 0 -> "$done of ${yt.size} downloaded · $busy in the queue"
                done == 0 -> "Plays online · not downloaded"
                else -> "$done of ${yt.size} downloaded · the rest plays online"
            },
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        if (missing.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            FilledTonalButton(onClick = {
                val artist = yt.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "Unknown artist"
                val songs = yt.mapIndexed { i, t -> t.toYtSong(i + 1) }
                val album = YtAlbum("pl:${playlist.name}", playlist.name, artist, playlist.coverUri ?: yt.firstNotNullOfOrNull { it.coverUri }, songs)
                scope.launch {
                    val n = Downloads.enqueue(songs.filter { s -> missing.any { it.ytVideoId() == s.videoId } }, album)
                    Toast.makeText(ctx, if (n == 0) "Already in the queue" else "$n songs added to downloads", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (done == 0 && busy == 0) "Download album" else "Download the rest (${missing.size})")
            }
        }
    }
}
