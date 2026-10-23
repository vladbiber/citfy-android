package com.ctify.player.ui.radio

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctify.player.player.PlayerHub
import com.ctify.player.ui.formatMs
import com.ctify.player.ui.skins.LiveProgress
import com.ctify.player.ui.skins.PillSlider

/** Live radio with the rewind buffer: scrub inside what was recorded, jump back to live. */
@Composable
fun RewindRow(live: LiveProgress, start: Long, edge: Long, accent: Color) {
    val span = (edge - start).toFloat()
    val behind = (edge - live.positionMs).coerceAtLeast(0L)
    val atLive = behind < 6_000
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
        Text(if (atLive) "" else "-${formatMs(behind.toInt())}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 36.dp))
        PillSlider(
            value = ((live.positionMs - start) / span).coerceIn(0f, 1f),
            onChange = { live.preview = start + (it * span).toLong() },
            onDone = { live.seekTo(start + (it * span).toLong()) },
            accent = accent,
            label = "Rewind",
            stateText = if (atLive) "Live" else "${formatMs(behind.toInt())} behind live",
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (atLive) LiveBadge(accent)
        else TextButton(onClick = { PlayerHub.seek(edge.toInt()) }) { Text("GO LIVE", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}
