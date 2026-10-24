package com.ctify.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ctify.player.player.SleepTimer
import com.ctify.player.ui.SectionHeader
import kotlinx.coroutines.delay

/** "in 23 minutes", "at the end of this track", or null when the sleep timer is off; ticks while shown. */
@Composable
internal fun sleepLeft(): String? {
    val at by SleepTimer.endsAt.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(at) { while (at != null) { now = System.currentTimeMillis(); delay(1_000) } }
    return when (val a = at) {
        null -> null
        SleepTimer.END_OF_TRACK -> "at the end of this track"
        else -> SleepTimer.minutesLeft(a, now).let { "in $it minute${if (it == 1L) "" else "s"}" }
    }
}

/** Sleep timer choices; [live] radio has no track end. */
@Composable
internal fun SleepSheet(live: Boolean, onDone: () -> Unit) {
    val left = sleepLeft()
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        SectionHeader("Sleep timer")
        Text(
            left?.let { "Pausing $it" } ?: "Fades out, then pauses",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        listOf(15, 30, 45, 60, 90).forEach { m -> Choice("$m minutes") { SleepTimer.start(m); onDone() } }
        if (!live) Choice("End of this track") { SleepTimer.endOfTrack(); onDone() }
        if (left != null) Choice("Off") { SleepTimer.cancel(); onDone() }
    }
}

@Composable
private fun Choice(label: String, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
    )
}
