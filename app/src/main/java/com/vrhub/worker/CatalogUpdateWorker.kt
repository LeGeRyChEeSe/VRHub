package com.vrhub.worker

import android.util.Log
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vrhub.data.AppDatabase
import com.vrhub.data.Constants
import com.vrhub.data.NetworkModule
import com.vrhub.data.ServerConfigRepository
import com.vrhub.logic.CatalogParser
import com.vrhub.logic.CatalogUtils
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
 * Uses the user-configured server settings via ServerConfigRepository.
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
            // Get server config from user settings (required for catalog sync)
            val configRepository = ServerConfigRepository(applicationContext)
            val savedConfig = configRepository.loadConfig()
            if (savedConfig == null || !savedConfig.isValid()) {
                Log.d(TAG, "No server configuration found. Skipping background catalog update.")
                return@withContext Result.success()
            }

            val baseUri = savedConfig.baseUri

            // 1. Initial lightweight check (outside lock)
            val remoteMetadata = CatalogUtils.getRemoteCatalogMetadata(baseUri)
            
            if (!CatalogUtils.isUpdateAvailable(applicationContext, baseUri, remoteMetadata, "notified_meta_")) {
                Log.d(TAG, "Catalog is up to date.")
                return@withContext Result.success()
            }

            // 2. Download to a PRIVATE temp file (outside lock)
            // This prevents blocking manual "Sync Now" UI operations during long background downloads.
            // We only move it to the shared cache location once it's fully downloaded.
            val privateTempFile = File(applicationContext.cacheDir, "worker_meta_temp.7z")
            val sanitizedBase = if (baseUri.endsWith("/")) baseUri else "$baseUri/"
            val metaUrl = "${sanitizedBase}meta.7z"
            
            CatalogUtils.downloadFile(metaUrl, privateTempFile)
            
                        
            
                        // 3. Coordination Phase (inside lock)
            
                        // We hold the lock during extraction and state updates to ensure atomicity
            
                        // between the background worker and manual repository syncs.
            
                        // TRADE-OFF: While this blocks manual syncs during extraction, it is necessary
            
                        // to prevent concurrent database writes and race conditions in metadata flags.
            
                        // The download (slowest part) was intentionally performed outside this lock.
            
                        CatalogUtils.catalogSyncMutex.withLock {
            
                            // Re-verify update is STILL needed (avoids race if repository just finished sync while we were downloading)
            
                            if (CatalogUtils.isUpdateAvailable(applicationContext, baseUri, remoteMetadata, "notified_meta_")) {
            
                                val calculatingMsg = applicationContext.getString(com.vrhub.R.string.catalog_update_calculating)
            
                                Log.i(TAG, "New catalog version detected! $calculatingMsg")
            
            
            
                                // Move private file to shared cache location
            
                                val sharedCacheFile = CatalogUtils.getCatalogMetaFile(applicationContext)
            
                                privateTempFile.renameTo(sharedCacheFile)
            
            
            
                                // Decode password from user-configured server settings
                                val password = try {
                                    if (savedConfig.password.isNotBlank()) {
                                        String(android.util.Base64.decode(savedConfig.password, android.util.Base64.NO_WRAP))
                                    } else {
                                        ""
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to decode password64 from config")
                                    savedConfig.password
                                }
            
            
            
                                val gameListContent = extractGameList(sharedCacheFile, password)
            
                                if (gameListContent.isNotEmpty()) {
            
                                    val count = CatalogUtils.calculateUpdateCount(applicationContext, gameListContent)
            
            
            
                                    val prefs = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            
                                    prefs.edit()
            
                                        .putBoolean("catalog_update_available", true)
            
                                        .putInt("catalog_update_count", count)
            
                                        .apply()
            
            
            
                                    // Save NOTIFICATION metadata so we don't notify again until NEXT server update
            
                                    CatalogUtils.saveMetadata(applicationContext, remoteMetadata, "notified_meta_")
            
                                    Log.i(TAG, "Update detected and notified: $count games added/updated.")
            
                                } else {
            
                                    Log.e(TAG, "Failed to extract game list content")
            
                                }
            
                            } else {
            
                                Log.i(TAG, "Update was already processed by manual sync during worker download.")
            
                                if (privateTempFile.exists()) privateTempFile.delete()
            
                            }
            
                            Unit // Ensure the withLock lambda returns Unit to avoid 'if' expression errors
            
                        }
            
            
            
                        Result.success()
            
                    } catch (e: Exception) {
            
                        Log.e(TAG, "Error during catalog update check", e)
            
                        Result.retry()
            
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
                    if (entry.name.endsWith("VRP-GameList.txt", ignoreCase = true) ||
                        entry.name.equals("GameList.txt", ignoreCase = true)) {
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
