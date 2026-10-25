package com.ctify.player.ui.radio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.genre.Genre
import com.ctify.player.keys.typingField
import com.ctify.player.radio.CountryCount
import com.ctify.player.radio.RadioTab
import com.ctify.player.radio.Station
import com.ctify.player.radio.StationQuery
import com.ctify.player.radio.StationSort
import com.ctify.player.radio.TabKind
import com.ctify.player.radio.TagCount
import com.ctify.player.ui.SectionHeader
import com.ctify.player.ui.enterStagger
import com.ctify.player.ui.pressFeedback
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

/** Countries, genres and name search over the whole directory. */
@Composable
internal fun BrowsePage() {
    var query by rememberSaveable { mutableStateOf("") }
    var committed by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(0) }
    var countryCode by rememberSaveable { mutableStateOf<String?>(null) }
    var countryName by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var genreKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) { committed = ""; return@LaunchedEffect }
        delay(500)
        if (query.trim().length >= 2) committed = query.trim()
    }

    val inDetail = committed.isNotEmpty() || countryCode != null || tag != null || genreKey != null
    BackHandler(enabled = inDetail) {
        when {
            committed.isNotEmpty() -> { query = ""; committed = "" }
            else -> { countryCode = null; tag = null; genreKey = null }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query, onChange = { query = it }, onSubmit = { if (query.isNotBlank()) committed = query.trim() })
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                committed.isNotEmpty() -> StationsFor(
                    title = "Results for \"$committed\"", key = "q:$committed",
                    query = StationQuery(name = committed, sort = StationSort.CLICKS), onBack = { query = ""; committed = "" },
                )
                countryCode != null -> CountryStations(countryCode!!, countryName, onBack = { countryCode = null })
                genreKey != null -> {
                    val g = Genre.valueOf(genreKey!!)
                    StationsFor(
                        title = g.label, key = "g:${g.name}", query = StationQuery(tags = g.tags),
                        onBack = { genreKey = null }, pin = TabKind.GENRE to g.name,
                    )
                }
                tag != null -> StationsFor(
                    title = tag!!, key = "t:$tag", query = StationQuery(tags = listOf(tag!!)),
                    onBack = { tag = null }, pin = TabKind.TAG to tag!!,
                )
                else -> BrowseHome(
                    section, onSection = { section = it },
                    onCountry = { countryCode = it.code; countryName = it.name },
                    onGenre = { genreKey = it.name },
                    onTag = { tag = it },
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.typingField().fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Station name", color = TextMuted) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Filled.Close, "Clear search", tint = TextSecondary)
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceElevated,
            unfocusedContainerColor = SurfaceElevated,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent,
        ),
    )
}

@Composable
private fun BrowseHome(
    section: Int,
    onSection: (Int) -> Unit,
    onCountry: (CountryCount) -> Unit,
    onGenre: (Genre) -> Unit,
    onTag: (String) -> Unit,
) {
    val radio = radioRepo()
    val blocked = rememberRadioBlock()
    val tabs by radio.tabs.collectAsStateWithLifecycle(initialValue = emptyList())
    val own = remember { Locale.getDefault().country.uppercase() }
    val countries = rememberLoad("countries", blocked = blocked) { radio.countries() }
    val tagIndex = rememberLoad("tags", blocked = blocked) { radio.tagIndex() }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "sections") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = section == 0, onClick = { onSection(0) }, label = { Text("Genres") })
                FilterChip(selected = section == 1, onClick = { onSection(1) }, label = { Text("Countries") })
            }
        }
        if (section == 0) {
            item(key = "families") { SectionHeader("Genre families", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)) }
            itemsIndexed(Genre.entries, key = { _, g -> "g" + g.name }) { i, g ->
                val pinned = tabs.firstOrNull { it.kind == TabKind.GENRE && it.key == g.name }
                PinRow(
                    modifier = Modifier.enterStagger(i),
                    title = g.label,
                    subtitle = g.tags.take(4).joinToString(", "),
                    pinned = pinned != null,
                    onClick = { onGenre(g) },
                    onPin = { if (pinned != null) radio.removeTab(pinned) else radio.pinTab(TabKind.GENRE, g.name, g.label) },
                )
            }
            item(key = "alltags") { SectionHeader("All tags", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)) }
            loadStatus(tagIndex, blocked, List<TagCount>::isEmpty, "No tags found.")
            tagIndex.data?.let { list ->
                items(list, key = { "t" + it.name }) { t ->
                    val pinned = tabs.firstOrNull { it.kind == TabKind.TAG && it.key == t.name }
                    PinRow(
                        title = t.name,
                        subtitle = "${t.count} stations",
                        pinned = pinned != null,
                        onClick = { onTag(t.name) },
                        onPin = { if (pinned != null) radio.removeTab(pinned) else radio.pinTab(TabKind.TAG, t.name, t.name) },
                    )
                }
            }
        } else {
            loadStatus(countries, blocked, List<CountryCount>::isEmpty, "No countries found.")
            countries.data?.let { list ->
                val sorted = list.filter { it.code == own } + list.filter { it.code != own }
                itemsIndexed(sorted, key = { _, c -> "c" + c.code }) { i, c ->
                    CountryRow(c, isOwn = c.code == own, Modifier.enterStagger(i)) { onCountry(c) }
                }
            }
        }
    }
}

@Composable
private fun PinRow(title: String, subtitle: String, pinned: Boolean, onClick: () -> Unit, onPin: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { MutableInteractionSource() }
    Row(
        modifier.pressFeedback(press).fillMaxWidth().heightIn(min = 56.dp).clickable(press, LocalIndication.current, onClickLabel = "show stations", onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPin) {
            Icon(
                if (pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                if (pinned) "Unpin $title" else "Pin $title as a tab",
                tint = if (pinned) Accent else TextSecondary,
            )
        }
    }
}

@Composable
private fun CountryRow(c: CountryCount, isOwn: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        modifier.pressFeedback(press).fillMaxWidth().heightIn(min = 56.dp).clickable(press, LocalIndication.current, onClickLabel = "show stations", onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(flagOf(c.code), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = if (isOwn) Accent else TextPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isOwn) Text("Your country", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text("${c.count}", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit, pin: Pair<TabKind, String>? = null, tabs: List<RadioTab> = emptyList()) {
    val radio = radioRepo()
    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
        Text(
            title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (pin != null) {
            val pinned = tabs.firstOrNull { it.kind == pin.first && it.key == pin.second }
            IconButton(onClick = { if (pinned != null) radio.removeTab(pinned) else radio.pinTab(pin.first, pin.second, title) }) {
                Icon(
                    if (pinned != null) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (pinned != null) "Unpin $title" else "Pin $title as a tab",
                    tint = if (pinned != null) Accent else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun StationsFor(title: String, key: String, query: StationQuery, onBack: () -> Unit, pin: Pair<TabKind, String>? = null) {
    val radio = radioRepo()
    val blocked = rememberRadioBlock()
    val ui = rememberRadioUi()
    val tabs by radio.tabs.collectAsStateWithLifecycle(initialValue = emptyList())
    val state = rememberLoad(key, blocked = blocked) { radio.search(query) }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "back") { BackRow(title, onBack, pin, tabs) }
        stationList(state, blocked, ui)
    }
}

@Composable
private fun CountryStations(code: String, name: String, onBack: () -> Unit) {
    val radio = radioRepo()
    val blocked = rememberRadioBlock()
    val ui = rememberRadioUi()
    val isOwn = remember(code) { code == Locale.getDefault().country.uppercase() }
    var region by rememberSaveable(code) { mutableStateOf<String?>(null) }
    // regions only for the user's own country; a non-null block skips the request
    val regions = rememberLoad("regions:$name", blocked = if (isOwn) blocked else "skip") { radio.regions(name) }
    val cacheKey = "country_$code"
    val state = rememberLoad(code, region, blocked = blocked, cached = { if (region == null) radio.cached(cacheKey) else null }) {
        radio.search(StationQuery(countryCode = code, state = region), cacheKey = if (region == null) cacheKey else null)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "back") { BackRow("${flagOf(code)} $name".trim(), onBack) }
        val shown = regions.data.orEmpty().filter { it.count >= 2 }.take(30)
        if (isOwn && shown.isNotEmpty()) {
            item(key = "regions") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = region == null, onClick = { region = null }, label = { Text("Whole country") })
                    shown.forEach { r ->
                        FilterChip(selected = region == r.name, onClick = { region = r.name }, label = { Text(r.name, maxLines = 1) })
                    }
                }
            }
        }
        stationList(state, blocked, ui)
    }
}

private fun LazyListScope.stationList(state: LoadState<List<Station>>, blocked: String?, ui: RadioUi) {
    loadStatus(state, blocked, List<Station>::isEmpty, "No stations found.")
    state.data?.let { stationRows(it, ui) }
}
