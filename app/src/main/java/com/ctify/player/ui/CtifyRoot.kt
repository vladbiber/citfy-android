package com.ctify.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctify.player.player.PlayerHub
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.radio.RadioScreen
import com.ctify.player.ui.radio.radioRepo
import com.ctify.player.ui.wheel.WheelScreen
import com.ctify.player.ui.wheel.liveRadioSource
import com.ctify.player.ui.wheel.pendingYouTubeSources
import com.ctify.player.ui.youtube.DownloadsSheet
import com.ctify.player.ui.youtube.YouTubeScreen
import com.ctify.player.ui.theme.BgBlack
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.Appearance
import androidx.compose.ui.Alignment
import com.ctify.player.ui.theme.ThemeSource
import com.ctify.player.ui.theme.accentBrush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.ui.youtube.SpotifyImportDialog
import com.ctify.player.youtube.YtIntake
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarHost
import com.ctify.player.recommend.HiddenArtists
import com.ctify.player.ui.songs.SelectionBar
import com.ctify.player.ui.songs.SongSheetHost
import com.ctify.player.ui.songs.SongSheets
import com.ctify.player.ui.songs.rememberSnackState

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Library("Library", Icons.Filled.LibraryMusic),
    Radio("Radio", Icons.Filled.Radio),
    Settings("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CtifyRoot(viewModel: PlayerViewModel) = SharedTransitionLayout { CompositionLocalProvider(LocalSharedCover provides this) {
    var tab by remember { mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showWheel by remember { mutableStateOf(false) }
    var showYouTube by remember { mutableStateOf(false) }
    val sharedLink by YtIntake.pending.collectAsState()
    LaunchedEffect(sharedLink) { if (sharedLink != null) showYouTube = true }
    var showDownloads by remember { mutableStateOf(false) }
    var openedPlaylist by remember { mutableStateOf<String?>(null) }
    var librarySection by remember { mutableIntStateOf(0) }

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val now by PlayerHub.playback.collectAsStateWithLifecycle()
    // radio items aren't in the library, so fall back to what the player publishes
    val current = viewModel.repository.trackById(ui.currentTrackId) ?: now?.track
    val station = now?.station
    val playerOpen = showNowPlaying && current != null

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importFile(uri) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.importFolder(uri) }
    val add = AddActions(
        importFolder = { folderLauncher.launch(null) },
        importFile = { importLauncher.launch(arrayOf("audio/*")) },
        youTube = { showYouTube = true },
        spotify = { YtIntake.spotify.value = "" },
        downloads = { showDownloads = true },
    )

    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember { HiddenArtists.init(ctx) }
    val snack = rememberSnackState()
    val overlay = showWheel || showYouTube || showNowPlaying
    val goTo by SongSheets.goTo.collectAsState()
    LaunchedEffect(goTo) { if (goTo != null) { tab = Tab.Home.ordinal; showWheel = false; showYouTube = false; showNowPlaying = false } }

    Scaffold(
        containerColor = BgBlack,
        snackbarHost = { if (!overlay) SnackbarHost(snack) },
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                if (current != null) {
                    val progress = if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f
                    MiniPlayer(
                        track = current,
                        isPlaying = ui.isPlaying,
                        progress = progress,
                        onClick = { showNowPlaying = true },
                        onTogglePlay = viewModel::togglePlay,
                        onNext = viewModel::next,
                        onPrevious = PlayerHub::previous,
                        isLive = station != null,
                        status = now?.radioStatus.takeIf { station != null },
                        playerOpen = playerOpen,
                    )
                }
                val duo = Appearance.source.collectAsState().value == ThemeSource.DUO
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEachIndexed { index, t ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                // M3 indicators take one color, so Two colors draws its own fade pill (64x32 like the stock one)
                                if (duo && tab == index) Box(
                                    Modifier.size(64.dp, 32.dp).background(accentBrush(), CircleShape, alpha = 0.35f),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(t.icon, t.label) }
                                else Icon(t.icon, t.label)
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = if (duo) Color.Transparent else Accent.copy(alpha = 0.16f),
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            val still = reducedMotion()
            AnimatedContent(tab, transitionSpec = {
                // the new tab comes in from the side it sits on in the bar
                val dir = if (targetState > initialState) 1 else -1
                if (still) EnterTransition.None togetherWith ExitTransition.None
                else (slideInHorizontally(standardSpring()) { dir * it / 5 } + fadeIn(fastSpring())) togetherWith
                    (slideOutHorizontally(standardSpring()) { -dir * it / 5 } + fadeOut(fastSpring()))
            }, label = "tab") { page ->
                when (page) {
                    0 -> HomeScreen(
                        viewModel,
                        onOpenNowPlaying = { showNowPlaying = true },
                        onOpenPlaylist = { name -> openedPlaylist = name; tab = Tab.Library.ordinal },
                        onSpinWheel = { showWheel = true },
                        add = add,
                    )
                    Tab.Library.ordinal -> LibraryScreen(
                        viewModel,
                        add = add,
                        openedPlaylist = openedPlaylist,
                        onOpenPlaylist = { openedPlaylist = it },
                        section = librarySection,
                        onSection = { librarySection = it },
                    )
                    Tab.Radio.ordinal -> RadioScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showWheel,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        BackHandler { showWheel = false }
        val radio = radioRepo()
        val extra = remember(radio) { listOf(liveRadioSource(radio)) + pendingYouTubeSources(viewModel.repository) }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            WheelScreen(
                onClose = { showWheel = false },
                library = viewModel.repository,
                sources = extra,
            )
        }
    }

    AnimatedVisibility(
        visible = showYouTube,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        BackHandler { showYouTube = false }
        YouTubeScreen(viewModel, onClose = { showYouTube = false }, onOpenDownloads = { showDownloads = true })
    }

    if (showDownloads) DownloadsSheet(onDismiss = { showDownloads = false })

    val spotifyLink by YtIntake.spotify.collectAsState()
    spotifyLink?.let { link ->
        SpotifyImportDialog(viewModel.repository, link.ifBlank { null }, onDismiss = { YtIntake.spotify.value = null })
    }


    AnimatedVisibility(
        visible = playerOpen,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        // an in-window overlay, not a Dialog, so system bar insets and Back work
        BackHandler { showNowPlaying = false }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            CompositionLocalProvider(LocalPlayerVisibility provides this@AnimatedVisibility) {
                PlayerContent(onClose = { showNowPlaying = false }, onOpenRadio = { showNowPlaying = false; tab = Tab.Radio.ordinal })
            }
        }
    }

    // song sheet, selection bar and, over full-screen overlays, the snackbar
    SongSheetHost()
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(bottom = 8.dp), contentAlignment = Alignment.BottomCenter) { if (overlay) SnackbarHost(snack) }
    // the parent Surface stretches its children, so the bar gets a full-size box to sit at the top of
    Box(Modifier.fillMaxSize()) { SelectionBar() }
} } // SharedTransitionLayout, LocalSharedCover: the mini player's cover grows into the player

internal fun contentPadding() = PaddingValues(bottom = 12.dp)
