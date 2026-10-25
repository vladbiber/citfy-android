package com.ctify.player.ui.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import com.ctify.player.ui.PillTabs
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ctify.player.data.Track
import com.ctify.player.ui.AlbumArt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.CtifyApp
import com.ctify.player.radio.HeardSong
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongSheets
import com.ctify.player.ui.songs.SongTarget
import com.ctify.player.ui.EmptyHint
import com.ctify.player.ui.SectionHeader
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.youtube.HeardAction
import com.ctify.player.ui.youtube.DownloadPicker
import com.ctify.player.ui.youtube.HeardYouTube
import com.ctify.player.ui.youtube.PickSong
import com.ctify.player.youtube.SongMatcher
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.Downloads

/**
 * Home's "Heard on the radio": Liked / Listened tabs over a row of cover cards, with the download
 * picker and Clear history. Hidden until something was heard.
 */
@Composable
internal fun HeardSection() {
    val radio = radioRepo()
    val songs by radio.heard.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var likedTab by rememberSaveable { mutableStateOf<Boolean?>(null) }
    if (songs.isEmpty()) return
    // opens on Liked when there is something liked
    val liked = likedTab ?: songs.any { it.liked }
    val shown = remember(songs, liked) { if (liked) songs.filter { it.liked } else songs }

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeader("Heard on the radio", Modifier.padding(top = 10.dp)) }
            IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Download, contentDescription = "Download heard songs", tint = TextSecondary) }
            TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
        }
        PillTabs(listOf("Liked", "Listened"), if (liked) 0 else 1, onSelect = { likedTab = it == 0 })
        Spacer(Modifier.height(4.dp))
        if (shown.isEmpty()) EmptyHint("Open ⋮ on a heard song and add it to Liked songs to keep it here.")
        LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { HeardCard(it) }
        }
    }

    if (picking) {
        val app = LocalContext.current.applicationContext as CtifyApp
        val pick = remember(songs) {
            songs.filter { it.downloadedTrackId == null }.distinctBy { it.query.lowercase() }.map { h ->
                val artist = h.artist ?: "Unknown artist"
                PickSong(
                    "h${h.id}", h.title, artist, h.youtubeVideoId,
                    // unsure matches count as not found here; the song's own Download menu still offers the top 3
                    find = {
                        h.youtubeVideoId?.let { YtSong(it, h.title, artist) }
                            ?: SongMatcher.candidates(h.artist, h.title).firstOrNull()?.takeIf { it.score >= SongMatcher.ACCEPT }?.song
                                ?.also { app.database.heardSongs().setVideoId(h.id, it.videoId) }
                    },
                    enqueue = { Downloads.enqueue(listOf(it), genre = h.genre, heardSongId = h.id) },
                )
            }
        }
        DownloadPicker("Download heard songs", pick, onDismiss = { picking = false })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes every song from the list. Liked ones too.") },
            confirmButton = { TextButton(onClick = { radio.clearHeard(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun HeardRow(song: HeardSong) {
    var play by remember { mutableStateOf(false) }
    if (play) HeardYouTube(song, HeardAction.PLAY) { play = false }
    // same shape as a song row: cover, title, artist, ⋮
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClickLabel = "play from YouTube", onLongClickLabel = "more options", onLongClick = { showSheet(song) }) { play = true }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(song.asTrack(), 50.dp, shape = MaterialTheme.shapes.small)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    song.artist ?: song.stationName,
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MoreButton(song)
    }
}

private fun HeardSong.asTrack(): Track {
    val cover = youtubeVideoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    return Track("h$id", title, artist.orEmpty(), title, "", cover, 0L, isDemo = false)
}

/** A heard song as a cover card; a tap plays it from YouTube. */
@Composable
private fun HeardCard(song: HeardSong) {
    var play by remember { mutableStateOf(false) }
    Column(Modifier.width(140.dp)) {
        Box(
            Modifier.clip(MaterialTheme.shapes.small)
                .combinedClickable(onClickLabel = "play from YouTube", onLongClickLabel = "more options", onLongClick = { showSheet(song) }) { play = true }
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(song.title, song.artist).joinToString(", by ") },
        ) {
            AlbumArt(song.asTrack(), 140.dp, shape = MaterialTheme.shapes.small)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: song.stationName, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MoreButton(song)
        }
    }
    if (play) HeardYouTube(song, HeardAction.PLAY) { play = false }
}

/** The ⋮ that opens the song sheet, where Like lives too. */
@Composable
private fun MoreButton(song: HeardSong) {
    IconButton(onClick = { showSheet(song) }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${song.title}", tint = TextSecondary) }
}

private fun showSheet(song: HeardSong) = SongSheets.show(SongTarget.of(song), SongContext.Heard)

