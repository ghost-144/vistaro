package com.vistaro.app

import android.content.Context
import androidx.room.Room
import com.vistaro.app.core.database.CatalogDatabase
import com.vistaro.app.data.catalog.CatalogRepository
import com.vistaro.app.data.catalog.FallbackCatalogRepository
import com.vistaro.app.data.catalog.FakeCatalogRepository
import com.vistaro.app.data.catalog.RoomCatalogRepository
import com.vistaro.app.data.tmdb.TmdbApiFactory
import com.vistaro.app.data.tmdb.TmdbRepository
import com.vistaro.app.data.sync.CatalogSyncManager
import com.vistaro.app.data.playback.VidApiPlaybackProvider
import com.vistaro.app.data.user.FavoriteRepository
import com.vistaro.app.data.user.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-scoped dependency container. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val catalogDatabase: CatalogDatabase by lazy {
        Room.databaseBuilder(applicationContext, CatalogDatabase::class.java, "vistaro.db")
            .addMigrations(CatalogDatabase.MIGRATION_1_2, CatalogDatabase.MIGRATION_2_3)
            .build()
    }

    val roomCatalogRepository: CatalogRepository by lazy { RoomCatalogRepository(catalogDatabase) }
    private val sampleCatalogRepository: CatalogRepository by lazy { FakeCatalogRepository() }
    val catalogRepository: CatalogRepository by lazy {
        FallbackCatalogRepository(roomCatalogRepository, sampleCatalogRepository)
    }
    val tmdbRepository: TmdbRepository by lazy {
        TmdbRepository(TmdbApiFactory(BuildConfig.TMDB_ACCESS_TOKEN), catalogDatabase)
    }
    val catalogSyncManager: CatalogSyncManager by lazy {
        CatalogSyncManager(applicationContext, catalogDatabase, tmdbRepository)
    }
    val playbackProvider by lazy { VidApiPlaybackProvider() }
    val favoriteRepository by lazy { FavoriteRepository(catalogDatabase) }
    val watchProgressRepository by lazy { WatchProgressRepository(catalogDatabase) }
}
