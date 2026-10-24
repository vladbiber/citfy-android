package com.ctify.player.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.ctify.player.data.db.CtifyDatabase
import com.ctify.player.data.db.FavoriteEntity
import com.ctify.player.data.db.PlayEntity
import com.ctify.player.data.db.PlaybackSnapshotEntity
import com.ctify.player.data.db.TrackEntity
import com.ctify.player.data.db.TrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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

    /** pre-Room storage, read once by [LegacyPrefs] */
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

    fun trackById(id: String?): Track? = id?.let { synchronized(byId) { byId[it] } }

    /** Makes streamed YouTube tracks findable by id without adding them to the library. */
    fun remember(tracks: List<Track>) = synchronized(byId) { tracks.forEach { byId.putIfAbsent(it.id, it) } }

    fun favoriteTracks(): List<Track> = _tracks.value.filter { it.id in _favorites.value }

    fun playlistTracks(name: String): List<Track> {
        val pl = _playlists.value.firstOrNull { it.name == name } ?: return emptyList()
        return pl.trackIds.mapNotNull { byId[it] }
    }

    /** Loads demo assets, then user-imported files, then merges any device audio. */
    suspend fun load(includeDevice: Boolean) = withContext(Dispatchers.IO) { loadLock.withLock {
        if (!stateLoaded) {
            LegacyPrefs.migrate(prefs, dao)
            // in memory from here on; later loads must not overwrite queued writes
            _favorites.value = dao.favoriteIds().toSet()
            _playlists.value = dao.playlists().map { Playlist(it.name, dao.playlistTrackIds(it.id), it.coverUri) }
            stateLoaded = true
        }
        val demo = loadDemoTracks()
        val imported = loadImportedTracks().map { ensureImportedCover(it) }
        val youtube = loadYoutubeTracks()
        val ytUris = youtube.mapTo(HashSet()) { it.uri }
        val ytDevIds = youtube.mapNotNullTo(HashSet()) { devIdOfDocument(it.uri) }
        val device = if (includeDevice) runCatching { scanDevice() }.getOrDefault(emptyList()).filter { it.uri !in ytUris && it.id !in ytDevIds } else emptyList()
        val all = youtube + loadStreamTracks() + imported + demo + device
        synchronized(byId) {
            val streamed = byId.values.filter { it.id.startsWith(STREAM_PREFIX) }
            byId.clear()
            streamed.forEach { byId[it.id] = it }
            all.forEach { byId[it.id] = it }
        }
        _tracks.value = all
        swapDownloaded()
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

    /**
     * Songs saved into a picked folder have a document uri, so the device scan sees them again under
     * their MediaStore id; this finds that id. shortcut: needs Android 10, older phones may list such songs twice.
     */
    private fun devIdOfDocument(uri: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !uri.startsWith("content://")) return null
        val u = Uri.parse(uri)
        if (!DocumentsContract.isDocumentUri(appContext, u)) return null
        return runCatching { MediaStore.getMediaUri(appContext, u)?.let { "dev_" + ContentUris.parseId(it) } }.getOrNull()
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

    /** Re-reads saved YouTube songs after a download without rescanning the device. */
    suspend fun refreshYoutube() = withContext(Dispatchers.IO) { loadLock.withLock {
        val youtube = loadYoutubeTracks()
        val uris = youtube.mapTo(HashSet()) { it.uri }
        val rest = _tracks.value.filter { !it.id.startsWith("yt_") && it.uri !in uris }
        synchronized(byId) { youtube.forEach { byId[it.id] = it } }
        _tracks.value = youtube + rest
        swapDownloaded()
    } }

    /** Points playlists and favorites at the downloaded copy once a saved stream is on the phone. */
    private fun swapDownloaded() {
        val done = _tracks.value.mapNotNullTo(HashSet()) { t -> t.id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_") }
        val swap = { id: String -> if (id.startsWith(STREAM_PREFIX) && id.removePrefix(STREAM_PREFIX) in done) "yt_" + id.removePrefix(STREAM_PREFIX) else id }
        _playlists.value.forEachIndexed { i, pl ->
            val ids = pl.trackIds.map(swap).distinct()
            if (ids != pl.trackIds) {
                _playlists.value = _playlists.value.toMutableList().also { it[i] = pl.copy(trackIds = ids) }
                savePlaylist(pl.name)
            }
        }
        val favs = _favorites.value
        val newFavs = favs.mapTo(LinkedHashSet(), swap)
        if (newFavs != favs) {
            _favorites.value = newFavs
            val now = System.currentTimeMillis()
            writer.launch {
                (favs - newFavs).forEach { dao.deleteFavorite(it) }
                dao.upsertFavorites((newFavs - favs).map { FavoriteEntity(it, now) })
            }
        }
        val streamDone = _tracks.value.filter { it.id.startsWith(STREAM_PREFIX) && it.id.removePrefix(STREAM_PREFIX) in done }
        if (streamDone.isNotEmpty()) {
            _tracks.value = _tracks.value - streamDone.toSet()
            writer.launch { streamDone.forEach { dao.deleteTrack(it.id) } }
        }
    }

    private suspend fun loadYoutubeTracks(): List<Track> = dao.tracksBySource(TrackSource.YOUTUBE).map {
        val cover = it.youtubeVideoId?.let { v -> File(appContext.filesDir, "yt-covers/$v.jpg") }?.takeIf(File::exists)
        Track(it.id, it.title, it.artist, it.album, it.uri, cover?.let(Uri::fromFile)?.toString(), it.durationMs, isDemo = false)
    }

    private suspend fun loadStreamTracks(): List<Track> = dao.tracksBySource(TrackSource.STREAM).map {
        Track(it.id, it.title, it.artist, it.album, it.uri, it.youtubeVideoId?.let(::ytCover), it.durationMs, isDemo = false)
    }

    /** Keeps streamed YouTube songs in the library so playlists and favorites survive a restart. */
    fun saveStreamed(tracks: List<Track>) {
        val fresh = tracks.filter { it.id.startsWith(STREAM_PREFIX) }.distinctBy { it.id }
        if (fresh.isEmpty()) return
        synchronized(byId) { fresh.forEach { byId[it.id] = it } }
        val have = _tracks.value.mapTo(HashSet()) { it.id }
        val added = fresh.filter { it.id !in have }
        if (added.isNotEmpty()) _tracks.value = added + _tracks.value
        val now = System.currentTimeMillis()
        writer.launch {
            dao.upsertTracks(fresh.mapIndexed { i, t ->
                TrackEntity(
                    id = t.id, source = TrackSource.STREAM, title = t.title, artist = t.artist, album = t.album,
                    uri = t.uri, durationMs = t.durationMs, youtubeVideoId = t.id.removePrefix(STREAM_PREFIX), addedAt = now + i,
                )
            })
        }
    }

    fun isSaved(id: String) = _tracks.value.any { it.id == id }

    /** Saves a YouTube album or playlist as a Ctify playlist that streams. */
    fun saveAsPlaylist(name: String, tracks: List<Track>, coverUrl: String?) {
        saveStreamed(tracks)
        createPlaylistWithTracks(name, tracks.map { it.id })
        val trimmed = name.trim().ifEmpty { "Folder" }
        if (coverUrl != null) {
            _playlists.value = _playlists.value.map { if (it.name == trimmed && it.coverUri == null) it.copy(coverUri = coverUrl) else it }
            savePlaylist(trimmed)
        }
    }

    /** Takes a saved YouTube song out of the library, its playlists and favorites. */
    fun removeStreamed(id: String) {
        if (!id.startsWith(STREAM_PREFIX)) return
        _tracks.value = _tracks.value.filterNot { it.id == id }
        _playlists.value.filter { id in it.trackIds }.forEach { removeFromPlaylist(it.name, id) }
        if (id in _favorites.value) toggleFavorite(id)
        writer.launch { dao.deleteTrack(id) }
    }

    /** Deletes a playlist and the saved YouTube songs no other playlist or favorite still uses. */
    fun deletePlaylistWithStreams(name: String) {
        val ids = _playlists.value.firstOrNull { it.name == name }?.trackIds.orEmpty().filter { it.startsWith(STREAM_PREFIX) }
        deletePlaylist(name)
        val kept = _playlists.value.flatMapTo(HashSet()) { it.trackIds } + _favorites.value
        ids.filter { it !in kept }.forEach(::removeStreamed)
    }

    private fun persistStreamed(ids: Collection<String>) {
        val streamed = ids.filter { it.startsWith(STREAM_PREFIX) }.mapNotNull { trackById(it) }
        if (streamed.isNotEmpty()) saveStreamed(streamed)
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
        persistStreamed(listOf(id))
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
        persistStreamed(trackIds)
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
        persistStreamed(listOf(trackId))
        _playlists.value = _playlists.value.map {
            if (it.name == name && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
        }
        savePlaylist(name)
    }

    /** Moves the song at [from] to [to] inside a playlist. */
    fun moveInPlaylist(name: String, from: Int, to: Int) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.name != name || from !in pl.trackIds.indices || to !in pl.trackIds.indices) pl
            else pl.copy(trackIds = pl.trackIds.toMutableList().apply { add(to, removeAt(from)) })
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

    // ---- listening history ----

    fun recordPlay(t: Track) {
        val now = System.currentTimeMillis()
        writer.launch {
            dao.insertPlay(PlayEntity(trackId = t.id, playedAt = now, title = t.title, artist = t.artist, album = t.album, uri = t.uri, coverUri = t.coverUri, durationMs = t.durationMs))
            dao.prunePlays(MAX_PLAYS)
        }
    }

    /** A streamed song and its downloaded copy share one history entry, see [playedTracks]. */
    private fun historyIds(id: String) = listOf(id, id.replaceFirst(Regex("^yt_"), STREAM_PREFIX), id.replaceFirst(Regex("^$STREAM_PREFIX"), "yt_")).distinct()

    suspend fun hasPlays(id: String): Boolean = dao.hasPlays(historyIds(id))

    fun removeFromHistory(id: String) { writer.launch { dao.deletePlays(historyIds(id)) } }

    fun clearHistory() { writer.launch { dao.clearPlays() } }

    /** Distinct tracks, newest play first. */
    fun recentlyPlayed(limit: Int): Flow<List<Track>> = dao.recentPlays(limit).combine(tracks) { rows, _ -> rows.playedTracks() }

    /** Tracks played more than once, most plays first. */
    fun mostPlayed(limit: Int): Flow<List<Track>> = dao.mostPlays(limit).combine(tracks) { rows, _ -> rows.playedTracks() }

    /** Prefers the downloaded copy of a streamed song; streams not in the library replay from the stored row; gone local files drop out. */
    private fun List<PlayEntity>.playedTracks(): List<Track> = mapNotNull { p ->
        trackById(p.trackId.replaceFirst(STREAM_PREFIX, "yt_").takeIf { p.trackId.startsWith(STREAM_PREFIX) }) ?: trackById(p.trackId)
            ?: if (p.trackId.startsWith(STREAM_PREFIX)) Track(p.trackId, p.title, p.artist, p.album, p.uri, p.coverUri, p.durationMs, isDemo = false) else null
    }.distinctBy { it.id }

    companion object {
        private const val STREAM_PREFIX = "yts_"
        private const val MAX_PLAYS = 5000
        private fun ytCover(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        @Volatile private var instance: MusicRepository? = null
        fun get(context: Context, database: CtifyDatabase): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext, database).also { instance = it }
            }
    }
}
