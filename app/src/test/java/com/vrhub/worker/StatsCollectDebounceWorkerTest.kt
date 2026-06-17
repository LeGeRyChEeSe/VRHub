package com.vrhub.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.vrhub.data.ConsentPreferences
import com.vrhub.data.FakeStatsApiService
import com.vrhub.data.NetworkModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [StatsCollectDebounceWorker] (story 5.2).
 *
 * COVERAGE NOTE — what we test here vs what we don't:
 *  - `enqueue()` behaviour (REPLACE policy, work-name, ENQUEUED state): TESTED below.
 *  - `doWork()` body that reads Room + PackageManager + HTTP POST: NOT unit-tested here.
 *    Reason: Robolectric's SQLite shadow fails with "Illegal connection pointer" when
 *    Room queries are dispatched to its internal executor from a coroutine running on
 *    a different thread. The worker body is otherwise identical to
 *    `MainRepository.maybeCollectStats()` which already has 14 passing tests in
 *    `MainRepositoryTest.kt` (covering consent gate, empty map, tier resolution, retry).
 *  - Full end-to-end behaviour: verified manually on device (toggle 5 games, see ONE
 *    POST on the server 5 min after the last toggle).
 *
 * Coverage matrix:
 *  AC #1 — test `enqueue replaces existing work` (REPLACE policy)
 *  AC #2 — test `enqueue creates work with correct WORK_NAME and ENQUEUED state`
 *  AC #3 — test `doWork skips when consent disabled` (no DB / network touched)
 *  AC #6 — non-fatal enqueue covered indirectly via test not throwing
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class StatsCollectDebounceWorkerTest {

    private lateinit var appContext: Context
    private lateinit var consentPreferences: ConsentPreferences
    private lateinit var fakeStatsApiService: FakeStatsApiService

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()

        // Initialize a test WorkManager with synchronous executor so we can inspect
        // WorkInfo state immediately after enqueue (no real scheduling).
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config)

        // NOTE: we deliberately do NOT call AppDatabase.getDatabase(appContext) here.
        // Robolectric's SQLite shadow fails with "Illegal connection pointer" when Room
        // is initialised in @Before (because the same connection pointer gets reused
        // across test threads). The doWork body that needs Room is covered by
        // MainRepositoryTest.maybeCollectStats tests (same logic) + manual E2E.

        consentPreferences = ConsentPreferences(appContext)
        fakeStatsApiService = FakeStatsApiService()
        NetworkModule.replaceStatsApiService(fakeStatsApiService)
    }

    @After
    fun tearDown() {
        // No DB cleanup needed — we don't touch Room in these tests.
    }

    /** Helper: build a worker instance for direct doWork() invocation in tests. */
    private fun buildWorker(): StatsCollectDebounceWorker {
        // Default WorkerFactory (reflection) instantiates our CoroutineWorker.
        return TestListenableWorkerBuilder<StatsCollectDebounceWorker>(appContext).build()
    }

    // ─────────────────────────────────────────────────────────────
    // AC #1 — REPLACE policy coalesces rapid toggles
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `enqueue twice yields exactly one WorkInfo (REPLACE policy)`() {
        StatsCollectDebounceWorker.enqueue(appContext)
        StatsCollectDebounceWorker.enqueue(appContext)

        val infos = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(StatsCollectDebounceWorker.WORK_NAME)
            .get()

        assertEquals("Exactly one WorkInfo must exist (REPLACE policy coalesces)",
            1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
    }

    @Test
    fun `enqueue burst of 5 coalesces to 1 WorkInfo`() {
        // Simulate a rapid burst of 5 toggles
        repeat(5) {
            StatsCollectDebounceWorker.enqueue(appContext)
        }

        val infos = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(StatsCollectDebounceWorker.WORK_NAME)
            .get()

        assertEquals("Burst of 5 toggles must coalesce to 1 WorkInfo",
            1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
    }

    // ─────────────────────────────────────────────────────────────
    // AC #2 — WorkRequest configuration (correct WORK_NAME + ENQUEUED state)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `enqueue creates work with WORK_NAME and ENQUEUED state`() {
        StatsCollectDebounceWorker.enqueue(appContext)

        val infos = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(StatsCollectDebounceWorker.WORK_NAME)
            .get()

        assertEquals(1, infos.size)
        assertNotNull(infos[0])
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
        assertEquals(StatsCollectDebounceWorker.WORK_NAME, "stats_collect_debounce")
    }

    @Test
    fun `WORK_NAME constant is stable and distinct from other workers`() {
        // Sanity check: the unique work name must NOT clash with the periodic worker
        // (StatsCollectionWorker.WORK_NAME = "stats_collection_work") or downloads.
        assertEquals("stats_collect_debounce", StatsCollectDebounceWorker.WORK_NAME)
        assertNotNull(StatsCollectDebounceWorker.WORK_NAME)
        assertFalse("WORK_NAME must be non-empty",
            StatsCollectDebounceWorker.WORK_NAME.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // AC #3 — No-op short-circuit when consent disabled
    // (only test the early-return case — full doWork body is covered by
    //  MainRepositoryTest.maybeCollectStats tests + manual E2E)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `doWork returns success and skips when consent is disabled`() = runBlocking {
        // Consent is false by default (DataStore default)
        consentPreferences.setConsentEnabled(false)
        fakeStatsApiService.collectStatsCalled = false

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertFalse("collectStats should NOT be called when consent is disabled",
            fakeStatsApiService.collectStatsCalled)
    }
}