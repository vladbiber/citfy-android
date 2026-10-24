package com.ctify.player.ui

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
import com.ctify.player.ui.home.GenrePage
import com.ctify.player.ui.home.GenresRow
import com.ctify.player.ui.home.HomePage
import com.ctify.player.ui.home.rememberCreators
import com.ctify.player.ui.home.rememberGenres
import com.ctify.player.ui.radio.radioRepo
import com.ctify.player.ui.radio.HeardSection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.ctify.player.ui.radio.HomeStationRows

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onMore: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSpinWheel: () -> Unit = {},
    add: AddActions,
) {
    val tracks by viewModel.repository.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.repository.playlists.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recent by remember { viewModel.repository.recentlyPlayed(20) }.collectAsStateWithLifecycle(emptyList())
    val most by remember { viewModel.repository.mostPlayed(20) }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    // saved YouTube songs load in addedAt order
    val downloaded = remember(tracks) { tracks.filter { it.id.startsWith("yt_") }.asReversed() }
    val ctx = LocalContext.current
    val shelves by Recommendations.shelves.collectAsStateWithLifecycle()
    val shelf = remember(shelves) { shelves.mapValues { (_, songs) -> songs.map { it.toTrack() } } }
    // waits for the library so liked and downloaded seeds exist
    LaunchedEffect(tracks.isNotEmpty()) { if (tracks.isNotEmpty()) Recommendations.refresh(ctx, viewModel.repository) }
    remember { LastFm.init(ctx) }
    val genres = rememberGenres(viewModel.repository, radioRepo())
    val creators = rememberCreators(viewModel.repository, genres.firstOrNull())
    val pages = remember { mutableStateListOf<HomePage>() }
    pages.lastOrNull()?.let { page ->
        val back: () -> Unit = { pages.removeAt(pages.lastIndex) }
        val open: (LastFm.Artist) -> Unit = { pages += HomePage.ArtistPage(it) }
        BackHandler(onBack = back)
        when (page) {
            is HomePage.GenrePage -> GenrePage(page.tag, viewModel.repository, back)
            HomePage.Creators -> CreatorsPage(creators, open, back)
            is HomePage.ArtistPage -> ArtistPage(page.artist, viewModel.repository, open, back)
        }
        return
    }

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
    Column(Modifier.fillMaxSize()) {
        // the box stays put above both views, so typing never loses focus when results replace Home
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, "Songs, artists, heard songs, stations", Modifier.weight(1f))
            AddMenu(add)
        }
        if (query.isNotBlank()) {
            SearchResults(viewModel, query, onMore, HOME_SCOPES)
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tracks.isEmpty()) full { EmptyHint("Tap + to import a folder or add songs from YouTube.") }
            shelf[Shelf.FOR_YOU]?.takeIf { it.isNotEmpty() }?.let { full { ForYouPager(it, ui.currentTrackId, viewModel, onOpenNowPlaying) } }
            full {
                HeroCard(
                    "Spin the wheel", if (tracks.isEmpty()) "Let chance pick a song" else "1 in ${"%,d".format(tracks.size)} songs",
                    Icons.Filled.Casino, listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primaryContainer),
                    onClick = onSpinWheel,
                )
            }
            if (recent.isNotEmpty()) full { TrackCarousel("Recently played", recent, ui.currentTrackId, viewModel, onOpenNowPlaying) }
            if (most.isNotEmpty()) full { TrackCarousel("Most played", most, ui.currentTrackId, viewModel, onOpenNowPlaying) }
            listOf(Shelf.PLAYED, Shelf.LIKED, Shelf.DOWNLOADS).forEach { s ->
                shelf[s]?.takeIf { it.isNotEmpty() }?.let { full { TrackCarousel(s.title, it, ui.currentTrackId, viewModel, onOpenNowPlaying) } }
            }
            if (downloaded.isNotEmpty()) full { TrackCarousel("Downloaded", downloaded, ui.currentTrackId, viewModel, onOpenNowPlaying) }
            if (genres.isNotEmpty()) full { GenresRow(genres) { pages += HomePage.GenrePage(it) } }
            if (creators.isNotEmpty()) full { CreatorsRow(creators, onOpen = { pages += HomePage.ArtistPage(it) }, onSeeAll = { pages += HomePage.Creators }) }
            full { HomeStationRows() }
            if (playlists.isNotEmpty()) {
                full { SectionHeader("Playlists", Modifier.padding(top = 10.dp, start = 4.dp)) }
                items(playlists, key = { it.name }) { pl ->
                    PlaylistCard(pl, pl.trackIds.firstNotNullOfOrNull { viewModel.repository.trackById(it) }) {
                        onOpenPlaylist(pl.name)
                    }
                }
            }
            full { HeardSection() }
        }
    }
    }
}

/** A titled row of song covers, like Home's station row; a tap plays the row from that song. */
@Composable
private fun TrackCarousel(title: String, list: List<Track>, current: String?, viewModel: PlayerViewModel, onOpenNowPlaying: () -> Unit) {
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
                        .clickable(onClickLabel = "play") {
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
                    AlbumArt(t, 140.dp, shape = MaterialTheme.shapes.small)
                    Spacer(Modifier.height(6.dp))
                    Text(t.title, color = if (on) Accent else TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(colors))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(MaterialTheme.shapes.small).background(ink.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = ink) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ink)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f))
        }
    }
}
