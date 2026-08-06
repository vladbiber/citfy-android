package com.ctify.player.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class MusicRepository private constructor(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences("ctify_state", Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow(loadPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val byId = HashMap<String, Track>()

    fun trackById(id: String?): Track? = id?.let { byId[it] }

    fun favoriteTracks(): List<Track> = _tracks.value.filter { it.id in _favorites.value }

    fun playlistTracks(name: String): List<Track> {
        val pl = _playlists.value.firstOrNull { it.name == name } ?: return emptyList()
        return pl.trackIds.mapNotNull { byId[it] }
    }

    /** Loads demo assets, then user-imported files, then merges any device audio. */
    suspend fun load(includeDevice: Boolean) = withContext(Dispatchers.IO) {
        val demo = loadDemoTracks()
        val imported = loadImportedTracks().map { ensureImportedCover(it) }
        val device = if (includeDevice) runCatching { scanDevice() }.getOrDefault(emptyList()) else emptyList()
        val all = imported + demo + device
        byId.clear()
        all.forEach { byId[it.id] = it }
        _tracks.value = all
    }

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
        val current = loadImportedRaw().filterNot { it.id == track.id } + track
        saveImported(current)
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

        val byUri = loadImportedRaw().associateBy { it.uri }.toMutableMap()
        val ids = ArrayList<String>()
        audioDocs.forEach { doc ->
            val track = buildImportedTrack(doc.uri, doc.name)
            byUri[track.uri] = track
            ids.add(track.id)
        }
        saveImported(byUri.values.toList())
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
        val remaining = loadImportedRaw().filterNot { it.id == id }
        saveImported(remaining)
        runCatching { appContext.contentResolver.releasePersistableUriPermission(Uri.parse(byId[id]?.uri ?: ""), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun loadImportedRaw(): List<Track> {
        val raw = prefs.getString("imported", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Track(
                id = o.getString("id"),
                title = o.getString("title"),
                artist = o.getString("artist"),
                album = o.getString("album"),
                uri = o.getString("uri"),
                coverUri = null,
                durationMs = o.optLong("durationMs", 0L),
                isDemo = false,
                isImported = true,
            )
        }
    }

    private fun loadImportedTracks(): List<Track> = loadImportedRaw()

    private fun saveImported(list: List<Track>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id).put("title", t.title).put("artist", t.artist)
                    .put("album", t.album).put("uri", t.uri).put("durationMs", t.durationMs)
            )
        }
        prefs.edit().putString("imported", arr.toString()).apply()
    }

    // ---- favorites ----

    fun toggleFavorite(id: String) {
        val set = _favorites.value.toMutableSet()
        if (!set.add(id)) set.remove(id)
        _favorites.value = set
        prefs.edit().putString("favorites", JSONArray(set.toList()).toString()).apply()
    }

    fun isFavorite(id: String): Boolean = id in _favorites.value

    private fun loadFavorites(): Set<String> {
        val raw = prefs.getString("favorites", null) ?: return emptySet()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    // ---- playlists ----

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _playlists.value.any { it.name == trimmed }) return
        _playlists.value = _playlists.value + Playlist(trimmed, emptyList())
        savePlaylists()
    }

    fun deletePlaylist(name: String) {
        _playlists.value = _playlists.value.filterNot { it.name == name }
        savePlaylists()
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
        savePlaylists()
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
        savePlaylists()
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
        savePlaylists()
    }

    fun addToPlaylist(name: String, trackId: String) {
        _playlists.value = _playlists.value.map {
            if (it.name == name && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
        }
        savePlaylists()
    }

    fun removeFromPlaylist(name: String, trackId: String) {
        _playlists.value = _playlists.value.map {
            if (it.name == name) it.copy(trackIds = it.trackIds.filterNot { id -> id == trackId }) else it
        }
        savePlaylists()
    }

    private fun loadPlaylists(): List<Playlist> {
        val raw = prefs.getString("playlists", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("tracks")
            Playlist(
                o.getString("name"),
                (0 until ids.length()).map { ids.getString(it) },
                o.optString("cover").takeIf { it.isNotEmpty() },
            )
        }
    }

    private fun savePlaylists() {
        val arr = JSONArray()
        _playlists.value.forEach { pl ->
            val o = JSONObject().put("name", pl.name).put("tracks", JSONArray(pl.trackIds))
            pl.coverUri?.let { o.put("cover", it) }
            arr.put(o)
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    // ---- playback snapshot ----

    fun savePlayback(state: PlaybackState) {
        prefs.edit()
            .putString("pb_track", state.trackId)
            .putLong("pb_pos", state.positionMs)
            .putBoolean("pb_shuffle", state.shuffle)
            .putInt("pb_repeat", state.repeatMode)
            .putFloat("pb_volume", state.volume)
            .apply()
    }

    fun loadPlayback(): PlaybackState = PlaybackState(
        trackId = prefs.getString("pb_track", null),
        positionMs = prefs.getLong("pb_pos", 0L),
        shuffle = prefs.getBoolean("pb_shuffle", false),
        repeatMode = prefs.getInt("pb_repeat", 0),
        volume = prefs.getFloat("pb_volume", 1f),
    )

    companion object {
        @Volatile private var instance: MusicRepository? = null
        fun get(context: Context): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext).also { instance = it }
            }
    }
}
