package com.ctify.player.youtube.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ctify.player.CtifyApp
import com.ctify.player.data.db.TrackEntity
import com.ctify.player.data.db.TrackSource
import com.ctify.player.diag.Diagnostics
import com.ctify.player.diag.Failure
import com.ctify.player.diag.classOf
import com.ctify.player.genre.Genre
import com.ctify.player.net.NetworkBlockedException
import com.ctify.player.youtube.StreamResolver
import com.ctify.player.youtube.YtFailures
import com.ctify.player.youtube.YtHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Saves queued songs one at a time, with a short random pause between them. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val ctify = context.applicationContext as CtifyApp
    private val dao = ctify.database.downloads()
    private val files = SongFile(context, dao)

    override suspend fun doWork(): Result {
        if (Downloads.pausedUntil.value > System.currentTimeMillis()) return Result.retry()
        Downloads.clearPause()
        runCatching { setForeground(foreground("Getting ready")) }
        var saved = 0
        while (!isStopped) {
            val item = dao.next() ?: break
            dao.setState(item.videoId, DownloadState.RUNNING)
            try {
                if (skipIfOwned(item)) continue
                setProgressNote(item)
                save(item)
                saved++
            } catch (e: CancellationException) {
                dao.setState(item.videoId, DownloadState.QUEUED)
                throw e
            } catch (e: Throwable) {
                if (isStopped) { dao.setState(item.videoId, DownloadState.QUEUED); break }
                if (e is NetworkBlockedException || YtFailures.isNetwork(e)) {
                    // switched off, offline or no network: try again when conditions allow
                    dao.setState(item.videoId, DownloadState.QUEUED)
                    record(item, e)
                    return if (e is NetworkBlockedException) Result.success() else Result.retry()
                }
                record(item, e)
                if (YtFailures.needsBackoff(e)) {
                    dao.setState(item.videoId, DownloadState.QUEUED, "YouTube asked us to slow down")
                    Downloads.pause(pauseNote(e))
                    return Result.retry()
                }
                dao.setState(item.videoId, DownloadState.FAILED, e.message?.take(200), errorClass(e))
            }
            if (dao.pending() > 0) delay(Random.nextLong(4_000, 12_000))
        }
        if (saved > 0) ctify.repository.refreshYoutube()
        return Result.success()
    }

    private suspend fun skipIfOwned(item: DownloadEntity): Boolean {
        val owned = dao.trackForVideo(item.videoId) ?: dao.sameSong(item.title, item.artist, item.durationMs) ?: return false
        dao.done(item.videoId, owned)
        dao.setState(item.videoId, DownloadState.SKIPPED, "Already in your library")
        return true
    }

    private suspend fun save(item: DownloadEntity) {
        val part = files.fetch(item) { isStopped }
        val cover = files.cover(item)
        val genre = item.genre?.let { Genre.of(it)?.label ?: it }
        val tagged = files.tag(
            files.remux(part),
            M4aTags(
                title = item.title,
                artist = item.artist,
                album = item.album ?: "",
                albumArtist = item.albumArtist ?: item.artist,
                trackNumber = item.trackNumber ?: 0,
                trackTotal = item.trackTotal ?: 0,
                year = item.year?.toString() ?: "",
                genre = genre ?: "",
                comment = "https://music.youtube.com/watch?v=${item.videoId}",
                cover = cover,
            ),
        )
        val uri = files.save(tagged, item)
        val trackId = "yt_${item.videoId}"
        files.keepCover(item.videoId, cover)
        ctify.database.library().upsertTracks(
            listOf(
                TrackEntity(
                    id = trackId,
                    source = TrackSource.YOUTUBE,
                    title = item.title,
                    artist = item.artist,
                    album = item.album ?: "YouTube",
                    albumArtist = item.albumArtist,
                    trackNumber = item.trackNumber,
                    year = item.year,
                    uri = uri.toString(),
                    durationMs = item.durationMs,
                    genre = genre,
                    youtubeVideoId = item.videoId,
                    addedAt = System.currentTimeMillis(),
                )
            )
        )
        item.heardSongId?.let { id ->
            ctify.database.heardSongs().setVideoId(id, item.videoId)
            ctify.database.heardSongs().setDownloaded(id, trackId)
        }
        dao.done(item.videoId, trackId)
        files.cleanup(item.videoId)
        StreamResolver.invalidate(item.videoId)
    }

    private fun errorClass(e: Throwable) = YtFailures.errorClass(e) ?: classOf(e, (e as? YtHttpException)?.status)

    private fun record(item: DownloadEntity, e: Throwable) = Diagnostics.record(
        Failure(
            feature = "YouTube",
            action = "Download song",
            component = StreamResolver.COMPONENT,
            item = item.videoId,
            error = e,
            httpStatus = (e as? YtHttpException)?.status,
            errorClass = errorClass(e),
        )
    )

    private fun pauseNote(e: Throwable): String {
        val cm = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
        val vpn = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        val base = if (errorClass(e) == "yt_bot_check") "YouTube asked to confirm you're not a bot" else "YouTube refused (${e.message?.take(80)})"
        return if (vpn) "$base. A VPN is on; turning it off usually helps." else base
    }

    private suspend fun setProgressNote(item: DownloadEntity) {
        val group = item.groupId?.let { g -> item.trackNumber?.let { n -> " ($n/${item.trackTotal ?: "?"})" } } ?: ""
        runCatching { setForeground(foreground("${item.title}$group")) }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("Saving songs")

    private fun foreground(text: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Saving from YouTube")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIFICATION_ID = 4107
    }
}
