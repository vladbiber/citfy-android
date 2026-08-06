package com.ctify.player.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ctify.player.CtifyApp
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.PlaybackState
import com.ctify.player.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val volume: Float = 1f,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val repository: MusicRepository = (app as CtifyApp).repository

    private var controller: MediaController? = null

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
    }

    init {
        viewModelScope.launch { repository.load(includeDevice = false) }
        connectController()
        startPositionLoop()
    }

    fun refreshLibrary(includeDevice: Boolean) {
        viewModelScope.launch { repository.load(includeDevice) }
    }

    fun importFile(uri: android.net.Uri) {
        viewModelScope.launch { repository.importFile(uri) }
    }

    fun importFolder(treeUri: android.net.Uri) {
        viewModelScope.launch {
            val result = repository.importFolder(treeUri)
            val msg = when {
                result == null -> "Couldn't read that folder"
                result.second == 0 -> "No audio files found in \"${result.first}\""
                else -> "Imported ${result.second} songs · playlist \"${result.first}\" created"
            }
            android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun connectController() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            restoreSnapshot(c)
            syncFromPlayer()
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun restoreSnapshot(c: MediaController) {
        val snap = repository.loadPlayback()
        c.shuffleModeEnabled = snap.shuffle
        c.repeatMode = snap.repeatMode
        c.volume = snap.volume
        val track = repository.trackById(snap.trackId)
        if (track != null && c.mediaItemCount == 0) {
            c.setMediaItem(track.toMediaItem(), snap.positionMs)
            c.prepare()
            c.playWhenReady = false
        }
    }

    private fun startPositionLoop() {
        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _ui.value = _ui.value.copy(
                            positionMs = c.currentPosition,
                            durationMs = c.duration.coerceAtLeast(0L),
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        _ui.value = PlayerUiState(
            currentTrackId = c.currentMediaItem?.mediaId,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            volume = c.volume,
        )
        persistSnapshot()
    }

    private fun persistSnapshot() {
        val c = controller ?: return
        repository.savePlayback(
            PlaybackState(
                trackId = c.currentMediaItem?.mediaId,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                volume = c.volume,
            )
        )
    }

    // ---- playback controls ----

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    /** Appends a track to the end of the current play queue, starting one if nothing is loaded. */
    fun addToQueue(trackId: String) {
        val c = controller ?: return
        val track = repository.trackById(trackId) ?: return
        if (c.mediaItemCount == 0) {
            c.setMediaItem(track.toMediaItem())
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(track.toMediaItem())
        }
        android.widget.Toast.makeText(
            getApplication(), "Added to queue: ${track.title}", android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause()
        else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                // After a track ends, play() alone is a no-op — rewind first so the
                // single tap actually restarts playback.
                Player.STATE_ENDED -> c.seekTo(0)
            }
            c.play()
        }
    }

    fun next() = controller?.seekToNext().let { }
    fun previous() {
        val c = controller ?: return
        // After 5s of playback, the back arrow restarts the current track instead of skipping back.
        if (c.currentPosition > 5000) c.seekTo(0) else c.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _ui.value = _ui.value.copy(positionMs = ms)
    }

    fun seekToFraction(fraction: Float) {
        val dur = controller?.duration ?: return
        if (dur > 0) seekTo((dur * fraction).toLong())
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setVolume(v: Float) {
        controller?.volume = v.coerceIn(0f, 1f)
        _ui.value = _ui.value.copy(volume = v.coerceIn(0f, 1f))
    }

    fun toggleFavorite(id: String) = repository.toggleFavorite(id)

    override fun onCleared() {
        persistSnapshot()
        controller?.release()
        controller = null
        super.onCleared()
    }
}

fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(coverUri?.let(Uri::parse))
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}
