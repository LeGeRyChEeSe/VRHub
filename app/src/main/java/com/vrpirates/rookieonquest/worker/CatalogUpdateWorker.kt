package com.vrpirates.rookieonquest.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.vrpirates.rookieonquest.data.Constants
import com.vrpirates.rookieonquest.data.MainRepository
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically checks for catalog updates.
 *
 * Implements AC 1: App checks VRPirates mirror for catalog updates using WorkManager.
 * Implements AC 5: Resource efficiency (NetworkType.CONNECTED, 6h interval).
 */
class CatalogUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CatalogUpdateWorker"
        private const val WORK_NAME = "catalog_update_check"
        
        /** Key used in SharedPreferences and WorkData to track update availability */
        const val KEY_UPDATE_AVAILABLE = "catalog_update_available"

        /**
         * Schedules the periodic catalog update check.
         * Uses ExistingPeriodicWorkPolicy.KEEP to avoid rescheduling on every app launch.
         *
         * The worker runs every 6 hours and requires an active network connection.
         * 
         * @param context Android context used to get WorkManager instance
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // AC 1: Periodic check every 6 hours
            val request = PeriodicWorkRequestBuilder<CatalogUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Catalog update worker scheduled (6h interval)")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting periodic catalog update check...")
        val repository = MainRepository(applicationContext)
        return doWorkInternal(repository)
    }

    /**
     * Internal implementation of work logic for testability.
     * @param repository The repository to use for catalog operations
     */
    suspend fun doWorkInternal(repository: MainRepository): Result {
        return try {
            // AC 1: Lightweight HTTP HEAD request
            val remoteLastModified = repository.getRemoteCatalogInfo()
            
            // AC 1: Compare with local preference
            val isUpdateAvailable = repository.isCatalogUpdateAvailable(remoteLastModified)
            
            val prefs = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            
            if (isUpdateAvailable) {
                // AC 2: Get "[X] games added/updated" count
                // This requires downloading meta.7z but it's small
                val updateCount = repository.calculateUpdateCountFromMeta()
                val editor = prefs.edit()
                    .putBoolean(KEY_UPDATE_AVAILABLE, true)
                    .putInt(Constants.PreferenceKeys.CATALOG_UPDATE_GAME_COUNT, updateCount)
                
                if (remoteLastModified != null) {
                    editor.putString(Constants.PreferenceKeys.CATALOG_UPDATE_REMOTE_LAST_MODIFIED, remoteLastModified)
                }
                editor.apply()
                Log.i(TAG, "Catalog update available: $updateCount games added/updated")
            } else {
                val editor = prefs.edit()
                    .putBoolean(KEY_UPDATE_AVAILABLE, false)
                    .putInt(Constants.PreferenceKeys.CATALOG_UPDATE_GAME_COUNT, 0)
                
                if (remoteLastModified != null) {
                    editor.putString(Constants.PreferenceKeys.CATALOG_UPDATE_REMOTE_LAST_MODIFIED, remoteLastModified)
                }
                editor.apply()
                Log.i(TAG, "Catalog is up to date")
            }
            
            Result.success(workDataOf(KEY_UPDATE_AVAILABLE to isUpdateAvailable))
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic catalog update check", e)
            // Retry on transient failures (network issues, etc.)
            Result.retry()
        }
    }
}
