package com.ctify.player.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface LibraryDao {

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY addedAt")
    suspend fun tracksBySource(source: TrackSource): List<TrackEntity>

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)

    @Query("SELECT trackId FROM favorites ORDER BY addedAt")
    suspend fun favoriteIds(): List<String>

    @Upsert
    suspend fun upsertFavorites(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: String)

    @Query("SELECT * FROM playlists ORDER BY position")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE name = :name")
    suspend fun playlistByName(name: String): PlaylistEntity?

    @Query("SELECT trackId FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    suspend fun playlistTrackIds(playlistId: Long): List<String>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE name = :name")
    suspend fun deletePlaylist(name: String)

    @Query("UPDATE playlists SET name = :newName WHERE name = :oldName")
    suspend fun renamePlaylist(oldName: String, newName: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Upsert
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    /** Writes a playlist and its full track order, creating it if the name is new. */
    @Transaction
    suspend fun savePlaylist(name: String, coverUri: String?, position: Int, trackIds: List<String>) {
        val existing = playlistByName(name)
        val id = if (existing == null) {
            upsertPlaylist(PlaylistEntity(name = name, coverUri = coverUri, position = position))
        } else {
            upsertPlaylist(existing.copy(coverUri = coverUri, position = position))
            existing.id
        }
        clearPlaylistItems(id)
        insertPlaylistItems(trackIds.mapIndexed { i, t -> PlaylistItemEntity(id, i, t) })
    }

    @Query("SELECT * FROM playback_snapshot WHERE id = 0")
    suspend fun snapshot(): PlaybackSnapshotEntity?

    @Upsert
    suspend fun saveSnapshot(snapshot: PlaybackSnapshotEntity)
}
