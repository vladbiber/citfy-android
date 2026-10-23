package com.ctify.player.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ctify.player.CtifyApp
import com.ctify.player.ui.theme.isAppInDarkTheme
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.player.PlayerHub
import com.ctify.player.ui.radio.LiveBadge
import com.ctify.player.ui.radio.RewindRow
import com.ctify.player.ui.skins.CdSkin
import com.ctify.player.ui.skins.CoverSkin
import com.ctify.player.ui.skins.LiveProgress
import com.ctify.player.ui.skins.PillSlider
import com.ctify.player.ui.skins.Skin
import com.ctify.player.ui.skins.TapeSkin
import com.ctify.player.ui.skins.Tp7Skin
import com.ctify.player.ui.skins.VinylSkin
import com.ctify.player.ui.skins.rememberArtAccent
import com.ctify.player.ui.skins.rememberArtPainter
import com.ctify.player.ui.skins.rememberLiveProgress
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.WaterDrop
import com.ctify.player.player.fx.RainPreset
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ctify.player.player.fx.RETUNE_PRESETS
import kotlin.math.abs

private val Glass: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
private val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
private val InkDim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContent(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val state by PlayerHub.playback.collectAsState()
    val track = state?.track
    var skin by remember { mutableStateOf(Skin.load(ctx)) }
    var sheet by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    val dark = isAppInDarkTheme()
    val coverColors by Appearance.coverColors.collectAsState()
    val accent = rememberArtAccent(if (coverColors) track?.coverUri else null, MaterialTheme.colorScheme.primary)
        .let { if (dark) it else it.darkened() }
    val art = rememberArtPainter(track?.coverUri)
    val live = rememberLiveProgress()

    // not inside a Surface, so set the text color here or bare Text turns black
    CompositionLocalProvider(LocalContentColor provides Ink) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Backdrop(track?.coverUri, accent)
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            // very short screens (or huge fonts) scroll; otherwise the stage takes what is left
            val compact = maxHeight < 560.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopPill(skin, onClose, onSkin = { skin = it; Skin.save(ctx, it) }, onSound = { sheet = true })
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .then(if (compact) Modifier.height(340.dp) else Modifier.weight(1f))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val h = maxHeight
                    val side = when (skin) {
                        Skin.TP7 -> minOf(maxWidth - 24.dp, h / 1.3f)
                        Skin.TAPE -> minOf(maxWidth, (h - 48.dp) * 1.6f)
                        Skin.COVER -> minOf(maxWidth, h - 56.dp)
                        else -> minOf(maxWidth, h)
                    }.coerceIn(120.dp, 480.dp)
                    AnimatedContent(
                        targetState = skin,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                        label = "skin",
                    ) { s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val m = Modifier.width(side)
                            val title = track?.title ?: "Nothing playing"
                            when (s) {
                                Skin.VINYL -> VinylSkin(live, art, accent, m, title)
                                Skin.CD -> CdSkin(live, art, accent, m, title)
                                Skin.TAPE -> TapeSkin(live, art, accent, track?.album ?: track?.title ?: "ctify", m)
                                Skin.TP7 -> Tp7Skin(live, art, accent, track?.let { "${it.title} · ${it.artist}" } ?: "ctify", m)
                                Skin.COVER -> {
                                    CoverSkin(live, art, accent, m)
                                    if (state?.station == null) SeekRow(live, accent, Modifier.width(side * 0.86f))
                                }
                            }
                        }
                    }
                }
                ControlCard(live, accent, onQueue = { queue = true }, onSound = { sheet = true })
                SoundStrip(accent, onOpen = { sheet = true })
            }
        }
    }
    }

    if (sheet) {
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            SoundSheet()
        }
    }
    if (queue) {
        ModalBottomSheet(
            onDismissRequest = { queue = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            QueueSheet()
        }
    }
}

/** Pastel cover accents are too light on a light background; keep the hue, lower the lightness. */
private fun Color.darkened(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = hsl[2].coerceAtMost(0.40f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
private fun Backdrop(art: String?, accent: Color) {
    val scrim = MaterialTheme.colorScheme.background
    val blurry = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    AsyncImage(
        model = art,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
            .then(if (blurry) Modifier.blur(70.dp) else Modifier)
            .alpha(if (blurry) 0.7f else 0.28f),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.10f), scrim.copy(alpha = 0.25f), scrim.copy(alpha = 0.7f))
            )
        )
    )
}

@Composable
private fun TopPill(skin: Skin, onClose: () -> Unit, onSkin: (Skin) -> Unit, onSound: () -> Unit) {
    Surface(color = Glass, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player", tint = Ink)
            }
            Row(
                Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skin.entries.forEach { s ->
                    val on = s == skin
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .selectable(selected = on, role = Role.Tab) { onSkin(s) }
                            .semantics { contentDescription = "${s.label} look" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.label,
                            fontSize = 13.sp,
                            color = if (on) Ink else InkDim,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Ink.copy(alpha = 0.12f) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onSound, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Sound settings", tint = Ink)
            }
        }
    }
}

/** What the sound is doing right now, always one tap away from the full settings. */
@Composable
private fun SoundStrip(accent: Color, onOpen: () -> Unit) {
    val rate by PlayerHub.rate.collectAsState()
    val reverb by PlayerHub.reverb.collectAsState()
    val bass by PlayerHub.bassGain.collectAsState()
    val retune by PlayerHub.retuneIndex.collectAsState()
    val pan by PlayerHub.pan8d.collectAsState()
    val muffle by PlayerHub.muffle.collectAsState()
    val v by PlayerHub.vinyl.collectAsState()
    val rain by PlayerHub.rain.collectAsState()
    val reversed by PlayerHub.reverseOn.collectAsState()
    val preparing by PlayerHub.reversePreparing.collectAsState()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripChip("Sound", Icons.Default.Tune, true, accent, description = "Sound settings", onClick = onOpen)
        StripChip("Vinyl", Icons.Default.Album, v.on, accent, toggle = true) { PlayerHub.setVinyl(v.copy(on = !v.on)) }
        StripChip(
            if (rain == RainPreset.OFF) "Rain" else rain.label, Icons.Default.WaterDrop,
            rain != RainPreset.OFF, accent, toggle = true, description = "Rain sounds",
        ) { PlayerHub.toggleRain() }
        StripChip(
            if (preparing) "Reverse: decoding…" else "Reverse",
            Icons.Default.FastRewind, reversed, accent, toggle = true,
        ) { PlayerHub.toggleReverse() }
        StripChip("Slow ${"%.2f".format(rate)}×", null, abs(rate - 1f) > 0.004f, accent, onClick = onOpen)
        StripChip("Reverb ${(reverb * 100).roundToInt()}%", null, reverb > 0.004f, accent, onClick = onOpen)
        StripChip(if (bass < 0.05f) "Bass off" else "Bass +${bass.roundToInt()} dB", null, bass >= 0.05f, accent, onClick = onOpen)
        StripChip("${RETUNE_PRESETS[retune].hz} Hz", null, retune != 0, accent, onClick = onOpen)
        if (pan > 0.01f) StripChip("8D", null, true, accent, onClick = onOpen)
        if (muffle > 0.01f) StripChip("Another room", null, true, accent, onClick = onOpen)
    }
}

@Composable
private fun StripChip(
    label: String, icon: ImageVector?, on: Boolean, accent: Color,
    toggle: Boolean = false, description: String? = null, onClick: () -> Unit,
) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                if (toggle) stateDescription = if (on) "on" else "off"
                description?.let { contentDescription = it }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (on) accent.copy(alpha = 0.24f) else Glass)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (on) accent else Ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, fontSize = 12.sp, color = if (on) Ink else InkDim, maxLines = 1)
        }
    }
}

@Composable
private fun SeekRow(live: LiveProgress, accent: Color, modifier: Modifier) {
    Column(modifier) {
        PillSlider(
            value = live.fraction,
            onChange = { live.preview = (it * live.durationMs).toLong() },
            onDone = { live.seekTo((it * live.durationMs).toLong()) },
            accent = accent,
            label = "Position",
            stateText = "${formatMs(live.positionMs.toInt())} of ${formatMs(live.durationMs.toInt())}",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(live.positionMs.toInt()), fontSize = 11.sp, color = InkDim)
            Text(formatMs(live.durationMs.toInt()), fontSize = 11.sp, color = InkDim)
        }
    }
}

@Composable
private fun ControlCard(live: LiveProgress, accent: Color, onQueue: () -> Unit, onSound: () -> Unit) {
    val state by PlayerHub.playback.collectAsState()
    val track = state?.track
    val station = state?.station
    val app = LocalContext.current.applicationContext as CtifyApp
    val repository = app.repository
    val favorites by repository.favorites.collectAsState()
    val stationFavs by app.radio.favoriteIds.collectAsState(initial = emptySet())
    val fav = if (station != null) station.uuid in stationFavs else track != null && track.id in favorites

    Surface(color = Glass, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "Nothing playing", color = Ink, fontSize = 17.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state?.radioStatus.takeIf { station != null } ?: track?.artist ?: "pick something in Library",
                        color = InkDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    if (station != null) app.radio.toggleFavorite(station, fav)
                    else track?.let { repository.toggleFavorite(it.id) }
                }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
                        tint = if (fav) accent else Ink,
                    )
                }
                if (station == null) IconButton(onClick = onQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Up next", tint = Ink)
                }
            }
            Spacer(Modifier.height(4.dp))
            run {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (station == null) Toggle(Icons.Default.Shuffle, "Shuffle", state?.shuffle == true, accent) { PlayerHub.toggleShuffle() }
                        IconButton(onClick = { PlayerHub.previous() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = if (station != null) "Previous station" else "Previous track", tint = Ink)
                        }
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 48.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable(role = Role.Button) { PlayerHub.togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state?.isPlaying == true) "Pause" else "Play", tint = if (accent.luminance() > 0.4f) Color(0xFF1A1719) else Color.White,
                            )
                        }
                        IconButton(onClick = { PlayerHub.next() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = if (station != null) "Next station" else "Next track", tint = Ink)
                        }
                        val repeat = state?.repeat ?: "off"
                        if (station == null) Toggle(
                            if (repeat == "track") Icons.Default.RepeatOne else Icons.Default.Repeat,
                            if (repeat == "track") "Repeat one" else "Repeat", repeat != "off", accent,
                        ) { PlayerHub.cycleRepeat() }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onSound, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "Sound settings", tint = Ink)
                        }
                    }
                    val bufferStart = state?.bufferStartMs
                    val edge = state?.durationMs ?: 0L
                    if (station != null && bufferStart != null && edge > bufferStart + 5_000) {
                        RewindRow(live, bufferStart, edge, accent)
                    } else if (station != null) {
                        Row(Modifier.heightIn(min = 32.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            LiveBadge(accent)
                            Text(station.name, fontSize = 11.sp, color = InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                        }
                    } else Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                        Text(formatMs(live.positionMs.toInt()), fontSize = 11.sp, color = InkDim)
                        PillSlider(
                            value = live.fraction,
                            onChange = { live.preview = (it * live.durationMs).toLong() },
                            onDone = { live.seekTo((it * live.durationMs).toLong()) },
                            accent = accent,
                            label = "Position",
                            stateText = "${formatMs(live.positionMs.toInt())} of ${formatMs(live.durationMs.toInt())}",
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        Text(formatMs(live.durationMs.toInt()), fontSize = 11.sp, color = InkDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (on) accent.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (on) accent else Ink, modifier = Modifier.size(20.dp))
        }
    }
}
