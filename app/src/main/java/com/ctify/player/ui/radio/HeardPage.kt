package com.ctify.player.ui.radio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.ctify.player.radio.SongLinks
import com.ctify.player.ui.EmptyHint
import com.ctify.player.ui.SectionHeader
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.youtube.HeardAction
import com.ctify.player.ui.youtube.DownloadPicker
import com.ctify.player.ui.youtube.HeardYouTube
import com.ctify.player.ui.youtube.PickSong
import com.ctify.player.youtube.SongMatcher
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.Downloads
import java.text.DateFormat
import java.util.Date

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
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = liked, onClick = { likedTab = true }, label = { Text("Liked") })
            FilterChip(selected = !liked, onClick = { likedTab = false }, label = { Text("Listened") })
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) EmptyHint("Tap the heart on a heard song to keep it here.")
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.title, color = TextPrimary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(song.artist, song.stationName, timeLabel(song.startedAt)).joinToString(" · "),
                color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        HeardActions(song)
    }
}

/** A heard song as a cover card; a tap plays it from YouTube. */
@Composable
private fun HeardCard(song: HeardSong) {
    var play by remember { mutableStateOf(false) }
    val cover = song.youtubeVideoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    Column(Modifier.width(140.dp)) {
        Box(
            Modifier.clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "play from YouTube") { play = true }
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(song.title, song.artist).joinToString(", by ") },
        ) {
            AlbumArt(Track("h${song.id}", song.title, song.artist.orEmpty(), song.title, "", cover, 0L, isDemo = false), 140.dp, shape = MaterialTheme.shapes.small)
        }
        Spacer(Modifier.height(6.dp))
        Text(song.title, color = TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist ?: song.stationName, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.align(Alignment.End)) { HeardActions(song) }
    }
    if (play) HeardYouTube(song, HeardAction.PLAY) { play = false }
}

/** The heart and the "more" menu of a heard song. */
@Composable
private fun HeardActions(song: HeardSong) {
    val radio = radioRepo()
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var ytAction by remember { mutableStateOf<HeardAction?>(null) }
    val open: (String) -> Unit = { url ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    IconButton(onClick = { radio.setHeardLiked(song, !song.liked) }) {
        Icon(
            if (song.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (song.liked) "Unlike" else "Like",
            tint = if (song.liked) Accent else TextSecondary,
        )
    }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${song.title}", tint = TextSecondary) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Play from YouTube") }, onClick = { menu = false; ytAction = HeardAction.PLAY })
            DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; ytAction = HeardAction.DOWNLOAD })
            DropdownMenuItem(text = { Text("Find on YouTube") }, onClick = { menu = false; open(SongLinks.youtube(song)) })
            DropdownMenuItem(text = { Text("Find on YouTube Music") }, onClick = { menu = false; open(SongLinks.youtubeMusic(song)) })
            DropdownMenuItem(text = { Text("Find on Spotify") }, onClick = { menu = false; open(SongLinks.spotify(song)) })
            DropdownMenuItem(text = { Text("Find on Apple Music") }, onClick = { menu = false; open(SongLinks.appleMusic(song)) })
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; radio.deleteHeard(song) },
            )
        }
    }
    ytAction?.let { HeardYouTube(song, it) { ytAction = null } }
}

private fun timeLabel(at: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
