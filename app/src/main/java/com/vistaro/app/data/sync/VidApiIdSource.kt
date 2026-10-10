package com.vistaro.app.data.sync

import com.vistaro.app.core.database.CatalogAvailabilityDao
import com.vistaro.app.core.database.CatalogAvailabilityEntity
import com.vistaro.app.core.database.CatalogIdSnapshotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

enum class CatalogContentType(val key: String, val fileName: String) {
    MOVIE("MOVIE", "movie_list_tmdb.txt"),
    TV_SHOW("TV_SHOW", "tv_list_tmdb.txt"),
}

/** Streams VidAPI's daily ID exports into SQLite; it never holds the full ID list in memory. */
class VidApiIdSource(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://vidapi.ru/ids/",
) {
    suspend fun syncIds(
        type: CatalogContentType,
        syncId: Long,
        dao: CatalogAvailabilityDao,
        onProgress: suspend (Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + type.fileName).header("Accept", "text/plain").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("VidAPI ${type.key.lowercase()} ID list failed (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("VidAPI returned an empty ${type.key.lowercase()} ID list.")
            val source = body.source()
            val batch = ArrayList<Int>(BATCH_SIZE)
            var total = 0
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                val tmdbId = line.trim().toIntOrNull() ?: continue
                if (tmdbId <= 0) continue
                batch += tmdbId
                total++
                if (batch.size == BATCH_SIZE) {
                    persistBatch(type, syncId, batch, dao)
                    batch.clear()
                    onProgress(total)
                }
            }
            if (batch.isNotEmpty()) persistBatch(type, syncId, batch, dao)
            onProgress(total)
            if (total == 0) throw IOException("VidAPI returned no ${type.key.lowercase()} IDs.")
            total
        }
    }

    private suspend fun persistBatch(
        type: CatalogContentType,
        syncId: Long,
        ids: List<Int>,
        dao: CatalogAvailabilityDao,
    ) {
        dao.insertIfNew(ids.map { CatalogAvailabilityEntity(type.key, it, true, syncId) })
        dao.markSeen(type.key, syncId, ids)
        dao.insertSnapshotIds(ids.map { CatalogIdSnapshotEntity(syncId, type.key, it) })
    }

    companion object {
        private const val BATCH_SIZE = 500
    }
}
