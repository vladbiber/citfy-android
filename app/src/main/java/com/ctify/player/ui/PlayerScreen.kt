package com.ctify.player.ui

import com.ctify.player.ui.skins.radio.RadioSkin
import com.ctify.player.ui.skins.radio.RadioSkinView
import com.ctify.player.ui.skins.radio.aspectRatio
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import com.ctify.player.ui.player.SleepSheet
import com.ctify.player.ui.player.sleepLeft
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ctify.player.CtifyApp
import com.ctify.player.ui.theme.isAppInDarkTheme
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.ui.theme.LiquidBackground
import com.ctify.player.ui.theme.ThemeSource
import com.ctify.player.ui.theme.accentBrush
import androidx.compose.ui.graphics.SolidColor
import com.ctify.player.player.PlayerHub
import com.ctify.player.ui.radio.LiveBadge
import com.ctify.player.radio.Station
import com.ctify.player.ui.theme.boosted
import com.ctify.player.ui.theme.rememberMusicPulse
import androidx.compose.runtime.State
import androidx.compose.ui.draw.drawBehind
import com.ctify.player.ui.visualizer.Visualizer
import com.ctify.player.ui.visualizer.VisualizerView
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import com.ctify.player.ui.youtube.isStreamed
import com.ctify.player.ui.youtube.toYtSong
import com.ctify.player.ui.youtube.ytVideoId
import com.ctify.player.youtube.download.DownloadState
import com.ctify.player.youtube.download.Downloads
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import com.ctify.player.lyrics.Lyrics
import com.ctify.player.lyrics.LyricsRepository
import com.ctify.player.ui.player.LyricsView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.produceState
import com.ctify.player.ui.radio.RadioUi
import com.ctify.player.ui.radio.rememberRadioUi
import com.ctify.player.ui.radio.stationRows
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ctify.player.player.fx.RETUNE_PRESETS
import kotlin.math.abs
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.ctify.player.data.Track
import com.ctify.player.player.sound.GenrePreset
import com.ctify.player.player.sound.SoundSettings
import com.ctify.player.ui.player.SkinPicker

private val Glass: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
private val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
private val InkDim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContent(onClose: () -> Unit, onOpenRadio: () -> Unit = {}) {
    val ctx = LocalContext.current
    val state by PlayerHub.playback.collectAsState()
    val track = state?.track
    var skin by remember { mutableStateOf(Skin.load(ctx)) }
    // a visualizer, when chosen, takes the stage instead of the skin
    var visualizer by remember { mutableStateOf(Visualizer.load(ctx)) }
    var radioSkin by remember { mutableStateOf(RadioSkin.load(ctx)) }
    var sheet by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    // null while looking; fetched for the Now Playing side only, so the button can grey out early
    val lyrics by produceState<Lyrics?>(null, track?.id, state?.station == null) {
        value = track?.let(LyricsRepository::cached)
        if (value == null && track != null && state?.station == null) value = LyricsRepository.get(track)
    }
    var panel by remember { mutableStateOf<ChipPanel?>(null) }
    val radio = (ctx.applicationContext as CtifyApp).radio
    val recents by radio.recents.collectAsState(initial = emptyList())
    val favStations by radio.favorites.collectAsState(initial = emptyList())
    // Radio picked with no station ever played or favorited: show the way to the Radio tab
    var pickStation by remember { mutableStateOf(false) }
    val onRadio = state?.station != null || pickStation
    LaunchedEffect(state?.station != null) { if (state?.station != null) pickStation = false }
    val dark = isAppInDarkTheme()
    val coverColors by Appearance.coverColors.collectAsState()
    val accent = rememberArtAccent(if (coverColors) track?.coverUri else null, MaterialTheme.colorScheme.primary)
        .let { if (dark) it else it.darkened() }
    val art = rememberArtPainter(track?.coverUri)
    val live = rememberLiveProgress()
    val source by Appearance.source.collectAsState()
    // two-color fade on the small spots; cover colors win when they are on
    val fill: Brush? = if (source == ThemeSource.DUO && !coverColors) accentBrush() else null

    val scope = rememberCoroutineScope()
    val drop = remember { Animatable(0f) }
    val closeAt = with(LocalDensity.current) { 120.dp.toPx() }

    // not inside a Surface, so set the text color here or bare Text turns black
    CompositionLocalProvider(LocalContentColor provides Ink) {
    Box(
        Modifier
            .fillMaxSize()
            // drag down where no skin, slider or scroll claims the touch: follows the finger, then the usual slide-out
            // shortcut: the compact layout's and the radio side's scroll take vertical drags, so there only the arrow and Back close
            .draggable(
                rememberDraggableState { d -> scope.launch { drop.snapTo((drop.value + d).coerceAtLeast(0f)) } },
                Orientation.Vertical,
                onDragStopped = { v -> if (drop.value > closeAt || v > 2_000f) onClose() else drop.animateTo(0f) },
            )
            .graphicsLayer { translationY = drop.value }
            .background(MaterialTheme.colorScheme.background),
    ) {
        val pulse = rememberMusicPulse(live.playing)
        Backdrop(track?.coverUri, accent, pulse)
        if (!coverColors) LiquidBackground(Modifier.fillMaxSize(), pulse)
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            // very short screens (or huge fonts) scroll; otherwise the stage takes what is left
            val compact = maxHeight < 560.dp
            val screenHeight = maxHeight
            val main: @Composable ColumnScope.() -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                TopPill(
                    onRadio, onClose,
                    onSource = { toRadio ->
                        if (toRadio && !onRadio) {
                            // the most recent station first, so next/previous walk the history
                            val list = recents.ifEmpty { favStations }
                            if (list.isEmpty()) pickStation = true else PlayerHub.playStations(list, 0)
                        } else if (!toRadio && onRadio) {
                            pickStation = false
                            if (state?.station != null) PlayerHub.resumeTracks()
                        }
                    },
                    onSound = { sheet = true },
                )
                if (!onRadio && track != null) {
                    Spacer(Modifier.width(8.dp))
                    LyricsButton(lyrics, showLyrics, accent) { showLyrics = it }
                }
                }
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .then(if (compact) Modifier.height(340.dp) else Modifier.weight(1f))
                        .padding(vertical = 6.dp)
                        // the stage belongs to the skins (scratch, shuttle, tap to play), so drags here never close the player
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                do {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                                } while (e.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (pickStation) FilledTonalButton(onClick = onOpenRadio) { Text("Pick a station") }
                    else {
                    val stageMax = maxWidth
                    val stageHeight = maxHeight
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        // lyrics on the left, the skin shrinks to the right
                        val lyricsOn = showLyrics && !onRadio && lyrics?.found == true
                        AnimatedVisibility(lyricsOn, Modifier.weight(1f), enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            lyrics?.let { LyricsView(it, live, Ink, InkDim, Modifier.fillMaxSize().padding(end = 8.dp)) }
                        }
                        val stageWidth by animateDpAsState(if (lyricsOn) stageMax * 0.42f else stageMax, label = "stage")
                        Box(Modifier.width(stageWidth), contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = visualizer ?: if (state?.station != null) radioSkin else skin,
                                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                                label = "skin",
                            ) { s ->
                                if (s is Visualizer) VisualizerView(s, art, accent, live.playing, still = false, side = minOf(stageWidth, stageHeight))
                                else if (s is Skin) SkinView(s, live, art, accent, track, skinSide(s, stageWidth, stageHeight), seek = state?.station == null, fill = fill)
                                else if (s is RadioSkin) state?.station?.let { st ->
                                    RadioSkinView(
                                        s, st.name, track?.title?.takeIf { it != st.name }, art, accent, live.playing, still = false,
                                        onTogglePlay = { PlayerHub.togglePlay() }, modifier = radioSkinSize(s, stageWidth, stageHeight),
                                    )
                                }
                            }
                        }
                    }
                    }
                }
                ControlCard(live, accent, seekBar = visualizer != null || skin != Skin.TAPE && skin != Skin.COVER, fill = fill, onSleep = { sleep = true })
                SoundStrip(accent, onSkin = { picker = true }, onPanel = { panel = it })
            }
            val pad = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            if (state?.station != null) {
                // radio: the player fills the first screen, the stations to switch to scroll up below it
                val ui = rememberRadioUi()
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "player") {
                        Column(
                            Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.height(screenHeight)).then(pad),
                            horizontalAlignment = Alignment.CenterHorizontally, content = main,
                        )
                    }
                    playerStations(favStations, recents, ui)
                }
            } else Column(
                Modifier
                    .fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .then(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = main,
            )
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
    panel?.let { p ->
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            ChipPanelSheet(p)
        }
    }
    if (picker) {
        val still = remember { LiveProgress(still = true) }
        SkinPicker<Enum<*>>(
            // the radio side picks from the radio skins; songs keep theirs
            tabs = listOf((if (state?.station != null) "Skins" to RadioSkin.entries else "Skins" to Skin.entries), "Visualizers" to Visualizer.entries),
            current = visualizer ?: if (state?.station != null) radioSkin else skin,
            label = { (it as? Skin)?.label ?: (it as? RadioSkin)?.label ?: (it as Visualizer).label },
            preview = { item, space ->
                if (item is Skin) SkinView(item, still, art, accent, track, skinSide(item, space.width, space.height), seek = false)
                else if (item is Visualizer) VisualizerView(item, art, accent, playing = false, still = true, side = minOf(space.width, space.height))
                else if (item is RadioSkin) RadioSkinView(
                    item, state?.station?.name ?: "ctify radio", null, art, accent, playing = false, still = true,
                    onTogglePlay = {}, modifier = radioSkinSize(item, space.width, space.height),
                )
            },
            onSelect = {
                when (it) {
                    is Skin -> { skin = it; Skin.save(ctx, it); visualizer = null }
                    is RadioSkin -> { radioSkin = it; RadioSkin.save(ctx, it); visualizer = null }
                    else -> visualizer = it as Visualizer
                }
                Visualizer.save(ctx, visualizer)
                picker = false
            },
            onDismiss = { picker = false },
        )
    }
    if (sleep) {
        ModalBottomSheet(
            onDismissRequest = { sleep = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            SleepSheet(live = state?.station != null, onDone = { sleep = false })
        }
    }
}

/** Favorite stations, then recent ones not already listed; a tap switches straight to it. */
private fun LazyListScope.playerStations(favorites: List<Station>, recents: List<Station>, ui: RadioUi) {
    val rest = recents.filter { r -> favorites.none { it.uuid == r.uuid } }
    if (favorites.isNotEmpty()) {
        item(key = "fav-title") { SectionHeader("Favorite stations", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
        stationRows(favorites, ui, keyPrefix = "pf")
    }
    if (rest.isNotEmpty()) {
        item(key = "recent-title") { SectionHeader("Recent stations", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
        stationRows(rest, ui, keyPrefix = "pr")
    }
    item(key = "end") { Spacer(Modifier.height(24.dp)) }
}

/** Pastel cover accents are too light on a light background; keep the hue, lower the lightness. */
private fun Color.darkened(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = hsl[2].coerceAtMost(0.40f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Width a skin gets in a [maxWidth] x [h] stage. */
internal fun skinSide(skin: Skin, maxWidth: Dp, h: Dp): Dp = when (skin) {
    Skin.TP7 -> minOf(maxWidth - 24.dp, h / 1.3f)
    Skin.TAPE -> minOf(maxWidth, (h - 48.dp) * 1.6f)
    Skin.COVER -> minOf(maxWidth, h - 56.dp)
    else -> minOf(maxWidth, h)
}.coerceIn(120.dp, 480.dp)

/** One skin drawn for [track]; [seek] adds the cover's own seek row. */
@Composable
internal fun SkinView(s: Skin, live: LiveProgress, art: Painter, accent: Color, track: Track?, side: Dp, seek: Boolean, fill: Brush? = null) {
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
                if (seek) SeekRow(live, accent, Modifier.width(side * 0.86f), fill)
            }
        }
    }
}

/** Blurred cover under a scrim; with a [pulse] the colors are boosted and swell with the bass. */
@Composable
private fun Backdrop(art: String?, accent: Color, pulse: State<Float>? = null) {
    val scrim = MaterialTheme.colorScheme.background
    val blurry = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glow = if (pulse != null) accent.boosted() else accent
    val artAlpha = if (blurry) 0.7f else 0.28f
    AsyncImage(
        model = art,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            // pulse is read in the layer and draw blocks only, so beats never recompose the player
            .graphicsLayer { val e = pulse?.value ?: 0f; scaleX = 1.25f + 0.08f * e; scaleY = scaleX }
            .then(if (blurry) Modifier.blur(70.dp) else Modifier)
            .graphicsLayer { alpha = if (pulse == null) artAlpha else (artAlpha * 1.25f + 0.15f * pulse.value).coerceAtMost(1f) },
    )
    Box(
        Modifier.fillMaxSize().drawBehind {
            val e = pulse?.value ?: 0f
            // the lower stops stay as they were, so the controls and text keep their contrast
            val top = if (pulse == null) 0.10f else 0.22f + 0.18f * e
            drawRect(Brush.verticalGradient(listOf(glow.copy(alpha = top), scrim.copy(alpha = 0.25f), scrim.copy(alpha = 0.7f))))
            if (e > 0f) drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.35f * e), Color.Transparent), Offset(size.width / 2, size.height * 0.35f), size.maxDimension * 0.6f),
                size.maxDimension * 0.6f, Offset(size.width / 2, size.height * 0.35f),
            )
        }
    )
}

/** Round lyrics switch beside the top pill; greyed out when the song has none. */
@Composable
private fun LyricsButton(lyrics: Lyrics?, on: Boolean, accent: Color, onToggle: (Boolean) -> Unit) {
    val found = lyrics?.found == true
    Surface(color = if (on) accent.copy(alpha = 0.3f) else Glass, shape = CircleShape, modifier = Modifier.size(48.dp)) {
        IconToggleButton(checked = on, onCheckedChange = onToggle, enabled = found) {
            Icon(
                Icons.Default.Lyrics,
                contentDescription = when {
                    lyrics == null -> "Lyrics, looking for them"
                    !found -> "Lyrics, none found for this song"
                    else -> "Lyrics"
                },
                tint = if (found) Ink else InkDim.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun TopPill(onRadio: Boolean, onClose: () -> Unit, onSource: (radio: Boolean) -> Unit, onSound: () -> Unit) {
    Surface(color = Glass, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player", tint = Ink)
            }
            Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                listOf(false to "Now Playing", true to "Radio").forEach { (radio, label) ->
                    val on = radio == onRadio
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .selectable(selected = on, role = Role.Tab) { onSource(radio) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) Ink else InkDim,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Ink.copy(alpha = 0.12f) else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
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

/** What the sound is doing right now; each chip opens only its own control. */
@Composable
private fun SoundStrip(accent: Color, onSkin: () -> Unit, onPanel: (ChipPanel) -> Unit) {
    val rate by PlayerHub.rate.collectAsState()
    val reverb by PlayerHub.reverb.collectAsState()
    val bass by PlayerHub.bassGain.collectAsState()
    val retune by PlayerHub.retuneIndex.collectAsState()
    val width by PlayerHub.width.collectAsState()
    val pan by PlayerHub.pan8d.collectAsState()
    val muffle by PlayerHub.muffle.collectAsState()
    val v by PlayerHub.vinyl.collectAsState()
    val rain by PlayerHub.rain.collectAsState()
    val reversed by PlayerHub.reverseOn.collectAsState()
    val preparing by PlayerHub.reversePreparing.collectAsState()
    val genre by SoundSettings.genreKey.collectAsState()
    val space = spaceSummary(width, pan, muffle)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripChip("Skin", Icons.Default.Palette, false, accent, description = "Player look", onClick = onSkin)
        StripChip(GenrePreset.labelOf(genre), null, genre != GenrePreset.BALANCED, accent, description = "Genre preset ${GenrePreset.labelOf(genre)}") {
            onPanel(ChipPanel.GENRE)
        }
        StripChip("Speed ${"%.2f".format(rate)}×", null, abs(rate - 1f) > 0.004f, accent) { onPanel(ChipPanel.SPEED) }
        StripChip("Reverb ${(reverb * 100).roundToInt()}%", null, reverb > 0.004f, accent) { onPanel(ChipPanel.REVERB) }
        StripChip(if (bass < 0.05f) "Bass off" else "Bass +${bass.roundToInt()} dB", null, bass >= 0.05f, accent) { onPanel(ChipPanel.BASS) }
        StripChip("${RETUNE_PRESETS[retune].hz} Hz", null, retune != 0, accent) { onPanel(ChipPanel.HZ) }
        StripChip(if (space == "off") "Space" else "Space · $space", null, space != "off", accent) { onPanel(ChipPanel.SPACE) }
        StripChip(
            if (preparing) "Reverse: decoding…" else "More", Icons.Default.MoreHoriz,
            v.on || rain != RainPreset.OFF || reversed, accent, description = "Rain, vinyl and reverse",
        ) { onPanel(ChipPanel.MORE) }
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) Ink else InkDim, maxLines = 1)
        }
    }
}

@Composable
private fun SeekRow(live: LiveProgress, accent: Color, modifier: Modifier, fill: Brush? = null) {
    Column(modifier) {
        PillSlider(
            value = live.fraction,
            onChange = { live.preview = (it * live.durationMs).toLong() },
            onDone = { live.seekTo((it * live.durationMs).toLong()) },
            accent = accent,
            label = "Position",
            stateText = "${formatMs(live.positionMs.toInt())} of ${formatMs(live.durationMs.toInt())}",
            marks = chapterMarks(live),
            brush = fill,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(live.positionMs.toInt()), style = MaterialTheme.typography.labelSmall, color = InkDim)
            Text(formatMs(live.durationMs.toInt()), style = MaterialTheme.typography.labelSmall, color = InkDim)
        }
    }
}

@Composable
private fun ControlCard(live: LiveProgress, accent: Color, seekBar: Boolean, fill: Brush?, onSleep: () -> Unit) {
    val state by PlayerHub.playback.collectAsState()
    val track = state?.track
    val station = state?.station
    val app = LocalContext.current.applicationContext as CtifyApp
    val repository = app.repository
    val favorites by repository.favorites.collectAsState()
    val stationFavs by app.radio.favoriteIds.collectAsState(initial = emptySet())
    val fav = if (station != null) station.uuid in stationFavs else track != null && track.id in favorites

    Surface(color = Glass, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "Nothing playing", color = Ink, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state?.radioStatus.takeIf { station != null } ?: track?.artist ?: "pick something in Library",
                        color = InkDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                if (station == null && track?.isStreamed() == true) DownloadButton(track, accent)
                val sleepLeft = sleepLeft()
                IconButton(onClick = onSleep) {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = sleepLeft?.let { "Sleep timer, pausing $it" } ?: "Sleep timer",
                        tint = if (sleepLeft != null) accent else Ink,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            run {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (station == null) Toggle(Icons.Default.Shuffle, "Shuffle", state?.shuffle == true, accent) { PlayerHub.toggleShuffle() }
                        if (station == null) IconButton(onClick = { PlayerHub.previous() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", tint = Ink)
                        }
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 48.dp)
                                .clip(CircleShape)
                                .background(fill ?: SolidColor(accent))
                                .clickable(role = Role.Button) { PlayerHub.togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state?.isPlaying == true) "Pause" else "Play", tint = if (fill != null) MaterialTheme.colorScheme.onPrimary else if (accent.luminance() > 0.4f) Color(0xFF1A1719) else Color.White,
                            )
                        }
                        if (station == null) IconButton(onClick = { PlayerHub.next() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = Ink)
                        }
                        val repeat = state?.repeat ?: "off"
                        if (station == null) Toggle(
                            if (repeat == "track") Icons.Default.RepeatOne else Icons.Default.Repeat,
                            if (repeat == "track") "Repeat one" else "Repeat", repeat != "off", accent,
                        ) { PlayerHub.cycleRepeat() }
                        if (station != null) {
                            LiveBadge(accent, Modifier.padding(start = 12.dp))
                            Text(station.name, style = MaterialTheme.typography.labelSmall, color = InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                    if (station != null) Spacer(Modifier.height(8.dp))
                    else if (seekBar) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                        Text(formatMs(live.positionMs.toInt()), style = MaterialTheme.typography.labelSmall, color = InkDim)
                        PillSlider(
                            value = live.fraction,
                            onChange = { live.preview = (it * live.durationMs).toLong() },
                            onDone = { live.seekTo((it * live.durationMs).toLong()) },
                            accent = accent,
                            label = "Position",
                            stateText = "${formatMs(live.positionMs.toInt())} of ${formatMs(live.durationMs.toInt())}",
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            marks = chapterMarks(live),
                            brush = fill,
                        )
                        Text(formatMs(live.durationMs.toInt()), style = MaterialTheme.typography.labelSmall, color = InkDim)
                    }
                    if (station == null) ChapterRow(live, Ink)
                    if (station == null) {
                        var upNext by rememberSaveable { mutableStateOf(false) }
                        IconButton(onClick = { upNext = !upNext }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(
                                if (upNext) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (upNext) "Hide up next" else "Show up next", tint = InkDim,
                            )
                        }
                        AnimatedVisibility(upNext, enter = expandVertically(), exit = shrinkVertically()) {
                            Column(Modifier.padding(end = 10.dp)) { QueueSheet(maxHeight = 260.dp) }
                        }
                    }
                }
            }
        }
    }
}

/** Saves the streamed song: progress while it runs, a check when it is on the phone; a tap while pending cancels. */
@Composable
private fun DownloadButton(track: Track, accent: Color) {
    val vid = track.ytVideoId() ?: return
    val scope = rememberCoroutineScope()
    val flow = remember { Downloads.all() }
    val all by flow.collectAsState(initial = emptyList())
    val d = all.firstOrNull { it.videoId == vid }
    // isMetered() can't be observed, so look again every few seconds while a download waits
    val blocked by produceState<String?>(null, d?.state) {
        while (d?.state == DownloadState.QUEUED) {
            value = NetworkGate.blockReason(Feature.YOUTUBE, Traffic.DOWNLOAD)
            delay(2_000)
        }
    }
    val pending = d?.state == DownloadState.QUEUED || d?.state == DownloadState.RUNNING
    val done = d?.state == DownloadState.DONE || d?.state == DownloadState.SKIPPED
    val label = when {
        done -> "Downloaded"
        d?.state == DownloadState.RUNNING && d.bytesTotal > 0 -> "Downloading, ${d.bytesDone * 100 / d.bytesTotal}%, tap to cancel"
        d?.state == DownloadState.RUNNING -> "Downloading, tap to cancel"
        pending && blocked != null && NetworkGate.blockReason(Feature.YOUTUBE) == null -> "Waiting for Wi-Fi, tap to cancel"
        pending -> "Waiting to download, tap to cancel"
        d?.state == DownloadState.FAILED -> "Download failed, tap to retry"
        else -> "Download"
    }
    IconButton(
        enabled = !done,
        onClick = { if (pending) Downloads.cancel(vid) else scope.launch { Downloads.enqueue(listOf(track.toYtSong())) } },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                d?.state == DownloadState.RUNNING && d.bytesTotal > 0 -> CircularProgressIndicator(
                    progress = { d.bytesDone.toFloat() / d.bytesTotal }, modifier = Modifier.size(26.dp), color = accent, strokeWidth = 2.dp,
                )
                d?.state == DownloadState.RUNNING -> CircularProgressIndicator(Modifier.size(26.dp), color = accent, strokeWidth = 2.dp)
            }
            Icon(
                when {
                    done -> Icons.Default.DownloadDone
                    d?.state == DownloadState.QUEUED -> Icons.Default.Schedule
                    d?.state == DownloadState.FAILED -> Icons.Default.ErrorOutline
                    else -> Icons.Default.Download
                },
                contentDescription = label,
                tint = if (done || pending) accent else Ink,
                modifier = Modifier.size(if (d?.state == DownloadState.RUNNING) 16.dp else 24.dp),
            )
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

/** Fits a radio skin into the stage at its own width/height ratio. */
private fun radioSkinSize(s: RadioSkin, maxW: Dp, maxH: Dp): Modifier {
    val r = s.aspectRatio()
    val w = minOf(maxW, maxH * r)
    return Modifier.width(w).aspectRatio(r)
}
