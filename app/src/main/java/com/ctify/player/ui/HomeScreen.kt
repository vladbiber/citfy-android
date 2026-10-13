package com.ctify.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.player.PlayerViewModel

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onMore: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenFavorites: () -> Unit = {},
) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        full {
            Row(Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CtifyLogo(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    ScreenTitle("ctify")
                }
            }
        }
        full {
            HeroCard(
                "Shuffle everything", "${tracks.size} songs on this phone", Icons.Filled.Shuffle,
                listOf(Color(0xFF2B5FD9), Color(0xFF8AB8FF)),
            ) {
                if (tracks.isNotEmpty()) { viewModel.playQueue(tracks.shuffled(), 0); onOpenNowPlaying() }
            }
        }
        full {
            HeroCard(
                "Favorites", if (favorites.isEmpty()) "Tap a heart to save songs" else "${favorites.size} songs",
                Icons.Filled.Favorite, listOf(Color(0xFF3B3F9E), Color(0xFF5B9DFF)),
                onClick = onOpenFavorites,
            )
        }
        if (playlists.isNotEmpty()) {
            full { GridHeader("Playlists") }
            items(playlists, key = { it.name }) { pl ->
                PlaylistCard(pl, pl.trackIds.firstNotNullOfOrNull { viewModel.repository.trackById(it) }) {
                    onOpenPlaylist(pl.name)
                }
            }
        }
        full { GridHeader("All songs") }
        if (tracks.isEmpty()) full { EmptyHint("Import a folder from the Library tab to get started.") }
        tracks.forEach { track ->
            full {
                TrackRow(
                    track = track,
                    isCurrent = track.id == ui.currentTrackId,
                    isPlaying = ui.isPlaying && track.id == ui.currentTrackId,
                    isFavorite = track.id in favorites,
                    onClick = { viewModel.playQueue(tracks, tracks.indexOf(track)) },
                    onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                    onMore = { onMore(track.id) },
                )
            }
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.88f))
        }
    }
}

@Composable
fun SectionHeader(title: String) = GridHeader(title)
