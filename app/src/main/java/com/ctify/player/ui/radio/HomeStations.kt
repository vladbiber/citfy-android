package com.ctify.player.ui.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.player.PlayerHub
import com.ctify.player.radio.Station
import com.ctify.player.ui.GridHeader
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextPrimary

/** Home's "Favorite stations" row, hidden when there are none. Recent stations live on the Radio tab. */
@Composable
fun HomeStationRows() {
    val radio = radioRepo()
    val favorites by radio.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val now by PlayerHub.playback.collectAsStateWithLifecycle()
    val current = now?.station?.uuid
    Column {
        if (favorites.isNotEmpty()) StationCarousel("Favorite stations", favorites, current)
    }
}

@Composable
private fun StationCarousel(title: String, list: List<Station>, current: String?) {
    GridHeader(title)
    Spacer(Modifier.height(8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(list, key = { _, s -> s.uuid }) { i, s ->
            val on = s.uuid == current
            Column(
                Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "play") { PlayerHub.playStations(list, i) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = s.name
                        if (on) stateDescription = "now playing"
                    },
            ) {
                StationArt(s, 96.dp, corner = 14.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    s.name, color = if (on) Accent else TextPrimary, fontSize = 12.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
