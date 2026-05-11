# VRHub Client — Stats Collection Implementation Guide

> **Version**: 4.2.0
> **Project**: VRHub (Android App)
> **Backend**: vrhub-monetization
> **Last Updated**: 2025-05-07

---

## Table of Contents

1. [Implementation Checklist](#1-implementation-checklist)
2. [New Files to Create](#2-new-files-to-create)
3. [Network Module Updates](#3-network-module-updates)
4. [DataStore Implementation](#4-datastore-implementation)
5. [Consent Dialog](#5-consent-dialog)
6. [Settings Screen Integration](#6-settings-screen-integration)
7. [StatsCollector Service](#7-statscollector-service)
8. [Integration with MainRepository](#8-integration-with-mainrepository)
9. [WorkManager Integration](#9-workmanager-integration)
10. [Testing Strategy](#10-testing-strategy)

---

## 1. Implementation Checklist

```
□ Create StatsApiService interface in network package
□ Create StatsModels data classes
□ Create ConsentPreferences DataStore class
□ Create ConsentDialog composable
□ Add privacy section to Settings screen
□ Create StatsCollector service
□ Integrate StatsCollector with MainRepository
□ Create StatsCollectionWorker for periodic collection
□ Add tests for StatsCollector
□ Verify consent flow end-to-end
```

---

## 2. New Files to Create

```
app/src/main/java/com/vrhub/
├── network/
│   ├── StatsApiService.kt       (NEW - Retrofit interface)
│   └── StatsModels.kt           (NEW - data classes)
├── data/
│   └── ConsentPreferences.kt    (NEW - DataStore wrapper)
└── worker/
    └── StatsCollectionWorker.kt  (NEW - WorkManager periodic task)
```

---

## 3. Network Module Updates

### 3.1 StatsApiService.kt

Create `app/src/main/java/com/vrhub/network/StatsApiService.kt`:

```kotlin
package com.vrhub.network

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit service interface for VRHub stats collection API.
 * Communicates with vrhub-monetization server.
 */
interface StatsApiService {

    /**
     * POST /stats/collect - Collect anonymized user statistics
     */
    @POST("stats/collect")
    suspend fun collectStats(@Body request: StatsCollectRequest): Response<StatsCollectResponse>

    /**
     * POST /stats/consent - Update user consent status
     */
    @POST("stats/consent")
    suspend fun updateConsent(@Body request: ConsentRequest): Response<ConsentResponse>

    /**
     * GET /user/tier - Get user tier from server
     */
    @GET("user/tier")
    suspend fun getUserTier(@Query("email") email: String): Response<UserTierResponse>
}
```

### 3.2 StatsModels.kt

Create `app/src/main/java/com/vrhub/network/StatsModels.kt`:

```kotlin
package com.vrhub.network

import com.google.gson.annotations.SerializedName

/**
 * POST /stats/collect - Collect anonymized user statistics
 */
@com.google.gson.annotations.SerializedName("games")
data class StatsCollectRequest(
    val games: List<GameStat>,
    val tier: String,
    val timestamp: Long
)

data class GameStat(
    @SerializedName("package_name")
    val packageName: String,
    @SerializedName("is_favorite")
    val isFavorite: Boolean
)

data class StatsCollectResponse(
    val message: String
)

/**
 * POST /stats/consent - Update user consent status
 */
data class ConsentRequest(
    val consent: Boolean
)

data class ConsentResponse(
    val message: String,
    val consent: Boolean
)

/**
 * GET /user/tier - Get user tier from server
 */
data class UserTierResponse(
    val email: String,
    val tier: String?,
    val status: String
)
```

### 3.3 Adding to Retrofit Builder

Update your existing network module (e.g., `NetworkModule.kt` or wherever you build Retrofit):

```kotlin
private fun createMonetizationRetrofit(baseUrl: String): Retrofit {
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

// Create StatsApiService
val statsApiService: StatsApiService = createMonetizationRetrofit(
    serverConfig.monetizationUrl ?: "https://vrhub.sunshine-aio.com"
).create(StatsApiService::class.java)
```

---

## 4. DataStore Implementation

### 4.1 ConsentPreferences.kt

Create `app/src/main/java/com/vrhub/data/ConsentPreferences.kt`:

```kotlin
package com.vrhub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore wrapper for user consent preferences.
 * Uses VRHub's settings DataStore to persist consent state.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vrhub_settings"
)

class ConsentPreferences(private val context: Context) {

    private val dataStore = context.settingsDataStore

    companion object {
        /** Key for consent enabled preference */
        val CONSENT_ENABLED = booleanPreferencesKey("consent_enabled")
    }

    /**
     * Flow of the user's consent state.
     * Emits `true` if user has consented to anonymous stats collection.
     */
    val consentEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[CONSENT_ENABLED] ?: false
        }

    /**
     * Sets the consent preference.
     * @param enabled `true` to enable stats collection, `false` to disable
     */
    suspend fun setConsentEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CONSENT_ENABLED] = enabled
        }
    }
}
```

---

## 5. Consent Dialog

### 5.1 ConsentDialog.kt

Create `app/src/main/java/com/vrhub/ui/components/ConsentDialog.kt`:

```kotlin
package com.vrhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-launch consent dialog for anonymous statistics collection.
 * Shown once when the user first opens the app.
 */
@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Must choose */ },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Help Improve VRHub",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Share anonymous usage statistics to help us understand VR game trends and improve your experience.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "We collect:",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                BulletPoint("Installed games on your device")
                BulletPoint("Your favorite games (marked with ⭐)")
                BulletPoint("Your tier (standard, supporter, or lucky)")

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "We do NOT collect any personal information, device identifiers, or location data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Accept")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

### 5.2 Integration in MainActivity

Update `MainActivity.kt` to show the consent dialog on first launch:

```kotlin
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    // existing parameters...
) {
    val context = LocalContext.current
    val consentPreferences = remember { ConsentPreferences(context) }
    var showConsentDialog by remember { mutableStateOf(false) }

    // Check if consent dialog should be shown on first launch
    LaunchedEffect(Unit) {
        val hasSeenConsent = context.getSharedPreferences("vrhub_prefs", Context.MODE_PRIVATE)
            .getBoolean("has_seen_consent_dialog", false)
        if (!hasSeenConsent) {
            showConsentDialog = true
        }
    }

    // Show consent dialog
    if (showConsentDialog) {
        ConsentDialog(
            onAccept = {
                viewModelScope.launch {
                    consentPreferences.setConsentEnabled(true)
                }
                context.getSharedPreferences("vrhub_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_seen_consent_dialog", true)
                    .apply()
                showConsentDialog = false
            },
            onDecline = {
                context.getSharedPreferences("vrhub_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_seen_consent_dialog", true)
                    .apply()
                showConsentDialog = false
            }
        )
    }

    // Rest of MainScreen implementation...
}
```

---

## 6. Settings Screen Integration

### 6.1 Add Privacy Section to Settings

Update your existing Settings screen (e.g., `SettingsScreen.kt`) to include:

```kotlin
@Composable
fun SettingsPrivacySection(
    consentEnabled: Boolean,
    onConsentChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Privacy",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Anonymous Statistics",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Help improve VRHub by sharing anonymous usage data. " +
                        "We collect installed games, favorites, and your tier. " +
                        "No personal information is collected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Share anonymous statistics")
                    if (consentEnabled) {
                        Text(
                            text = "✓ Your data is being shared",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                Switch(
                    checked = consentEnabled,
                    onCheckedChange = onConsentChanged
                )
            }
        }
    }
}
```

---

## 7. StatsCollector Service

### 7.1 StatsCollector.kt

Create `app/src/main/java/com/vrhub/data/StatsCollector.kt`:

```kotlin
package com.vrhub.data

import android.content.Context
import android.util.Log
import com.vrhub.network.GameStat
import com.vrhub.network.StatsApiService
import com.vrhub.network.StatsCollectRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Service for collecting and sending anonymized statistics to the VRHub server.
 * Respects user consent and only sends data when permitted.
 */
class StatsCollector(
    private val context: Context,
    private val statsApiService: StatsApiService,
    private val consentPreferences: ConsentPreferences
) {
    companion object {
        private const val TAG = "StatsCollector"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Collects and sends anonymous statistics to the server.
     * Called when:
     * - App starts (if consent given)
     * - User changes favorite status
     * - Periodic WorkManager task triggers
     *
     * @param installedGames Map of package name to is_favorite
     * @param userTier User's tier (standard, supporter, lucky)
     */
    suspend fun collectStats(
        installedGames: Map<String, Boolean>,
        userTier: String = "standard"
    ) = withContext(Dispatchers.IO) {
        // Check consent first
        val consentEnabled = consentPreferences.consentEnabled.first()
        if (!consentEnabled) {
            Log.d(TAG, "Stats collection skipped - user has not consented")
            return@withContext
        }

        val games = installedGames.map { (packageName, isFavorite) ->
            GameStat(packageName = packageName, isFavorite = isFavorite)
        }

        val request = StatsCollectRequest(
            games = games,
            tier = userTier,
            timestamp = System.currentTimeMillis() / 1000
        )

        try {
            val response = statsApiService.collectStats(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Stats collected successfully: ${games.size} games")
            } else {
                Log.w(TAG, "Stats collection failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stats collection error", e)
        }
    }

    /**
     * Updates the user's consent preference on the server.
     * Called when user toggles consent in settings.
     *
     * @param enabled Whether the user has consented to stats collection
     */
    suspend fun updateConsent(enabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            val response = statsApiService.updateConsent(
                com.vrhub.network.ConsentRequest(consent = enabled)
            )
            if (response.isSuccessful) {
                Log.d(TAG, "Consent updated on server: $enabled")
            } else {
                Log.w(TAG, "Consent update failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Consent update error", e)
        }
    }

    /**
     * Gets the user's tier from the server.
     *
     * @param email User's email address
     * @return User's tier (standard, supporter, lucky) or null if not found
     */
    suspend fun getUserTier(email: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = statsApiService.getUserTier(email)
            if (response.isSuccessful) {
                response.body()?.tier
            } else {
                Log.w(TAG, "Get tier failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get tier error", e)
            null
        }
    }
}
```

---

## 8. Integration with MainRepository

### 8.1 Integration Points

In `MainRepository.kt`, add stats collection after successful operations:

```kotlin
// After syncCatalog() completes successfully
suspend fun syncCatalog(...) {
    // ... existing code ...
    onProgress(1f)
    Log.i(TAG, "Catalog sync complete")

    // NEW: Trigger stats collection after catalog sync
    maybeCollectStats()
}

// After toggleFavorite() is called
suspend fun toggleFavorite(releaseName: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
    gameDao.updateFavorite(releaseName, isFavorite)

    // NEW: Trigger stats collection after favorite change
    maybeCollectStats()
}

// New helper method
private suspend fun maybeCollectStats() {
    if (!consentPreferences.consentEnabled.first()) return

    val installedGames = getInstalledPackagesMap()
    val favoriteGames = gameDao.getAllGamesList()
        .filter { it.isFavorite }
        .associate { it.packageName to true }

    val allGames = installedGames.keys.associateWith { pkg ->
        favoriteGames[pkg] ?: false
    }

    val tier = getCurrentUserTier() ?: "standard"
    statsCollector.collectStats(allGames, tier)
}

private fun getCurrentUserTier(): String? {
    // TODO: Implement based on your existing tier management
    // Return user's current tier (standard/supporter/lucky)
    return null
}
```

### 8.2 Initialize StatsCollector in Application

In your Application class or MainActivity:

```kotlin
class VRHubApplication : Application() {
    lateinit var statsCollector: StatsCollector
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize consent preferences
        val consentPreferences = ConsentPreferences(this)

        // Get base URL from server config
        val serverConfig = ServerConfigRepository.getActiveConfig(this)
        val baseUrl = serverConfig?.monetizationUrl ?: "https://vrhub.sunshine-aio.com"

        // Create Retrofit instance
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(NetworkModule.okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val statsApiService = retrofit.create(StatsApiService::class.java)

        // Initialize StatsCollector
        statsCollector = StatsCollector(this, statsApiService, consentPreferences)
    }
}
```

---

## 9. WorkManager Integration

### 9.1 StatsCollectionWorker.kt

Create `app/src/main/java/com/vrhub/worker/StatsCollectionWorker.kt`:

```kotlin
package com.vrhub.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.vrhub.network.StatsApiService
import com.vrhub.data.ConsentPreferences
import kotlinx.coroutines.flow.first
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic stats collection.
 * Runs daily to collect and send anonymized statistics.
 */
class StatsCollectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "StatsCollectionWorker"
        private const val WORK_NAME = "stats_collection_work"

        /**
         * Enqueues periodic stats collection work.
         * Call this when the app starts or when consent is granted.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<StatsCollectionWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Stats collection work enqueued")
        }

        /**
         * Cancels periodic stats collection.
         * Call this when consent is revoked.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Stats collection work cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic stats collection")

        val consentPreferences = ConsentPreferences(applicationContext)

        // Check consent
        val consentEnabled = consentPreferences.consentEnabled.first()
        if (!consentEnabled) {
            Log.d(TAG, "Skipping stats collection - user has not consented")
            return Result.success()
        }

        // Get dependencies
        val statsApiService = getStatsApiService()

        // Get installed games
        val installedGames = getInstalledGames()
        val favoriteGames = getFavoriteGames()
        val allGames = installedGames.keys.associateWith { pkg ->
            favoriteGames[pkg] ?: false
        }

        // Send stats
        val request = com.vrhub.network.StatsCollectRequest(
            games = allGames.map { (pkg, fav) ->
                com.vrhub.network.GameStat(packageName = pkg, isFavorite = fav)
            },
            tier = "standard", // TODO: Get actual tier
            timestamp = System.currentTimeMillis() / 1000
        )

        return try {
            val response = statsApiService.collectStats(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Periodic stats collection successful")
                Result.success()
            } else {
                Log.w(TAG, "Periodic stats collection failed: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Periodic stats collection error", e)
            Result.retry()
        }
    }

    private fun getStatsApiService(): StatsApiService {
        // TODO: Use actual base URL from config
        val retrofit = Retrofit.Builder()
            .baseUrl("https://vrhub.sunshine-aio.com")
            .client(okhttp3.OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(StatsApiService::class.java)
    }

    private fun getInstalledGames(): Map<String, Boolean> {
        val pm = applicationContext.packageManager
        val packages = pm.getInstalledPackages(0)
        return packages.associate {
            it.packageName to false // Default to not favorite
        }
    }

    private fun getFavoriteGames(): Set<String> {
        // TODO: Query database for favorite games
        return emptySet()
    }
}
```

### 9.2 WorkManager Lifecycle

```kotlin
// When consent is granted
fun onConsentGranted() {
    StatsCollectionWorker.enqueue(context)
}

// When consent is revoked
fun onConsentRevoked() {
    StatsCollectionWorker.cancel(context)
}
```

---

## 10. Testing Strategy

### 10.1 StatsCollector Tests

```kotlin
class StatsCollectorTest {

    @Test
    fun `collectStats does nothing when consent is false`() = runTest {
        // Given
        val context = FakeContext()
        val consentPrefs = FakeConsentPreferences(consentEnabled = false)
        val apiService = FakeStatsApiService()
        val collector = StatsCollector(context, apiService, consentPrefs)

        // When
        collector.collectStats(mapOf("com.test.game" to false))

        // Then - no network call made
        assertTrue(apiService.collectStatsCalled)
        assertNull(apiService.lastRequest)
    }

    @Test
    fun `collectStats sends correct payload when consent is true`() = runTest {
        // Given
        val context = FakeContext()
        val consentPrefs = FakeConsentPreferences(consentEnabled = true)
        val apiService = FakeStatsApiService()
        val collector = StatsCollector(context, apiService, consentPrefs)

        val games = mapOf(
            "com.beatgames.beatsaber" to true,
            "com.vrchat.vrchat" to false
        )

        // When
        collector.collectStats(games, "lucky")

        // Then
        assertTrue(apiService.collectStatsCalled)
        val request = apiService.lastRequest!!
        assertEquals(2, request.games.size)
        assertEquals("com.beatgames.beatsaber", request.games[0].packageName)
        assertTrue(request.games[0].isFavorite)
        assertEquals("lucky", request.tier)
    }

    @Test
    fun `updateConsent calls API with correct value`() = runTest {
        // Given
        val context = FakeContext()
        val consentPrefs = FakeConsentPreferences(consentEnabled = false)
        val apiService = FakeStatsApiService()
        val collector = StatsCollector(context, apiService, consentPrefs)

        // When
        collector.updateConsent(true)

        // Then
        assertTrue(apiService.updateConsentCalled)
        assertTrue(apiService.lastConsentRequest!!.consent)
    }
}

class FakeConsentPreferences(
    private var consentEnabled: Boolean
) : ConsentPreferences(FakeContext()) {
    override val consentEnabled: Flow<Boolean> = flowOf(consentEnabled)
    override suspend fun setConsentEnabled(enabled: Boolean) {
        consentEnabled = enabled
    }
}

class FakeStatsApiService : StatsApiService {
    var collectStatsCalled = false
    var updateConsentCalled = false
    var lastRequest: StatsCollectRequest? = null
    var lastConsentRequest: ConsentRequest? = null

    override suspend fun collectStats(request: StatsCollectRequest): Response<StatsCollectResponse> {
        collectStatsCalled = true
        lastRequest = request
        return Response.success(StatsCollectResponse("stats_received"))
    }

    override suspend fun updateConsent(request: ConsentRequest): Response<ConsentResponse> {
        updateConsentCalled = true
        lastConsentRequest = request
        return Response.success(ConsentResponse("consent_updated", request.consent))
    }

    override suspend fun getUserTier(email: String): Response<UserTierResponse> {
        return Response.success(UserTierResponse(email, "standard", "verified"))
    }
}
```

### 10.2 DataStore Tests

```kotlin
class ConsentPreferencesTest {

    @Test
    fun `consentEnabled defaults to false`() = runTest {
        val context = ApplicationProvider.getApplicationContext()
        val prefs = ConsentPreferences(context)

        val consentEnabled = prefs.consentEnabled.first()
        assertFalse(consentEnabled)
    }

    @Test
    fun `setConsentEnabled persists value`() = runTest {
        val context = ApplicationProvider.getApplicationContext()
        val prefs = ConsentPreferences(context)

        prefs.setConsentEnabled(true)
        assertTrue(prefs.consentEnabled.first())

        prefs.setConsentEnabled(false)
        assertFalse(prefs.consentEnabled.first())
    }
}
```

### 10.3 Integration Test

```kotlin
class StatsCollectionIntegrationTest {

    @Test
    fun `full flow - consent granted, stats sent, worker enqueued`() = runTest {
        // Given - fresh app state
        val context = ApplicationProvider.getApplicationContext()
        val prefs = ConsentPreferences(context)
        val collector = StatsCollector(context, FakeStatsApiService(), prefs)

        // When - user grants consent
        prefs.setConsentEnabled(true)
        StatsCollectionWorker.enqueue(context)

        // Then - verify worker is scheduled
        val workInfos = WorkManager.getInstance(context)
            .getPeriodicWorkInfosForUniqueWork(StatsCollectionWorker.WORK_NAME)
            .get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)
    }
}
```

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-05-07 | Initial implementation guide |

---

## References

- [Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Retrofit](https://square.github.io/retrofit/)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Jetpack Compose](https://developer.android.com/compose)
