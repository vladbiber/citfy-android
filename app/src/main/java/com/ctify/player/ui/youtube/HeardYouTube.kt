package com.ctify.player.ui.youtube

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ctify.player.CtifyApp
import com.ctify.player.player.PlayerHub
import com.ctify.player.radio.HeardSong
import com.ctify.player.ui.theme.Accent
import com.ctify.player.youtube.SongMatcher
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.launch

enum class HeardAction { PLAY, DOWNLOAD }

/** Finds a heard radio song on YouTube, asks when unsure, then plays or queues the download. */
@Composable
fun HeardYouTube(heard: HeardSong, action: HeardAction, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CtifyApp
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<SongMatcher.Match>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun use(song: YtSong) = scope.launch {
        if (heard.youtubeVideoId != song.videoId) app.database.heardSongs().setVideoId(heard.id, song.videoId)
        when (action) {
            HeardAction.PLAY -> {
                val t = song.toTrack()
                app.repository.remember(listOf(t))
                PlayerHub.playQueue(listOf(t.id), 0)
            }
            HeardAction.DOWNLOAD -> {
                val n = Downloads.enqueue(listOf(song), genre = heard.genre, heardSongId = heard.id)
                Toast.makeText(ctx, if (n > 0) "Added to downloads" else "Already saved or in the queue", Toast.LENGTH_SHORT).show()
            }
        }
        onDone()
    }

    LaunchedEffect(heard.id, action) {
        // a remembered videoId is never searched again
        heard.youtubeVideoId?.let { id ->
            use(YtSong(id, heard.title, heard.artist ?: "Unknown artist"))
            return@LaunchedEffect
        }
        try {
            val found = SongMatcher.candidates(heard.artist, heard.title)
            val best = found.firstOrNull()
            when {
                best == null -> error = "Nothing found on YouTube for \"${heard.query}\""
                best.score >= SongMatcher.ACCEPT -> use(best.song)
                else -> choices = found.take(3)
            }
        } catch (e: Exception) {
            error = e.message ?: "YouTube search failed"
        }
    }

    when {
        error != null -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Couldn't find it") },
            text = { Text(error!!) },
            confirmButton = { TextButton(onClick = onDone) { Text("OK") } },
        )
        choices != null -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Which one is it?") },
            text = {
                Column {
                    choices!!.forEach { m ->
                        CardRow(m.song.thumbnail, m.song.title, m.song.artist) { use(m.song) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
        else -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Looking on YouTube") },
            text = { CircularProgressIndicator(color = Accent) },
            confirmButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
    }
}
