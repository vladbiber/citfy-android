package com.ctify.player.ui.youtube

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctify.player.data.Track
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.DownloadEntity
import com.ctify.player.youtube.download.DownloadState
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A song the picker can save. [find] looks it up on YouTube when [videoId] is unknown; null means no match. */
class PickSong(
    val key: String,
    val title: String,
    val artist: String,
    val videoId: String?,
    val find: suspend () -> YtSong?,
    val enqueue: suspend (YtSong) -> Unit,
)

fun Track.pickSong() = PickSong(id, title, artist, ytVideoId(), find = { toYtSong() }, enqueue = { Downloads.enqueue(listOf(it)) })

/**
 * One list of songs not on the phone yet. A tapped song slides right into the "Downloading" row;
 * tapping a pending one there cancels it and puts it back. Finished ones keep their check mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPicker(title: String, songs: List<PickSong>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    // closing the picker drops lookups still running; queued downloads carry on
    val scope = rememberCoroutineScope()
    val all by Downloads.all().collectAsState(initial = emptyList())
    val dl = remember(all) { all.associateBy { it.videoId } }
    val picked = remember { mutableStateListOf<PickSong>() }
    val found = remember { mutableStateMapOf<String, String>() }
    val looking = remember { mutableStateMapOf<String, Job>() }
    // shortcut: "not found" lasts while the picker is open; persisting it needs a heard_songs column
    val notFound = remember { mutableStateListOf<String>() }
    // isMetered() can't be observed, so look again every few seconds while the picker is open
    val blocked by produceState<String?>(null) {
        while (true) {
            value = NetworkGate.blockReason(Feature.YOUTUBE, Traffic.DOWNLOAD)
            delay(2_000)
        }
    }
    // lookups still allowed means only "Wi-Fi only" stops the download
    val waitLabel = when {
        blocked == null -> "Waiting"
        NetworkGate.blockReason(Feature.YOUTUBE) == null -> "Waiting for Wi-Fi"
        else -> "Paused"
    }

    fun vid(s: PickSong) = s.videoId ?: found[s.key]
    fun entity(s: PickSong) = vid(s)?.let { dl[it] }
    val (row, listed) = splitPicked(picked, songs) { entity(it)?.state }

    fun pick(s: PickSong) {
        picked.removeAll { it.key == s.key }
        picked += s
        // started after it is in the map, so the finally below always finds it
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val song = s.find()
                if (song == null) {
                    notFound += s.key
                    picked.removeAll { it.key == s.key }
                } else {
                    found[s.key] = song.videoId
                    s.enqueue(song)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                picked.removeAll { it.key == s.key }
                Toast.makeText(ctx, e.message ?: "YouTube search failed", Toast.LENGTH_SHORT).show()
            } finally {
                looking.remove(s.key)
            }
        }
        looking[s.key] = job
        job.start()
    }

    fun cancel(s: PickSong) {
        looking.remove(s.key)?.cancel()
        vid(s)?.let { Downloads.cancel(it) }
        picked.removeAll { it.key == s.key }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Text(
            title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        blocked?.let { Text(it, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        if (row.isNotEmpty()) {
            Text(
                "Downloading", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp).semantics { heading() },
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(row, key = { it.key }) { s ->
                    val d = entity(s)
                    // gone from the source list with no queue entry left means it was saved
                    val done = d?.state == DownloadState.DONE || d?.state == DownloadState.SKIPPED ||
                        (d == null && s.key !in looking && songs.none { it.key == s.key })
                    PendingCard(s, d, looking = s.key in looking, done = done, waitLabel = waitLabel, modifier = Modifier.animateItem()) { cancel(s) }
                }
            }
        }
        if (listed.isEmpty() && row.isEmpty()) {
            Text("Everything here is already on the phone.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            items(listed, key = { it.key }) { s ->
                val missing = s.key in notFound
                val note = when {
                    missing -> "not found"
                    entity(s)?.state == DownloadState.FAILED -> "Download failed, tap to retry"
                    else -> null
                }
                PickRow(s, note, enabled = !missing, modifier = Modifier.animateItem()) { pick(s) }
            }
        }
    }
}

/**
 * The "Downloading" row and the list below it. The row holds what was tapped plus anything already
 * queued elsewhere; failed ones go back to the list so a tap retries them.
 */
internal fun splitPicked(
    picked: List<PickSong>,
    songs: List<PickSong>,
    state: (PickSong) -> DownloadState?,
): Pair<List<PickSong>, List<PickSong>> {
    val row = picked.filter { state(it) != DownloadState.FAILED } +
        songs.filter { s -> picked.none { it.key == s.key } && state(s).let { it == DownloadState.QUEUED || it == DownloadState.RUNNING } }
    return row to songs.filter { s -> row.none { it.key == s.key } }
}

@Composable
private fun PickRow(s: PickSong, note: String?, enabled: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(0f) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer { translationX = slide.value * size.width; alpha = 1f - slide.value }
            .clickable(enabled = enabled && !slide.isRunning, role = Role.Button, onClickLabel = "download") {
                scope.launch { slide.animateTo(1f, tween(220)); onPick() }
            }
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(note, s.artist).joinToString(" · "),
                color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(if (enabled) Icons.Filled.Download else Icons.Filled.SearchOff, null, tint = TextSecondary)
    }
}

@Composable
private fun PendingCard(
    s: PickSong,
    d: DownloadEntity?,
    looking: Boolean,
    done: Boolean,
    waitLabel: String,
    modifier: Modifier,
    onCancel: () -> Unit,
) {
    val running = !done && d?.state == DownloadState.RUNNING
    val label = when {
        done -> "Saved"
        looking -> "Looking on YouTube"
        running && d.bytesTotal > 0 -> "${d.bytesDone * 100 / d.bytesTotal}%"
        running -> "Starting"
        else -> waitLabel
    }
    Column(
        modifier
            .width(128.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = 0.10f))
            .clickable(enabled = !done, role = Role.Button, onClickLabel = "cancel download", onClick = onCancel)
            .padding(10.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Filled.Check, null, tint = Accent)
                running && d.bytesTotal > 0 -> CircularProgressIndicator(
                    progress = { d.bytesDone.toFloat() / d.bytesTotal }, modifier = Modifier.size(24.dp), color = Accent, strokeWidth = 3.dp,
                )
                looking || running -> CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 3.dp)
                else -> Icon(Icons.Filled.Schedule, null, tint = TextMuted)
            }
        }
        Text(s.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
