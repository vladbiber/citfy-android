package com.ctify.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.data.Track
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.theme.SpotifyGreen
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onMore: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = contentPadding()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CtifyLogo(size = 64.dp)
                Spacer(Modifier.height(8.dp))
                Text("ctify", color = SpotifyGreen, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Text("Your music, your way", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { if (tracks.isNotEmpty()) { viewModel.playQueue(tracks.shuffled(), 0); onOpenNowPlaying() } },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = androidx.compose.ui.graphics.Color.Black),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Shuffle, "shuffle", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            SectionHeader("Playlists")
            if (playlists.isEmpty()) {
                Text(
                    "No playlists yet. Create one from the Library tab.",
                    color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(playlists, key = { it.name }) { pl ->
                        PlaylistCard(
                            playlist = pl,
                            firstTrack = pl.trackIds.firstNotNullOfOrNull { viewModel.repository.trackById(it) },
                            onClick = { onOpenPlaylist(pl.name) },
                        )
                    }
                }
            }
        }

        item { SectionHeader("All songs") }

        items(tracks, key = { it.id }) { track ->
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

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 12.dp),
    )
}

@Composable
private fun PlaylistCard(playlist: com.ctify.player.data.Playlist, firstTrack: Track?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
    ) {
        PlaylistArt(playlist, firstTrack, 150.dp, corner = 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackIds.size} songs", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
