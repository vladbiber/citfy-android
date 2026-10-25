package com.ctify.player.radio

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class StationSort(val label: String, val apiOrder: String, val reverse: Boolean) {
    VOTES("Most voted", "votes", true),
    CLICKS("Most listened", "clickcount", true),
    TREND("Trending", "clicktrend", true),
    NAME("Name", "name", false),
    RANDOM("Random", "random", false),
}

enum class TabKind { GENRE, TAG }

@Entity(tableName = "station_favorites")
data class StationFavorite(@PrimaryKey val uuid: String, val addedAt: Long)

@Entity(tableName = "station_recents")
data class StationRecent(@PrimaryKey val uuid: String, val playedAt: Long)

/** A pinned genre (a [com.ctify.player.genre.Genre] family) or single raw tag, with its own filters. */
@Entity(tableName = "radio_tabs")
data class RadioTab(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: TabKind,
    /** Genre enum name for GENRE, the raw tag for TAG */
    val key: String,
    val title: String,
    val position: Int,
    val sort: StationSort = StationSort.VOTES,
    val countryCode: String? = null,
    val minBitrate: Int = 0,
    val hideBroken: Boolean = true,
)

/** The last loaded page of each tab, so lists show instantly and when offline. */
@Entity(tableName = "radio_cache")
data class RadioCacheEntry(@PrimaryKey val key: String, val json: String, val fetchedAt: Long)

@Dao
interface RadioDao {
    @Upsert suspend fun upsertStations(stations: List<Station>)
    @Query("SELECT * FROM stations WHERE uuid = :uuid") suspend fun station(uuid: String): Station?

    @Query("SELECT s.* FROM stations s JOIN station_favorites f ON f.uuid = s.uuid ORDER BY f.addedAt DESC")
    fun favorites(): Flow<List<Station>>
    @Query("SELECT uuid FROM station_favorites") fun favoriteIds(): Flow<List<String>>
    @Upsert suspend fun addFavorite(f: StationFavorite)
    @Query("DELETE FROM station_favorites WHERE uuid = :uuid") suspend fun removeFavorite(uuid: String)

    @Query("SELECT s.* FROM stations s JOIN station_recents r ON r.uuid = s.uuid ORDER BY r.playedAt DESC LIMIT :limit")
    fun recents(limit: Int = 20): Flow<List<Station>>
    @Upsert suspend fun addRecent(r: StationRecent)
    @Query("DELETE FROM station_recents WHERE uuid NOT IN (SELECT uuid FROM station_recents ORDER BY playedAt DESC LIMIT 50)")
    suspend fun trimRecents()
    @Query("DELETE FROM station_recents") suspend fun clearRecents()

    @Query("SELECT * FROM stations WHERE source = 'CUSTOM' ORDER BY name") fun customStations(): Flow<List<Station>>
    @Query("DELETE FROM stations WHERE uuid = :uuid") suspend fun deleteStation(uuid: String)

    @Query("SELECT * FROM radio_tabs ORDER BY position") fun tabs(): Flow<List<RadioTab>>
    @Query("SELECT * FROM radio_tabs ORDER BY position") suspend fun tabsNow(): List<RadioTab>
    @Upsert suspend fun upsertTab(tab: RadioTab): Long
    @Query("DELETE FROM radio_tabs WHERE id = :id") suspend fun deleteTab(id: Long)

    @Transaction
    suspend fun reorderTabs(ids: List<Long>) {
        val byId = tabsNow().associateBy { it.id }
        ids.forEachIndexed { i, id -> byId[id]?.let { upsertTab(it.copy(position = i)) } }
    }

    @Query("SELECT * FROM radio_cache WHERE `key` = :key") suspend fun cached(key: String): RadioCacheEntry?
    @Upsert suspend fun putCache(e: RadioCacheEntry)
}
