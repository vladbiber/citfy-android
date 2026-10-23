package com.ctify.player.radio

import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
    @Query("UPDATE heard_songs SET liked = :liked WHERE id = :id") suspend fun setLiked(id: Long, liked: Boolean)
    @Query("UPDATE heard_songs SET youtubeVideoId = :videoId WHERE id = :id") suspend fun setVideoId(id: Long, videoId: String)
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
