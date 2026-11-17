package com.vrhub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vrhub.worker.DownloadWorker
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device coverage for MainRepository.isDownloadWorkActive / cancelDownloadWork, the check zombie
 * recovery uses to decide whether a task may be reset to QUEUED.
 *
 * Confirmed bug: BLOCKED was missing from the "active" test, so a download blocked by its
 * constraints (waiting for network, charging, idle) looked finished and got re-enqueued as a
 * second worker for the same release. The state classification itself is unit-tested in
 * app/src/test/java/com/vrhub/data/DownloadWorkActiveTest.kt; the real WorkManager round-trip
 * cannot be exercised there (enqueuing the real DownloadWorker under Robolectric's
 * SynchronousExecutor runs it with no input data and fails the work), so it lives here.
 *
 * Requires a device/emulator: run with
 *   ./scripts/gradlew.bat connectedAndroidTest   (or the vrhub_e2e AVD)
 */
@RunWith(AndroidJUnit4::class)
class DownloadWorkActiveInstrumentedTest {

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
        WorkManager.getInstance(appContext).cancelAllWork()
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(appContext).cancelAllWork()
        db.close()
    }

    @Test
    fun pendingDownloadWorkIsReportedActiveSoZombieRecoveryDoesNotReEnqueueIt() = runTest {
        val releaseName = "Audit Zombie Game v1"
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "download_$releaseName",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DownloadWorker>().build()
        )

        assertTrue(
            "pending download work must be reported active so zombie recovery does not re-enqueue it",
            repository.isDownloadWorkActive(releaseName)
        )
    }

    @Test
    fun noWorkForAReleaseMeansItIsResettable() = runTest {
        assertFalse(repository.isDownloadWorkActive("Audit Never Enqueued Game v1"))
        assertEquals(emptyList<WorkInfo.State>(), repository.getDownloadWorkStates("Audit Never Enqueued Game v1"))
    }

    @Test
    fun cancelDownloadWorkMakesTheTaskResettableAgain() = runTest {
        val releaseName = "Audit Cancelled Game v1"
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "download_$releaseName",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DownloadWorker>().build()
        )
        assertTrue(repository.isDownloadWorkActive(releaseName))

        repository.cancelDownloadWork(releaseName)

        assertEquals(listOf(WorkInfo.State.CANCELLED), repository.getDownloadWorkStates(releaseName))
        assertFalse(repository.isDownloadWorkActive(releaseName))
    }
}
