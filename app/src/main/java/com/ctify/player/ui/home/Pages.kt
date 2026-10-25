package com.ctify.player.ui.home

import androidx.compose.runtime.Composable
import com.ctify.player.data.MusicRepository
import com.ctify.player.lastfm.LastFm
import com.ctify.player.ui.library.PlaylistStudio

/** Draws one [HomePage]; [replace] swaps the top page, e.g. after a playlist is renamed. */
@Composable
internal fun PageHost(page: HomePage, repo: MusicRepository, open: (HomePage) -> Unit, back: () -> Unit, replace: (HomePage) -> Unit) {
    val openArtist: (LastFm.Artist) -> Unit = { open(HomePage.ArtistPage(it)) }
    when (page) {
        is HomePage.GenrePage -> GenrePage(page.tag, repo, back)
        is HomePage.ArtistPage -> ArtistPage(page.artist, repo, open, back)
        is HomePage.Artists -> CreatorsPage(page.artists, openArtist, back, page.title)
        is HomePage.Top100 -> Top100Page(page.continent, repo, back)
        is HomePage.Album -> AlbumPage(page.album, page.artist, repo, open, back)
        is HomePage.Playlist -> PlaylistStudio(repo, page.name, onBack = back, onRenamed = { replace(HomePage.Playlist(it)) })
    }
}
