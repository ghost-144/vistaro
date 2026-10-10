package com.vistaro.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MovieEntity::class,
        TvShowEntity::class,
        EpisodeEntity::class,
        GenreEntity::class,
        FavoriteEntity::class,
        WatchProgressEntity::class,
        CatalogMetadataEntity::class,
        CatalogAvailabilityEntity::class,
        CatalogIdSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun tvShowDao(): TvShowDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun catalogMetadataDao(): CatalogMetadataDao
    abstract fun catalogAvailabilityDao(): CatalogAvailabilityDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catalog_availability (contentType TEXT NOT NULL, tmdbId INTEGER NOT NULL, available INTEGER NOT NULL, lastSeenSyncId INTEGER NOT NULL, metadataAttemptedAt INTEGER, metadataUnavailable INTEGER NOT NULL, PRIMARY KEY(contentType, tmdbId))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catalog_availability_contentType_available_lastSeenSyncId ON catalog_availability (contentType, available, lastSeenSyncId)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catalog_id_snapshot (syncId INTEGER NOT NULL, contentType TEXT NOT NULL, tmdbId INTEGER NOT NULL, PRIMARY KEY(syncId, contentType, tmdbId))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catalog_id_snapshot_syncId_contentType_tmdbId ON catalog_id_snapshot (syncId, contentType, tmdbId)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO catalog_id_snapshot (syncId, contentType, tmdbId) SELECT a.lastSeenSyncId, a.contentType, a.tmdbId FROM catalog_availability a WHERE a.available = 1 AND a.lastSeenSyncId = CAST((SELECT value FROM catalog_metadata WHERE `key` = 'vidapi_catalog_id_snapshot' LIMIT 1) AS INTEGER)",
                )
            }
        }
    }
}
