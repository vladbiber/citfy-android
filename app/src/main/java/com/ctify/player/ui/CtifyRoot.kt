package com.ctify.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import com.ctify.player.keys.KeyAction
import com.ctify.player.keys.KeyRouter
import com.ctify.player.keys.LocalDeviceProfile

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.ctify.player.ui.home.HomePage
import com.ctify.player.ui.home.PageHost

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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CtifyRoot(viewModel: PlayerViewModel) = SharedTransitionLayout { CompositionLocalProvider(LocalSharedCover provides this) {
    var tab by remember { mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showWheel by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // the search overlay over Home, opened on one of its pills; the last pill stays for the closing slide
    var showSearch by remember { mutableStateOf(false) }
    var searchScope by remember { mutableStateOf(Scope.ALL) }
    var librarySection by rememberSaveable { mutableIntStateOf(0) }
    // artist, album, playlist and genre pages open over the current tab; Back walks back through them
    val pages = remember { mutableStateListOf<HomePage>() }
    val openPage: (HomePage) -> Unit = { pages += it }
    val selectTab: (Int) -> Unit = { pages.clear(); showSearch = false; tab = it }
    val openSearch: (Scope) -> Unit = { s ->
        showNowPlaying = false; showSettings = false; showWheel = false
        pages.clear(); tab = Tab.Home.ordinal; searchScope = s; showSearch = true
    }
    // the same top bar buttons on every tab
    val openWheel: () -> Unit = { showWheel = true }
    val openSettings: () -> Unit = { showSettings = true }
    // a link shared from another app opens search on the YouTube pill, which resolves it
    val sharedLink by YtIntake.pending.collectAsState()
    LaunchedEffect(sharedLink) { if (sharedLink != null) openSearch(Scope.YOUTUBE) }

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
        youTube = { openSearch(Scope.YOUTUBE) },
        spotify = { YtIntake.spotify.value = "" },
        downloads = { showDownloads = true },
    )

    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember { HiddenArtists.init(ctx) }
    val snack = rememberSnackState()
    var showQueue by remember { mutableStateOf(false) }
    var keyPick by remember { mutableStateOf<com.ctify.player.data.Track?>(null) }
    val nowTrack by androidx.compose.runtime.rememberUpdatedState(current)
    // hardware keyboard shortcuts that need the UI (keyboard phones, tablets)
    LaunchedEffect(Unit) {
        KeyRouter.uiActions.collect { a ->
            when (a) {
                KeyAction.SEARCH -> openSearch(Scope.ALL)
                KeyAction.QUEUE -> if (nowTrack != null) showQueue = true
                KeyAction.OPEN_PLAYER -> showNowPlaying = true
                KeyAction.ADD_TO_PLAYLIST -> keyPick = nowTrack.takeIf { now?.station == null }
                KeyAction.TAB_1, KeyAction.TAB_2, KeyAction.TAB_3, KeyAction.TAB_4 -> {
                    // three tabs now: Tab 4 has nothing to open
                    val i = a.ordinal - KeyAction.TAB_1.ordinal
                    if (i < Tab.entries.size) { showNowPlaying = false; showSettings = false; selectTab(i) }
                }
                else -> {}
            }
        }
    }
    val overlay = showWheel || showNowPlaying || showSettings
    val goTo by SongSheets.goTo.collectAsState()
    LaunchedEffect(goTo) {
        goTo?.let { pages += it; SongSheets.goTo.value = null; showWheel = false; showNowPlaying = false; showSettings = false }
    }
    // Library and Radio go back to Home first; Back on Home leaves the app. Pages and overlays come later, so they win
    BackHandler(enabled = tab != Tab.Home.ordinal) { selectTab(Tab.Home.ordinal) }

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
                // the search overlay takes the whole screen; the mini player stays
                if (!showSearch) {
                // square keyboard phones have little height: icons only, in a slim bar
                if (LocalDeviceProfile.current.isSquare) Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().height(52.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.entries.forEachIndexed { index, t ->
                        androidx.compose.material3.IconButton(onClick = { selectTab(index) }) {
                            Icon(t.icon, t.label, tint = if (tab == index) Accent else TextSecondary)
                        }
                    }
                } else NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEachIndexed { index, t ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { selectTab(index) },
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
                    Tab.Home.ordinal -> HomeScreen(
                        viewModel,
                        onOpenNowPlaying = { showNowPlaying = true },
                        onOpenPage = openPage,
                        onOpenSearch = { openSearch(Scope.ALL) },
                        onOpenSettings = openSettings,
                        onSpinWheel = openWheel,
                        add = add,
                    )
                    Tab.Library.ordinal -> LibraryScreen(
                        viewModel,
                        add = add,
                        onOpenPage = openPage,
                        onOpenSettings = openSettings,
                        onOpenWheel = openWheel,
                        section = librarySection,
                        onSection = { librarySection = it },
                    )
                    else -> RadioScreen(onOpenWheel = openWheel, onOpenSettings = openSettings)
                }
            }
            AnimatedVisibility(
                visible = showSearch,
                enter = slideInVertically(initialOffsetY = { it / 8 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 8 }) + fadeOut(),
            ) {
                androidx.compose.material3.Surface(
                    Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    color = BgBlack,
                ) {
                    SearchOverlay(viewModel, searchScope, onClose = { showSearch = false }, onOpenDownloads = { showDownloads = true })
                }
            }
            pages.lastOrNull()?.let { page ->
                BackHandler { pages.removeAt(pages.lastIndex) }
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = BgBlack) {
                    key(pages.size) {
                        PageHost(
                            page, viewModel.repository, open = openPage,
                            back = { if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex) },
                            replace = { if (pages.isNotEmpty()) pages[pages.lastIndex] = it },
                        )
                    }
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
        visible = showSettings,
        enter = slideInVertically(initialOffsetY = { it / 8 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 8 }) + fadeOut(),
    ) {
        BackHandler { showSettings = false }
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            SettingsScreen(onClose = { showSettings = false })
        }
    }

    if (showDownloads) DownloadsSheet(onDismiss = { showDownloads = false })
    if (showQueue) QueueSheet(onDismiss = { showQueue = false })
    keyPick?.let { AddToPlaylistSheet(listOf(it), onDismiss = { keyPick = null }) }

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
                PlayerContent(
                    onClose = { showNowPlaying = false },
                    onOpenRadio = { showNowPlaying = false; selectTab(Tab.Radio.ordinal) },
                    onOpenPlaylist = { name -> showNowPlaying = false; pages += HomePage.Playlist(name) },
                )
            }
        }
    }

    // song sheet, selection bar and, over full-screen overlays, the snackbar
    SongSheetHost()
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(bottom = 8.dp), contentAlignment = Alignment.BottomCenter) { if (overlay) SnackbarHost(snack) }
    // over the tab bar while songs are picked, where thumbs already are
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SelectionBar() }
} } // SharedTransitionLayout, LocalSharedCover: the mini player's cover grows into the player

internal fun contentPadding() = PaddingValues(bottom = 12.dp)
