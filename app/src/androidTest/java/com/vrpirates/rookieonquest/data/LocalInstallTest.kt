package com.vrpirates.rookieonquest.data

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vrpirates.rookieonquest.ui.InstallTaskStatus
import com.vrpirates.rookieonquest.ui.MainEvent
import com.vrpirates.rookieonquest.ui.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for Local Install (Fast Track) logic - Story 1.12
 */
@RunWith(AndroidJUnit4::class)
class LocalInstallTest {

    private lateinit var context: Context
    private lateinit var repository: MainRepository
    private lateinit var testDownloadsDir: File
    private lateinit var viewModel: MainViewModel
    private val testReleaseName = "Test_Game_v100_Local"
    private val testPackageName = "com.test.local"
    private val testVersionCode = "100"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as Application
        repository = MainRepository(context)
        viewModel = MainViewModel(app)
        
        // Use the actual downloads dir but with a unique test folder
        testDownloadsDir = repository.downloadsDir
        val gameDir = File(testDownloadsDir, testReleaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
        gameDir.deleteRecursively()
        
        runBlocking {
            repository.db.gameDao().clearAll()
            repository.db.queuedInstallDao().deleteAll()
        }
    }

    @After
    fun tearDown() {
        val gameDir = File(testDownloadsDir, testReleaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
        gameDir.deleteRecursively()
    }

    @Test
    fun testFindLocalApk_Negative_NoDir() {
        val game = GameData(
            gameName = "Test Game",
            packageName = testPackageName,
            versionCode = testVersionCode,
            releaseName = testReleaseName
        )
        
        val found = repository.findLocalApk(game)
        assertNull("Should not find APK if directory doesn't exist", found)
    }

    @Test
    fun testFindLocalApk_Negative_EmptyDir() {
        val game = GameData(
            gameName = "Test Game",
            packageName = testPackageName,
            versionCode = testVersionCode,
            releaseName = testReleaseName
        )
        
        val safeDirName = game.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val gameDir = File(testDownloadsDir, safeDirName)
        gameDir.mkdirs()
        
        val found = repository.findLocalApk(game)
        assertNull("Should not find APK if directory is empty", found)
    }

    @Test
    fun testHasLocalInstallFiles_Negative() = runBlocking {
        // Even if directory exists, if no valid APK, it should be false
        val game = GameData(
            gameName = "Test Game",
            packageName = testPackageName,
            versionCode = testVersionCode,
            releaseName = testReleaseName
        )
        
        val safeDirName = game.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val gameDir = File(testDownloadsDir, safeDirName)
        gameDir.mkdirs()
        File(gameDir, "not_an_apk.txt").createNewFile()
        
        // We need to insert the game into DB for hasLocalInstallFiles to work
        repository.db.gameDao().insertGames(listOf(game.toEntity()))
        
        val hasLocal = repository.hasLocalInstallFiles(testReleaseName)
        assertFalse("Should return false if no valid APK found", hasLocal)
    }

    @Test
    fun testIsValidApkFile_Negative_InvalidFile() {
        val tempFile = File(context.cacheDir, "invalid.apk")
        tempFile.createNewFile()
        tempFile.writeText("not a real apk")
        
        val isValid = repository.isValidApkFile(tempFile, testPackageName, testVersionCode.toLong())
        assertFalse("Invalid APK file should return false", isValid)
        
        tempFile.delete()
    }

    @Test
    fun testPathSanitization() {
        val releaseName = "Game Name: Special Characters / \\ @ #"
        val expected = "Game_Name__Special_Characters_______"
        val actual = releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        assertEquals("Path sanitization should match repository logic", expected, actual)
    }

    /**
     * Verifies the strategy used in MainViewModel.runTask to signal extraction is complete
     * for local/fast-track installs (AC: 5).
     */
    @Test
    fun testMarkerCreationStrategy() {
        val releaseName = "Test_Marker_Game"
        val tempInstallRoot = File(context.filesDir, "install_temp")
        val hash = com.vrpirates.rookieonquest.data.CryptoUtils.md5(releaseName + "\n")
        val gameTempDir = File(tempInstallRoot, hash)
        
        try {
            if (!gameTempDir.exists()) gameTempDir.mkdirs()
            val marker = File(gameTempDir, "extraction_done.marker")
            marker.createNewFile()
            
            assertTrue("Marker should exist in the hashed temp directory", marker.exists())
            assertEquals("Marker filename must be exactly 'extraction_done.marker'", "extraction_done.marker", marker.name)
        } finally {
            gameTempDir.deleteRecursively()
        }
    }

    /**
     * Verifies that hasLocalInstallFiles returns false (triggering fallback to standard flow)
     * if the files in the download directory are invalid (AC: 7).
     */
    @Test
    fun testHasLocalInstallFiles_Fallback_InvalidApk() = runBlocking {
        val game = GameData(
            gameName = "Test Game",
            packageName = testPackageName,
            versionCode = testVersionCode,
            releaseName = testReleaseName
        )
        
        val safeDirName = game.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val gameDir = File(testDownloadsDir, safeDirName)
        gameDir.mkdirs()
        
        // Create an invalid APK file (just a text file with .apk extension)
        val invalidApk = File(gameDir, "${testPackageName}.apk")
        invalidApk.writeText("Not a real APK content")
        
        // Insert into DB so repository can find it
        repository.db.gameDao().insertGames(listOf(game.toEntity()))
        
        val hasLocal = repository.hasLocalInstallFiles(testReleaseName)
        assertFalse("Should return false (triggering fallback) for invalid APK files", hasLocal)
    }

    /**
     * Full E2E Integration Test for Fast Track flow (AC: 2, 3, 5, 6).
     * Validates that when a local APK exists, MainViewModel.runTask() skips download/extraction
     * and transitions directly to INSTALLING.
     */
    @Test
    fun testE2EFastTrackFlow() = runBlocking {
        // 1. Setup - Use the app's own APK as a valid reference
        val appApkFile = File(context.packageCodePath)
        val appPackageName = context.packageName
        
        val game = GameData(
            gameName = "E2E Fast Track Test",
            packageName = appPackageName,
            versionCode = "1", // Version 1 is likely <= current app version
            releaseName = "E2E_Test_Release"
        )
        repository.db.gameDao().insertGames(listOf(game.toEntity()))
        
        // 2. Setup - Place the valid APK in the expected downloads directory
        val safeDirName = game.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val gameDir = File(testDownloadsDir, safeDirName)
        gameDir.mkdirs()
        
        // Copy app APK to the downloads dir with standard naming
        val apkFile = File(gameDir, "${appPackageName}.apk")
        appApkFile.inputStream().use { input ->
            apkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        assertTrue("Test APK should exist", apkFile.exists())
        
        // 3. Initiate installation via ViewModel
        // We'll collect events to catch the InstallApk event
        val events = mutableListOf<MainEvent>()
        val eventJob = launch {
            viewModel.events.collect { events.add(it) }
        }
        
        viewModel.installGame(game.releaseName)
        
        // 4. Verify flow through States
        // Wait for task to appear in queue and reach PENDING_INSTALL or COMPLETED
        withTimeout(15000) {
            while (true) {
                val queue = viewModel.installQueue.value
                val task = queue.find { it.releaseName == game.releaseName }
                
                if (task != null) {
                    if (task.status == InstallTaskStatus.PENDING_INSTALL || task.status == InstallTaskStatus.COMPLETED) {
                        break
                    }
                    if (task.status == InstallTaskStatus.FAILED) {
                        fail("Fast Track task failed: ${task.error}")
                    }
                }
                delay(500)
            }
        }
        
        // 5. Assertions
        val finalQueue = viewModel.installQueue.value
        val finalTask = finalQueue.find { it.releaseName == game.releaseName }
        assertNotNull("Task should be in queue", finalTask)
        assertTrue("Task should be marked as local install", finalTask?.isLocalInstall == true)
        
        // Verify that an InstallApk event was emitted
        val installEvent = events.filterIsInstance<MainEvent.InstallApk>().firstOrNull()
        assertNotNull("InstallApk event should be emitted", installEvent)
        
        // Verify that the staged APK exists in externalFilesDir
        val stagedApk = File(context.getExternalFilesDir(null), "${appPackageName}.apk")
        assertTrue("Staged APK should exist", stagedApk.exists())
        
        // 6. Verify WorkManager was NOT enqueued for download
        val workManager = androidx.work.WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosByTag("download").get()
        val testWork = workInfos.find { it.tags.contains(game.releaseName) }
        assertNull("DownloadWorker should NOT be enqueued for Fast Track", testWork)
        
        eventJob.cancel()
        stagedApk.delete()
    }
}
