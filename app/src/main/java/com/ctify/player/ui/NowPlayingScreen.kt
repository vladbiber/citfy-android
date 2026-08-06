package com.ctify.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.theme.BgBlack
import com.ctify.player.ui.theme.SpotifyGreen
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

@Composable
fun NowPlayingScreen(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val favorites by viewModel.repository.favorites.collectAsStateWithLifecycle()
    val track = viewModel.repository.trackById(ui.currentTrackId) ?: return

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    val duration = ui.durationMs.coerceAtLeast(1L)
    val livePosition = if (scrubbing) (scrubValue * duration).toLong() else ui.positionMs
    val sliderValue = if (scrubbing) scrubValue else (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val isFav = track.id in favorites

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2540), BgBlack, BgBlack)))
            // Absorb taps on empty areas so they don't fall through to the screen behind this
            // overlay. A plain clickable (no ripple) does this without fighting the child
            // controls for the gesture — the previous global pointer-consumer was swallowing
            // the first tap on the transport buttons.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, "close", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(30.dp))
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 8.dp),
            ) {
                AlbumArt(track, 320.dp, corner = 12.dp, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { viewModel.toggleFavorite(track.id) }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "favorite", tint = if (isFav) SpotifyGreen else TextSecondary, modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = { scrubbing = true; scrubValue = it },
                onValueChangeFinished = { viewModel.seekToFraction(scrubValue); scrubbing = false },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = SpotifyGreen,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(livePosition), color = TextSecondary, fontSize = 11.sp)
                Text(formatTime(ui.durationMs), color = TextSecondary, fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(Icons.Filled.Shuffle, "shuffle", tint = if (ui.shuffle) SpotifyGreen else TextSecondary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = viewModel::previous) {
                    Icon(Icons.Filled.SkipPrevious, "previous", tint = TextPrimary, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(SpotifyGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    // Make the whole 72dp circle the touch target, not just the inner
                    // ~48dp of a default IconButton (taps on the green ring were dead).
                    IconButton(onClick = viewModel::togglePlay, modifier = Modifier.size(72.dp)) {
                        Icon(
                            if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "play/pause", tint = Color.Black, modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = viewModel::next) {
                    Icon(Icons.Filled.SkipNext, "next", tint = TextPrimary, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = viewModel::cycleRepeat) {
                    Icon(
                        if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "repeat",
                        tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VolumeUp, "volume", tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = ui.volume,
                    onValueChange = viewModel::setVolume,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
            }
        }
    }
}
