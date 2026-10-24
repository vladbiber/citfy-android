package com.ctify.player.player

import android.content.Context
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
import com.ctify.player.integration.HttpStatusException
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkBlockedException
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import com.ctify.player.radio.ExternalNowPlaying
import com.ctify.player.radio.LiveTitle
import com.ctify.player.radio.RadioPrefs
import com.ctify.player.radio.RadioRepository
import com.ctify.player.radio.RadioUrlPolicy
import com.ctify.player.radio.RewindRecorder
import com.ctify.player.radio.Station
import com.ctify.player.radio.StreamError
import com.ctify.player.radio.StreamErrors
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
 * An http station is tried over https first. A dropped stream reconnects with backoff instead
 * of skipping; after a few failures the same station's other directory URLs are tried. Errors
 * that will repeat, or too many drops, leave the station "Off air" until the user taps play.
 */
class RadioMode(
    private val context: Context,
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
    /** index into [RadioUrlPolicy.streamCandidates] of the station's URL */
    private var variant = 0
    private var dnsFailed = false
    private var offAir = false
    private var startJob: Job? = null
    private var reconnectJob: Job? = null
    private var pollJob: Job? = null
    private var watchdogJob: Job? = null
    /** set when the station plays through the rewind buffer */
    private var recorder: RewindRecorder? = null

    val active: Boolean get() = station != null

    init {
        player.addListener(object : Player.Listener {
            // while off air the play button is the retry
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady && offAir) retry()
            }
        })
    }

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
        val order = (1..list.size).map { (index + dir * it).mod(list.size) }
        index = order.firstOrNull { !radio.isOffAir(list[it].uuid) } ?: order.first()
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
        closeRecorder()
        radio.endHeard()
        station = null; title = LiveTitle.NONE; status = null; offAir = false
        onActiveChanged(false)
    }

    private fun start(s: Station, fresh: Boolean, autoplay: Boolean = true) {
        val wasActive = active
        startJob?.cancel(); reconnectJob?.cancel()
        if (fresh) { setTitle(LiveTitle.NONE); triedSiblings = false; variant = 0; dnsFailed = false }
        station = s
        offAir = false
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
            val candidates = RadioUrlPolicy.streamCandidates(s.streamUrl)
            val url = try {
                StreamResolver.resolve(candidates[variant.coerceAtMost(candidates.lastIndex)])
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e, (e as? HttpStatusException)?.status)
                return@launch
            }
            val hls = s.hls || url.contains(".m3u8", true)
            closeRecorder()
            val minutes = RadioPrefs.bufferMinutes(context)
            // HLS plays live only in this version
            val playUri = if (minutes > 0 && !hls) {
                val rec = RewindRecorder.create(context, url, minutes, s.bitrate)
                rec.start()
                recorder = rec
                Uri.parse("${RewindRecorder.SCHEME}://${rec.id}")
            } else Uri.parse(url)
            val item = MediaItem.Builder()
                .setMediaId(s.mediaId)
                .setUri(playUri)
                .apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
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
        station?.let { radio.setOffAir(it.uuid, false) }
        if (status != null) { status = null; onChanged() }
    }

    fun onMetadata(metadata: Metadata) {
        val s = station ?: return
        // with the buffer, titles follow the playback position instead (see [followBuffer])
        if (recorder != null) return
        for (i in 0 until metadata.length()) {
            val icy = metadata[i] as? IcyInfo ?: continue
            val parsed = StreamTitles.parse(icy.title, s.name, s.streamUrl)
            setTitle(parsed)
        }
    }

    /** true when the error belonged to a radio stream and was handled here. */
    fun onPlayerError(error: PlaybackException): Boolean {
        if (!active || player.currentMediaItem?.mediaId?.startsWith(Station.MEDIA_PREFIX) != true) return false
        val root = error.rootCause()
        val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            ?: (root as? HttpStatusException)?.status
        failed(root, status, error.errorCode)
        return true
    }

    private fun failed(cause: Throwable, httpStatus: Int?, errorCode: Int = PlaybackException.ERROR_CODE_UNSPECIFIED) {
        val s = station ?: return
        if (offAir) return
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
        // the https try failed for any reason: the URL as filed comes next, right away
        if (variant < RadioUrlPolicy.streamCandidates(s.streamUrl).lastIndex) {
            variant++
            start(s, fresh = false)
            return
        }
        val kind = StreamErrors.classify(cause, httpStatus, errorCode)
        // a name that didn't resolve gets one more go before it counts as gone
        val permanent = kind == StreamError.PERMANENT || (kind == StreamError.DNS && dnsFailed)
        if (kind == StreamError.DNS) dnsFailed = true
        val giveUp = permanent || attempts >= MAX_ATTEMPTS
        if ((permanent || attempts >= SIBLINGS_AFTER) && !triedSiblings) {
            triedSiblings = true
            scope.launch {
                val other = radio.siblings(s).firstOrNull()
                if (station?.uuid != s.uuid) return@launch
                if (other != null) {
                    status = "Trying another stream for ${s.name}"
                    variant = 0
                    start(other.copy(uuid = s.uuid, name = s.name, favicon = s.favicon ?: other.favicon), fresh = false)
                } else if (giveUp) goOffAir(s) else scheduleReconnect(s)
            }
            return
        }
        if (giveUp) goOffAir(s) else scheduleReconnect(s)
    }

    private fun goOffAir(s: Station) {
        offAir = true
        reconnectJob?.cancel()
        closeRecorder()
        player.stop()
        // paused, so the next tap on play reaches [retry]
        player.playWhenReady = false
        radio.setOffAir(s.uuid, true)
        status = OFF_AIR
        onChanged()
    }

    /** One more round after a tap on play: each URL variant once, no backoff, no new sibling lookup. */
    private fun retry() {
        val s = station ?: return
        player.stop()
        attempts = MAX_ATTEMPTS - 1
        triedSiblings = true
        variant = 0
        status = "Reconnecting…"
        start(s, fresh = false)
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
                followBuffer()
                delay(1000)
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
                        setTitle(parsed)
                    }
                }
                delay(interval)
            }
        }
    }

    /** Buffered window in player time, or null when the station plays straight from the server. */
    fun window(): LongRange? {
        val rec = recorder ?: return null
        return rec.msAt(rec.windowStart)..rec.msAt(rec.written)
    }

    /** Where a seek may land: inside the buffer, a little back from both ends. */
    fun clampSeek(ms: Long): Long? {
        val w = window() ?: return null
        val lo = w.first + EDGE_MS
        val hi = (w.last - EDGE_MS).coerceAtLeast(lo)
        return ms.coerceIn(lo, hi)
    }

    /** Shows the title that was on air at the playback position and recovers from falling behind. */
    private fun followBuffer() {
        val rec = recorder ?: return
        val s = station ?: return
        val pos = player.currentPosition
        if (rec.offsetAt(pos) < rec.windowStart && rec.written > 0) {
            clampSeek(pos)?.let { player.seekTo(it) }
            return
        }
        val parsed = StreamTitles.parse(rec.titleAt(rec.offsetAt(pos)), s.name, s.streamUrl)
        setTitle(parsed)
    }

    private fun setTitle(t: LiveTitle) {
        if (t == title) return
        title = t
        station?.let { radio.logHeard(it, t) }
        onChanged()
    }

    private fun closeRecorder() {
        recorder?.close()
        recorder = null
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
        const val MAX_ATTEMPTS = 5
        const val OFF_AIR = "Off air · tap play to retry"
        const val STALL_MS = 20_000L
        const val EDGE_MS = 2_000L
    }
}
