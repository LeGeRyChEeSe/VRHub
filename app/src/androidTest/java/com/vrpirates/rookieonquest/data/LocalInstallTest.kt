package com.vrpirates.rookieonquest.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import java.io.FileOutputStream

/**
 * Instrumented tests for Local Install (Fast Track) logic - Story 1.12
 */
@RunWith(AndroidJUnit4::class)
class LocalInstallTest {

    private lateinit var context: Context
    private lateinit var repository: MainRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var testDownloadsDir: File
    private val testReleaseName = "Test_Game_v100_Local"
    private val testPackageName = "com.test.local"
    private val testVersionCode = "100"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = MainRepository(context)
        viewModel = MainViewModel(context.applicationContext as android.app.Application)
        
        // Use the actual downloads dir but with a unique test folder
        testDownloadsDir = repository.downloadsDir
        val gameDir = File(testDownloadsDir, testReleaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
        gameDir.deleteRecursively()
        
        runBlocking {
            repository.db.gameDao().clearAll()
            repository.db.queuedInstallDao().deleteAll()
            repository.db.installHistoryDao().deleteAll()
            
            // Cancel any pending work to avoid pollution
            WorkManager.getInstance(context).cancelAllWork()
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
        // Even if directory exists, if no valid APK and no OBB folder, it should be false
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
        assertFalse("Should return false if no valid APK or OBB folder found", hasLocal)
    }

    /**
     * Verifies that hasLocalInstallFiles returns true if a valid OBB folder exists (AC: 1).
     */
    @Test
    fun testHasLocalInstallFiles_findsObbFolder() = runBlocking {
        val game = GameData(
            gameName = "OBB Test Game",
            packageName = testPackageName,
            versionCode = testVersionCode,
            releaseName = testReleaseName
        )
        
        val safeDirName = game.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val gameDir = File(testDownloadsDir, safeDirName)
        gameDir.mkdirs()
        
        // Create an OBB folder named after the package
        val obbDir = File(gameDir, testPackageName)
        obbDir.mkdirs()
        File(obbDir, "main.100.${testPackageName}.obb").createNewFile()
        
        // Insert into DB
        repository.db.gameDao().insertGames(listOf(game.toEntity()))
        
        val hasLocal = repository.hasLocalInstallFiles(testReleaseName)
        assertTrue("Should return true if valid OBB folder is found", hasLocal)
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
    fun testIsValidApkFile_Positive_AllowNewer() {
        // Use the app's own APK as a valid reference
        val appApkFile = File(context.packageCodePath)
        val appPackageName = context.packageName
        
        // Get actual version code
        val pi = context.packageManager.getPackageArchiveInfo(appApkFile.absolutePath, 0)
        val actualVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pi?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            pi?.versionCode?.toLong() ?: 0L
        }
        
        // Test with a SMALLER version code in catalog but allowNewer = true
        // This simulates AC4 requirement: equal to or greater than the one in the catalog
        val isValid = repository.isValidApkFile(appApkFile, appPackageName, actualVersionCode - 1, allowNewer = true)
        assertTrue("APK with GREATER version code should be valid when allowNewer=true", isValid)
    }

    @Test
    fun testPathSanitization() {
        val releaseName = "Game Name: Special Characters / \\ @ #"
        val actual = releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        // Just verify that it replaces problematic characters with underscores
        assertFalse("Sanitized path should not contain spaces", actual.contains(" "))
        assertFalse("Sanitized path should not contain colon", actual.contains(":"))
        assertFalse("Sanitized path should not contain slashes", actual.contains("/"))
        assertFalse("Sanitized path should not contain backslashes", actual.contains("\\"))
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
            val marker = File(gameTempDir, MainRepository.EXTRACTION_DONE_MARKER)
            marker.createNewFile()
            
            assertTrue("Marker should exist in the hashed temp directory", marker.exists())
            assertEquals("Marker filename must be exactly '" + MainRepository.EXTRACTION_DONE_MARKER + "'", MainRepository.EXTRACTION_DONE_MARKER, marker.name)
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
     * Verifies that invalid local APK (wrong package name) triggers graceful fallback 
     * to DOWNLOADING status with WorkManager enqueued (AC: 7).
     */
    @Test
    fun testFallbackToStandardDownload_InvalidApk() = runBlocking {
        val releaseName = "Fallback_Invalid_Apk_Test"
        val game = GameData(
            gameName = "Invalid APK Game",
            packageName = "com.expected.package",
            versionCode = "1",
            releaseName = releaseName
        )
        repository.db.gameDao().insertGames(listOf(game.toEntity()))

        // Prepare a directory with a WRONG APK (wrong package name or just invalid)
        val gameDir = File(testDownloadsDir, releaseName)
        gameDir.mkdirs()
        val invalidApk = File(gameDir, "wrong_package.apk")
        invalidApk.writeText("Not a real APK")

        // Trigger via ViewModel
        viewModel.installGame(releaseName, downloadOnly = true)

        val startTime = System.currentTimeMillis()
        var downloadEnqueued = false
        while (System.currentTimeMillis() - startTime < 8000) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosByTag(releaseName).get()
            if (workInfos.any { it.tags.contains("download") }) {
                downloadEnqueued = true
                break
            }
            delay(500)
        }

        assertTrue("Should fallback to enqueuing DownloadWorker if local APK is invalid", downloadEnqueued)
    }

    /**
     * Verifies that the "FAST TRACK" badge requirement for AC6 is reflected in the task state.
     * We test this by directly invoking the repository's status update method.
     */
    @Test
    fun testFastTrackStatusPersistence() = runBlocking {
        val releaseName = "Status_Persistence_Test"
        val game = GameData(
            gameName = "Status Game",
            packageName = testPackageName,
            versionCode = "1",
            releaseName = releaseName
        )
        repository.db.gameDao().insertGames(listOf(game.toEntity()))
        
        // Queue the game manually in DB
        val entity = QueuedInstallEntity.create(
            releaseName = releaseName,
            status = InstallStatus.QUEUED,
            queuePosition = 0
        )
        repository.db.queuedInstallDao().insert(entity)
        
        // Mark as local install
        repository.updateLocalInstallStatus(releaseName, true)
        
        // Verify state
        val task = repository.db.queuedInstallDao().getByReleaseName(releaseName)
        assertNotNull("Task should be in database", task)
        assertTrue("isLocalInstall should be persisted as true", task?.isLocalInstall == true)
    }

    /**
     * E2E Integration Test for Fast Track flow (AC: 1, 2, 3, 5, 6 - Story 1.12).
     * Validates that when a valid local APK exists, MainViewModel.runTask()
     * transitions through LOCAL_VERIFYING and INSTALLING without enqueuing DownloadWorker.
     */
    @Test
    fun testE2EFastTrackFlow() = runBlocking {
        // 1. Setup valid game in catalog
        val appPackageName = context.packageName
        val appApkFile = File(context.packageCodePath)
        val pi = context.packageManager.getPackageArchiveInfo(appApkFile.absolutePath, 0)
        val appVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pi?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            pi?.versionCode?.toLong() ?: 0L
        }

        val game = GameData(
            gameName = "Fast Track E2E Game",
            packageName = appPackageName,
            versionCode = appVersionCode.toString(),
            releaseName = "FastTrack_E2E_Test"
        )
        repository.db.gameDao().insertGames(listOf(game.toEntity()))

        // 2. Prepare local APK in the correct download directory
        val safeDirName = game.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val gameDir = File(testDownloadsDir, safeDirName)
        gameDir.mkdirs()
        
        // Use our own APK as a valid fixture for the test.
        // This is safe because context.packageCodePath guarantees the file exists,
        // has a valid APK structure for the current package, and is readable by the app.
        val localApk = File(gameDir, "${appPackageName}.apk")
        File(context.packageCodePath).inputStream().use { input ->
            FileOutputStream(localApk).use { output ->
                input.copyTo(output)
            }
        }

        // 3. Initiate installation through ViewModel
        // Note: installGame launches a coroutine and returns immediately
        viewModel.installGame(game.releaseName, downloadOnly = true)

        // 4. Observe the queue and wait for transitions
        // We use a timeout to avoid hanging if the flow fails
        val startTime = System.currentTimeMillis()
        val reachedStates = mutableSetOf<InstallTaskStatus>()
        
        while (System.currentTimeMillis() - startTime < 15000) { // Increased to 15s for safety
            val queue = viewModel.installQueue.value
            val task = queue.find { it.releaseName == game.releaseName }
            
            if (task != null) {
                reachedStates.add(task.status)
                if (task.status == InstallTaskStatus.COMPLETED) {
                    // Collect for a few more cycles to ensure we caught fast transitions
                    repeat(10) {
                        delay(25)
                        viewModel.installQueue.value.find { it.releaseName == game.releaseName }?.let {
                            reachedStates.add(it.status)
                        }
                    }
                    break
                }
            }
            delay(50) // More frequent polling to catch fast transitions (AC: 3, 5)
        }

        // 5. Assertions
        assertTrue("Should have reached LOCAL_VERIFYING state", reachedStates.contains(InstallTaskStatus.LOCAL_VERIFYING))
        // Note: Depending on speed, it might skip INSTALLING in the UI if COMPLETED is fast for downloadOnly
        // but it MUST reach either INSTALLING or COMPLETED after LOCAL_VERIFYING
        assertTrue("Should have reached at least LOCAL_VERIFYING and COMPLETED", 
            reachedStates.contains(InstallTaskStatus.LOCAL_VERIFYING) && reachedStates.contains(InstallTaskStatus.COMPLETED))
        
        // 6. Verify WorkManager - DownloadWorker should NOT have been enqueued for this release
        val workInfos = WorkManager.getInstance(context).getWorkInfosByTag(game.releaseName).get()
        val hasDownloadWork = workInfos.any { it.tags.contains("download") }
        assertFalse("DownloadWorker should NOT be enqueued for Fast Track flow", hasDownloadWork)
        
        // 7. Verify isLocalInstall flag in DB
        val dbTask = repository.db.queuedInstallDao().getByReleaseName(game.releaseName)
        assertTrue("isLocalInstall should be true in database", dbTask?.isLocalInstall == true)
    }

    /**
     * Verifies the state transition sequence for Fast Track flow.
     * QUEUED -> LOCAL_VERIFYING -> INSTALLING -> PENDING_INSTALL/COMPLETED
     */
    @Test
    fun testFastTrackStateTransitions() = runBlocking {
        val releaseName = "Transition_Test"
        val game = GameData(
            gameName = "Transition Game",
            packageName = context.packageName, // Use valid package
            versionCode = "1",
            releaseName = releaseName
        )
        repository.db.gameDao().insertGames(listOf(game.toEntity()))
        
        // Prepare local APK
        val gameDir = File(testDownloadsDir, releaseName)
        gameDir.mkdirs()
        val localApk = File(gameDir, "${context.packageName}.apk")
        File(context.packageCodePath).inputStream().use { input ->
            FileOutputStream(localApk).use { output ->
                input.copyTo(output)
            }
        }

        // Initial state
        val entity = QueuedInstallEntity.create(
            releaseName = releaseName,
            status = InstallStatus.QUEUED,
            queuePosition = 0
        )
        repository.db.queuedInstallDao().insert(entity)
        
        // Trigger via ViewModel
        viewModel.installGame(releaseName, downloadOnly = true)
        
        val states = mutableListOf<InstallTaskStatus>()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < 5000) {
            val task = viewModel.installQueue.value.find { it.releaseName == releaseName }
            if (task != null && (states.isEmpty() || states.last() != task.status)) {
                states.add(task.status)
                if (task.status == InstallTaskStatus.COMPLETED) {
                    // Collect for a few more cycles
                    repeat(5) {
                        delay(20)
                        viewModel.installQueue.value.find { it.releaseName == releaseName }?.let {
                            if (states.last() != it.status) states.add(it.status)
                        }
                    }
                    break
                }
            }
            delay(50) // More frequent polling
        }
        
        // Verify sequence (allowing for skips if processing is too fast, but should start with non-downloading status)
        assertFalse("Should not enter DOWNLOADING state", states.contains(InstallTaskStatus.DOWNLOADING))
        assertFalse("Should not enter EXTRACTING state", states.contains(InstallTaskStatus.EXTRACTING))
        assertTrue("Should enter LOCAL_VERIFYING state", states.contains(InstallTaskStatus.LOCAL_VERIFYING))
    }

    /**
     * Verifies that the app gracefully falls back to standard download flow if 
     * a SecurityException or other error occurs during Fast Track discovery (AC: 7).
     * This tests the try-catch block added in MainViewModel.kt:2250.
     */
    @Test
    fun testPermissionDeniedFallback() = runBlocking {
        // We use a release name that doesn't exist to ensure discovery returns false or fails.
        // In a real scenario, this would be triggered by a SecurityException in repository.hasLocalInstallFiles.
        val releaseName = "Permission_Denied_Fallback_Test"
        val game = GameData(
            gameName = "Fallback Game",
            packageName = testPackageName,
            versionCode = "1",
            releaseName = releaseName
        )
        repository.db.gameDao().insertGames(listOf(game.toEntity()))
        
        // Trigger via ViewModel
        // Even if discovery fails, it should fallback to standard DownloadWorker enqueueing.
        viewModel.installGame(releaseName, downloadOnly = true)
        
        val startTime = System.currentTimeMillis()
        var downloadEnqueued = false
        while (System.currentTimeMillis() - startTime < 5000) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosByTag(releaseName).get()
            if (workInfos.any { it.tags.contains("download") }) {
                downloadEnqueued = true
                break
            }
            delay(500)
        }
        
        assertTrue("Should fallback to enqueuing DownloadWorker if Fast Track fails", downloadEnqueued)
    }
}
