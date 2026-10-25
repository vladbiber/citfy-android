package com.ctify.player.youtube.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ctify.player.net.NetworkGate
import com.ctify.player.youtube.YtAlbum
import com.ctify.player.youtube.YtSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The download queue: what to save, the user's conditions, and the pause after YouTube says stop. */
object Downloads {
    const val PREFS = "ctify-downloads"
    private const val WORK = "yt-downloads"
    const val BACKOFF_MS = 60 * 60 * 1000L

    private lateinit var app: Context
    lateinit var dao: DownloadDao
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val chargingOnly = MutableStateFlow(false)
    /** folder tree picked by the user; null saves into Music/Ctify */
    val folder = MutableStateFlow<Uri?>(null)
    /** when the pause after a 403/429/bot check ends; 0 when not paused */
    val pausedUntil = MutableStateFlow(0L)
    val pauseReason = MutableStateFlow<String?>(null)
    /** songs cancelled while the worker may have them; it drops them at the next chunk */
    internal val cancelled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun init(context: Context, dao: DownloadDao) {
        if (::app.isInitialized) return
        app = context.applicationContext
        this.dao = dao
        val p = prefs()
        chargingOnly.value = p.getBoolean("charging_only", false)
        folder.value = p.getString("folder", null)?.let(Uri::parse)
        pausedUntil.value = p.getLong("paused_until", 0L)
        pauseReason.value = p.getString("pause_reason", null)
        scope.launch { if (dao.pending() > 0) kick() }
    }

    fun all(): Flow<List<DownloadEntity>> = dao.all()

    fun setChargingOnly(v: Boolean) {
        chargingOnly.value = v
        prefs().edit().putBoolean("charging_only", v).apply()
        kick(replace = true)
    }

    /** [tree] from the system folder picker, or null for Music/Ctify. Earlier grants are kept so songs saved there still play. */
    fun setFolder(tree: Uri?) {
        if (tree != null) {
            app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        folder.value = tree
        prefs().edit().putString("folder", tree?.toString()).apply()
    }

    /** The picked folder can't be written any more: go back to Music/Ctify and say so once. */
    internal fun folderLost() {
        if (folder.value == null) return
        setFolder(null)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, "Download folder isn't available. Saving to Music/Guava Player instead.", Toast.LENGTH_LONG).show()
        }
    }

    /** Queues songs the user picked. Songs already saved or queued are left out; returns how many were added. */
    suspend fun enqueue(songs: List<YtSong>, album: YtAlbum? = null, genre: String? = null, heardSongId: Long? = null): Int {
        val now = System.currentTimeMillis()
        val fresh = songs.filter { s ->
            val existing = dao.get(s.videoId)
            existing == null || existing.state == DownloadState.FAILED
        }.mapIndexed { i, s ->
            DownloadEntity(
                videoId = s.videoId,
                title = s.title,
                artist = s.artist,
                album = album?.title ?: s.album,
                albumArtist = album?.artist,
                trackNumber = s.trackNumber,
                trackTotal = album?.songs?.size,
                durationMs = s.durationMs,
                thumbnail = album?.thumbnail ?: s.thumbnail,
                genre = genre,
                groupId = album?.id,
                groupTitle = album?.title,
                heardSongId = heardSongId,
                // keeps album order when several songs share a moment
                addedAt = now + i,
            )
        }
        if (fresh.isEmpty()) return 0
        cancelled.removeAll(fresh.map { it.videoId }.toSet())
        dao.upsert(fresh)
        kick()
        return fresh.size
    }

    fun retryFailed() = scope.launch { dao.retryFailed(); kick() }
    fun remove(videoId: String) = scope.launch { dao.remove(videoId) }
    /** Takes a song off the queue even while it is downloading; saved songs stay saved. */
    fun cancel(videoId: String) = scope.launch { cancelled += videoId; dao.cancel(videoId) }
    fun clearFinished() = scope.launch { dao.clearFinished() }

    /** Starts the worker if it isn't already running or waiting; [replace] applies changed conditions. */
    fun kick(replace: Boolean = false) {
        if (!::app.isInitialized) return
        val wait = (pausedUntil.value - System.currentTimeMillis()).coerceAtLeast(0L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (NetworkGate.wifiOnlyDownloads.value) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly.value)
            .build()
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInitialDelay(wait, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    internal fun pause(reason: String) {
        val until = System.currentTimeMillis() + BACKOFF_MS
        pausedUntil.value = until
        pauseReason.value = reason
        prefs().edit().putLong("paused_until", until).putString("pause_reason", reason).apply()
    }

    internal fun clearPause() {
        if (pausedUntil.value == 0L) return
        pausedUntil.value = 0L
        pauseReason.value = null
        prefs().edit().remove("paused_until").remove("pause_reason").apply()
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
