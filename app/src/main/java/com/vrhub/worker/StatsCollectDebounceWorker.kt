package com.vrhub.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vrhub.data.AppDatabase
import com.vrhub.data.ConsentPreferences
import com.vrhub.data.NetworkModule
import com.vrhub.data.StatsCollector
import com.vrhub.network.resolveTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Debounced stats collection worker.
 *
 * Triggered by user-initiated events (favorite toggle). Each call to
 * [enqueue] cancels any pending work and reschedules a fresh 5-minute timer
 * via [ExistingWorkPolicy.REPLACE]. After 5 minutes of inactivity, exactly
 * one consolidated POST is sent with the current state (favorites, installed
 * games) read live from Room and PackageManager at fire time.
 *
 * Differs from the periodic [StatsCollectionWorker] in three ways:
 *  - Uses [OneTimeWorkRequest] (one-shot) instead of periodic
 *  - Uses [ExistingWorkPolicy.REPLACE] instead of KEEP (so each trigger resets the timer)
 *  - Passes the **real** `isFavorite` from Room (the periodic worker hardcodes `false`
 *    because its trigger is broad stats, not a favorite toggle)
 *
 * The periodic [StatsCollectionWorker] remains as a fallback in case the user toggles
 * once and then closes the app before the 5-minute timer fires.
 */
class StatsCollectDebounceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "StatsDebounceWorker"
        const val WORK_NAME = "stats_collect_debounce"
        private const val DEBOUNCE_MS = 5L * 60L * 1000L  // 5 minutes
        private const val BACKOFF_MS = 10_000L            // 10 seconds (WorkManager min)

        /**
         * Enqueue a debounced stats collection. Each call replaces any pending work,
         * so rapid toggles coalesce into a single POST fired 5 minutes after the
         * LAST toggle.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<StatsCollectDebounceWorker>()
                .setConstraints(constraints)
                .setInitialDelay(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MS, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            Log.d(TAG, "Stats debounce work enqueued (${DEBOUNCE_MS}ms initial delay)")
        }

        /**
         * Cancel any pending debounced stats collection. Currently unused, exposed
         * for completeness (e.g. consent-revoked flow).
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Stats debounce work cancelled")
        }

        /**
         * Fetch the user's tier with a safe fallback to "standard" on any failure.
         * Mirrors `MainRepository.resolveUserTierOrDefault()` but uses the network
         * helper directly (no `Dispatchers.IO` wrapping — the worker is already on IO).
         */
        private suspend fun resolveTier(): String {
            return try {
                val response = NetworkModule.statsApiService.getUserTier("anonymous")
                if (response.isSuccessful) {
                    resolveTier(response.body()?.tier)
                } else {
                    Log.w(TAG, "resolveTier: server returned ${response.code()}, defaulting")
                    "standard"
                }
            } catch (e: Exception) {
                Log.w(TAG, "resolveTier: failed, defaulting", e)
                "standard"
            }
        }
    }

    override suspend fun doWork(): Result {
        // Note: we deliberately don't wrap the body in `withContext(Dispatchers.IO)` —
        // CoroutineWorker already runs on a background dispatcher (WorkManager default),
        // Room suspend DAOs handle their own threading, and `StatsCollector.collectStats`
        // does its own `withContext(Dispatchers.IO)` internally. Wrapping here would only
        // add an unnecessary dispatcher hop (and complicate unit testing under Robolectric).
        return try {
            // 1. Consent gate
            val consentPreferences = ConsentPreferences(applicationContext)
            val consentEnabled = consentPreferences.consentEnabled.first()
            if (!consentEnabled) {
                Log.d(TAG, "doWork: skipped — no consent")
                return Result.success()
            }

            // 2. Read installed packages (PackageManager.getInstalledPackages is blocking
            //    but CoroutineWorker runs off the main thread so this is safe)
            val pm = applicationContext.packageManager
            val installedPackageNames = pm.getInstalledPackages(0)
                .mapNotNull { it.packageName }
                .toSet()

            if (installedPackageNames.isEmpty()) {
                Log.d(TAG, "doWork: no installed packages")
                return Result.success()
            }

            // 3. Join with Room catalogue — keep REAL isFavorite from Room
            //    (this is the debounce-triggered worker, so the favorite flag is
            //    the whole point of the report — must NOT be hardcoded to false)
            val db = AppDatabase.getDatabase(applicationContext)
            val catalogGames = db.gameDao().getAllGamesList()
                .filter { installedPackageNames.contains(it.packageName) }

            val games: Map<String, Pair<Boolean, String?>> = catalogGames
                .associate { it.packageName to Pair(it.isFavorite, it.gameName) }

            if (games.isEmpty()) {
                Log.d(TAG, "doWork: no installed games in catalogue")
                return Result.success()
            }

            // 4. Resolve tier and post (StatsCollector handles its own IO dispatching)
            val tier = resolveTier()
            val statsCollector = StatsCollector(
                NetworkModule.statsApiService,
                consentPreferences
            )
            statsCollector.collectStats(null, games, tier)

            Log.d(TAG, "doWork: stats collection completed (${games.size} games, tier=$tier)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: failed, will retry", e)
            Result.retry()
        }
    }
}