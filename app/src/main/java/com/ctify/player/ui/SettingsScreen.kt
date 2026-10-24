package com.ctify.player.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ctify.player.CtifyApp
import com.ctify.player.data.Backup
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.radio.RadioPrefs
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.ui.theme.GradientStyle
import com.ctify.player.ui.theme.Swatch
import com.ctify.player.ui.theme.ThemeMode
import com.ctify.player.ui.theme.ThemeSource
import com.ctify.player.ui.theme.isAppInDarkTheme
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(onClose: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CtifyApp
    val scope = rememberCoroutineScope()
    val mode by Appearance.mode.collectAsState()
    val source by Appearance.source.collectAsState()
    val color1 by Appearance.color1.collectAsState()
    val color2 by Appearance.color2.collectAsState()
    val gradient by Appearance.gradient.collectAsState()
    val pureBlack by Appearance.pureBlack.collectAsState()
    val coverColors by Appearance.coverColors.collectAsState()
    val dark = isAppInDarkTheme()
    val offline by NetworkGate.offline.collectAsState()
    val features by NetworkGate.enabled.collectAsState()
    val wifiOnlyStreaming by NetworkGate.wifiOnlyStreaming.collectAsState()
    val wifiOnlyDownloads by NetworkGate.wifiOnlyDownloads.collectAsState()
    val radioOn = features[Feature.RADIO] != false
    val youtubeOn = features[Feature.YOUTUBE] != false
    val chargingOnly by Downloads.chargingOnly.collectAsState()

    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var restored by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            message = Backup.export(ctx, app.database, uri).fold(
                onSuccess = { "Backup saved." },
                onFailure = { "Backup failed: ${it.message}" },
            )
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestore = uri }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Settings")
            }

            SectionHeader("Colors", Modifier.padding(top = 24.dp, bottom = 4.dp))
            Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            // Below Android 12 there are no wallpaper colors and Wallpaper looks like Ctify Blue, so only Ctify Blue is offered.
            val sources = if (Appearance.dynamicSupported) ThemeSource.entries else ThemeSource.entries - ThemeSource.WALLPAPER
            val shownSource = if (source in sources) source else ThemeSource.CTIFY_BLUE
            Choice(sources, shownSource, { it.label }, Appearance::setSource)
            if (source == ThemeSource.DUO) {
                SwatchRow("Color 1", "Play button, selected tab, sliders and switches", color1, Appearance::setColor1)
                SwatchRow("Color 2", "Chips, badges and the gradient. Same as color 1 gives a darker shade.", color2, Appearance::setColor2)
                Text("Gradient", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                Choice(GradientStyle.entries, gradient, { it.label }, Appearance::setGradient)
            }
            Text("Light or dark", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Choice(ThemeMode.entries, mode, { it.label }, Appearance::setMode)
            SwitchRow("Pure black", "Black background in dark theme, for OLED screens", pureBlack, Appearance::setPureBlack, enabled = dark)
            SwitchRow("Cover colors in Now Playing", "Tint the player with the album cover instead of the theme", coverColors, Appearance::setCoverColors)

            SectionHeader("Network", Modifier.padding(top = 24.dp, bottom = 4.dp))
            SwitchRow("Offline mode", "Block all internet use. Your music keeps working.", offline, { NetworkGate.setOffline(it); if (!it) Downloads.kick() })
            SwitchRow("Radio", "Station directory and live streams", radioOn, { NetworkGate.setEnabled(Feature.RADIO, it) }, enabled = !offline)
            SwitchRow(
                "Radio on mobile data", "Off means radio only plays on Wi-Fi",
                !wifiOnlyStreaming, { NetworkGate.setWifiOnlyStreaming(!it) }, enabled = !offline && radioOn,
            )
            SwitchRow(
                "YouTube", "Search, stream and download songs and albums",
                youtubeOn, { NetworkGate.setEnabled(Feature.YOUTUBE, it); if (it) Downloads.kick() }, enabled = !offline,
            )
            SwitchRow(
                "Downloads on Wi-Fi only", "Wait for Wi-Fi before downloading", wifiOnlyDownloads,
                { NetworkGate.setWifiOnlyDownloads(it); Downloads.kick(replace = true) }, enabled = !offline,
            )
            SwitchRow("Downloads only while charging", "Wait until the phone is plugged in", chargingOnly, Downloads::setChargingOnly, enabled = !offline && youtubeOn)
            var bufferMin by remember { mutableIntStateOf(RadioPrefs.bufferMinutes(ctx)) }
            Text("Radio rewind buffer", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
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

            SectionHeader("Backup", Modifier.padding(top = 24.dp, bottom = 4.dp))
            Text(
                "Saves your favorites, playlists, imported songs, sound and look settings in one file. " +
                    "Imported songs and custom covers point to files on this phone, so after a reinstall you may need to import those again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { exportLauncher.launch("ctify-backup-${LocalDate.now()}.zip") }) { Text("Save backup") }
                FilledTonalButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Restore") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces your current favorites, playlists and settings. Ctify will close afterwards; open it again.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        Backup.restore(ctx, app.database, uri).fold(
                            onSuccess = { restored = true },
                            onFailure = { message = "Restore failed: ${it.message}" },
                        )
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup restored") },
            text = { Text("Ctify will close now. Open it again to load your data.") },
            confirmButton = {
                TextButton(onClick = {
                    (ctx as? Activity)?.finishAffinity()
                    exitProcess(0)
                }) { Text("Close Ctify") }
            },
        )
    }
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = selected == o,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(o)) }
        }
    }
}

@Composable
private fun SwatchRow(title: String, subtitle: String, selected: Swatch, onSelect: (Swatch) -> Unit) {
    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup()) {
        Swatch.entries.forEach { sw ->
            val on = sw == selected
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(on, role = Role.RadioButton) { onSelect(sw) }
                    .semantics { contentDescription = "$title: ${sw.label}" }
                    .padding(4.dp)
                    // Selected = thick ring with a gap, so the choice does not rely on color alone.
                    .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .padding(if (on) 6.dp else 2.dp)
                    .clip(CircleShape)
                    .background(sw.color),
            )
        }
    }
}
