package com.ctify.player.data

import android.content.SharedPreferences
import com.ctify.player.data.db.FavoriteEntity
import com.ctify.player.data.db.LibraryDao
import com.ctify.player.data.db.PlaybackSnapshotEntity
import com.ctify.player.data.db.TrackEntity
import com.ctify.player.data.db.TrackSource
import org.json.JSONArray

/** One-time move of the pre-Room SharedPreferences state into the database. */
internal object LegacyPrefs {
    private const val MIGRATED_KEY = "migrated_to_room_v1"

    suspend fun migrate(prefs: SharedPreferences, dao: LibraryDao) {
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
}
