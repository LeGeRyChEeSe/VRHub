package com.vrhub.data

import android.util.Log
import com.vrhub.network.GameStat
import com.vrhub.network.StatsApiService
import com.vrhub.network.StatsCollectRequest
import com.vrhub.network.ConsentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class StatsCollector(
    private val statsApiService: StatsApiService,
    private val consentPreferences: ConsentPreferences
) {
    companion object {
        private const val TAG = "StatsCollector"
        private val collectionEnabled = AtomicBoolean(true)
    }

    suspend fun collectStats(
        games: Map<String, Boolean>,
        tier: String
    ): Unit = withContext(Dispatchers.IO) {
        val consentEnabled = consentPreferences.consentEnabled.first()
        if (!consentEnabled) {
            Log.d(TAG, "skipped — no consent")
            return@withContext
        }

        if (!collectionEnabled.get()) {
            Log.d(TAG, "skipped — collection disabled after server rejection")
            return@withContext
        }

        val gameStats = games.mapNotNull { (packageName, isFavorite) ->
            if (packageName == null) {
                Log.w(TAG, "Skipping game with null packageName")
                null
            } else {
                GameStat(packageName = packageName, isFavorite = isFavorite)
            }
        }

        if (gameStats.isEmpty()) {
            Log.d(TAG, "skipped — no games to report")
            return@withContext
        }

        val request = StatsCollectRequest(
            games = gameStats,
            tier = tier,
            timestamp = System.currentTimeMillis()
        )

        try {
            val response = statsApiService.collectStats(request)

            if (response.isSuccessful) {
                Log.d(TAG, "Stats collected successfully: ${gameStats.size} games")
            } else {
                val code = response.code()
                val errorBody = response.errorBody()?.string()
                when (code) {
                    403 -> {
                        Log.w(TAG, "Server rejected stats (403) — disabling future collection. Error: $errorBody")
                        collectionEnabled.set(false)
                    }
                    else -> {
                        Log.e(TAG, "Stats collection failed: $code. Error: $errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stats collection error", e)
        }
    }

    suspend fun updateConsent(consent: Boolean): Unit = withContext(Dispatchers.IO) {
        val response = statsApiService.updateConsent(ConsentRequest(consent = consent))
        if (response.isSuccessful) {
            Log.d(TAG, "Consent updated on server")
            if (consent) {
                collectionEnabled.set(true)
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "Consent update failed: ${response.code()}. Error: $errorBody")
            throw IOException("Consent update failed: ${response.code()}")
        }
    }
}