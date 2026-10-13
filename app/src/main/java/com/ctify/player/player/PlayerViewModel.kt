package com.ctify.player.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
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
    val queueIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** off in screenshot tests, where no media session can be bound */
        @Volatile var connectService = true
    }

    val repository: MusicRepository = (app as CtifyApp).repository

    private var controller: MediaController? = null

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED && PlayerHub.reverseOn.value) reverseStep(+1)
        }
    }

    private val reverser = ReverseDecoder(app)
    /** the forward queue that reverse mode walks through, track by track */
    private var reverseQueue: List<Track> = emptyList()
    private var reverseIndex = 0

    init {
        PlayerHub.init(app)
        PlayerHub.controls = this
        viewModelScope.launch { repository.load(includeDevice = false) }
        if (connectService) connectController()
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
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            restoreSnapshot(c)
            applyRate()
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
                            positionMs = shownPosition(c),
                            durationMs = c.duration.coerceAtLeast(0L),
                        )
                        publish()
                    }
                }
                delay(250)
            }
        }
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        val rev = PlayerHub.reverseOn.value
        _ui.value = PlayerUiState(
            currentTrackId = c.currentMediaItem?.mediaId?.removePrefix(REV),
            isPlaying = c.isPlaying,
            positionMs = shownPosition(c),
            durationMs = c.duration.coerceAtLeast(0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            volume = c.volume,
            queueIds = if (rev) reverseQueue.map { it.id } else (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId },
            currentIndex = if (rev) reverseIndex else c.currentMediaItemIndex,
        )
        publish()
        if (!rev) persistSnapshot()
    }

    /** In reverse mode the file runs backwards, so the shown position is mirrored. */
    private fun shownPosition(c: MediaController): Long {
        val pos = c.currentPosition.coerceAtLeast(0L)
        return if (PlayerHub.reverseOn.value && c.duration > 0) (c.duration - pos).coerceAtLeast(0L) else pos
    }

    private fun publish() {
        val u = _ui.value
        val track = repository.trackById(u.currentTrackId)
        PlayerHub.playback.value = if (track == null) null else NowState(
            track = track,
            isPlaying = u.isPlaying,
            progressMs = u.positionMs.toInt(),
            durationMs = if (u.durationMs > 0) u.durationMs else track.durationMs,
            shuffle = u.shuffle,
            repeat = when (u.repeatMode) {
                Player.REPEAT_MODE_ONE -> "track"
                Player.REPEAT_MODE_ALL -> "context"
                else -> "off"
            },
            volumePercent = (u.volume * 100).toInt(),
        )
    }

    /** Speed and pitch move together, like slowva's resampler. */
    fun applyRate() {
        val r = PlayerHub.fx.rate
        controller?.playbackParameters = PlaybackParameters(r, r)
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
        if (PlayerHub.reverseOn.value) {
            reverseQueue = tracks
            reverseIndex = startIndex.coerceIn(0, tracks.lastIndex)
            viewModelScope.launch { launchReverse(tracks[reverseIndex], 0L) }
            return
        }
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
                // After a track ends, play() alone does nothing; rewind first so the
                // single tap actually restarts playback.
                Player.STATE_ENDED -> c.seekTo(0)
            }
            c.play()
        }
    }

    /** Jumps to a specific song in the current play queue. */
    fun playQueueIndex(index: Int) {
        if (PlayerHub.reverseOn.value) {
            val t = reverseQueue.getOrNull(index) ?: return
            reverseIndex = index
            viewModelScope.launch { launchReverse(t, 0L) }
            return
        }
        val c = controller ?: return
        if (index < 0 || index >= c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun next() {
        if (PlayerHub.reverseOn.value) reverseStep(+1) else controller?.seekToNext()
    }

    fun previous() {
        if (PlayerHub.reverseOn.value) { reverseStep(-1); return }
        val c = controller ?: return
        // After 5s of playback, the back arrow restarts the current track instead of skipping back.
        if (c.currentPosition > 5000) c.seekTo(0) else c.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        val c = controller ?: return
        if (PlayerHub.reverseOn.value && c.duration > 0) c.seekTo((c.duration - ms).coerceAtLeast(0L))
        else c.seekTo(ms)
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

    // ---- reverse mode ----

    fun toggleReverse() {
        viewModelScope.launch { if (PlayerHub.reverseOn.value) leaveReverse() else enterReverse() }
    }

    private suspend fun enterReverse() {
        val c = controller ?: return
        val id = c.currentMediaItem?.mediaId ?: return
        val track = repository.trackById(id) ?: return
        reverseQueue = (0 until c.mediaItemCount).mapNotNull { repository.trackById(c.getMediaItemAt(it).mediaId) }
            .ifEmpty { listOf(track) }
        reverseIndex = reverseQueue.indexOfFirst { it.id == id }.coerceAtLeast(0)
        val fwd = c.currentPosition.coerceAtLeast(0L)
        val dur = c.duration.takeIf { it > 0 } ?: track.durationMs
        PlayerHub.reverseOn.value = true
        launchReverse(track, (dur - fwd).coerceAtLeast(0L))
    }

    /** Starts [track] reversed, [revStartMs] into the reversed file. */
    private suspend fun launchReverse(track: Track, revStartMs: Long) {
        val c = controller ?: return
        PlayerHub.reversePreparing.value = true
        c.pause()
        val file = reverser.reversed(track).getOrElse {
            PlayerHub.reversePreparing.value = false
            PlayerHub.reverseOn.value = false
            android.widget.Toast.makeText(getApplication(), "Reverse failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        PlayerHub.reversePreparing.value = false
        if (!PlayerHub.reverseOn.value) return
        val item = track.toMediaItem().buildUpon()
            .setMediaId(REV + track.id)
            .setUri(Uri.fromFile(file))
            .build()
        c.setMediaItem(item, revStartMs)
        c.prepare()
        c.play()
    }

    private fun reverseStep(dir: Int) {
        val next = reverseIndex + dir
        val track = reverseQueue.getOrNull(next) ?: run {
            if (dir < 0) controller?.seekTo(0)
            return
        }
        reverseIndex = next
        viewModelScope.launch { launchReverse(track, 0L) }
    }

    private fun leaveReverse() {
        val c = controller ?: return
        val shown = shownPosition(c)
        val wasPlaying = c.isPlaying
        PlayerHub.reverseOn.value = false
        if (reverseQueue.isEmpty()) return
        c.setMediaItems(reverseQueue.map { it.toMediaItem() }, reverseIndex.coerceIn(0, reverseQueue.lastIndex), shown)
        c.prepare()
        if (wasPlaying) c.play()
    }

    fun toggleFavorite(id: String) = repository.toggleFavorite(id)

    override fun onCleared() {
        if (PlayerHub.controls === this) PlayerHub.controls = null
        persistSnapshot()
        controller?.release()
        controller = null
        super.onCleared()
    }
}

private const val REV = "rev:"

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
