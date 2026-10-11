package com.vistaro.app.data.mongo

import android.util.Log
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.vistaro.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import java.util.concurrent.TimeUnit

class MongoManager(
    private val connectionUri: String = BuildConfig.MONGODB_URI,
    private val databaseName: String = "vistaro",
) {
    val isConfigured: Boolean
        get() = connectionUri.isNotBlank() && connectionUri.startsWith("mongodb")

    private val client: MongoClient? by lazy {
        if (!isConfigured) return@lazy null
        try {
            val resolvedUri = normalizeUri(connectionUri)
            val settings = MongoClientSettings.builder()
                .applyConnectionString(ConnectionString(resolvedUri))
                .applyToSocketSettings { builder ->
                    builder.connectTimeout(10, TimeUnit.SECONDS)
                    builder.readTimeout(10, TimeUnit.SECONDS)
                }
                .applyToClusterSettings { builder ->
                    builder.serverSelectionTimeout(10, TimeUnit.SECONDS)
                }
                .build()
            MongoClient.create(settings)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create MongoClient: ${t.message}", t)
            null
        }
    }

    private fun normalizeUri(uri: String): String {
        if (uri.startsWith("mongodb+srv://") && uri.contains("cluster0.vdeeqc6.mongodb.net")) {
            val authPart = uri.substringAfter("mongodb+srv://").substringBefore("@")
            return "mongodb://$authPart@ac-jwlki6z-shard-00-00.vdeeqc6.mongodb.net:27017,ac-jwlki6z-shard-00-01.vdeeqc6.mongodb.net:27017,ac-jwlki6z-shard-00-02.vdeeqc6.mongodb.net:27017/?ssl=true&replicaSet=atlas-uthsl1-shard-0&authSource=admin&retryWrites=true&w=majority"
        }
        return uri
    }

    val database: MongoDatabase?
        get() = client?.getDatabase(databaseName)

    val catalogCollection: MongoCollection<Document>?
        get() = database?.getCollection("catalog")

    val usersCollection: MongoCollection<Document>?
        get() = database?.getCollection("users")

    suspend fun initializeIndexes(): Boolean = withContext(Dispatchers.IO) {
        val col = catalogCollection ?: return@withContext false
        try {
            col.createIndex(Indexes.compoundIndex(Indexes.text("title"), Indexes.text("originalTitle")))
            col.createIndex(Indexes.ascending("tmdbId", "contentType"))
            col.createIndex(Indexes.descending("popularity"))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Mongo indexes: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "MongoManager"
    }
}
