package com.ctify.player.youtube

import kotlinx.coroutines.flow.MutableStateFlow

/** A YouTube link shared into Ctify from another app, waiting for the YouTube page to open it. */
object YtIntake {
    val pending = MutableStateFlow<String?>(null)

    fun offer(text: String?) {
        val t = text?.trim().orEmpty()
        if (YouTubeSource.isLink(t)) pending.value = t
    }
}
