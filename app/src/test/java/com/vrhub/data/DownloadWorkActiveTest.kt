package com.vrhub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MainRepository.isDownloadWorkActive — the check zombie recovery uses to decide whether a task
 * may be reset to QUEUED.
 *
 * Confirmed bug: BLOCKED was missing from the "active" test, so a download blocked by its
 * constraints (waiting for network, charging, idle) looked finished and got re-enqueued as a
 * second worker for the same release.
 *
 * Scope of this JVM unit test: the state-classification logic only. The real WorkManager
 * round-trip (enqueue -> pending -> isDownloadWorkActive true -> cancel -> CANCELLED -> false)
 * needs a device and lives in
 * app/src/androidTest/java/com/vrhub/data/DownloadWorkActiveInstrumentedTest.kt — enqueuing the
 * real DownloadWorker here would run it through SynchronousExecutor with no input data, which
 * fails the work and invalidates the assertion's premise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DownloadWorkActiveTest {

    private lateinit var appContext: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: MainRepository

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()

        db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MainRepository(appContext, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `BLOCKED counts as active download work`() {
        // A constraint-blocked download is still pending WorkManager work.
        assertTrue(repository.isActiveDownloadWorkState(WorkInfo.State.BLOCKED))
    }

    @Test
    fun `running and enqueued count as active, terminal states do not`() {
        assertTrue(repository.isActiveDownloadWorkState(WorkInfo.State.RUNNING))
        assertTrue(repository.isActiveDownloadWorkState(WorkInfo.State.ENQUEUED))
        assertFalse(repository.isActiveDownloadWorkState(WorkInfo.State.SUCCEEDED))
        assertFalse(repository.isActiveDownloadWorkState(WorkInfo.State.FAILED))
        assertFalse(repository.isActiveDownloadWorkState(WorkInfo.State.CANCELLED))
    }
}
