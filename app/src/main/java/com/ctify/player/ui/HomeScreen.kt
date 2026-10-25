package com.ctify.player.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import com.ctify.player.data.HistoryPrefs
import com.ctify.player.data.Playlist
import com.ctify.player.data.PlaylistRecency
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted

import com.ctify.player.ui.theme.LiquidBackground
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ctify.player.data.Track
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.recommend.Recommendations
import com.ctify.player.recommend.Shelf
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import com.ctify.player.lastfm.LastFm
import com.ctify.player.ui.home.ArtistPage
import com.ctify.player.ui.home.CreatorsPage
import com.ctify.player.ui.home.CreatorsRow
import com.ctify.player.ui.home.ForYouPager
import com.ctify.player.ui.home.StarsShelf
import com.ctify.player.ui.home.ForYouPlaceholder
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import com.ctify.player.ui.home.GenrePage
import com.ctify.player.ui.home.GenresRow
import com.ctify.player.ui.home.HomePage
import com.ctify.player.ui.home.Top100Page
import com.ctify.player.ui.home.WorldSection
import com.ctify.player.ui.home.rememberCreators
import com.ctify.player.ui.home.rememberGenres
import com.ctify.player.ui.radio.radioRepo
import com.ctify.player.ui.radio.HeardSection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.ctify.player.ui.radio.HomeStationRows
import com.ctify.player.radio.HeardLikes
import com.ctify.player.ui.songs.SelectedMark
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongSheets
import com.ctify.player.ui.songs.songClicks

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onOpenPage: (HomePage) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSpinWheel: () -> Unit = {},
    add: AddActions,
) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recent by remember { viewModel.repository.recentlyPlayed(20) }.collectAsStateWithLifecycle(emptyList())
    val showRecent by HistoryPrefs.flow(HistoryPrefs.Key.SHOW_RECENT).collectAsStateWithLifecycle()
    val showMost by HistoryPrefs.flow(HistoryPrefs.Key.SHOW_MOST).collectAsStateWithLifecycle()
    val most by remember { viewModel.repository.mostPlayed(20) }.collectAsStateWithLifecycle(emptyList())
    val ctx = LocalContext.current
    val shelves by Recommendations.shelves.collectAsStateWithLifecycle()
    val stars by Recommendations.stars.collectAsStateWithLifecycle()
    val shelf = remember(shelves) { shelves.mapValues { (_, songs) -> songs.map { it.toTrack() } } }
    // waits for the library so liked and downloaded seeds exist
    LaunchedEffect(tracks.isNotEmpty()) {
        if (tracks.isNotEmpty()) { Recommendations.refresh(ctx, viewModel.repository); HeardLikes.sync(ctx) }
    }
    remember { LastFm.init(ctx) }
    val genres = rememberGenres(viewModel.repository, radioRepo())
    val creators = rememberCreators(viewModel.repository, genres?.firstOrNull())
    val building by Recommendations.building.collectAsStateWithLifecycle()
    val recentPlaylists = remember(playlists) { PlaylistRecency.order(playlists) }

    Box(Modifier.fillMaxSize()) {
    // Two colors: the liquid wash sits behind the header and fades out downward
    LiquidBackground(
        Modifier.fillMaxWidth().height(280.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn)
            },
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var shown = 0
        // Home's own full(): shadows the shared one so every shelf below staggers in on first show
        fun LazyGridScope.full(content: @Composable () -> Unit) {
            val i = shown++
            item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.enterStagger(i)) { content() } }
        }
        full {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Home", Modifier.weight(1f))
                IconButton(onClick = onSpinWheel) { Icon(Icons.Filled.Casino, "Spin the wheel", tint = TextPrimary) }
                AddMenu(add)
                SettingsButton(onOpenSettings)
            }
        }
        full { SearchPill(onOpenSearch) }
        if (tracks.isEmpty()) full { EmptyHint("Tap + to import a folder, or search YouTube to add songs.") }
        if (building && shelf[Shelf.FOR_YOU].isNullOrEmpty()) full { ForYouPlaceholder() }
        shelf[Shelf.FOR_YOU]?.takeIf { it.isNotEmpty() }?.let { full { ForYouPager(it, ui.currentTrackId, viewModel, onOpenNowPlaying) } }
        if (showRecent && recent.isNotEmpty()) full { TrackCarousel("Recently played", recent, SongContext.History, ui.currentTrackId, viewModel, onOpenNowPlaying) }
        if (recentPlaylists.isNotEmpty()) full { PlaylistsRow(recentPlaylists, viewModel) { onOpenPage(HomePage.Playlist(it)) } }
        stars.forEachIndexed { i, g -> full { StarsShelf(g, i == 0, ui.currentTrackId, viewModel, onOpenNowPlaying) } }
        listOf(Shelf.PLAYED, Shelf.DOWNLOADS).forEach { s ->
            shelf[s]?.takeIf { it.isNotEmpty() }?.let { full { TrackCarousel(s.title, it, SongContext.Recommended, ui.currentTrackId, viewModel, onOpenNowPlaying) } }
        }
        if (showMost && most.isNotEmpty()) full { TrackCarousel("Most played", most, SongContext.History, ui.currentTrackId, viewModel, onOpenNowPlaying) }
        full { HeardSection() }
        if (genres == null) full { ShelfPlaceholder("Genres", 150.dp) }
        if (!genres.isNullOrEmpty()) full { GenresRow(genres) { onOpenPage(HomePage.GenrePage(it)) } }
        if (creators == null) full { ShelfPlaceholder("Creators", 104.dp, CircleShape) }
        if (!creators.isNullOrEmpty()) full {
            CreatorsRow(creators, onOpen = { onOpenPage(HomePage.ArtistPage(it)) }, onSeeAll = { onOpenPage(HomePage.Artists("Creators", creators)) })
        }
        if (LastFm.enabled) full {
            WorldSection(
                onGenre = { onOpenPage(HomePage.GenrePage(it)) }, onArtist = { onOpenPage(HomePage.ArtistPage(it)) },
                onSeeAll = { title, list -> onOpenPage(HomePage.Artists(title, list)) }, onTop100 = { onOpenPage(HomePage.Top100(it)) },
            )
        }
    }
    }
}

/** Looks like a search box; a tap goes to the Search tab. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 52.dp).clip(MaterialTheme.shapes.extraLarge)
            .background(SurfaceElevated).clickable(role = Role.Button, onClickLabel = "search", onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text("Songs, artists, albums, stations", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Your playlists, the last one you added to first. */
@Composable
private fun PlaylistsRow(playlists: List<Playlist>, viewModel: PlayerViewModel, onOpen: (String) -> Unit) {
    Column {
        SectionHeader("Your playlists", Modifier.padding(top = 10.dp, start = 4.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playlists, key = { it.name }) { pl ->
                Box(Modifier.width(140.dp)) {
                    PlaylistCard(pl, pl.trackIds.firstNotNullOfOrNull { viewModel.repository.trackById(it) }) { onOpen(pl.name) }
                }
            }
        }
    }
}

/** A titled row of song covers, like Home's station row; a tap plays the row from that song. */
@Composable
private fun TrackCarousel(title: String, list: List<Track>, context: SongContext, current: String?, viewModel: PlayerViewModel, onOpenNowPlaying: () -> Unit) {
    Column {
        SectionHeader(title, Modifier.padding(top = 10.dp, start = 4.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                val on = t.id == current
                Column(
                    Modifier
                        .width(140.dp)
                        .clip(MaterialTheme.shapes.small)
                        .songClicks(t, context, list) {
                            // streamed songs from history may not be loaded this session
                            viewModel.repository.remember(list)
                            viewModel.playQueue(list, i)
                            onOpenNowPlaying()
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${t.title}, by ${t.artist}"
                            if (on) stateDescription = "now playing"
                        },
                ) {
                    Box(Modifier.clip(MaterialTheme.shapes.small)) {
                        AlbumArt(t, 140.dp, shape = MaterialTheme.shapes.small)
                        SelectedMark(t, context)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t.title, color = if (on) Accent else TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
