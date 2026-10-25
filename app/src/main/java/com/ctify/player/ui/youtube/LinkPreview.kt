package com.ctify.player.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.contentPadding
import com.ctify.player.ui.songCount
import com.ctify.player.ui.songs.Snack
import com.ctify.player.ui.songs.SongContext
import com.ctify.player.ui.songs.SongRow
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtAlbum
import com.ctify.player.youtube.YtLink
import com.ctify.player.youtube.YtSong
import com.ctify.player.youtube.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class LinkKind(val label: String) { VIDEO("Video"), ALBUM("Album"), PLAYLIST("Playlist") }

/** One resolved link: a video, an album or a playlist, in one shape. */
private class Linked(val kind: LinkKind, val title: String, val by: String, val cover: String?, val songs: List<YtSong>, val album: YtAlbum? = null)

private fun YtLink.linked() = when (this) {
    is YtLink.Song -> Linked(LinkKind.VIDEO, song.title, song.artist, song.thumbnail, listOf(song))
    is YtLink.Album -> Linked(LinkKind.ALBUM, album.title, album.artist, album.thumbnail, album.songs, album)
    is YtLink.Playlist -> Linked(LinkKind.PLAYLIST, playlist.title, playlist.owner, playlist.thumbnail, playlist.songs)
}

/** What a pasted YouTube link is, with Play, Save and Download + Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LinkPreview(viewModel: PlayerViewModel, link: String, modifier: Modifier = Modifier) {
    val repo = viewModel.repository
    val scope = rememberCoroutineScope()
    var result by remember(link) { mutableStateOf<Linked?>(null) }
    var error by remember(link) { mutableStateOf<String?>(null) }
    var busy by remember(link) { mutableStateOf(false) }
    LaunchedEffect(link) {
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { error = it; return@LaunchedEffect }
        error = try {
            result = YouTubeSource.fromLink(link).linked(); null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Couldn't open this link"
        }
    }
    val r = result
    if (r == null) {
        Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(12.dp))
                Text("Opening the link", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            } else Text(error!!, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val tracks = remember(r) { r.songs.map { it.toTrack() } }
    val one = r.kind == LinkKind.VIDEO

    fun play(i: Int) { repo.remember(tracks); viewModel.playQueue(tracks, i) }

    // a video goes in the library, a list becomes a playlist that plays online
    fun save(): String {
        if (one) {
            val fresh = tracks.filterNot { repo.isSaved(it.id) }.map { it.id }
            repo.saveStreamed(tracks)
            Snack.show("Saved \"${r.title}\" to your library · plays online") { repo.removeStreamed(fresh) }
            return r.title
        }
        val name = r.title.trim().ifEmpty { "YouTube playlist" }
        val existed = repo.playlists.value.any { it.name == name }
        repo.saveAsPlaylist(name, tracks, r.cover)
        Snack.show("Saved ${songCount(tracks.size)} to $name", undo = if (existed) null else ({ repo.deletePlaylistWithStreams(name) }))
        return name
    }

    fun downloadAndSave() {
        val name = save()
        busy = true
        scope.launch {
            val n = try { Downloads.enqueue(r.songs, r.album) } finally { busy = false }
            Snack.show(
                when {
                    n == 0 -> "Saved $name · already downloaded or in the queue"
                    one -> "Saved and downloading \"$name\""
                    else -> "Saved ${songCount(tracks.size)} to $name · ${songCount(n)} downloading"
                }
            )
        }
    }

    LazyColumn(modifier, contentPadding = contentPadding()) {
        item(key = "head") {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(r.cover, null, Modifier.size(if (one) 128.dp else 120.dp).clip(MaterialTheme.shapes.medium))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.kind.label, color = Accent, style = MaterialTheme.typography.labelLarge)
                        Text(
                            r.title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 3,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                        )
                        Text(r.by, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!one) Text(songCount(r.songs.size), color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                val enabled = r.songs.isNotEmpty() && !busy
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { play(0) }, enabled = r.songs.isNotEmpty()) { Label(Icons.Filled.PlayArrow, "Play") }
                    FilledTonalButton(onClick = { save() }, enabled = enabled) {
                        Label(Icons.Filled.LibraryAdd, when (r.kind) { LinkKind.VIDEO -> "Save"; LinkKind.ALBUM -> "Save album"; LinkKind.PLAYLIST -> "Save playlist" })
                    }
                    FilledTonalButton(onClick = ::downloadAndSave, enabled = enabled) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (one) "Download + Save" else "Download all + Save")
                    }
                }
                if (r.songs.isEmpty()) Text("This list has no songs that can be played.", color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (!one) itemsIndexed(r.songs, key = { i, s -> "ls$i" + s.videoId }) { i, _ ->
            SongRow(tracks[i], SongContext.Search, tracks, onPlay = { play(i) })
        }
    }
}

@Composable
private fun Label(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(text)
}

/** The YouTube filter before anything is typed: what it can do, paste, Spotify import and downloads. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun YouTubeStart(onPaste: () -> Unit, onSpotify: () -> Unit, onDownloads: () -> Unit, pending: Int) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Add from YouTube", color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(
            "Search YouTube Music, or paste a link to a song, album or playlist. " +
                "You can also share a link from the YouTube app to Guava Player.\n\n" +
                "Tap a song to listen, or save and download it to keep it in your library.",
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPaste) { Label(Icons.Filled.ContentPaste, "Paste a link") }
            FilledTonalButton(onClick = onSpotify) { Label(Icons.AutoMirrored.Filled.QueueMusic, "Import from Spotify") }
            FilledTonalButton(onClick = onDownloads) {
                Label(Icons.Filled.Download, if (pending > 0) "Downloads ($pending)" else "Downloads")
            }
        }
    }
}
