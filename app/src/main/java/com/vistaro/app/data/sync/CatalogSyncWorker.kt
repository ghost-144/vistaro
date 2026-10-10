package com.vistaro.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.vistaro.app.VistaroApplication
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class CatalogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val syncManager = (applicationContext as VistaroApplication).container.catalogSyncManager
        return try {
            val outcome = syncManager.runSync(inputData.getBoolean(KEY_REFRESH_IDS, true)) { message, current, total ->
                setProgress(
                    Data.Builder()
                        .putString(KEY_PROGRESS_MESSAGE, message)
                        .putInt(KEY_PROGRESS_CURRENT, current)
                        .putInt(KEY_PROGRESS_TOTAL, total)
                        .build(),
                )
            }
            Result.success(
                Data.Builder()
                    .putInt("available_movies", outcome.availableMovies)
                    .putInt("available_tv_shows", outcome.availableTvShows)
                    .putInt("metadata_remaining", outcome.metadataRemaining)
                    .build(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            syncManager.recordFailure(error.message ?: "Catalog sync failed. Try again.")
            when {
                error is CatalogSyncManager.CatalogSyncException && error.retryable -> Result.retry()
                error is HttpException && (error.code() == 429 || error.code() >= 500) -> Result.retry()
                error is IOException && error !is CatalogSyncManager.CatalogSyncException -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        const val KEY_REFRESH_IDS = "refresh_id_lists"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
    }
}
