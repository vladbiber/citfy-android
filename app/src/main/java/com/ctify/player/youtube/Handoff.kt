package com.ctify.player.youtube

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.db.LibraryDao
import com.ctify.player.data.db.TrackEntity
import com.ctify.player.data.db.TrackSource
import com.ctify.player.diag.Diagnostics
import com.ctify.player.diag.Verdict
import com.ctify.player.genre.Genre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** When our downloader can't work until an update, another installed app can take the link. */
object Handoff {
    private val APPS = listOf("com.luc4n3x.levyra" to "Levyra", "com.junkfood.seal" to "Seal")

    /** The installed downloader's name, or null. */
    fun installedApp(context: Context): Pair<String, String>? = APPS.firstOrNull { (pkg, _) ->
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(pkg, 0)
        }.isSuccess
    }

    fun offered(errorClass: String?): Boolean = errorClass != null && Diagnostics.verdictFor(errorClass) == Verdict.WAIT_FOR_UPDATE

    /** Sends the link as text to the installed app, or to a chooser if it won't take it. */
    fun send(context: Context, videoId: String) {
        val url = "https://music.youtube.com/watch?v=$videoId"
        val base = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        val pkg = installedApp(context)?.first
        try {
            if (pkg == null) throw ActivityNotFoundException()
            context.startActivity(Intent(base).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(base, "Download with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        MusicWatcher.expectFiles()
    }
}

/** Picks up audio files another app saves under Music/ after a hand-off, and imports them with a genre. */
object MusicWatcher {
    private lateinit var app: Context
    private lateinit var dao: LibraryDao
    private lateinit var repository: MusicRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    @Volatile private var since = 0L
    @Volatile private var watchUntil = 0L

    fun init(context: Context, dao: LibraryDao, repository: MusicRepository) {
        if (::app.isInitialized) return
        app = context.applicationContext
        this.dao = dao
        this.repository = repository
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = schedule()
        }
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    /** Called after a hand-off: files added in the next half hour are the user's downloads. */
    fun expectFiles() {
        since = System.currentTimeMillis() / 1000 - 5
        watchUntil = System.currentTimeMillis() + 30 * 60 * 1000L
    }

    private fun schedule() {
        if (!::app.isInitialized || System.currentTimeMillis() > watchUntil) return
        pending?.cancel()
        // a download app writes in bursts; wait for it to settle
        pending = scope.launch { delay(3_000); runCatching { importNew() } }
    }

    private suspend fun importNew() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.DATE_ADDED} >= ? AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val args = arrayOf(since.toString(), "%/Music/%")
        val found = ArrayList<TrackEntity>()
        app.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(5).orEmpty()
                if (path.contains("/Music/Ctify/") || path.contains("/Music/Guava Player/")) continue
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                found += TrackEntity(
                    id = "imp_ms_${c.getLong(0)}",
                    source = TrackSource.IMPORTED,
                    title = c.getString(1) ?: path.substringAfterLast('/'),
                    artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    album = c.getString(3) ?: "Unknown album",
                    uri = uri.toString(),
                    durationMs = c.getLong(4),
                    genre = genreOf(uri)?.label,
                    addedAt = System.currentTimeMillis(),
                )
            }
        }
        if (found.isEmpty()) return
        dao.upsertTracks(found)
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        repository.load(includeDevice = app.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED)
    }

    private fun genreOf(uri: Uri): Genre? = meta(uri, MediaMetadataRetriever.METADATA_KEY_GENRE)?.let(Genre::of)

    private fun meta(uri: Uri, key: Int): String? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(app, uri)
            mmr.extractMetadata(key)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }
}
