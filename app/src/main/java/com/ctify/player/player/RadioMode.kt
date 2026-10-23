package com.ctify.player.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.ctify.player.diag.Diagnostics
import com.ctify.player.diag.Failure
import com.ctify.player.diag.classOf
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkBlockedException
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import com.ctify.player.radio.ExternalNowPlaying
import com.ctify.player.radio.LiveTitle
import com.ctify.player.radio.RadioRepository
import com.ctify.player.radio.Station
import com.ctify.player.radio.StreamResolver
import com.ctify.player.radio.StreamTitles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live radio inside [PlaybackController]: starting a station, its title, and staying on air.
 * A dropped stream reconnects with backoff instead of skipping; after a few failures the
 * same station's other directory URLs are tried.
 */
class RadioMode(
    private val player: Player,
    private val radio: RadioRepository,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
) {
    var station: Station? = null
        private set
    var title: LiveTitle = LiveTitle.NONE
        private set
    /** "Reconnecting in 4 s", "Radio is switched off in Settings"… shown under the station name */
    var status: String? = null
        private set

    private var list: List<Station> = emptyList()
    private var index = 0
    private var attempts = 0
    private var triedSiblings = false
    private var startJob: Job? = null
    private var reconnectJob: Job? = null
    private var pollJob: Job? = null
    private var watchdogJob: Job? = null

    val active: Boolean get() = station != null

    fun play(stations: List<Station>, startIndex: Int) {
        if (stations.isEmpty()) return
        list = stations
        index = startIndex.coerceIn(0, stations.lastIndex)
        attempts = 0
        start(stations[index], fresh = true)
    }

    fun next() = step(+1)
    fun previous() = step(-1)

    private fun step(dir: Int) {
        if (list.isEmpty()) return
        index = (index + dir).mod(list.size)
        attempts = 0
        start(list[index], fresh = true)
    }

    /** Puts a station back after an app restart without starting it. */
    fun restore(s: Station) {
        list = listOf(s); index = 0
        start(s, fresh = true, autoplay = false)
    }

    /** Leaves radio mode; the caller is about to load local music. */
    fun stop() {
        if (!active) return
        cancelJobs()
        station = null; title = LiveTitle.NONE; status = null
        onActiveChanged(false)
    }

    private fun start(s: Station, fresh: Boolean, autoplay: Boolean = true) {
        val wasActive = active
        startJob?.cancel(); reconnectJob?.cancel()
        if (fresh) { title = LiveTitle.NONE; triedSiblings = false }
        station = s
        if (!wasActive) onActiveChanged(true)
        NetworkGate.blockReason(Feature.RADIO, Traffic.STREAM)?.let { reason ->
            status = reason
            player.stop()
            onChanged()
            return
        }
        status = if (fresh) "Connecting…" else status
        onChanged()
        startJob = scope.launch {
            val url = try {
                StreamResolver.resolve(s.streamUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e, null)
                return@launch
            }
            val item = MediaItem.Builder()
                .setMediaId(s.mediaId)
                .setUri(Uri.parse(url))
                .apply { if (s.hls || url.contains(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.name)
                        .setStation(s.name)
                        .setArtworkUri(s.favicon?.let(Uri::parse))
                        .setIsPlayable(true)
                        .build()
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = autoplay
            if (fresh && autoplay) radio.recordPlay(s)
            startPolling(s)
            startWatchdog()
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!active || !isPlaying) return
        attempts = 0
        if (status != null) { status = null; onChanged() }
    }

    fun onMetadata(metadata: Metadata) {
        val s = station ?: return
        for (i in 0 until metadata.length()) {
            val icy = metadata[i] as? IcyInfo ?: continue
            val parsed = StreamTitles.parse(icy.title, s.name, s.streamUrl)
            if (parsed != title) { title = parsed; onChanged() }
        }
    }

    /** true when the error belonged to a radio stream and was handled here. */
    fun onPlayerError(error: PlaybackException): Boolean {
        if (!active || player.currentMediaItem?.mediaId?.startsWith(Station.MEDIA_PREFIX) != true) return false
        val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        failed(error.rootCause(), status)
        return true
    }

    private fun failed(cause: Throwable, httpStatus: Int?) {
        val s = station ?: return
        if (cause is NetworkBlockedException) {
            status = cause.message
            onChanged()
            return
        }
        attempts++
        if (attempts == 1) {
            Diagnostics.record(
                Failure(
                    feature = "Radio",
                    action = "play stream",
                    component = "station server",
                    item = s.uuid,
                    httpStatus = httpStatus,
                    error = cause,
                    errorClass = classOf(cause, httpStatus),
                )
            )
        }
        if (attempts >= SIBLINGS_AFTER && !triedSiblings) {
            triedSiblings = true
            scope.launch {
                val other = radio.siblings(s).firstOrNull()
                if (other != null && station?.uuid == s.uuid) {
                    status = "Trying another stream for ${s.name}"
                    start(other.copy(uuid = s.uuid, name = s.name, favicon = s.favicon ?: other.favicon), fresh = false)
                } else scheduleReconnect(s)
            }
            return
        }
        scheduleReconnect(s)
    }

    private fun scheduleReconnect(s: Station) {
        val wait = BACKOFF_S[(attempts - 1).coerceIn(0, BACKOFF_S.lastIndex)]
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (left in wait downTo 1) {
                status = "Reconnecting in $left s"
                onChanged()
                delay(1000)
            }
            status = "Reconnecting…"
            onChanged()
            if (station?.uuid == s.uuid) start(station!!, fresh = false)
        }
    }

    /** A stream that buffers for 20 s without an error is treated as dropped. */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var bufferingSince = 0L
            while (isActive && active) {
                val stuck = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
                val now = System.currentTimeMillis()
                if (!stuck) bufferingSince = 0L
                else if (bufferingSince == 0L) bufferingSince = now
                else if (now - bufferingSince > STALL_MS && reconnectJob?.isActive != true) {
                    bufferingSince = 0L
                    failed(java.io.InterruptedIOException("Stream stalled for 20 s"), null)
                }
                delay(2000)
            }
        }
    }

    /** NTS and FIP publish titles separately; poll them only while that station plays. */
    private fun startPolling(s: Station) {
        pollJob?.cancel()
        val (interval, fetch) = ExternalNowPlaying.pollerFor(s.streamUrl) ?: return
        pollJob = scope.launch {
            while (isActive && station?.uuid == s.uuid) {
                if (player.isPlaying) {
                    runCatching { fetch() }.getOrNull()?.let { raw ->
                        val parsed = StreamTitles.parse(raw, s.name)
                        if (parsed != title) { title = parsed; onChanged() }
                    }
                }
                delay(interval)
            }
        }
    }

    private fun cancelJobs() {
        startJob?.cancel(); reconnectJob?.cancel(); pollJob?.cancel(); watchdogJob?.cancel()
    }

    private fun Throwable.rootCause(): Throwable {
        var t: Throwable = this
        while (true) {
            if (t is NetworkBlockedException) return t
            t = t.cause ?: return t
        }
    }

    private companion object {
        val BACKOFF_S = intArrayOf(1, 2, 4, 8, 15, 30)
        const val SIBLINGS_AFTER = 3
        const val STALL_MS = 20_000L
    }
}
