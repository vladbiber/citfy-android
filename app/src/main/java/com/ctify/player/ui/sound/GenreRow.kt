package com.ctify.player.ui.sound

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.ctify.player.ui.pressFeedback
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ctify.player.player.PlayerHub
import com.ctify.player.player.sound.GenrePreset
import com.ctify.player.player.sound.SoundEngine
import com.ctify.player.player.sound.SoundSettings
import com.ctify.player.ui.FxSlider
import com.ctify.player.ui.SectionHeader

/** "Suggested for this song": the preset and why. It only applies on a tap. */
@Composable
fun SuggestionCard() {
    val s by SoundEngine.suggestion.collectAsState()
    val current by SoundSettings.genreKey.collectAsState()
    val now by PlayerHub.playback.collectAsState()
    val radio = now?.station != null
    val shape = MaterialTheme.shapes.large
    val sug = s
    if (sug == null) {
        Text(
            "Play something to get a preset suggestion.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    val label = GenrePreset.labelOf(sug.key)
    if (sug.key == current) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                if (sug.autoFrom != null) "$label applied automatically" else "$label, ${sug.reason}",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (sug.autoFrom != null) TextButton(onClick = { SoundEngine.undoAuto() }) { Text("Undo") }
        }
        return
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (radio) "Suggested for this station" else "Suggested for this song",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(sug.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Button(onClick = { SoundEngine.applySuggestion() }) { Text("Apply") }
            }
        }
    }
}

/** The preset chips with the auto-apply switch; long-press a chip to edit or reset it. */
@Composable
fun GenrePresetsRow() {
    val presets by SoundSettings.presets.collectAsState()
    val current by SoundSettings.genreKey.collectAsState()
    val auto by SoundSettings.genreAuto.collectAsState()
    var editing by remember { mutableStateOf<GenrePreset?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Genre presets", Modifier.weight(1f))
            Text("Auto-apply", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = auto, onCheckedChange = { SoundSettings.setGenreAuto(it) },
                modifier = Modifier.semantics { contentDescription = "Auto-apply genre presets" },
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEach { p ->
                PresetChip(
                    label = p.label, selected = p.key == current, edited = p.edited,
                    onClick = { SoundEngine.pickGenre(p.key) },
                    onLongClick = { editing = p },
                )
            }
            IconButton(onClick = { editing = SoundSettings.preset(current) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit the current preset")
            }
        }
        Text(
            if (auto) "Each new song gets its suggested preset. Picking another one teaches it for the song and the artist."
            else "Presets change only when you tap one. Long-press to edit.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    editing?.let { p -> PresetEditor(p) { editing = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(label: String, selected: Boolean, edited: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (selected) c.secondaryContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, c.outline),
        modifier = Modifier
            .pressFeedback(press)
            .minimumInteractiveComponentSize()
            .semantics { stateDescription = if (selected) "selected" else "not selected" }
            .combinedClickable(
                interactionSource = press, indication = LocalIndication.current,
                role = Role.Button, onClick = onClick, onLongClick = onLongClick,
                onLongClickLabel = "edit preset",
            ),
    ) {
        Text(
            if (edited) "$label *" else label,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Edits a copy of the preset's curve; saving keeps it as the user's own, reset brings the bundled one back. */
@Composable
private fun PresetEditor(p: GenrePreset, onDismiss: () -> Unit) {
    var bands by remember(p.key) { mutableStateOf(p.bands) }
    var tape by remember(p.key) { mutableStateOf(p.tape) }
    var loud by remember(p.key) { mutableStateOf(p.loudness) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${p.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CurvePreview(p.copy(bands = bands).spec(0))
                GraphicEditor(bands) { i, v -> bands = bands.toMutableList().also { it[i] = v } }
                FxSlider("Tape warmth", "${(tape * 100).toInt()}%", tape, 0f..1f, 0f) { tape = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loudness compensation", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = loud, onCheckedChange = { loud = it })
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                SoundSettings.saveGenreCopy(p.copy(bands = bands, tape = tape, loudness = loud))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (p.edited) {
                    TextButton(onClick = { SoundSettings.resetGenre(p.key); onDismiss() }) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
