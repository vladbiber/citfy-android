package com.ctify.player.youtube

import kotlinx.coroutines.flow.MutableStateFlow

/** A YouTube link shared into Guava Player from another app, waiting for search to open it. */
object YtIntake {
    val pending = MutableStateFlow<String?>(null)
    /** a Spotify playlist or album link, for the import dialog */
    val spotify = MutableStateFlow<String?>(null)

    fun offer(text: String?) {
        val t = text?.trim().orEmpty()
        if (SpotifyImport.isLink(t)) spotify.value = t
        else if (YouTubeSource.isLink(t)) pending.value = t
    }
}
