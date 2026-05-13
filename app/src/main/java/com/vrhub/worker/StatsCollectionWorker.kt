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
            val installedGames = packages.associate {
                it.packageName to false
            }

            if (installedGames.isEmpty()) {
                Log.d(TAG, "doWork: no installed games")
                return@withContext Result.success()
            }

            // Collect stats with "standard" tier for background worker
            statsCollector.collectStats(installedGames, "standard")

            Log.d(TAG, "doWork: stats collection completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: failed", e)
            Result.retry()
        }
    }
}
