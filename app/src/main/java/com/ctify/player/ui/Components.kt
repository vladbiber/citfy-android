package com.ctify.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.ctify.player.data.Track
import com.ctify.player.ui.theme.SpotifyGreen
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private fun gradientFor(seed: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF6D28D9), Color(0xFF12B981)),
        listOf(Color(0xFF0EA5E9), Color(0xFF1E3A8A)),
        listOf(Color(0xFFF59E0B), Color(0xFFEC4899)),
        listOf(Color(0xFFEF4444), Color(0xFF7C3AED)),
    )
    val p = palettes[(seed.hashCode() and 0x7fffffff) % palettes.size]
    return Brush.linearGradient(p)
}

@Composable
fun AlbumArt(track: Track?, size: Dp, corner: Dp = 6.dp, modifier: Modifier = Modifier.size(size)) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(gradientFor(track?.album ?: "ctify")),
        contentAlignment = Alignment.Center,
    ) {
        if (track?.coverUri != null) {
            SubcomposeAsyncImage(
                model = track.coverUri,
                contentDescription = track.album,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { },
                error = {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size / 3))
                },
            )
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size / 3))
        }
    }
}

/** The ctify logo: a green gradient disc with a music note, used centered on the Home screen. */
@Composable
fun CtifyLogo(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1ED760), Color(0xFF14833B)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote, "ctify logo",
            tint = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.size(size / 2),
        )
    }
}

/** Playlist artwork: the custom cover if one was set, else the first track's art. */
@Composable
fun PlaylistArt(
    playlist: com.ctify.player.data.Playlist,
    firstTrack: Track?,
    size: Dp,
    corner: Dp = 6.dp,
    modifier: Modifier = Modifier.size(size),
) {
    if (playlist.coverUri != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(corner))
                .background(gradientFor(playlist.name)),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = playlist.coverUri,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { },
                error = {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size / 3))
                },
            )
        }
    } else {
        AlbumArt(firstTrack, size, corner, modifier)
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val titleColor by animateColorAsState(if (isCurrent) SpotifyGreen else TextPrimary, label = "title")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AlbumArt(track, 52.dp)
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, tint = SpotifyGreen, modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(track.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(track.durationMs), color = TextMuted, fontSize = 12.sp)
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                "favorite",
                tint = if (isFavorite) SpotifyGreen else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.RemoveCircleOutline, "remove from playlist", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, "more", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlbumArt(track, 44.dp, corner = 4.dp)
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "play/pause", tint = TextPrimary, modifier = Modifier.size(30.dp),
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = SpotifyGreen,
            trackColor = Color.White.copy(alpha = 0.15f),
        )
    }
}
