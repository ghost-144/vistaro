package com.vistaro.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.Observer
import com.vistaro.app.core.database.CatalogDatabase
import androidx.room.withTransaction
import com.vistaro.app.core.database.CatalogMetadataEntity
import com.vistaro.app.data.tmdb.TmdbRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CatalogSyncOutcome(
    val availableMovies: Int,
    val availableTvShows: Int,
    val metadataProcessed: Int,
    val metadataRemaining: Int,
)

class CatalogSyncManager(
    context: Context,
    private val database: CatalogDatabase,
    private val tmdbRepository: TmdbRepository,
    private val idSource: VidApiIdSource = VidApiIdSource(),
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val syncMutex = Mutex()

    fun scheduleDailySync() {
        workManager.cancelUniqueWork(CONTINUATION_WORK_NAME)
        val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(0, TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints)
            .addTag(WORK_TAG)
            .addTag(PERIODIC_WORK_TAG)
            .setInputData(Data.Builder().putBoolean(CatalogSyncWorker.KEY_REFRESH_IDS, true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueManualSync() {
        val request = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setConstraints(networkConstraints)
            .addTag(WORK_TAG)
            .setInputData(Data.Builder().putBoolean(CatalogSyncWorker.KEY_REFRESH_IDS, true).build())
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun observeWork(): Flow<List<WorkInfo>> = callbackFlow {
        val liveData = workManager.getWorkInfosByTagLiveData(WORK_TAG)
        val observer = Observer<List<WorkInfo>> { value -> trySend(value.orEmpty()) }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }
    fun observeLastSuccess(): Flow<CatalogMetadataEntity?> = database.catalogMetadataDao().observe(KEY_LAST_SUCCESS)
    fun observeLastError(): Flow<CatalogMetadataEntity?> = database.catalogMetadataDao().observe(KEY_LAST_ERROR)

    suspend fun runSync(refreshIdLists: Boolean, onProgress: suspend (String, Int, Int) -> Unit): CatalogSyncOutcome =
        syncMutex.withLock {
            val dao = database.catalogAvailabilityDao()
            val syncId = System.currentTimeMillis()
            database.catalogMetadataDao().upsert(CatalogMetadataEntity(KEY_LAST_ERROR, "", syncId))
            if (refreshIdLists) {
                val completedSnapshot = database.catalogMetadataDao().get(KEY_ID_SNAPSHOT)?.value?.toLongOrNull()
                dao.deleteSnapshotsExcept(completedSnapshot ?: Long.MIN_VALUE)
                onProgress("Checking VidAPI movie IDs", 0, 0)
                val moviesSeen = idSource.syncIds(CatalogContentType.MOVIE, syncId, dao) { count ->
                    onProgress("Checking VidAPI movie IDs", count, 0)
                }
                dao.markMissing(CatalogContentType.MOVIE.key, syncId)
                onProgress("Checking VidAPI TV show IDs", 0, 0)
                val showsSeen = idSource.syncIds(CatalogContentType.TV_SHOW, syncId, dao) { count ->
                    onProgress("Checking VidAPI TV show IDs", count, 0)
                }
                dao.markMissing(CatalogContentType.TV_SHOW.key, syncId)
                database.withTransaction {
                    database.catalogMetadataDao().upsert(
                        CatalogMetadataEntity(KEY_ID_SNAPSHOT, syncId.toString(), System.currentTimeMillis()),
                    )
                    dao.deleteSnapshotsExcept(syncId)
                }
                onProgress("VidAPI IDs updated · $moviesSeen movies · $showsSeen TV shows", 0, 0)
            }

            if (!tmdbRepository.isConfigured) {
                throw CatalogSyncException("Add TMDB_ACCESS_TOKEN to secrets.properties before enriching the catalog.")
            }

            val staleBefore = System.currentTimeMillis() - METADATA_MAX_AGE_MS
            val movieIds = dao.moviesMissingMetadata(staleBefore, DETAILS_PER_TYPE_PER_RUN)
            val showIds = dao.showsMissingMetadata(staleBefore, DETAILS_PER_TYPE_PER_RUN)
            val totalPending = movieIds.size + showIds.size
            var processed = 0
            onProgress("Refreshing TMDB metadata", 0, totalPending)

            movieIds.forEach { id ->
                refreshMetadata(id, CatalogContentType.MOVIE)
                processed++
                onProgress("Refreshing TMDB metadata", processed, totalPending)
            }
            showIds.forEach { id ->
                refreshMetadata(id, CatalogContentType.TV_SHOW)
                processed++
                onProgress("Refreshing TMDB metadata", processed, totalPending)
            }

            val remaining = dao.countMoviesMissingMetadata(staleBefore) + dao.countShowsMissingMetadata(staleBefore)
            val movieCount = dao.countAvailable(CatalogContentType.MOVIE.key)
            val showCount = dao.countAvailable(CatalogContentType.TV_SHOW.key)
            database.catalogMetadataDao().upsert(
                CatalogMetadataEntity(
                    KEY_LAST_SUCCESS,
                    "${System.currentTimeMillis()}|$movieCount|$showCount|$remaining",
                    System.currentTimeMillis(),
                ),
            )
            database.catalogMetadataDao().upsert(CatalogMetadataEntity(KEY_LAST_ERROR, "", System.currentTimeMillis()))
            CatalogSyncOutcome(movieCount, showCount, processed, remaining)
        }

    suspend fun recordFailure(message: String) {
        database.catalogMetadataDao().upsert(
            CatalogMetadataEntity(KEY_LAST_ERROR, message, System.currentTimeMillis()),
        )
    }

    private suspend fun refreshMetadata(tmdbId: Int, type: CatalogContentType) {
        val error = when (type) {
            CatalogContentType.MOVIE -> tmdbRepository.refreshMovie(tmdbId)
            CatalogContentType.TV_SHOW -> tmdbRepository.refreshTvShow(tmdbId)
        }
        if (error == null) return
        if (error == "TMDB has no metadata for this item.") {
            database.catalogAvailabilityDao().recordMetadataAttempt(type.key, tmdbId, System.currentTimeMillis(), unavailable = true)
            return
        }
        throw CatalogSyncException(error, retryable = error.startsWith("Could not reach TMDB") || error.contains("temporarily unavailable"))
    }

    class CatalogSyncException(message: String, val retryable: Boolean = false) : IOException(message)

    companion object {
        const val WORK_TAG = "catalog-sync"
        const val PERIODIC_WORK_TAG = "catalog-sync-periodic"
        const val KEY_LAST_SUCCESS = "vidapi_catalog_last_success"
        const val KEY_LAST_ERROR = "vidapi_catalog_last_error"
        const val KEY_ID_SNAPSHOT = "vidapi_catalog_id_snapshot"
        private const val PERIODIC_WORK_NAME = "vidapi-catalog-periodic"
        private const val MANUAL_WORK_NAME = "vidapi-catalog-manual"
        private const val CONTINUATION_WORK_NAME = "vidapi-catalog-continuations"
        private const val DETAILS_PER_TYPE_PER_RUN = 250
        private const val METADATA_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        private val networkConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    }
}
