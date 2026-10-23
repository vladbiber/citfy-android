package com.ctify.player.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    version = CtifyDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class CtifyDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun diagnostics(): DiagnosticsDao

    companion object {
        const val NAME = "ctify.db"
        const val VERSION = 2

        fun open(context: Context): CtifyDatabase =
            Room.databaseBuilder(context.applicationContext, CtifyDatabase::class.java, NAME).build()
    }
}
