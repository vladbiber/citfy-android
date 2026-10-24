package com.ctify.player.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ctify.player.ui.skins.ChapterMark
import com.ctify.player.ui.skins.LiveProgress
import com.ctify.player.youtube.Chapters

/** Where the chapters start on a 0..1 seek bar; empty when the song has none. */
@Composable
fun chapterMarks(live: LiveProgress): List<Float> {
    val chapters by Chapters.current.collectAsState()
    val d = live.durationMs.coerceAtLeast(1L)
    return chapters.map { it.startMs.toFloat() / d }
}

/** Current chapter with previous/next chapter buttons. Draws nothing when the song has no chapters. */
@Composable
fun ChapterRow(live: LiveProgress, ink: Color) {
    val chapters by Chapters.current.collectAsState()
    if (chapters.isEmpty()) return
    val pos = live.positionMs
    val i = Chapters.indexAt(chapters, pos)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            // restart the chapter, or go back one when already near its start
            val start = chapters.getOrNull(i)?.startMs ?: 0L
            val to = if (pos - start > 3_000 || i <= 0) start else chapters[i - 1].startMs
            live.seekTo(to)
        }) { Icon(Icons.Filled.FastRewind, contentDescription = "Previous chapter", tint = ink) }
        Text(
            chapters.getOrNull(i)?.title?.ifBlank { null } ?: "Chapter ${i + 1}",
            color = ChapterMark, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { chapters.getOrNull(i + 1)?.let { live.seekTo(it.startMs) } }, enabled = i + 1 < chapters.size) {
            Icon(Icons.Filled.FastForward, contentDescription = "Next chapter", tint = if (i + 1 < chapters.size) ink else ink.copy(alpha = 0.3f))
        }
    }
}
