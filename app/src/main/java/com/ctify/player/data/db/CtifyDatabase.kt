package com.ctify.player.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ctify.player.radio.HeardSong
import com.ctify.player.radio.HeardSongDao
import com.ctify.player.radio.RadioCacheEntry
import com.ctify.player.radio.RadioDao
import com.ctify.player.radio.RadioTab
import com.ctify.player.radio.Station
import com.ctify.player.radio.StationFavorite
import com.ctify.player.radio.StationRecent
import com.ctify.player.youtube.download.DownloadDao
import com.ctify.player.youtube.download.DownloadEntity

/** Every schema change bumps [version] and adds a real migration; schemas are exported to app/schemas. */
@Database(
    entities = [
        TrackEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlaybackSnapshotEntity::class,
        DiagnosticIssueEntity::class,
        DiagnosticEventEntity::class,
        Station::class,
        StationFavorite::class,
        StationRecent::class,
        RadioTab::class,
        RadioCacheEntry::class,
        HeardSong::class,
        SongGenreOverride::class,
        ArtistGenre::class,
        GenrePresetEntity::class,
        DevicePresetEntity::class,
        TrackLoudness::class,
        DownloadEntity::class,
        PlayEntity::class,
    ],
    version = CtifyDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class CtifyDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun diagnostics(): DiagnosticsDao
    abstract fun radio(): RadioDao
    abstract fun heardSongs(): HeardSongDao
    abstract fun sound(): SoundDao
    abstract fun downloads(): DownloadDao

    companion object {
        const val NAME = "ctify.db"
        const val VERSION = 7

        fun open(context: Context): CtifyDatabase =
            Room.databaseBuilder(context.applicationContext, CtifyDatabase::class.java, NAME).build()
    }
}
