package com.ctify.player.ui.youtube

import com.ctify.player.ui.songCount
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.songs.Snack
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongRow
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtAlbum
import com.ctify.player.youtube.YtIntake
import com.ctify.player.youtube.YtLink
import com.ctify.player.youtube.YtPlaylistRef
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.DownloadState
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.launch

/** Search YouTube Music for songs and albums; play them or save them. */
@Composable
fun YouTubeScreen(viewModel: PlayerViewModel, onClose: () -> Unit, onOpenDownloads: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var albumsMode by remember { mutableStateOf(false) }
    var videosMode by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var songs by remember { mutableStateOf<List<YtSong>>(emptyList()) }
    var albums by remember { mutableStateOf<List<YtPlaylistRef>>(emptyList()) }
    var album by remember { mutableStateOf<YtAlbum?>(null) }
    val downloads by Downloads.all().collectAsState(initial = emptyList())
    val pending = downloads.count { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }

    fun run(block: suspend () -> Unit) {
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { error = it; return }
        loading = true
        error = null
        scope.launch {
            try { block() } catch (e: Exception) { error = e.message ?: "Something went wrong" } finally { loading = false }
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        album = null
        if (YouTubeSource.isLink(q)) {
            run {
                when (val link = YouTubeSource.fromLink(q)) {
                    is YtLink.Album -> album = link.album
                    is YtLink.Song -> { albumsMode = false; songs = listOf(link.song) }
                }
            }
            return
        }
        run {
            when {
                albumsMode -> albums = YouTubeSource.searchAlbums(q)
                videosMode -> songs = YouTubeSource.searchVideos(q)
                else -> songs = YouTubeSource.search(q)
            }
        }
    }

    fun play(list: List<YtSong>, index: Int) {
        val tracks = list.map { it.toTrack() }
        viewModel.repository.remember(tracks)
        viewModel.playQueue(tracks, index)
    }

    fun download(list: List<YtSong>, from: YtAlbum? = null) = scope.launch {
        val n = Downloads.enqueue(list, from)
        val msg = when {
            n == 0 -> "Already saved or in the queue"
            n == 1 -> "Added to downloads"
            else -> "$n songs added to downloads"
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    val shared by YtIntake.pending.collectAsState()
    LaunchedEffect(shared) {
        shared?.let { query = it; YtIntake.pending.value = null; search() }
    }

    fun save(list: List<YtSong>) {
        val tracks = list.map { it.toTrack() }
        viewModel.repository.saveStreamed(tracks)
        Snack.show(if (list.size == 1) "Saved to your library · plays online" else "${songCount(list.size)} saved to your library · they play online") {
            viewModel.repository.removeStreamed(tracks.map { it.id })
        }
    }

    fun saveAlbum(a: YtAlbum) {
        viewModel.repository.saveAsPlaylist(a.title, a.songs.map { it.toTrack() }, a.thumbnail)
        Snack.show("Saved \"${a.title}\" as a playlist")
    }

    BackHandler(enabled = album != null) { album = null }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (album != null) album = null else onClose() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Text("Add from YouTube", color = TextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onOpenDownloads) {
                    BadgedBox(badge = { if (pending > 0) Badge { Text("$pending") } }) {
                        Icon(Icons.Filled.Download, "Downloads", tint = TextPrimary)
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search, or paste a YouTube link") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    IconButton(onClick = {
                        (ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString())?.takeIf { it.isNotBlank() }?.let { query = it.trim(); search() }
                    }) { Icon(Icons.Filled.ContentPaste, "Paste link") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Songs", !albumsMode && !videosMode) { albumsMode = false; videosMode = false; search() }
                Chip("Albums", albumsMode) { albumsMode = true; videosMode = false; search() }
                Chip("All videos", videosMode) { albumsMode = false; videosMode = true; search() }
            }
            Box(Modifier.fillMaxSize()) {
                val shown = album
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                    error != null -> Hint(error!!)
                    shown != null -> AlbumDetail(shown, onPlay = { play(shown.songs, it) }, onDownload = { download(shown.songs, shown) }, onSave = { saveAlbum(shown) })
                    albumsMode -> if (albums.isEmpty()) Hint("Search for an album to play or save it whole.") else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(albums, key = { it.id }) { a ->
                            CardRow(a.thumbnail, a.title, a.subtitle) { run { album = YouTubeSource.album(a.id) } }
                        }
                    }
                    songs.isEmpty() -> Hint(
                        "Search YouTube Music, or paste a link to a song, album or playlist. " +
                            "You can also share a link from the YouTube app to Ctify.\n\n" +
                            "Tap a song to listen, or Download to keep it in your library."
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                                FilledTonalButton(onClick = { save(songs) }) {
                                    Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (songs.size == 1) "Save" else "Save all")
                                }
                                FilledTonalButton(onClick = { download(songs) }) {
                                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (songs.size == 1) "Download" else "Download all")
                                }
                            }
                        }
                        val tracks = songs.map { it.toTrack() }
                        items(songs, key = { it.videoId }) { s ->
                            SongRow(s.toTrack(), SongContext.Search, tracks, onPlay = { play(songs, songs.indexOf(s)) })
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun AlbumDetail(album: YtAlbum, onPlay: (Int) -> Unit, onDownload: () -> Unit, onSave: () -> Unit) {
    val tracks = album.songs.map { it.toTrack() }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(album.thumbnail, null, Modifier.size(120.dp).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("${songCount(album.songs.size)}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilledTonalButton(onClick = { onPlay(0) }, enabled = album.songs.isNotEmpty()) { Text("Play") }
                        FilledTonalButton(onClick = onSave, enabled = album.songs.isNotEmpty()) { Text("Save") }
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = onDownload, enabled = album.songs.isNotEmpty()) { Text("Download") }
                    }
                }
            }
        }
        items(album.songs, key = { it.videoId }) { s ->
            SongRow(s.toTrack(), SongContext.Search, tracks, onPlay = { onPlay(album.songs.indexOf(s)) })
        }
    }
}

@Composable
internal fun CardRow(image: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(image, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
internal fun Hint(text: String) {
    Text(text, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(24.dp))
}
