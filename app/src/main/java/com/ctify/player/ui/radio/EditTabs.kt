package com.ctify.player.ui.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.genre.Genre
import com.ctify.player.radio.RadioTab
import com.ctify.player.radio.StationSort
import com.ctify.player.radio.TabKind
import com.ctify.player.ui.ScreenTitle
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import java.util.Locale

private const val TAB_HINT = 8

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditTabsScreen(tabs: List<RadioTab>, onClose: () -> Unit) {
    val radio = radioRepo()
    val favorites by radio.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    var renaming by remember { mutableStateOf<RadioTab?>(null) }
    val pinnedGenres = tabs.filter { it.kind == TabKind.GENRE }.map { it.key }.toSet()
    // the families the user's favorites lean to, most first
    val suggested = favorites.mapNotNull { it.genre }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }.filter { it.name !in pinnedGenres }
    val others = Genre.entries.filter { it.name !in pinnedGenres && it !in suggested }

    fun move(i: Int, d: Int) {
        val ids = tabs.map { it.id }.toMutableList()
        val j = i + d
        if (j !in ids.indices) return
        ids[i] = ids[j].also { ids[j] = ids[i] }
        radio.reorderTabs(ids)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Edit tabs")
            }
            Text(
                "For you, Favorites and Browse always stay. Keep your own tabs to about $TAB_HINT so they are easy to swipe through.",
                color = TextSecondary, fontSize = 13.sp,
            )
            if (tabs.size > TAB_HINT) {
                Text("You have ${tabs.size} tabs.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            SectionTitle("Your tabs")
            if (tabs.isEmpty()) Text("No tabs yet. Pin a genre below or star a tag in Browse.", color = TextMuted, fontSize = 14.sp)
            tabs.forEachIndexed { i, tab ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tab.title, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (tab.kind == TabKind.GENRE) "Genre" else "Tag: ${tab.key}", color = TextMuted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { move(i, -1) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move ${tab.title} up")
                    }
                    IconButton(onClick = { move(i, 1) }, enabled = i < tabs.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move ${tab.title} down")
                    }
                    IconButton(onClick = { renaming = tab }) { Icon(Icons.Filled.Edit, "Rename ${tab.title}") }
                    IconButton(onClick = { radio.removeTab(tab) }) { Icon(Icons.Filled.Delete, "Remove ${tab.title}") }
                }
            }
            if (suggested.isNotEmpty()) {
                SectionTitle("Suggested from your favorites")
                GenreChips(suggested) { radio.pinTab(TabKind.GENRE, it.name, it.label) }
            }
            SectionTitle("More genres")
            GenreChips(others) { radio.pinTab(TabKind.GENRE, it.name, it.label) }
            Text(
                "For a single tag like \"80s\", star it in Browse, Genres.",
                color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
        }
    }

    renaming?.let { tab ->
        var title by remember(tab.id) { mutableStateOf(tab.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename tab") },
            text = {
                OutlinedTextField(
                    title, { title = it.take(30) }, singleLine = true, label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    radio.updateTab(tab.copy(title = title.trim())); renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreChips(genres: List<Genre>, onPin: (Genre) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.forEach { g ->
            AssistChip(
                onClick = { onPin(g) },
                label = { Text(g.label) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.semantics { contentDescription = "Pin ${g.label}" },
            )
        }
    }
}

private val BITRATES = listOf(0, 64, 128, 192, 320)

/** Sort and filters for one pinned tab; saved together on "Save". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TabSettingsSheet(tab: RadioTab, onDismiss: () -> Unit, onSave: (RadioTab) -> Unit) {
    var draft by remember(tab.id) { mutableStateOf(tab) }
    val own = remember { Locale.getDefault().country.uppercase().takeIf { it.length == 2 } }
    var otherCode by remember(tab.id) { mutableStateOf(tab.countryCode?.takeIf { it != own }.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(draft.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })

            SectionTitle("Sort")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StationSort.entries.forEach { s ->
                    FilterChip(selected = draft.sort == s, onClick = { draft = draft.copy(sort = s) }, label = { Text(s.label) })
                }
            }

            SectionTitle("Country")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.countryCode == null, onClick = { draft = draft.copy(countryCode = null); otherCode = "" }, label = { Text("Any") })
                if (own != null) {
                    FilterChip(
                        selected = draft.countryCode == own,
                        onClick = { draft = draft.copy(countryCode = own); otherCode = "" },
                        label = { Text("${flagOf(own)} $own") },
                    )
                }
            }
            OutlinedTextField(
                value = otherCode,
                onValueChange = { v ->
                    otherCode = v.filter { it.isLetter() }.take(2).uppercase()
                    val code = when {
                        otherCode.length == 2 -> otherCode
                        otherCode.isEmpty() -> draft.countryCode?.takeIf { it == own }
                        else -> draft.countryCode
                    }
                    draft = draft.copy(countryCode = code)
                },
                label = { Text("Other country code, e.g. DE") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.padding(top = 4.dp),
            )

            SectionTitle("Minimum bitrate")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BITRATES.forEach { b ->
                    FilterChip(
                        selected = draft.minBitrate == b,
                        onClick = { draft = draft.copy(minBitrate = b) },
                        label = { Text(if (b == 0) "Any" else "${b}k+") },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 12.dp)
                    .clickable(role = Role.Switch) { draft = draft.copy(hideBroken = !draft.hideBroken) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hide broken stations", style = MaterialTheme.typography.titleMedium)
                    Text("Skip stations the directory marks as offline", color = TextSecondary, fontSize = 13.sp)
                }
                Switch(checked = draft.hideBroken, onCheckedChange = null)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onSave(draft) }) { Text("Save") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
