package com.ctify.player.youtube.download

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED, SKIPPED }

/** One song the user asked to save. [groupId] ties album songs together for "7/12". */
@Entity(tableName = "downloads", indices = [Index("groupId"), Index("state")])
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val year: Int? = null,
    val durationMs: Long = 0L,
    val thumbnail: String? = null,
    val genre: String? = null,
    val groupId: String? = null,
    val groupTitle: String? = null,
    /** heard_songs row to link once the file is saved */
    val heardSongId: Long? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = -1L,
    /** itag and length of the partial file, so a resume only continues the same file */
    val partItag: Int? = null,
    val error: String? = null,
    /** diagnostics class of the last failure; "wait for update" ones offer the hand-off */
    val errorClass: String? = null,
    val trackId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun get(videoId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED', 'RUNNING') ORDER BY addedAt, trackNumber LIMIT 1")
    suspend fun next(): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE state IN ('QUEUED', 'RUNNING')")
    suspend fun pending(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<DownloadEntity>)

    @Query("UPDATE downloads SET state = :state, error = :error, errorClass = :errorClass WHERE videoId = :videoId")
    suspend fun setState(videoId: String, state: DownloadState, error: String? = null, errorClass: String? = null)

    @Query("UPDATE downloads SET bytesDone = :done, bytesTotal = :total, partItag = :itag WHERE videoId = :videoId")
    suspend fun progress(videoId: String, done: Long, total: Long, itag: Int?)

    @Query("UPDATE downloads SET state = 'DONE', error = NULL, trackId = :trackId, finishedAt = :at WHERE videoId = :videoId")
    suspend fun done(videoId: String, trackId: String, at: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET state = 'QUEUED', error = NULL WHERE state IN ('FAILED', 'RUNNING')")
    suspend fun retryFailed()

    @Query("DELETE FROM downloads WHERE videoId = :videoId AND state != 'RUNNING'")
    suspend fun remove(videoId: String)

    @Query("DELETE FROM downloads WHERE state IN ('DONE', 'SKIPPED')")
    suspend fun clearFinished()

    @Query("SELECT id FROM tracks WHERE youtubeVideoId = :videoId LIMIT 1")
    suspend fun trackForVideo(videoId: String): String?

    /** a saved song with the same title and artist, duration within three seconds */
    @Query(
        "SELECT id FROM tracks WHERE lower(title) = lower(:title) AND lower(artist) = lower(:artist) " +
            "AND (:durationMs <= 0 OR abs(durationMs - :durationMs) <= 3000) LIMIT 1"
    )
    suspend fun sameSong(title: String, artist: String, durationMs: Long): String?
}
