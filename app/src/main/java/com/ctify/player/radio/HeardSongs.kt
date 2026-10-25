package com.ctify.player.radio

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.ctify.player.CtifyApp
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.recommend.SongRadio
import com.ctify.player.ui.songs.Snack
import com.ctify.player.youtube.YtSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A song heard on the radio, text only: no audio is kept. */
@Entity(tableName = "heard_songs", indices = [Index("startedAt"), Index("stationUuid")])
data class HeardSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawTitle: String,
    val artist: String?,
    val title: String,
    val stationUuid: String,
    val stationName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** genre family name from the station's tags */
    val genre: String? = null,
    val liked: Boolean = false,
    val youtubeVideoId: String? = null,
    val downloadedTrackId: String? = null,
) {
    /** "artist title", the search text for every link */
    val query: String get() = listOfNotNull(artist, title).joinToString(" ")
}

@Dao
interface HeardSongDao {
    @Insert suspend fun insert(song: HeardSong): Long
    @Query("UPDATE heard_songs SET endedAt = :at WHERE id = :id") suspend fun end(id: Long, at: Long)
    @Query("SELECT * FROM heard_songs ORDER BY startedAt DESC LIMIT :limit") fun recent(limit: Int = 500): Flow<List<HeardSong>>
    @Query("SELECT * FROM heard_songs WHERE liked = 1") suspend fun liked(): List<HeardSong>
    @Query("UPDATE heard_songs SET liked = :liked WHERE id = :id") suspend fun setLiked(id: Long, liked: Boolean)
    @Query("UPDATE heard_songs SET youtubeVideoId = :videoId WHERE id = :id") suspend fun setVideoId(id: Long, videoId: String)
    @Query("UPDATE heard_songs SET downloadedTrackId = :trackId WHERE id = :id") suspend fun setDownloaded(id: Long, trackId: String)
    @Query("DELETE FROM heard_songs WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM heard_songs") suspend fun clear()
}

/** Search links built on the phone, no lookup service involved. */
object SongLinks {
    private fun q(s: HeardSong) = Uri.encode(s.query)

    fun youtube(s: HeardSong) = s.youtubeVideoId?.let { "https://www.youtube.com/watch?v=$it" }
        ?: "https://www.youtube.com/results?search_query=${q(s)}"
    fun youtubeMusic(s: HeardSong) = s.youtubeVideoId?.let { "https://music.youtube.com/watch?v=$it" }
        ?: "https://music.youtube.com/search?q=${q(s)}"
    fun spotify(s: HeardSong) = "https://open.spotify.com/search/${q(s)}"
    fun appleMusic(s: HeardSong) = "https://music.apple.com/search?term=${q(s)}"
}

/**
 * The heart on a heard song means Liked songs: the song is found on YouTube, saved to the library as a
 * stream and liked. heard_songs.liked stays set too, so Heard on the radio > Liked still lists it.
 * Liked songs that couldn't be found yet (offline, no match) are retried by [sync]; nothing is ever deleted.
 */
object HeardLikes {
    private const val DONE = "done"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var job: Job? = null

    private fun prefs(app: Context) = app.getSharedPreferences("heard_likes", Context.MODE_PRIVATE)
    private fun done(app: Context) = prefs(app).getStringSet(DONE, emptySet()).orEmpty()
    private fun markDone(app: Context, id: Long, on: Boolean) {
        val set = done(app).toMutableSet().apply { if (on) add(id.toString()) else remove(id.toString()) }
        prefs(app).edit().putStringSet(DONE, set).apply()
    }

    /** The YouTube song for [h], remembering its video id; null when offline or not found. */
    suspend fun find(context: Context, h: HeardSong): YtSong? {
        h.youtubeVideoId?.let { return YtSong(it, h.title, h.artist ?: "Unknown artist") }
        if (NetworkGate.blockReason(Feature.YOUTUBE) != null) return null
        val song = SongRadio.find(h.artist, h.title) ?: return null
        (context.applicationContext as CtifyApp).database.heardSongs().setVideoId(h.id, song.videoId)
        return song
    }

    fun setLiked(context: Context, h: HeardSong, liked: Boolean) {
        val app = context.applicationContext as CtifyApp
        scope.launch {
            app.database.heardSongs().setLiked(h.id, liked)
            if (liked) {
                Snack.show(if (addToLiked(app, h)) "Added to Liked songs" else "It'll be added to Liked songs once it's found on YouTube")
                return@launch
            }
            markDone(app, h.id, false)
            val v = h.youtubeVideoId
            withContext(Dispatchers.Main) {
                if (v != null) listOf(YtSong.STREAM_PREFIX + v, "yt_$v").filter(app.repository::isFavorite).forEach(app.repository::toggleFavorite)
            }
            Snack.show("Removed from Liked songs") { setLiked(app, h, true) }
        }
    }

    /** Finds, saves and likes one heard song; false leaves it for the next [sync]. */
    private suspend fun addToLiked(app: CtifyApp, h: HeardSong): Boolean {
        val song = find(app, h) ?: return false
        withContext(Dispatchers.Main) {
            val repo = app.repository
            // a downloaded copy is liked instead of a second, streamed one
            val id = "yt_${song.videoId}".takeIf { repo.trackById(it) != null } ?: song.toTrack().also { repo.remember(listOf(it)) }.id
            if (!repo.isFavorite(id)) repo.toggleFavorite(id)
        }
        markDone(app, h.id, true)
        return true
    }

    /** Likes every hearted heard song not handled yet; runs again whenever Home opens, until all are found. */
    fun sync(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext as CtifyApp
        job = scope.launch {
            if (NetworkGate.blockReason(Feature.YOUTUBE) != null) return@launch
            val done = done(app)
            app.database.heardSongs().liked().filter { it.id.toString() !in done }.forEach { addToLiked(app, it) }
        }
    }

    /** Removes a heard song; Undo puts the same row back. */
    fun delete(context: Context, h: HeardSong) {
        val dao = (context.applicationContext as CtifyApp).database.heardSongs()
        scope.launch {
            dao.delete(h.id)
            Snack.show("Removed from heard songs") { scope.launch { dao.insert(h) } }
        }
    }
}
