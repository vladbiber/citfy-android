package com.ctify.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.radio.HeardPage

/** New music: songs heard on the radio, YouTube and stations. Empty search shows the heard list. */
@Composable
fun DiscoverScreen(viewModel: PlayerViewModel, onMore: (String) -> Unit, add: AddActions) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Discover", Modifier.weight(1f))
            AddMenu(add)
        }
        SearchField(query, { query = it }, "Heard songs, new songs, stations", Modifier.padding(horizontal = 16.dp))
        if (query.isNotBlank()) SearchResults(viewModel, query, onMore, DISCOVER_SCOPES) else HeardPage()
    }
}
