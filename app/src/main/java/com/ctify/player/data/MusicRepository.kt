package com.ctify.player.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.ctify.player.data.db.CtifyDatabase
import com.ctify.player.data.db.FavoriteEntity
import com.ctify.player.data.db.PlaybackSnapshotEntity
import com.ctify.player.data.db.TrackEntity
import com.ctify.player.data.db.TrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Single source of truth for the music library and lightweight user state.
 *
 * Mirrors the original C `ctify`: bundled demo tracks plus an optional scan of the
 * device's local audio, with favorites and `.m3u`-style playlists persisted between runs.
 */
class MusicRepository private constructor(private val appContext: Context, database: CtifyDatabase) {

    /** pre-Room storage, read once by [migrateFromPrefs] */
    private val prefs = appContext.getSharedPreferences("ctify_state", Context.MODE_PRIVATE)
    private val dao = database.library()
    /** one writer thread keeps database writes in the order the user made them */
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var stateLoaded = false

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val byId = HashMap<String, Track>()
    /** the app and the playback service can both trigger a load */
    private val loadLock = Mutex()

    fun trackById(id: String?): Track? = id?.let { byId[it] }

    fun favoriteTracks(): List<Track> = _tracks.value.filter { it.id in _favorites.value }

    fun playlistTracks(name: String): List<Track> {
        val pl = _playlists.value.firstOrNull { it.name == name } ?: return emptyList()
        return pl.trackIds.mapNotNull { byId[it] }
    }

    /** Loads demo assets, then user-imported files, then merges any device audio. */
    suspend fun load(includeDevice: Boolean) = withContext(Dispatchers.IO) { loadLock.withLock {
        if (!stateLoaded) {
            migrateFromPrefs()
            // in memory from here on; later loads must not overwrite queued writes
            _favorites.value = dao.favoriteIds().toSet()
            _playlists.value = dao.playlists().map { Playlist(it.name, dao.playlistTrackIds(it.id), it.coverUri) }
            stateLoaded = true
        }
        val demo = loadDemoTracks()
        val imported = loadImportedTracks().map { ensureImportedCover(it) }
        val device = if (includeDevice) runCatching { scanDevice() }.getOrDefault(emptyList()) else emptyList()
        val all = imported + demo + device
        byId.clear()
        all.forEach { byId[it.id] = it }
        _tracks.value = all
    } }

    private fun loadDemoTracks(): List<Track> {
        val json = appContext.assets.open("demo/manifest.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("tracks")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Track(
                id = o.getString("id"),
                title = o.getString("title"),
                artist = o.getString("artist"),
                album = o.getString("album"),
                uri = "file:///android_asset/demo/${o.getString("file")}",
                coverUri = "file:///android_asset/demo/${o.getString("cover")}",
                durationMs = o.getInt("durationSec") * 1000L,
                isDemo = true,
            )
        }
    }

    private fun scanDevice(): List<Track> {
        val out = ArrayList<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        appContext.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artBase = Uri.parse("content://media/external/audio/albumart")
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                out += Track(
                    id = "dev_$id",
                    title = c.getString(titleCol) ?: "Unknown",
                    artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    album = c.getString(albumCol) ?: "Unknown album",
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    coverUri = ContentUris.withAppendedId(artBase, albumId).toString(),
                    durationMs = c.getLong(durCol),
                    isDemo = false,
                )
            }
        }
        return out
    }

    // ---- imported files (Storage Access Framework) ----

    /**
     * Imports a user-picked audio file: takes a persistable read grant, reads its tags,
     * stores it, and refreshes the library. Returns the new track id, or null on failure.
     */
    suspend fun importFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val track = buildImportedTrack(uri, queryDisplayName(uri))
        dao.upsertTracks(listOf(track.toEntity()))
        load(includeDevice = false)
        track.id
    }

    /**
     * Imports every audio file under a picked folder (recursively) and creates/refreshes a
     * playlist named after that folder. Returns Pair(folderName, importedCount), or null on failure.
     */
    suspend fun importFolder(treeUri: Uri): Pair<String, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return@withContext null
        val folderName = root.name ?: "Folder"

        val audioDocs = ArrayList<DocumentFile>()
        collectAudio(root, audioDocs, depth = 0)
        if (audioDocs.isEmpty()) return@withContext folderName to 0

        val tracks = audioDocs.map { buildImportedTrack(it.uri, it.name) }
        val ids = tracks.map { it.id }
        dao.upsertTracks(tracks.map { it.toEntity() })
        createPlaylistWithTracks(folderName, ids)
        load(includeDevice = false)
        folderName to ids.size
    }

    private fun collectAudio(dir: DocumentFile, out: MutableList<DocumentFile>, depth: Int) {
        if (depth > 8) return
        dir.listFiles().forEach { doc ->
            when {
                doc.isDirectory -> collectAudio(doc, out, depth + 1)
                doc.isFile && isAudioDoc(doc) -> out.add(doc)
            }
        }
    }

    private fun isAudioDoc(doc: DocumentFile): Boolean {
        if (doc.type?.startsWith("audio/") == true) return true
        val name = doc.name?.lowercase() ?: return false
        return listOf(".mp3", ".flac", ".ogg", ".m4a", ".wav", ".aac", ".opus", ".wma")
            .any { name.endsWith(it) }
    }

    private fun buildImportedTrack(uri: Uri, displayName: String?): Track {
        val fileName = displayName ?: queryDisplayName(uri) ?: "Imported track"
        val id = "imp_${uri.toString().hashCode()}"
        var title = fileName.substringBeforeLast('.')
        var artist = "Imported"
        var album = "Imported files"
        var durationMs = 0L
        var coverUri: String? = null
        runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(appContext, uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
                mmr.embeddedPicture?.let { bytes ->
                    val f = coverCacheFile(id)
                    f.writeBytes(bytes)
                    coverUri = Uri.fromFile(f).toString()
                }
            } finally {
                mmr.release()
            }
        }
        return Track(id, title, artist, album, uri.toString(), coverUri, durationMs, isDemo = false, isImported = true)
    }

    // ---- embedded cover art for imported files ----

    private fun coverCacheFile(id: String): File {
        val dir = File(appContext.filesDir, "covers")
        dir.mkdirs()
        return File(dir, "$id.jpg")
    }

    /**
     * Fills in the cover of an imported track from the audio file's embedded artwork.
     * Extracted images are cached in filesDir; a `.none` marker avoids re-probing files
     * that have no artwork on every load.
     */
    private fun ensureImportedCover(t: Track): Track {
        if (t.coverUri != null) return t
        val cached = coverCacheFile(t.id)
        if (cached.exists()) return t.copy(coverUri = Uri.fromFile(cached).toString())
        val marker = File(cached.parentFile, "${t.id}.none")
        if (marker.exists()) return t
        val bytes = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(appContext, Uri.parse(t.uri))
                mmr.embeddedPicture
            } finally {
                mmr.release()
            }
        }.getOrNull()
        return if (bytes != null) {
            cached.writeBytes(bytes)
            t.copy(coverUri = Uri.fromFile(cached).toString())
        } else {
            runCatching { marker.createNewFile() }
            t
        }
    }

    fun removeImported(id: String) {
        writer.launch { dao.deleteTrack(id) }
        runCatching { appContext.contentResolver.releasePersistableUriPermission(Uri.parse(byId[id]?.uri ?: ""), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private suspend fun loadImportedTracks(): List<Track> =
        dao.tracksBySource(TrackSource.IMPORTED).map {
            Track(it.id, it.title, it.artist, it.album, it.uri, null, it.durationMs, isDemo = false, isImported = true)
        }

    private fun Track.toEntity() = TrackEntity(
        id = id,
        source = TrackSource.IMPORTED,
        title = title,
        artist = artist,
        album = album,
        uri = uri,
        durationMs = durationMs,
        addedAt = System.currentTimeMillis(),
    )

    // ---- favorites ----

    fun toggleFavorite(id: String) {
        val set = _favorites.value.toMutableSet()
        if (!set.add(id)) set.remove(id)
        _favorites.value = set
        val added = id in set
        writer.launch {
            if (added) dao.upsertFavorites(listOf(FavoriteEntity(id, System.currentTimeMillis())))
            else dao.deleteFavorite(id)
        }
    }

    fun isFavorite(id: String): Boolean = id in _favorites.value

    // ---- playlists ----

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _playlists.value.any { it.name == trimmed }) return
        _playlists.value = _playlists.value + Playlist(trimmed, emptyList())
        savePlaylist(trimmed)
    }

    fun deletePlaylist(name: String) {
        _playlists.value = _playlists.value.filterNot { it.name == name }
        writer.launch { dao.deletePlaylist(name) }
    }

    /** Renames a playlist. Returns false if the new name is empty or already taken. */
    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == oldName) return true
        if (_playlists.value.any { it.name == trimmed }) return false
        _playlists.value = _playlists.value.map {
            if (it.name == oldName) it.copy(name = trimmed) else it
        }
        writer.launch { dao.renamePlaylist(oldName, trimmed) }
        return true
    }

    /** Sets a custom cover image for a playlist, keeping a persistable read grant on the uri. */
    fun setPlaylistCover(name: String, uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        _playlists.value = _playlists.value.map {
            if (it.name == name) it.copy(coverUri = uri.toString()) else it
        }
        savePlaylist(name)
    }

    /** Creates a playlist with the given tracks, or appends to it if one already exists by name. */
    fun createPlaylistWithTracks(name: String, trackIds: List<String>) {
        val trimmed = name.trim().ifEmpty { "Folder" }
        val existing = _playlists.value.firstOrNull { it.name == trimmed }
        _playlists.value = if (existing == null) {
            _playlists.value + Playlist(trimmed, trackIds.distinct())
        } else {
            _playlists.value.map {
                if (it.name == trimmed) it.copy(trackIds = (it.trackIds + trackIds).distinct()) else it
            }
        }
        savePlaylist(trimmed)
    }

    fun addToPlaylist(name: String, trackId: String) {
        _playlists.value = _playlists.value.map {
            if (it.name == name && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
        }
        savePlaylist(name)
    }

    fun removeFromPlaylist(name: String, trackId: String) {
        _playlists.value = _playlists.value.map {
            if (it.name == name) it.copy(trackIds = it.trackIds.filterNot { id -> id == trackId }) else it
        }
        savePlaylist(name)
    }

    /** Queues a write of the in-memory state of one playlist. */
    private fun savePlaylist(name: String) {
        val list = _playlists.value
        val index = list.indexOfFirst { it.name == name }
        val pl = list.getOrNull(index) ?: return
        writer.launch { dao.savePlaylist(pl.name, pl.coverUri, index, pl.trackIds) }
    }

    // ---- one-time move from SharedPreferences ----

    private suspend fun migrateFromPrefs() {
        if (prefs.getBoolean(MIGRATED_KEY, false)) return
        prefs.getString("imported", null)?.let { raw ->
            val arr = JSONArray(raw)
            val now = System.currentTimeMillis()
            dao.upsertTracks((0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrackEntity(
                    id = o.getString("id"),
                    source = TrackSource.IMPORTED,
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    album = o.getString("album"),
                    uri = o.getString("uri"),
                    durationMs = o.optLong("durationMs", 0L),
                    addedAt = now + i,
                )
            })
        }
        prefs.getString("favorites", null)?.let { raw ->
            val arr = JSONArray(raw)
            val now = System.currentTimeMillis()
            dao.upsertFavorites((0 until arr.length()).map { FavoriteEntity(arr.getString(it), now + it) })
        }
        prefs.getString("playlists", null)?.let { raw ->
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("tracks")
                dao.savePlaylist(
                    o.getString("name"),
                    o.optString("cover").takeIf { it.isNotEmpty() },
                    i,
                    (0 until ids.length()).map { ids.getString(it) },
                )
            }
        }
        if (prefs.contains("pb_track") || prefs.contains("pb_volume")) {
            dao.saveSnapshot(
                PlaybackSnapshotEntity(
                    trackId = prefs.getString("pb_track", null),
                    positionMs = prefs.getLong("pb_pos", 0L),
                    shuffle = prefs.getBoolean("pb_shuffle", false),
                    repeatMode = prefs.getInt("pb_repeat", 0),
                    volume = prefs.getFloat("pb_volume", 1f),
                )
            )
        }
        // the old keys stay as a fallback copy; only the flag is new
        prefs.edit().putBoolean(MIGRATED_KEY, true).commit()
    }

    // ---- playback snapshot ----

    fun savePlayback(state: PlaybackState) {
        writer.launch {
            dao.saveSnapshot(
                PlaybackSnapshotEntity(
                    trackId = state.trackId,
                    positionMs = state.positionMs,
                    shuffle = state.shuffle,
                    repeatMode = state.repeatMode,
                    volume = state.volume,
                )
            )
        }
    }

    suspend fun loadPlayback(): PlaybackState = withContext(Dispatchers.IO) {
        val s = dao.snapshot() ?: return@withContext PlaybackState()
        PlaybackState(s.trackId, s.positionMs, s.shuffle, s.repeatMode, s.volume)
    }

    companion object {
        private const val MIGRATED_KEY = "migrated_to_room_v1"
        @Volatile private var instance: MusicRepository? = null
        fun get(context: Context, database: CtifyDatabase): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext, database).also { instance = it }
            }
    }
}
