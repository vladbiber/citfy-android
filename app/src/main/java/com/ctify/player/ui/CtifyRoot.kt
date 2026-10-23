package com.ctify.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GraphicEq
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ctify.player.ui.theme.TextSecondary

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Library("Library", Icons.Filled.LibraryMusic),
    Radio("Radio", Icons.Filled.Radio),
    Sound("Sound", Icons.Filled.GraphicEq),
}

@Composable
fun CtifyRoot(viewModel: PlayerViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showWheel by remember { mutableStateOf(false) }
    var showYouTube by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var moreTrackId by remember { mutableStateOf<String?>(null) }
    var openedPlaylist by remember { mutableStateOf<String?>(null) }
    var librarySection by remember { mutableIntStateOf(0) }

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val now by PlayerHub.playback.collectAsStateWithLifecycle()
    // radio items aren't in the library, so fall back to what the player publishes
    val current = viewModel.repository.trackById(ui.currentTrackId) ?: now?.track
    val station = now?.station

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importFile(uri) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.importFolder(uri) }
    val onImport: () -> Unit = { importLauncher.launch(arrayOf("audio/*")) }
    val onImportFolder: () -> Unit = { folderLauncher.launch(null) }
    val onMore: (String) -> Unit = { moreTrackId = it }

    Scaffold(
        containerColor = BgBlack,
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
                        isLive = station != null,
                        status = now?.radioStatus.takeIf { station != null },
                    )
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEachIndexed { index, t ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = Accent.copy(alpha = 0.16f),
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                0 -> HomeScreen(
                    viewModel,
                    onOpenNowPlaying = { showNowPlaying = true },
                    onMore = onMore,
                    onOpenPlaylist = { name -> openedPlaylist = name; tab = 2 },
                    onOpenFavorites = { librarySection = 1; openedPlaylist = null; tab = 2 },
                    onOpenSettings = { showSettings = true },
                    onSpinWheel = { showWheel = true },
                )
                1 -> SearchScreen(viewModel, onMore = onMore)
                2 -> LibraryScreen(
                    viewModel,
                    onMore = onMore,
                    onImport = onImport,
                    onImportFolder = onImportFolder,
                    openedPlaylist = openedPlaylist,
                    onOpenPlaylist = { openedPlaylist = it },
                    section = librarySection,
                    onSection = { librarySection = it },
                    onYouTube = { showYouTube = true },
                    onDownloads = { showDownloads = true },
                )
                3 -> RadioScreen()
                else -> SoundScreen()
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
                onAddToPlaylist = { moreTrackId = it },
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

    moreTrackId?.let { id ->
        AddToPlaylistSheet(viewModel, trackId = id, onDismiss = { moreTrackId = null })
    }

    AnimatedVisibility(
        visible = showSettings,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        BackHandler { showSettings = false }
        SettingsScreen(onClose = { showSettings = false })
    }

    AnimatedVisibility(
        visible = showNowPlaying && current != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        // an in-window overlay, not a Dialog, so system bar insets and Back work
        BackHandler { showNowPlaying = false }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            PlayerContent(onClose = { showNowPlaying = false })
        }
    }
}

internal fun contentPadding() = PaddingValues(bottom = 12.dp)
