package com.ctify.player.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.radio.RadioPrefs
import com.ctify.player.ui.SectionHeader
import com.ctify.player.ui.pressFeedback
import com.ctify.player.youtube.download.Downloads

/** Offline mode, each online service, Wi-Fi rules, the download folder and the radio rewind buffer. */
@Composable
internal fun ColumnScope.NetworkSettings() {
    val ctx = LocalContext.current
    val offline by NetworkGate.offline.collectAsState()
    val features by NetworkGate.enabled.collectAsState()
    val wifiOnlyStreaming by NetworkGate.wifiOnlyStreaming.collectAsState()
    val wifiOnlyDownloads by NetworkGate.wifiOnlyDownloads.collectAsState()
    val radioOn = features[Feature.RADIO] != false
    val youtubeOn = features[Feature.YOUTUBE] != false
    val chargingOnly by Downloads.chargingOnly.collectAsState()
    val folder by Downloads.folder.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) runCatching { Downloads.setFolder(uri) }.onFailure { message = "Couldn't use that folder: ${it.message}" }
    }

    SwitchRow("Offline mode", "Block all internet use. Your music keeps working.", offline, { NetworkGate.setOffline(it); if (!it) Downloads.kick() })

    SectionHeader("Services", Modifier.padding(top = 16.dp, bottom = 4.dp))
    SwitchRow("Radio", "Station directory and live streams", radioOn, { NetworkGate.setEnabled(Feature.RADIO, it) }, enabled = !offline)
    SwitchRow(
        "Radio on mobile data", "Off means radio only plays on Wi-Fi",
        !wifiOnlyStreaming, { NetworkGate.setWifiOnlyStreaming(!it) }, enabled = !offline && radioOn,
    )
    SwitchRow(
        "YouTube", "Search, stream and download songs and albums",
        youtubeOn, { NetworkGate.setEnabled(Feature.YOUTUBE, it); if (it) Downloads.kick() }, enabled = !offline,
    )
    SwitchRow("Genres and creators (Last.fm)", "Genre and artist suggestions on Home", features[Feature.LASTFM] != false, { NetworkGate.setEnabled(Feature.LASTFM, it) }, enabled = !offline)
    SwitchRow("Lyrics (LRCLIB)", "Synced lyrics in the player", features[Feature.LYRICS] != false, { NetworkGate.setEnabled(Feature.LYRICS, it) }, enabled = !offline)
    SwitchRow("Album info (MusicBrainz)", "Track lists, years, community ratings and discographies", features[Feature.MUSICBRAINZ] != false, { NetworkGate.setEnabled(Feature.MUSICBRAINZ, it) }, enabled = !offline)

    SectionHeader("Downloads", Modifier.padding(top = 16.dp, bottom = 4.dp))
    SwitchRow(
        "Downloads on Wi-Fi only", "Wait for Wi-Fi before downloading", wifiOnlyDownloads,
        { NetworkGate.setWifiOnlyDownloads(it); Downloads.kick(replace = true) }, enabled = !offline,
    )
    SwitchRow("Downloads only while charging", "Wait until the phone is plugged in", chargingOnly, Downloads::setChargingOnly, enabled = !offline && youtubeOn)
    val folderName = remember(folder) { folder?.let { DocumentFile.fromTreeUri(ctx, it)?.name ?: it.lastPathSegment } }
    val folderPress = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressFeedback(folderPress)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(folderPress, LocalIndication.current, onClickLabel = "Choose download folder") { folderLauncher.launch(folder) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Download folder", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                folderName ?: "Default (Music/Guava Player)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (folder != null) TextButton(onClick = { Downloads.setFolder(null) }) { Text("Use default") }
    }
    Text(
        "New downloads go here. Songs already saved stay where they are.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SectionHeader("Radio", Modifier.padding(top = 16.dp, bottom = 4.dp))
    var bufferMin by remember { mutableIntStateOf(RadioPrefs.bufferMinutes(ctx)) }
    Text("Radio rewind buffer", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    Text(
        if (bufferMin == 0) "Off: radio plays live, no pause or rewind"
        else "Keeps the last $bufferMin min (about ${bufferMin} MB) so you can pause, rewind and scratch live radio",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = bufferMin.toFloat(),
        onValueChange = { bufferMin = it.toInt() },
        onValueChangeFinished = { RadioPrefs.setBufferMinutes(ctx, bufferMin) },
        valueRange = 0f..15f,
        steps = 14,
        modifier = Modifier.semantics { contentDescription = "Radio rewind buffer, $bufferMin minutes" },
    )

    MessageDialog(message) { message = null }
}
