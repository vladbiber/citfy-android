package com.ctify.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.ctify.player.data.Track
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

fun formatMs(ms: Int): String = formatTime(ms.toLong())

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private fun gradientFor(seed: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF1E3A8A), Color(0xFF5B9DFF)),
        listOf(Color(0xFF0EA5E9), Color(0xFF1E3A8A)),
        listOf(Color(0xFF3B5BDB), Color(0xFF9775FA)),
        listOf(Color(0xFF0B7285), Color(0xFF74C0FC)),
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

/** The ctify logo: a blue gradient disc with a music note, used centered on the Home screen. */
@Composable
fun CtifyLogo(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF8AB8FF), Color(0xFF2B5FD9)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote, "ctify logo",
            tint = Color.White,
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
    onToggleFavorite: (() -> Unit)?,
    onMore: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val titleColor by animateColorAsState(if (isCurrent) Accent else TextPrimary, label = "title")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) Accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClickLabel = "play", onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${track.title}, by ${track.artist}, ${formatTime(track.durationMs)}"
                if (isCurrent) stateDescription = if (isPlaying) "now playing" else "paused"
            }.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    AlbumArt(track, 50.dp, corner = 10.dp)
                    if (isCurrent) {
                        Box(
                            Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Pause,
                                null, tint = Accent, modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(track.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatTime(track.durationMs), color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (isFavorite) "Remove ${track.title} from favorites" else "Add ${track.title} to favorites",
                    tint = if (isFavorite) Accent else TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.RemoveCircleOutline, "Remove ${track.title} from playlist", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, "More options for ${track.title}", tint = TextSecondary, modifier = Modifier.size(20.dp))
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
    onNext: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "open player", onClick = onClick)
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlbumArt(track, 44.dp, corner = 10.dp)
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(track.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play", tint = TextPrimary, modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, "Next track", tint = TextPrimary)
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp),
            color = Accent,
            trackColor = TextPrimary.copy(alpha = 0.15f),
        )
        Spacer(Modifier.height(4.dp))
    }
}
