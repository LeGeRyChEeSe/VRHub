package com.vrhub.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vrhub.data.AppDatabase
import com.vrhub.data.ConsentPreferences
import com.vrhub.data.NetworkModule
import com.vrhub.data.StatsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically collects and sends anonymous game stats.
 * Runs daily when consent is enabled. Respects NetworkType.CONNECTED constraint.
 * Uses exponential backoff on failure via WorkManager's default retry policy.
 */
class StatsCollectionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "StatsCollectionWorker"
        private const val WORK_NAME = "stats_collection_work"
        private const val REPEAT_INTERVAL = 1L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<StatsCollectionWorker>(
                REPEAT_INTERVAL, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(REPEAT_INTERVAL, TimeUnit.DAYS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )

            Log.d(TAG, "Stats collection work enqueued")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Stats collection work cancelled")
        }

        private suspend fun resolveTier(): String? {
            return try {
                val response = NetworkModule.statsApiService.getUserTier("anonymous")
                if (response.isSuccessful) response.body()?.tier else null
            } catch (e: Exception) {
                Log.w(TAG, "resolveTier: failed", e)
                null
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val consentPreferences = ConsentPreferences(applicationContext)
            val consentEnabled = consentPreferences.consentEnabled.first()

            if (!consentEnabled) {
                Log.d(TAG, "doWork: skipped — no consent")
                return@withContext Result.success()
            }

            val statsCollector = StatsCollector(
                NetworkModule.statsApiService,
                consentPreferences
            )

            // Get installed packages from PackageManager
            val pm = applicationContext.packageManager
            val packages = pm.getInstalledPackages(0)
            val installedPackageNames = packages.map { it.packageName }.toSet()

            if (installedPackageNames.isEmpty()) {
                Log.d(TAG, "doWork: no installed games")
                return@withContext Result.success()
            }

            // Only report packages present in the VRHub catalog — mirrors maybeCollectStats()
            // to avoid sending system apps, test packages, and other non-VRHub content.
            val db = AppDatabase.getDatabase(applicationContext)
            val installedGames: Map<String, Pair<Boolean, String?>> = db.gameDao().getAllGamesList()
                .filter { installedPackageNames.contains(it.packageName) }
                .associate { it.packageName to Pair(it.isFavorite, it.gameName) }

            if (installedGames.isEmpty()) {
                Log.d(TAG, "doWork: no VRHub catalog games installed")
                return@withContext Result.success()
            }

            // Resolve real user tier (same pattern as MainRepository.maybeCollectStats)
            val tier = resolveTier() ?: "standard"
            statsCollector.collectStats(null, installedGames, tier)

            Log.d(TAG, "doWork: stats collection completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: failed", e)
            Result.retry()
        }
    }
}
