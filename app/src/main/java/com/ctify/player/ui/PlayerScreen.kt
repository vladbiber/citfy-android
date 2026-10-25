package com.ctify.player.ui

import com.ctify.player.ui.mood.moodText
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.ctify.player.ui.skins.stageSemantics

import androidx.compose.material.icons.automirrored.filled.QueueMusic

import androidx.compose.material.icons.automirrored.filled.PlaylistAdd

import com.ctify.player.ui.skins.radio.RadioSkin
import com.ctify.player.ui.skins.radio.RadioSkinView
import com.ctify.player.ui.skins.radio.aspectRatio
import androidx.compose.foundation.layout.aspectRatio
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.material.icons.filled.MoreVert
import com.ctify.player.ui.player.SongPlayerMenu
import com.ctify.player.ui.player.StationPlayerMenu
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
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
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.ui.theme.LiquidBackground
import com.ctify.player.ui.theme.ThemeSource
import com.ctify.player.ui.theme.accentBrush
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
import androidx.compose.runtime.produceState
import com.ctify.player.ui.radio.RadioUi
import com.ctify.player.ui.radio.rememberRadioUi
import com.ctify.player.ui.radio.stationRows
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import com.ctify.player.ui.skins.CdSkin
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

private val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
private val InkDim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContent(onClose: () -> Unit, onOpenRadio: () -> Unit = {}, onOpenPlaylist: (String) -> Unit = {}) {
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
    var menu by remember { mutableStateOf(false) }
    // null while looking; fetched for the Now Playing side only, so the button can grey out early
    val lyrics by produceState<Lyrics?>(null, track?.id, state?.station == null) {
        value = track?.let(LyricsRepository::cached)
        if (value == null && track != null && state?.station == null) value = LyricsRepository.get(track)
    }
    val radio = (ctx.applicationContext as CtifyApp).radio
    val recents by radio.recents.collectAsState(initial = emptyList())
    val favStations by radio.favorites.collectAsState(initial = emptyList())
    // Radio picked with no station ever played or favorited: show the way to the Radio tab
    var pickStation by remember { mutableStateOf(false) }
    val onRadio = state?.station != null || pickStation
    LaunchedEffect(state?.station != null) { if (state?.station != null) pickStation = false }
    val coverColors by Appearance.coverColors.collectAsState()
    val accent = rememberArtAccent(if (coverColors) track?.coverUri else null, MaterialTheme.colorScheme.primary)
    val art = rememberArtPainter(track?.coverUri)
    val live = rememberLiveProgress()
    val source by Appearance.source.collectAsState()
    // two-color fade on the small spots; cover colors win when they are on
    val fill: Brush? = if (source == ThemeSource.DUO && !coverColors) accentBrush() else null

    val scope = rememberCoroutineScope()
    val drop = remember { Animatable(0f) }
    val closeAt = with(LocalDensity.current) { 120.dp.toPx() }

    // not inside a Surface, so set the text color here or bare Text turns black
    // lyrics read best on plain black: drop the cover backdrop and draw the player in light-on-black colors
    val lyricsMode = showLyrics && state?.station == null && lyrics?.found == true
    val base = MaterialTheme.colorScheme
    val playerScheme = if (!lyricsMode) base else base.copy(
        background = Color.Black, onBackground = Color.White, onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        surfaceContainerHigh = base.surfaceContainerHigh,
    )
    MaterialTheme(colorScheme = playerScheme) {
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
        AnimatedVisibility(!lyricsMode, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Backdrop(track?.coverUri, accent, pulse)
                if (!coverColors) LiquidBackground(Modifier.fillMaxSize(), pulse)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            // very short screens (or huge fonts) scroll; otherwise the stage takes what is left
            val compact = maxHeight < 560.dp
            val screenHeight = maxHeight
            val main: @Composable ColumnScope.() -> Unit = {
                PlayerTopBar(
                    onRadio, onClose = onClose, onMenu = { menu = true },
                    canLyrics = !onRadio && track != null, lyrics = lyrics, showLyrics = showLyrics, accent = accent,
                    onLyrics = { showLyrics = it },
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
                )
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .then(if (compact) Modifier.height(340.dp) else Modifier.weight(1f))
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
                    if (pickStation) CtButton(onClick = onOpenRadio) { Text("Pick a station") }
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
                        Box(Modifier.width(stageWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            val visibility = LocalPlayerVisibility.current
                            AnimatedContent(
                                targetState = visualizer ?: if (state?.station != null) radioSkin else skin,
                                // fills the stage, so a smaller or larger skin never moves what is around it
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                                transitionSpec = {
                                    val fade = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                    (fadeIn(fade) + scaleIn(fade, initialScale = 0.96f)) togetherWith
                                        (fadeOut(fade) + scaleOut(fade, targetScale = 1.02f)) using SizeTransform(clip = false)
                                },
                                label = "skin",
                            ) { s ->
                                val shared = Modifier.sharedCover(visibility)
                                if (s is Visualizer) Box(shared) { VisualizerView(s, art, live.playing, still = false, side = minOf(stageWidth, stageHeight)) }
                                else if (s is Skin) SkinView(s, live, art, accent, track, skinSide(s, stageWidth, stageHeight), seek = state?.station == null, fill = fill, modifier = shared)
                                else if (s is RadioSkin) state?.station?.let { st ->
                                    RadioSkinView(
                                        s, st.name, track?.title?.takeIf { it != st.name }, art, accent, live.playing, still = false,
                                        onTogglePlay = { PlayerHub.togglePlay() }, modifier = shared.then(radioSkinSize(s, stageWidth, stageHeight)),
                                    )
                                }
                            }
                        }
                    }
                    }
                }
                NowPlayingDetails(
                    live, accent, seekBar = visualizer != null || skin != Skin.TAPE && skin != Skin.COVER, fill = fill,
                    onSleep = { sleep = true }, onSound = { sheet = true },
                )
            }
            val pad = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            // one rhythm between top bar, stage and controls
            val rhythm = Arrangement.spacedBy(12.dp)
            if (state?.station != null) {
                // radio: the player fills the first screen, the stations to switch to scroll up below it
                val ui = rememberRadioUi()
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "player") {
                        Column(
                            Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.height(screenHeight)).then(pad),
                            verticalArrangement = rhythm, horizontalAlignment = Alignment.CenterHorizontally, content = main,
                        )
                    }
                    playerStations(favStations, recents, ui)
                }
            } else Column(
                Modifier
                    .fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .then(pad),
                verticalArrangement = rhythm,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = main,
            )
        }
    }
    }
    }

    if (sheet) {
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            // every sound setting is a long scroll, so it opens tall
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            SoundSheet(radio = state?.station != null, onLook = { sheet = false; picker = true })
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
                else if (item is Visualizer) VisualizerView(item, art, playing = false, still = true, side = minOf(space.width, space.height))
                else if (item is RadioSkin) RadioSkinView(
                    item, state?.station?.name ?: "guava radio", null, art, accent, playing = false, still = true,
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
    if (menu) {
        val station = state?.station
        val onLook = { picker = true }
        val onSleep = { sleep = true }
        if (station != null) StationPlayerMenu(station, onDismiss = { menu = false }, onLook = onLook, onSleep = onSleep)
        else track?.let {
            SongPlayerMenu(it, onOpenPlaylist = { name -> onClose(); onOpenPlaylist(name) }, onDismiss = { menu = false }, onLook = onLook, onSleep = onSleep)
        } ?: run { menu = false }
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

/** One skin drawn for [track]; [seek] adds the cover's own seek row; [modifier] goes on the skin itself. */
@Composable
internal fun SkinView(s: Skin, live: LiveProgress, art: Painter, accent: Color, track: Track?, side: Dp, seek: Boolean, fill: Brush? = null, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val m = modifier.width(side)
        val title = track?.title ?: "Nothing playing"
        when (s) {
            Skin.VINYL -> VinylSkin(live, art, accent, m, title)
            Skin.CD -> CdSkin(live, art, accent, m, title)
            Skin.TAPE -> TapeSkin(live, art, accent, track?.album ?: track?.title ?: "guava", m)
            Skin.TP7 -> Tp7Skin(live, art, accent, track?.let { "${it.title} · ${it.artist}" } ?: "guava", m)
            Skin.COVER -> {
                PlainCover(live, art, m)
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

/** Close, the Now Playing / Radio switch, lyrics and the menu: the player's only top row. */
@Composable
private fun PlayerTopBar(
    onRadio: Boolean, onClose: () -> Unit, onMenu: () -> Unit,
    canLyrics: Boolean, lyrics: Lyrics?, showLyrics: Boolean, accent: Color, onLyrics: (Boolean) -> Unit,
    onSource: (radio: Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CtIconButton(Icons.Default.KeyboardArrowDown, "Close player", onClose, tint = Ink)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { SourceSwitch(onRadio, onSource) }
        // the slot stays when there are no lyrics, so the switch never shifts
        if (canLyrics) LyricsToggle(lyrics, showLyrics, accent, onLyrics) else Spacer(Modifier.size(48.dp))
        CtIconButton(Icons.Default.MoreVert, if (onRadio) "Station menu" else "Song menu", onMenu, tint = Ink)
    }
}

/** Lyrics on or off; greyed out while looking or when the song has none. */
@Composable
private fun LyricsToggle(lyrics: Lyrics?, on: Boolean, accent: Color, onToggle: (Boolean) -> Unit) {
    val found = lyrics?.found == true
    CtToggleIconButton(
        checked = on, onCheckedChange = onToggle, icon = Icons.Default.Lyrics, accent = accent, enabled = found,
        contentDescription = when {
            lyrics == null -> "Lyrics, looking for them"
            !found -> "Lyrics, none found for this song"
            else -> "Lyrics"
        },
    )
}

/** Two segments in one pill; the chosen one is filled. */
@Composable
private fun SourceSwitch(onRadio: Boolean, onSource: (radio: Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(c.surfaceContainerHigh).padding(4.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false to "Now Playing", true to "Radio").forEach { (radio, label) ->
            val on = radio == onRadio
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (on) c.secondaryContainer else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSource(radio) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    moodText(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false,
                    color = if (on) c.onSecondaryContainer else InkDim,
                )
            }
        }
    }
}

/** The album art itself in an expressive cookie shape, for the plain Cover look. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlainCover(live: LiveProgress, art: Painter, modifier: Modifier) {
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Box(modifier.aspectRatio(1f).stageSemantics("Album cover", live), contentAlignment = Alignment.Center) {
        Image(
            art, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .shadow(16.dp, shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
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
            wave = rememberWave(live.playing && live.preview == null),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(live.positionMs.toInt()), style = MaterialTheme.typography.labelSmall, color = InkDim)
            Text(formatMs(live.durationMs.toInt()), style = MaterialTheme.typography.labelSmall, color = InkDim)
        }
    }
}

/** Title and artist set big, then seek, transport and the two sheet buttons; no card behind them. */
@Composable
private fun NowPlayingDetails(live: LiveProgress, accent: Color, seekBar: Boolean, fill: Brush?, onSleep: () -> Unit, onSound: () -> Unit) {
    val state by PlayerHub.playback.collectAsState()
    val track = state?.track
    val station = state?.station
    val app = LocalContext.current.applicationContext as CtifyApp
    val repository = app.repository
    val favorites by repository.favorites.collectAsState()
    val stationFavs by app.radio.favoriteIds.collectAsState(initial = emptySet())
    val fav = if (station != null) station.uuid in stationFavs else track != null && track.id in favorites

    Column(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    track?.title ?: "Nothing playing", color = Ink, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.marquee(),
                )
                Text(
                    state?.radioStatus.takeIf { station != null } ?: track?.artist ?: "pick something in Library",
                    color = InkDim, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.marquee(),
                )
            }
            CtToggleIconButton(
                checked = fav,
                onCheckedChange = {
                    if (station != null) app.radio.toggleFavorite(station, fav)
                    else track?.let { repository.toggleFavorite(it.id) }
                },
                icon = Icons.Default.FavoriteBorder, checkedIcon = Icons.Default.Favorite,
                contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
                accent = accent, pop = true,
            )
            if (station == null && track != null) {
                var pick by remember { mutableStateOf(false) }
                CtIconButton(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", { pick = true }, tint = Ink)
                if (pick) AddToPlaylistSheet(listOf(track), onDismiss = { pick = false })
            }
            if (station == null && track?.isStreamed() == true) DownloadButton(track, accent)
        }
        if (station == null && seekBar) SeekRow(live, accent, Modifier.fillMaxWidth().padding(horizontal = 4.dp), fill)
        if (station == null) ChapterRow(live, Ink)
        if (station == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CtToggleIconButton(state?.shuffle == true, { PlayerHub.toggleShuffle() }, Icons.Default.Shuffle, "Shuffle", accent, dense = true)
            CtIconButton(Icons.Default.SkipPrevious, "Previous track", { PlayerHub.previous() })
            PlayPauseButton(state?.isPlaying == true, { PlayerHub.togglePlay() }, accent, Modifier.size(width = 76.dp, height = 64.dp), brush = fill)
            CtIconButton(Icons.Default.SkipNext, "Next track", { PlayerHub.next() })
            val repeat = state?.repeat ?: "off"
            CtToggleIconButton(
                repeat != "off", { PlayerHub.cycleRepeat() },
                if (repeat == "track") Icons.Default.RepeatOne else Icons.Default.Repeat,
                if (repeat == "track") "Repeat one" else "Repeat", accent, dense = true,
            )
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PlayPauseButton(state?.isPlaying == true, { PlayerHub.togglePlay() }, accent, Modifier.size(width = 76.dp, height = 64.dp), brush = fill)
            LiveBadge(accent, Modifier.padding(start = 16.dp))
            Text(
                station.name, style = MaterialTheme.typography.labelMedium, color = InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp).marquee(),
            )
        }
        // only while a timer runs; setting one lives in the menu
        sleepLeft()?.let { left ->
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "change the sleep timer", onClick = onSleep)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pausing $left", style = MaterialTheme.typography.labelLarge, color = Ink)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerSheetButton(Icons.Default.Tune, moodText("Sound"), Modifier.weight(1f), onSound)
            if (station == null) {
                var queue by rememberSaveable { mutableStateOf(false) }
                PlayerSheetButton(Icons.AutoMirrored.Filled.QueueMusic, moodText("Up next"), Modifier.weight(1f)) { queue = true }
                if (queue) QueueSheet(onDismiss = { queue = false })
            }
        }
    }
}

@Composable
private fun PlayerSheetButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
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
        modifier = Modifier.size(48.dp),
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

/** Fits a radio skin into the stage at its own width/height ratio. */
private fun radioSkinSize(s: RadioSkin, maxW: Dp, maxH: Dp): Modifier {
    val r = s.aspectRatio()
    val w = minOf(maxW, maxH * r)
    return Modifier.width(w).aspectRatio(r)
}

/** Set by CtifyRoot: the layout the cover is shared across, and the player overlay's visibility. */
internal val LocalSharedCover = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalPlayerVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The mini player's cover and the player stage share these bounds, so one grows out of the other. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedCover(visibility: AnimatedVisibilityScope?): Modifier {
    val shared = LocalSharedCover.current
    if (shared == null || visibility == null) return this
    return with(shared) { sharedBounds(rememberSharedContentState("cover"), visibility) }
}
