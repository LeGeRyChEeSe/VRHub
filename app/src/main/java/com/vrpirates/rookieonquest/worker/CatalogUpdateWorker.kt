package com.vrpirates.rookieonquest.worker

import android.util.Log
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vrpirates.rookieonquest.data.AppDatabase
import com.vrpirates.rookieonquest.data.Constants
import com.vrpirates.rookieonquest.data.NetworkModule
import com.vrpirates.rookieonquest.logic.CatalogParser
import com.vrpirates.rookieonquest.logic.CatalogUtils
import com.vrpirates.rookieonquest.network.VrpService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File

/**
 * Background worker that periodically checks for catalog updates.
 * If an update is detected (via Last-Modified or ETag comparison), it downloads
 * the metadata file, extracts the game list, and calculates the number of 
 * new or updated games compared to the local database.
 */
class CatalogUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CatalogUpdateWorker"
    }

            override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

                Log.i(TAG, "Checking for catalog updates in background...")

        

                try {

                    val service = NetworkModule.retrofit.create(VrpService::class.java)

                    val config = service.getPublicConfig()

                    val baseUri = config.baseUri

        

                    // Wrap the ENTIRE check and calculation in Mutex to prevent race conditions (Finding 5)

                    // This ensures that the whole "Read -> Download -> Calculate -> Write" flow is atomic

                    // relative to manual syncs in the repository.

                    CatalogUtils.catalogSyncMutex.withLock {

                        // 1. Check if update is available (lightweight check)

                        val remoteMetadata = CatalogUtils.getRemoteCatalogMetadata(baseUri)

                        

                        // Pass remoteMetadata and use "notified_meta_" prefix (Story 4.3 Round 4 Fix)

                        // This prevents the worker from interfering with the repository's sync state detection.

                        if (CatalogUtils.isUpdateAvailable(applicationContext, baseUri, remoteMetadata, "notified_meta_")) {

                            val calculatingMsg = applicationContext.getString(com.vrpirates.rookieonquest.R.string.catalog_update_calculating)

                            Log.i(TAG, "New catalog version detected! $calculatingMsg")

                            

                            // 2. Download and parse new catalog to find actual count of changes

                            // Since we are inside the lock, we are safe from concurrent downloads of the same file

                            val tempFile = CatalogUtils.getCatalogMetaFile(applicationContext)

                            val sanitizedBase = if (baseUri.endsWith("/")) baseUri else "$baseUri/"

                            val metaUrl = "${sanitizedBase}meta.7z"

                            var count = 0

                            

                            try {

                                downloadFile(metaUrl, tempFile)

                                

                                // We need the password to extract the list. Worker can use the one from repository (Story 4.3 Round 17 Fix)

                                val repository = com.vrpirates.rookieonquest.data.MainRepository(applicationContext)

                                repository.fetchConfig() // Ensure config is loaded to get decodedPassword

                                val password = repository.decodedPassword

                                

                                val gameListContent = extractGameList(tempFile, password)

                                count = CatalogUtils.calculateUpdateCount(applicationContext, gameListContent)

                            } catch (e: Exception) {

                                Log.e(TAG, "Failed to download or parse update for count calculation", e)

                            }

                            

                            // 3. Re-verify update is still needed before saving (avoids race if repository just finished sync)

                            if (CatalogUtils.isUpdateAvailable(applicationContext, baseUri, remoteMetadata, "notified_meta_")) {

                                val prefs = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

                                prefs.edit()

                                    .putBoolean("catalog_update_available", true)

                                    .putInt("catalog_update_count", count)

                                    .apply()

        

                                // 4. Save NOTIFICATION metadata so we don't notify again until NEXT server update

                                CatalogUtils.saveMetadata(applicationContext, remoteMetadata, "notified_meta_")

                                Log.i(TAG, "Update detected and notified: $count games added/updated.")

                            } else {

                                Log.i(TAG, "Update was already processed by manual sync during worker execution.")

                            }

                        } else {

                            Log.d(TAG, "Catalog is up to date.")

                        }

                    }

        

                    Result.success()

                } catch (e: Exception) {

                    Log.e(TAG, "Error during catalog update check", e)

                    Result.retry()

                }

            }

        

            private suspend fun downloadFile(url: String, targetFile: File) {

        
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", Constants.USER_AGENT)
            .build()
            
        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun extractGameList(file: File, password: String? = null): String {
        try {
            val builder = SevenZFile.builder().setFile(file)
            if (password != null) {
                builder.setPassword(password)
            }
            builder.get().use { sevenZFile ->
                var entry = sevenZFile.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith("VRP-GameList.txt", ignoreCase = true)) {
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (sevenZFile.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                        return out.toString("UTF-8")
                    }
                    entry = sevenZFile.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting game list from meta.7z", e)
        }
        return ""
    }
}
