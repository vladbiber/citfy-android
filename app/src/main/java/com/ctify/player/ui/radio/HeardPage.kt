package com.ctify.player.ui.radio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.radio.HeardSong
import com.ctify.player.radio.SongLinks
import com.ctify.player.ui.EmptyHint
import com.ctify.player.ui.GridHeader
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.youtube.HeardAction
import com.ctify.player.ui.youtube.HeardYouTube
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Songs heard on the radio, newest first, grouped by day. Text only. */
@Composable
internal fun HeardPage() {
    val radio = radioRepo()
    val songs by radio.heard.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    val byDay = remember(songs) { songs.groupBy { dayLabel(it.startedAt) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { GridHeader("Heard on the radio") }
                if (songs.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
            }
        }
        if (songs.isEmpty()) item { EmptyHint("Songs you hear on the radio show up here, so you can find them later.") }
        byDay.forEach { (day, list) ->
            item(key = "h_$day") { GridHeader(day) }
            items(list, key = { it.id }) { song -> HeardRow(song) }
        }
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
    val radio = radioRepo()
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var ytAction by remember { mutableStateOf<HeardAction?>(null) }
    val open: (String) -> Unit = { url ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(song.artist, song.stationName, timeLabel(song.startedAt)).joinToString(" · "),
                color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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
    }
    ytAction?.let { HeardYouTube(song, it) { ytAction = null } }
}

private fun dayLabel(at: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    if (same(day, today.apply { add(Calendar.DAY_OF_YEAR, -1) })) return "Yesterday"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
}

private fun timeLabel(at: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
