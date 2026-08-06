package com.ctify.player.data

/** A single playable track, either a bundled demo asset or a file scanned from the device. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val coverUri: String?,
    val durationMs: Long,
    val isDemo: Boolean,
    val isImported: Boolean = false,
)

/** A user playlist: an ordered list of track ids, with an optional custom cover image. */
data class Playlist(
    val name: String,
    val trackIds: List<String>,
    val coverUri: String? = null,
)

/** Persisted playback snapshot so the app reopens where the user left off. */
data class PlaybackState(
    val trackId: String? = null,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    val volume: Float = 1f,
)
