package com.ctify.player.ui.radio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.ui.EmptyHint
import com.ctify.player.ui.SectionHeader
import kotlinx.coroutines.launch

/** Hearted stations and the user's own stream URLs; both work offline as lists. */
@Composable
internal fun FavoritesPage() {
    val radio = radioRepo()
    val ui = rememberRadioUi()
    val favorites by radio.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val custom by radio.customStations.collectAsStateWithLifecycle(initialValue = emptyList())
    var adding by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "add") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilledTonalButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Add station")
                }
            }
        }
        item(key = "favh") { Column(Modifier.padding(horizontal = 8.dp)) { SectionHeader("Hearted", Modifier.padding(top = 10.dp, start = 4.dp)) } }
        if (favorites.isEmpty()) item(key = "favempty") { EmptyHint("Tap the heart on a station to keep it here.") }
        stationRows(favorites, ui, keyPrefix = "f")
        if (custom.isNotEmpty()) {
            item(key = "customh") { Column(Modifier.padding(horizontal = 8.dp)) { SectionHeader("Your stations", Modifier.padding(top = 10.dp, start = 4.dp)) } }
            stationRows(custom, ui, keyPrefix = "c", onDelete = { radio.deleteCustom(it) })
        }
        item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }

    if (adding) AddStationDialog(onDismiss = { adding = false })
}

@Composable
private fun AddStationDialog(onDismiss: () -> Unit) {
    val radio = radioRepo()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add station") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it; error = null }, label = { Text("Stream or playlist URL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags (optional, comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (busy) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Checking the stream…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && url.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        radio.addCustom(name, url, tags).fold(
                            onSuccess = { onDismiss() },
                            onFailure = { error = it.message ?: "That URL didn't give a playable stream" },
                        )
                        busy = false
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
