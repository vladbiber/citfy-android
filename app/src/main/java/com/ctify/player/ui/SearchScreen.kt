package com.ctify.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.theme.SpotifyGreen
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

@Composable
fun SearchScreen(viewModel: PlayerViewModel, onMore: (String) -> Unit) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val results = remember(query, tracks) {
        if (query.isBlank()) emptyList()
        else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }

    LazyColumn(contentPadding = contentPadding()) {
        item {
            Text(
                "Search",
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Songs, artists, albums", color = TextMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = SpotifyGreen,
                ),
            )
        }

        if (query.isNotBlank() && results.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No results for \"$query\"", color = TextSecondary)
                }
            }
        }

        items(results, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == ui.currentTrackId,
                isPlaying = ui.isPlaying && track.id == ui.currentTrackId,
                isFavorite = track.id in favorites,
                onClick = { viewModel.playQueue(results, results.indexOf(track)) },
                onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                onMore = { onMore(track.id) },
            )
        }
    }
}
