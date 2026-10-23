package com.ctify.player.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ctify.player.CtifyApp
import com.ctify.player.data.Backup
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.ui.theme.ThemeMode
import com.ctify.player.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CtifyApp
    val scope = rememberCoroutineScope()
    val mode by Appearance.mode.collectAsState()
    val dynamic by Appearance.dynamic.collectAsState()
    val pureBlack by Appearance.pureBlack.collectAsState()
    val coverColors by Appearance.coverColors.collectAsState()
    val dark = isAppInDarkTheme()

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
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Settings")
            }

            Header("Appearance")
            Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { Appearance.setMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(m.label) }
                }
            }
            if (Appearance.dynamicSupported) {
                SwitchRow("Wallpaper colors", "Use the colors of your wallpaper", dynamic, Appearance::setDynamic)
            }
            SwitchRow("Pure black", "Black background in dark theme, for OLED screens", pureBlack, Appearance::setPureBlack, enabled = dark)
            SwitchRow("Cover colors in Now Playing", "Tint the player with the album cover instead of the theme", coverColors, Appearance::setCoverColors)

            Header("Backup")
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
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() },
    )
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
