package com.vrhub.logic

import android.content.Context
import android.util.Log
import com.vrhub.data.Constants
import com.vrhub.data.NetworkModule
import com.vrhub.data.DownloadUtils
import com.vrhub.data.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utilities for checking catalog updates and metadata.
 */
object CatalogUtils {
    private const val TAG = "CatalogUtils"

    /**
     * Shared mutex to coordinate catalog operations between background worker
     * and manual sync in repository. Prevents race conditions when writing or clearing
     * update flags.
     */
    val catalogSyncMutex = Mutex()

    /**
     * Threshold for considering the cached catalog meta.7z as fresh (Story 4.3 Round 11 Fix).
     * 
     * Rationale: 1 hour (3,600,000 ms) provides an optimal balance between minimizing redundant 
     * server HEAD requests/downloads and ensuring users receive timely updates. This accounts 
     * for typical VRPirates mirror synchronization latencies and prevents excessive background 
     * traffic while the app is actively used.
     */
    const val CACHE_FRESHNESS_THRESHOLD_MS = 3600000L // 1 hour

    /**
     * Returns the file location for the temporary catalog metadata archive (meta.7z).
     * Using a consistent location allows the background worker to "hand off" the downloaded
     * file to the repository if a sync is triggered immediately after detection.
     *
     * @param context Android context to access cache directory.
     * @return File object pointing to the cached meta.7z location.
     */
    fun getCatalogMetaFile(context: Context): File {
        return File(context.cacheDir, "catalog_meta_cache.7z")
    }

    /**
     * Checks the remote metadata for meta.7z, retrieving Last-Modified, ETag, and potential hash headers.
     * 
     * Header Priority & Fallback:
     * 1. Standard "Last-Modified" and "ETag" are checked first.
     * 2. If "MD5" is not in standard headers, it looks for "X-Checksum-Md5" or "Content-MD5".
     * 3. "X-Checksum-Sha256" is also captured if available for future use.
     * 4. For file:// URLs, it uses the local file's last modified timestamp as a fallback (Story 4.3 Round 11 Fix).
     * 
     * @param baseUri The base URL of the VRPirates mirror.
     * @return A map containing header names and their values.
     */
    suspend fun getRemoteCatalogMetadata(baseUri: String): Map<String, String> = withContext(Dispatchers.IO) {
        val sanitizedBase = if (baseUri.endsWith("/")) baseUri else "$baseUri/"
        val metaUrl = "${sanitizedBase}meta.7z"
        val metadata = mutableMapOf<String, String>()

        // Fallback for local testing (Story 4.3 Round 11 Fix)
        if (metaUrl.startsWith("file://")) {
            try {
                val filePath = metaUrl.substring(7)
                val file = File(filePath)
                if (file.exists()) {
                    metadata["Last-Modified"] = file.lastModified().toString()
                    Log.d(TAG, "Local file metadata detected: ${metadata["Last-Modified"]}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read local file metadata", e)
            }
            return@withContext metadata
        }
        
        try {
            val request = Request.Builder()
                .url(metaUrl)
                .head()
                .header("User-Agent", Constants.USER_AGENT)
                .build()

            NetworkModule.okHttpClient.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    response.header("Last-Modified")?.let { metadata["Last-Modified"] = it }
                    response.header("ETag")?.let { metadata["ETag"] = it }
                    
                    // Fallback hash headers used by some CDNs or mirrors
                    response.header("X-Checksum-Md5")?.let { metadata["MD5"] = it }
                    response.header("Content-MD5")?.let { metadata["MD5"] = it }
                    response.header("X-Checksum-Sha256")?.let { metadata["SHA256"] = it }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking remote catalog metadata", e)
        }
        metadata
    }

    /**
     * Shared download utility for meta.7z files with cancellation support.
     * Replaces duplicated implementations in MainRepository and CatalogUpdateWorker.
     * 
     * @param url The URL of the file to download.
     * @param targetFile The destination file on disk.
     * @throws IOException if network or I/O operation fails.
     */
    suspend fun downloadFile(url: String, targetFile: File) = withContext(Dispatchers.IO) {
        // Fallback for local testing (Story 4.3 Round 11 Fix)
        if (url.startsWith("file://")) {
            try {
                val filePath = url.substring(7)
                val sourceFile = File(filePath)
                if (sourceFile.exists()) {
                    sourceFile.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@withContext
                } else {
                    throw IOException("Local file not found: $filePath")
                }
            } catch (e: Exception) {
                throw IOException("Failed to copy local file: ${e.message}", e)
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Constants.USER_AGENT)
            .build()

        NetworkModule.okHttpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} when downloading $url")
            val body = response.body ?: throw IOException("Empty body when downloading $url")
            
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    // Use shared utility for cancellable download with progress
                    // Progress is not reported for these small metadata downloads (usually < 1MB)
                    DownloadUtils.downloadWithProgress(
                        inputStream = input,
                        outputStream = output,
                        initialDownloaded = 0,
                        totalBytes = body.contentLength(),
                        isCancelled = { false },
                        onProgress = { _, _, _ -> }
                    )
                }
            }
        }
    }

    /**
     * Compares local saved metadata with remote one to determine if an update is available.
     * Uses Last-Modified, ETag, or MD5/SHA256 hashes for comparison.
     *
     * @param context Android context to access SharedPreferences.
     * @param baseUri The base URL of the VRPirates mirror.
     * @param remoteMetadata Optional pre-fetched metadata map to avoid redundant HEAD request.
     * @param prefix Prefix for the SharedPreferences keys (e.g., "meta_" or "notified_meta_").
     * @return True if a newer catalog version is detected on the server compared to the local state.
     */
    suspend fun isUpdateAvailable(
        context: Context,
        baseUri: String,
        remoteMetadata: Map<String, String>? = null,
        prefix: String = "meta_"
    ): Boolean {
        val metadata = remoteMetadata ?: getRemoteCatalogMetadata(baseUri)
        if (metadata.isEmpty()) return false
        
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check Last-Modified
        val remoteLastModified = metadata["Last-Modified"]
        val savedLastModified = prefs.getString("${prefix}last_modified", "")
        if (remoteLastModified != null && remoteLastModified != savedLastModified) {
            Log.d("CatalogUtils", "Update detected via Last-Modified: $remoteLastModified (was $savedLastModified)")
            return true
        }
        
        // Check ETag
        val remoteETag = metadata["ETag"]
        val savedETag = prefs.getString("${prefix}etag", "")
        if (remoteETag != null && remoteETag != savedETag) {
            Log.d("CatalogUtils", "Update detected via ETag: $remoteETag (was $savedETag)")
            return true
        }
        
        // Check MD5
        val remoteMD5 = metadata["MD5"]
        val savedMD5 = prefs.getString("${prefix}md5", "")
        if (remoteMD5 != null && remoteMD5 != savedMD5) {
            Log.d("CatalogUtils", "Update detected via MD5: $remoteMD5 (was $savedMD5)")
            return true
        }

        return false
    }

    /**
     * Saves the current catalog metadata to SharedPreferences to track the synchronized or notified version.
     * 
     * @param context Android context to access SharedPreferences.
     * @param metadata Map of metadata headers (Last-Modified, ETag, MD5) to persist.
     * @param prefix Prefix for the SharedPreferences keys (e.g., "meta_" or "notified_meta_").
     */
    fun saveMetadata(context: Context, metadata: Map<String, String>, prefix: String = "meta_") {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            metadata["Last-Modified"]?.let { putString("${prefix}last_modified", it) }
            metadata["ETag"]?.let { putString("${prefix}etag", it) }
            metadata["MD5"]?.let { putString("${prefix}md5", it) }
            apply()
        }
    }

    /**
     * Calculates the number of new or updated games by comparing provided catalog content
     * with the current local database.
     *
     * @param context Android context to access database.
     * @param gameListContent The raw content of VRP-GameList.txt.
     * @return Number of games that are either new or have a different versionCode.
     */
    suspend fun calculateUpdateCount(context: Context, gameListContent: String): Int = withContext(Dispatchers.IO) {
        if (gameListContent.isBlank()) return@withContext 0
        
        try {
            val newList = CatalogParser.parse(gameListContent)
            val db = com.vrhub.data.AppDatabase.getDatabase(context)
            val existingGames = db.gameDao().getAllGamesList().associateBy { it.releaseName }
            
            var changedCount = 0
            for (newGame in newList) {
                val existing = existingGames[newGame.releaseName]
                if (existing == null || existing.versionCode != newGame.versionCode) {
                    changedCount++
                }
            }
            changedCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate update count", e)
            0
        }
    }
}
