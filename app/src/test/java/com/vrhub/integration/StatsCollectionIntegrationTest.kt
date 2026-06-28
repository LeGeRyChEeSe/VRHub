package com.vrhub.integration

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vrhub.data.AppDatabase
import com.vrhub.data.ConsentPreferences
import com.vrhub.data.FakeStatsApiService
import com.vrhub.data.GameEntity
import com.vrhub.data.MainRepository
import com.vrhub.data.NetworkModule
import com.vrhub.worker.StatsCollectDebounceWorker
import com.vrhub.worker.StatsCollectionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Integration tests for the Stats Collection module (issue #42).
 *
 * Where the existing unit tests ([com.vrhub.data.StatsCollectorTest],
 * [com.vrhub.data.ConsentPreferencesTest], [com.vrhub.data.MainRepositoryTest],
 * [com.vrhub.worker.StatsCollectDebounceWorkerTest]) each exercise ONE component in
 * isolation with mocks, this suite wires the real collaborators together and verifies
 * the end-to-end flows requested by issue #42:
 *
 *   1. Consent granted  → stats actually sent  → background worker enqueued
 *   2. Consent revoked   → background worker cancelled
 *   3. Favorite change   → debounced collection worker enqueued
 *
 * The components under test are connected for real:
 *   - [ConsentPreferences]      → real DataStore (Robolectric-backed)
 *   - [MainRepository]          → real Room (in-memory) + real PackageManager (Robolectric shadow)
 *   - [com.vrhub.data.StatsCollector] → real, talking to a [FakeStatsApiService]
 *   - [StatsCollectionWorker] / [StatsCollectDebounceWorker] → real WorkManager (test driver)
 *
 * IMPORTANT — why this lives in the JVM/Robolectric `test` source set and not `androidTest`:
 * the instrumented `androidTest` source set is currently in a broken legacy package state
 * (it declares `com.vrpirates.rookieonquest.*` and references classes that no longer exist
 * after the rebrand to `com.vrhub`), so it does not compile and cannot host new tests. The
 * repo's established pattern for stats-flow integration coverage is Robolectric (see
 * StatsCollectDebounceWorkerTest / MainRepositoryTest), which also runs here without an
 * emulator. This suite follows that pattern.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class StatsCollectionIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScheduler = testDispatcher.scheduler

    private lateinit var appContext: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: MainRepository
    private lateinit var consentPreferences: ConsentPreferences
    private lateinit var fakeStatsApiService: FakeStatsApiService

    // Mirrors StatsCollectionWorker.WORK_NAME (private const). Kept as a literal so the test
    // documents the contract the periodic worker relies on; a drift here is intentional to catch.
    private val periodicWorkName = "stats_collection_work"

    private val testPackage = "com.beatgames.beatsaber"
    private val testRelease = "BeatSaber"
    private val testGameName = "Beat Saber"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        appContext = RuntimeEnvironment.getApplication() as Application

        db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Real consent DataStore (shared singleton — the repository reads the same file).
        consentPreferences = ConsentPreferences(appContext)

        // Real StatsCollector inside the repository talks to this fake instead of the network.
        fakeStatsApiService = FakeStatsApiService()
        NetworkModule.replaceStatsApiService(fakeStatsApiService)

        // Test WorkManager with a synchronous executor so WorkInfo state is observable
        // immediately after enqueue/cancel (no real scheduling).
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config)

        repository = MainRepository(appContext, db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** Registers [pkg] as installed in the Robolectric PackageManager shadow. */
    private fun markInstalled(pkg: String) {
        val info = PackageInfo().apply {
            packageName = pkg
            @Suppress("DEPRECATION")
            versionCode = 1
        }
        shadowOf(appContext.packageManager).installPackage(info)
    }

    private suspend fun insertCatalogGame(isFavorite: Boolean) {
        db.gameDao().insertGames(
            listOf(
                GameEntity(
                    releaseName = testRelease,
                    gameName = testGameName,
                    packageName = testPackage,
                    versionCode = "1",
                    description = "Integration test game",
                    sizeBytes = 1024L,
                    isFavorite = isFavorite,
                    lastUpdated = System.currentTimeMillis(),
                    popularity = 0
                )
            )
        )
    }

    private fun workInfosFor(uniqueName: String): List<WorkInfo> =
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(uniqueName)
            .get()

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1 — Full flow: consent granted → stats actually sent
    // (real ConsentPreferences + Room + PackageManager + StatsCollector + Fake API)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `consent granted with installed catalog game sends stats end to end`() = runTest(testDispatcher) {
        insertCatalogGame(isFavorite = true)
        markInstalled(testPackage)

        consentPreferences.setConsentEnabled(true)
        testScheduler.advanceUntilIdle()

        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()

        assertTrue(
            "Stats should be POSTed when consent is granted and an installed catalog game exists",
            fakeStatsApiService.collectStatsCalled
        )
        val games = fakeStatsApiService.receivedGames
        assertNotNull("Request payload should carry the game list", games)
        val reported = games!!.first { it.packageName == testPackage }
        assertTrue("isFavorite must be propagated from Room", reported.isFavorite)
        assertEquals("gameName must be propagated from Room", testGameName, reported.gameName)
        assertEquals("Tier should fall back to the fake's default", "standard", fakeStatsApiService.receivedTier)
        assertEquals("Anonymous collection must not leak an email", null, fakeStatsApiService.receivedEmail)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1 (gate) — Consent revoked → stats NOT sent even with games present
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `consent revoked does not send stats even with installed catalog game`() = runTest(testDispatcher) {
        insertCatalogGame(isFavorite = true)
        markInstalled(testPackage)

        consentPreferences.setConsentEnabled(false)
        testScheduler.advanceUntilIdle()

        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()

        assertFalse(
            "Stats must NOT be sent when consent is revoked",
            fakeStatsApiService.collectStatsCalled
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1 (worker) — Consent granted → periodic collection worker enqueued
    // (mirrors MainActivity's start-up consent→worker sync)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `enqueue schedules the periodic stats collection worker`() {
        StatsCollectionWorker.enqueue(appContext)

        val infos = workInfosFor(periodicWorkName)
        assertEquals("Exactly one periodic stats worker should be scheduled", 1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 2 — Consent revoked → periodic collection worker cancelled
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `cancel removes the periodic stats collection worker`() {
        StatsCollectionWorker.enqueue(appContext)
        assertEquals(WorkInfo.State.ENQUEUED, workInfosFor(periodicWorkName).single().state)

        StatsCollectionWorker.cancel(appContext)

        val infos = workInfosFor(periodicWorkName)
        // After cancellation WorkManager keeps the record in the CANCELLED state.
        assertTrue(
            "Periodic worker must be cancelled (none ENQUEUED/RUNNING)",
            infos.none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        )
        assertTrue(
            "Cancelled worker should report CANCELLED state",
            infos.isEmpty() || infos.all { it.state == WorkInfo.State.CANCELLED }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 3 — Favorite change → debounced collection worker enqueued
    // (real repository.toggleFavorite path: Room update + debounce enqueue)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggling a favorite updates Room and enqueues the debounce worker`() = runTest(testDispatcher) {
        insertCatalogGame(isFavorite = false)

        repository.toggleFavorite(testRelease, true)
        testScheduler.advanceUntilIdle()

        // Room side-effect
        val stored = db.gameDao().getByReleaseName(testRelease)
        assertNotNull(stored)
        assertTrue("Favorite flag should be persisted to Room", stored!!.isFavorite)

        // Worker side-effect: exactly one debounced collection scheduled
        val infos = workInfosFor(StatsCollectDebounceWorker.WORK_NAME)
        assertEquals("A favorite toggle must enqueue exactly one debounce worker", 1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
    }

    @Test
    fun `rapid favorite toggles coalesce into a single debounce worker`() = runTest(testDispatcher) {
        insertCatalogGame(isFavorite = false)

        // Simulate a burst of toggles — REPLACE policy must collapse them to one timer.
        repository.toggleFavorite(testRelease, true)
        repository.toggleFavorite(testRelease, false)
        repository.toggleFavorite(testRelease, true)
        testScheduler.advanceUntilIdle()

        val infos = workInfosFor(StatsCollectDebounceWorker.WORK_NAME)
        assertEquals("Rapid toggles must coalesce to a single debounce worker", 1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
    }
}
