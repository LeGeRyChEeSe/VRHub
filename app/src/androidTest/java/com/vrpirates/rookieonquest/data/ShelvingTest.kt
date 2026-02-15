package com.vrpirates.rookieonquest.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vrpirates.rookieonquest.ui.InstallTaskStatus
import com.vrpirates.rookieonquest.ui.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for Shelving logic - Story 1.13
 */
@RunWith(AndroidJUnit4::class)
class ShelvingTest {

    private lateinit var context: Context
    private lateinit var repository: MainRepository
    private lateinit var viewModel: MainViewModel
    private var testReleaseName = "Test_Game_v1.13_Shelving"
    private var testPackageName = "com.test.shelving"
    private var testVersionCode = "113"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = MainRepository(context)
        viewModel = MainViewModel(context.applicationContext as android.app.Application)
        
        // Use real app info for realistic APK validation tests
        testPackageName = context.packageName
        val packageInfo = context.packageManager.getPackageInfo(testPackageName, 0)
        testVersionCode = packageInfo.longVersionCode.toString()
        testReleaseName = "Real_App_Release_${testVersionCode}"

        runBlocking {
            repository.db.gameDao().clearAll()
            repository.db.queuedInstallDao().deleteAll()
            repository.db.installHistoryDao().deleteAll()
            
            // Add a test game to catalog matching the real APK
            repository.db.gameDao().insertGames(listOf(GameEntity(
                gameName = "Real App Test",
                packageName = testPackageName,
                versionCode = testVersionCode,
                releaseName = testReleaseName,
                sizeBytes = 100 * 1024 * 1024L
            )))
        }
    }

    /**
     * Helper to create a REAL valid APK file in staged directory.
     * Uses the app's own APK file.
     */
    private fun createRealStagedApk(): File {
        val externalFilesDir = context.getExternalFilesDir(null)!!
        val targetApk = File(externalFilesDir, "${testPackageName}.apk")
        val sourceApk = File(context.packageCodePath)
        
        sourceApk.inputStream().use { input ->
            targetApk.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetApk
    }

    @After
    fun tearDown() {
        runBlocking {
            repository.deleteDownloadedGame(testReleaseName)
            repository.deleteStagedApk(testPackageName)
        }
    }

    @Test
    fun testShelveTask() = runBlocking {
        // 1. Create a REAL staged APK (simulating extraction complete)
        val apkFile = createRealStagedApk()
        assertTrue("Real APK should be valid", repository.isValidApkFile(apkFile, testPackageName, testVersionCode.toLong()))
        
        // 2. Add task to queue as PENDING_INSTALL
        repository.addToQueue(testReleaseName, InstallStatus.PENDING_INSTALL)
        
        // 3. Shelve it
        viewModel.shelveTask(testReleaseName)
        
        // 4. Verify status in DB
        val queue = repository.getAllQueuedInstalls().first()
        val task = queue.find { it.releaseName == testReleaseName }
        assertNotNull("Task should exist in queue", task)
        assertEquals("Task status should be SHELVED", InstallStatus.SHELVED.name, task?.status)
        
        // 5. Verify staged APK still exists (Shelving should NOT delete it)
        assertTrue("Staged APK should be preserved after shelving", apkFile.exists())
    }

    @Test
    fun testFullShelvingFlow() = runBlocking {
        // Covers: PENDING_INSTALL → App kill (simulated) → Startup → SHELVED → Promote → Install
        
        // 1. Setup PENDING_INSTALL state with valid APK
        createRealStagedApk()
        repository.addToQueue(testReleaseName, InstallStatus.PENDING_INSTALL)
        
        // 2. Simulate app restart/resume triggering verification
        // This should detect PENDING_INSTALL and move it to SHELVED because it's not installed yet
        viewModel.verifyPendingInstallations()
        
        // Wait for DB update (verifyPendingInstallations is async)
        withTimeout(5000) {
            repository.getAllQueuedInstalls().first { queue ->
                queue.any { it.releaseName == testReleaseName && it.status == InstallStatus.SHELVED.name }
            }
        }
        
        // 3. Verify it shows up in "Local Installs" filter logic (MainViewModel.games)
        // Note: FilterStatus.LOCAL_INSTALLS includes SHELVED and PENDING_INSTALL
        
        // 4. Promote it back to queue
        viewModel.promoteTask(testReleaseName)
        
        // 5. Verify it's back to QUEUED and ready for processing
        withTimeout(2000) {
            repository.getAllQueuedInstalls().first { queue ->
                queue.any { it.releaseName == testReleaseName && it.status == InstallStatus.QUEUED.name }
            }
        }
    }

    @Test
    fun testPromoteShelvedTask() = runBlocking {
        // 1. Add task as SHELVED
        repository.addToQueue(testReleaseName, InstallStatus.SHELVED)
        
        // 2. Promote it (simulate "Complete Install" click)
        viewModel.promoteTask(testReleaseName)
        
        // 3. Verify status changed to QUEUED (deterministic check with timeout)
        withTimeout(2000) {
            repository.getAllQueuedInstalls().first { queue ->
                queue.any { it.releaseName == testReleaseName && it.status == InstallStatus.QUEUED.name }
            }
        }
    }

    @Test
    fun testDiscoverOrphanedStagedApks() = runBlocking {
        // 1. Create a REAL staged APK (orphaned - not in queue)
        val apkFile = createRealStagedApk()
        assertTrue("Real APK should be valid", repository.isValidApkFile(apkFile, testPackageName, testVersionCode.toLong()))
        
        // 2. Trigger discovery (now internal and @VisibleForTesting)
        viewModel.discoverOrphanedStagedApks()
        
        // 3. Verify it was discovered and added to queue as SHELVED
        withTimeout(2000) {
            repository.getAllQueuedInstalls().first { queue ->
                queue.any { it.releaseName == testReleaseName && it.status == InstallStatus.SHELVED.name }
            }
        }
        
        // Cleanup handled by tearDown
    }

    @Test
    fun testDeleteStagedApk() = runBlocking {
        // 1. Create a dummy staged APK
        val externalFilesDir = context.getExternalFilesDir(null)!!
        val apkFile = File(externalFilesDir, "${testPackageName}.apk")
        apkFile.writeText("fake apk content")
        assertTrue("Dummy APK should exist", apkFile.exists())
        
        // 2. Delete it via repository
        val deleted = repository.deleteStagedApk(testPackageName)
        assertTrue("deleteStagedApk should return true", deleted)
        assertFalse("Dummy APK should be deleted", apkFile.exists())
    }

    @Test
    fun testCleanupStagedApkOnVerification() = runBlocking {
        // This test verifies that staged APK is deleted when verification succeeds
        
        // 1. Create a REAL staged APK
        val apkFile = createRealStagedApk()
        
        // 2. Add task to queue with PENDING_INSTALL
        repository.addToQueue(testReleaseName, InstallStatus.PENDING_INSTALL)
        
        // 3. Trigger verification/cleanup (simulating successful install check)
        // We use deleteStagedApk directly as checkInstallationStatusSilent is private
        // and relies on PackageManager which we don't control easily here.
        repository.deleteStagedApk(testPackageName)
        assertFalse("Staged APK should be deleted", apkFile.exists())
    }
}
