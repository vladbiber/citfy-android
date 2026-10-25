package com.ctify.player.ui.radio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.radio.RadioRepository
import com.ctify.player.radio.RadioTab
import com.ctify.player.radio.Station
import com.ctify.player.ui.PillTabs
import com.ctify.player.ui.SettingsButton
import com.ctify.player.ui.TabTopBar
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.wheel.StationListSource
import com.ctify.player.ui.wheel.WheelScreen
import com.ctify.player.ui.wheel.WheelSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val FIXED_PAGES = 3

@Composable
fun RadioScreen(onOpenWheel: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    val radio = radioRepo()
    val tabs by radio.tabs.collectAsStateWithLifecycle(initialValue = emptyList())
    val titles = listOf("For you", "Favorites", "Browse") + tabs.map { it.title }
    val pager = rememberPagerState { titles.size }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var settingsFor by remember { mutableStateOf<RadioTab?>(null) }
    var wheelPage by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Radio", Modifier.padding(start = 8.dp, end = 4.dp, top = 12.dp)) {
            IconButton(onClick = onOpenWheel) { Icon(Icons.Filled.Casino, "Spin the wheel", tint = TextPrimary) }
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add station", tint = TextPrimary) }
            SettingsButton(onOpenSettings)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More radio options", tint = TextPrimary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Station wheel") },
                        leadingIcon = { Icon(Icons.Filled.Radio, null) },
                        onClick = { menu = false; wheelPage = pager.currentPage },
                    )
                    DropdownMenuItem(
                        text = { Text("Add or edit tabs") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; editing = true },
                    )
                }
            }
        }
        PillTabs(
            titles, selected = pager.currentPage.coerceAtMost(titles.lastIndex),
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } },
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), key = { pageKey(it, tabs) }) { page ->
            when (page) {
                0 -> ForYouPage()
                1 -> FavoritesPage()
                2 -> BrowsePage()
                else -> tabs.getOrNull(page - FIXED_PAGES)?.let { tab -> PinnedTabPage(tab, onSettings = { settingsFor = tab }) }
            }
        }
    }

    if (adding) AddStationDialog(onDismiss = { adding = false })

    settingsFor?.let { tab ->
        TabSettingsSheet(tab, onDismiss = { settingsFor = null }, onSave = { radio.updateTab(it); settingsFor = null })
    }

    AnimatedVisibility(
        visible = editing,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        BackHandler { editing = false }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            EditTabsScreen(tabs, onClose = { editing = false })
        }
    }

    AnimatedVisibility(
        visible = wheelPage != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        BackHandler { wheelPage = null }
        // keep the last page while the exit animation runs
        val page = remember { wheelPage ?: 1 }
        val blocked = rememberRadioBlock()
        val sources = remember(page) { stationWheelSources(radio, page, tabs) }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            WheelScreen(onClose = { wheelPage = null }, sources = sources, title = "Station wheel", notice = blocked)
        }
    }
}

/** The open tab's stations first, then the favorites. Saved lists only, no network. */
private fun stationWheelSources(radio: RadioRepository, page: Int, tabs: List<RadioTab>): List<WheelSource> {
    val favorites = StationListSource("favorites", "Favorites", radio) { radio.favorites.first() }
    val here = when (page) {
        0 -> StationListSource("for_you", "For you", radio) {
            radio.cached(RadioRepository.CLIAMP_KEY).orEmpty() + radio.cached(RadioRepository.FOR_YOU_KEY).orEmpty()
        }
        in FIXED_PAGES..Int.MAX_VALUE -> tabs.getOrNull(page - FIXED_PAGES)?.let { tab ->
            StationListSource("tab_${tab.id}", tab.title, radio) { radio.cached("tab_${tab.id}").orEmpty() }
        }
        else -> null
    }
    // recent stations used to come with the Heard page, which moved to Discover
    val recent = StationListSource("recent", "Recent", radio) { radio.recents.first() }
    return listOfNotNull(here, favorites, recent)
}

private fun pageKey(page: Int, tabs: List<RadioTab>): Any =
    if (page < FIXED_PAGES) "fixed$page" else tabs.getOrNull(page - FIXED_PAGES)?.id ?: "gone$page"

@Composable
private fun ForYouPage() {
    val radio = radioRepo()
    val blocked = rememberRadioBlock()
    val ui = rememberRadioUi()
    val state = rememberLoad(
        "for_you",
        blocked = blocked,
        cached = {
            (radio.cached(RadioRepository.CLIAMP_KEY).orEmpty() + radio.cached(RadioRepository.FOR_YOU_KEY).orEmpty())
                .distinctBy { it.uuid }.ifEmpty { null }
        },
    ) {
        // read the history once, so a heart doesn't reload the page
        radio.forYou(radio.recents.first(), radio.favorites.first())
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "intro") {
            Text(
                "Live channels picked for you, then stations like the ones you play and heart.",
                color = TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        loadStatus(state, blocked, List<Station>::isEmpty, "Nothing here yet. Try Browse.")
        state.data?.let { stationRows(it, ui) }
    }
}

@Composable
private fun PinnedTabPage(tab: RadioTab, onSettings: () -> Unit) {
    val radio = radioRepo()
    val blocked = rememberRadioBlock()
    val ui = rememberRadioUi()
    val key = "tab_${tab.id}"
    // moving or renaming the tab is not a reason to reload
    val state = rememberLoad(tab.copy(position = 0, title = ""), blocked = blocked, cached = { radio.cached(key) }) {
        radio.search(radio.queryFor(tab), cacheKey = key)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tabSummary(tab), color = TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = "Settings for ${tab.title}", tint = TextSecondary)
                }
            }
        }
        loadStatus(state, blocked, List<Station>::isEmpty, "No stations match. Try fewer filters.")
        state.data?.let { stationRows(it, ui) }
    }
}

internal fun tabSummary(tab: RadioTab): String = listOfNotNull(
    tab.sort.label,
    tab.countryCode?.let { "${flagOf(it)} $it".trim() },
    tab.minBitrate.takeIf { it > 0 }?.let { "${it}k+" },
    if (tab.hideBroken) null else "broken shown",
).joinToString(" · ")
