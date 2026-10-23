package com.vrhub.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vrhub.data.CryptoUtils
import com.vrhub.data.GameData
import com.vrhub.data.InstallStatus
import com.vrhub.data.MainRepository
import com.vrhub.data.ServerConfig
import com.vrhub.data.ServerConfigRepository
import com.vrhub.network.StatsApiService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * End-to-end battery against the real vrhub-server (port from e2e.env) on the headless AVD.
 * All inputs come from instrumentation runner arguments fed by e2e/fixtures/e2e.env via scripts/e2e.sh.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EndToEndPipelineTest {

    private lateinit var context: Context
    private lateinit var repo: MainRepository
    private lateinit var workManager: WorkManager

    private val baseUri: String get() = arg("baseUri", "http://10.0.2.2:39458")
    private val passwordB64: String get() = requireArg("password")
    private val releaseName: String get() = arg("release", "com.vrhub.e2e.fixture")
    private val packageName: String get() = arg("pkg", "com.vrhub.e2e.fixture")
    private val controlHost: String get() = arg("controlHost", "10.0.2.2")
    private val controlPort: Int get() = arg("controlPort", "39460").toInt()
    private val expectedShaA: String get() = requireArg("shaA")
    private val expectedShaB: String get() = requireArg("shaB")
    private val expectedShaC: String get() = requireArg("shaC")
    private val fixtureSegmentSha: String get() = requireArg("sha7z")
    private val segName: String get() = requireArg("segName")
    private val segSize: Long get() = requireArg("segSize").toLong()

    private fun arg(key: String, default: String): String =
        InstrumentationRegistry.getArguments().getString(key, default)

    // Fixture-dependent values drift when regenerated; a plain connectedAndroidTest
    // run without harness args would silently compare against stale expectations.
    private fun requireArg(key: String): String =
        InstrumentationRegistry.getArguments().getString(key)
            ?: error("missing instrumentation arg '$key': run this class via scripts/e2e.sh, which passes the live values from e2e/fixtures/e2e.env + e2e/run/e2e.gen.env")

    @Before
    fun setup() {
        // Harness-gated battery: when the live args are not provided (plain
        // connectedAndroidTest without scripts/e2e.sh), skip instead of failing,
        // so the regular CI instrumented run stays green.
        Assume.assumeNotNull(InstrumentationRegistry.getArguments().getString("password"))
        context = ApplicationProvider.getApplicationContext()
        // Production runtime: under test-mode init, setForeground() still routes through
        // SystemForegroundDispatcher (verified in work-testing 2.9.1 bytecode), which ANRs
        // with synchronous executors. The real runtime executes promptly on the emulator.
        workManager = WorkManager.getInstance(context)
        repo = MainRepository(context)
        repo.setActiveConfig(ServerConfig(baseUri = baseUri, password = passwordB64))
        runBlocking { repo.syncCatalog(baseUri) }
    }



    @After
    fun tearDown() {
        if (!this::repo.isInitialized) return
        runBlocking {
            repo.db.gameDao().clearAll()
            repo.db.queuedInstallDao().deleteAll()
            repo.db.installHistoryDao().deleteAll()
        }
    }

    private fun awaitWorkSettled(id: UUID, timeoutMs: Long = 120_000): WorkInfo {
        val deadline = System.currentTimeMillis() + timeoutMs
        var info = workManager.getWorkInfoById(id).get()
        while (System.currentTimeMillis() < deadline) {
            if (info.state.isFinished) break
            Thread.sleep(250)
            info = workManager.getWorkInfoById(id).get()
        }
        return info
    }

    private fun tempDirFor(release: String): File =
        File(context.filesDir, "install_temp").let { root ->
            File(root, CryptoUtils.md5(release + "\n"))
        }

    private fun injectConfig(config: ServerConfig) {
        val prefsRepo = ServerConfigRepository(context)
        prefsRepo.saveConfig(config, "MANUAL_KV")
        assertTrue("E2E-3/E2E-1: injected config must be valid", prefsRepo.hasValidConfig())
        assertEquals(config.baseUri, prefsRepo.loadConfig()?.baseUri)
        repo.setActiveConfig(config)
    }

    private fun controlExchange(payload: String): String {
        Socket(controlHost, controlPort).use { socket ->
            socket.soTimeout = 90_000
            val out = PrintWriter(socket.getOutputStream(), true)
            out.println(payload)
            val reader = socket.inputStream.bufferedReader()
            val line = reader.readLine()
            return line ?: ""
        }
    }

    /** E2E-3 + E2E-7: nominal pipeline config -> catalog -> download -> extraction -> Room installed. */
    private fun clearStagedApk() {
        val staged = repo.getStagedApkFile(packageName)
        staged?.delete()
    }

    @Test
    fun test01_nominalPipeline() = runBlocking {
        injectConfig(ServerConfig(baseUri = baseUri, password = passwordB64))
        val h = CryptoUtils.md5(releaseName + "\n")
        File(context.filesDir, "install_temp/$h").deleteRecursively()
        clearStagedApk()

        repo.syncCatalog(baseUri)
        val game = repo.getGameByReleaseName(releaseName)
        assertNotNull("E2E-3: game must be listed via real server API", game)
        assertEquals(packageName, game!!.packageName)

        val info = settleAfterEnqueue(releaseName)
        assertEquals("E2E-3: download must succeed", WorkInfo.State.SUCCEEDED, info.state)

        // Extraction (skipRemoteVerification: files already downloaded by the worker)
        val staged = repo.installGame(game, skipRemoteVerification = true, skipStorageCheck = true) { _, _, _, _ -> }
        assertNotNull("E2E-3: staged APK expected", staged)

        val hash = CryptoUtils.md5(releaseName + "\n")
        val gameTempDir = File(context.filesDir, "install_temp/$hash")
        val extracted = File(gameTempDir, "extracted")
        val a = File(extracted, "a.bin"); val b = File(extracted, "b.bin"); val c = File(extracted, "c.bin")
        assertTrue("E2E-3: extracted file a.bin missing", a.exists())
        assertTrue("E2E-3: extracted file b.bin missing", b.exists())
        assertTrue("E2E-3: extracted file c.bin missing", c.exists())

        // E2E-7: device-computed sha256 must match fixture hashes
        assertEquals("E2E-7: sha256 a.bin mismatch", expectedShaA, CryptoUtils.sha256(a))
        assertEquals("E2E-7: sha256 b.bin mismatch", expectedShaB, CryptoUtils.sha256(b))
        assertEquals("E2E-7: sha256 c.bin mismatch", expectedShaC, CryptoUtils.sha256(c))

        // E2E-3 (Room installed): archive to history as COMPLETED and confirm queue drained
        assertTrue(repo.archiveTask(releaseName, InstallStatus.COMPLETED))
        val history = repo.db.installHistoryDao().getAll()
        assertTrue("E2E-3: history entry must exist", history.isNotEmpty())
        assertEquals(InstallStatus.COMPLETED, history.first().status)
        val installed = repo.getInstalledPackagesMap()
        // If the runner pre-installed the fixture APK via adb, the installed flag must show v1234.
        if (installed.containsKey(packageName)) {
            assertEquals(1234L, installed[packageName])
        }
    }

    private suspend fun settleAfterEnqueue(release: String): WorkInfo {
        val id = enqueueAndRelease(release)
        return awaitWorkSettled(id)
    }

    private suspend fun enqueueAndRelease(release: String): UUID {
        workManager.pruneWork()
        repo.addToQueue(release, InstallStatus.QUEUED)
        repo.enqueueDownload(release, false, false, baseUri, passwordB64)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val infos = workManager.getWorkInfosForUniqueWork("download_$release").get()
            if (infos.isNotEmpty()) return infos.first().id
            Thread.sleep(250)
        }
        throw IllegalStateException("E2E: unique work not found after enqueue")
    }

    /** E2E-4: wrong passphrase -> clean failure, no partial extraction, Room status failed, non-empty message. */
    @Test
    fun test02_wrongPassword() = runBlocking {
        injectConfig(ServerConfig(baseUri = baseUri, password = "ZTJlLXNlY3JldC13cm9uZw==")) // "e2e-secret-wrong"
        clearStagedApk()
        repo.syncCatalog(baseUri)
        val game = repo.getGameByReleaseName(releaseName)
        assertNotNull(game)

        // Start from clean temp state (test01 already extracted), keeping downloaded segments
        val hash = CryptoUtils.md5(releaseName + "\n")
        val gameTempDir = File(context.filesDir, "install_temp/$hash")
        File(gameTempDir, "extracted").deleteRecursively()
        File(gameTempDir, MainRepository.EXTRACTION_DONE_MARKER).delete()
        if (gameTempDir.walkTopDown().none { it.isFile && it.name.contains(".7z") }) {
            repo.addToQueue(releaseName, InstallStatus.QUEUED)
            repo.enqueueDownload(releaseName, false, false, baseUri, passwordB64)
            assertEquals(WorkInfo.State.SUCCEEDED, settleAfterEnqueue(releaseName).state)
        }

        var failure: Exception? = null
        try {
            repo.installGame(game!!, skipRemoteVerification = true, skipStorageCheck = true) { _, _, _, _ -> }
        } catch (e: Exception) {
            failure = e
        }
        assertNotNull("E2E-4: extraction must fail with wrong password", failure)
        assertTrue("E2E-4: user message must be non-empty", failure!!.message!!.isNotBlank())
        val userMessage = failure!!.message!!
        assertTrue(
            "E2E-4: unexpected failure message: $userMessage",
            userMessage.contains("Wrong password") || userMessage.contains("encrypted archive") ||
                userMessage.contains("corrupted")
        )

        val extractionDir = File(gameTempDir, "extracted")
        assertTrue("E2E-4: extraction dir must be cleaned (no partial extraction)", !extractionDir.exists() || extractionDir.list()?.isEmpty() == true)
        val marker = File(gameTempDir, MainRepository.EXTRACTION_DONE_MARKER)
        assertTrue("E2E-4: extraction marker must not exist", !marker.exists())

        repo.addToQueue(releaseName, InstallStatus.FAILED)
        assertTrue(repo.archiveTask(releaseName, InstallStatus.FAILED, userMessage))
        val history = repo.db.installHistoryDao().getAll()
        assertTrue("E2E-4: Room status failed", history.any { it.status == InstallStatus.FAILED && it.errorMessage?.isNotBlank() == true })
    }

    /** E2E-5: cancel WorkManager mid-download -> no corrupt residual file, clean resume. */
    @Test
    fun test03_cancelMidDownload() = runBlocking {
        injectConfig(ServerConfig(baseUri = baseUri, password = passwordB64))
        val hash = CryptoUtils.md5(releaseName + "\n")
        val gameTempDir = File(context.filesDir, "install_temp/$hash")
        gameTempDir.deleteRecursively()

        val id = enqueueAndRelease(releaseName)

        // Poll until the big segment has partially downloaded, then cancel mid-flight.
        val seg = File(gameTempDir, segName)
        val deadline = System.currentTimeMillis() + 30_000
        var partial = false
        while (System.currentTimeMillis() < deadline) {
            if (seg.exists() && seg.length() > 100_000L && seg.length() < segSize) { partial = true; break }
            Thread.sleep(10)
        }
        assertTrue("E2E-5: could not observe a partial segment before cancel", partial)
        val sizeBeforeCancel = seg.length()
        workManager.cancelWorkById(id)
        val info = awaitWorkSettled(id, 20_000)
        assertEquals("E2E-5: work must be cancelled", WorkInfo.State.CANCELLED, info.state)

        // Resume: re-enqueue, worker must complete the segment to the exact fixture size
        val resumed = settleAfterEnqueue(releaseName)
        assertEquals("E2E-5: resumed download must succeed", WorkInfo.State.SUCCEEDED, resumed.state)
        assertTrue("E2E-5: residual file corrupted after cancel+resume", seg.length() > sizeBeforeCancel)
        assertEquals("E2E-5: resumed segment sha256 mismatch", fixtureSegmentSha, CryptoUtils.sha256(seg))
    }

    /** E2E-6: server killed mid-download (via control channel in scripts/e2e.sh), retry/backoff observable, clean recovery. */
    @Test
    fun test04_serverKilledMidDownload() = runBlocking {
        injectConfig(ServerConfig(baseUri = baseUri, password = passwordB64))
        val hash = CryptoUtils.md5(releaseName + "\n")
        val gameTempDir = File(context.filesDir, "install_temp/$hash")
        gameTempDir.deleteRecursively()

        val id = enqueueAndRelease(releaseName)

        val seg = File(gameTempDir, segName)
        // Poll fast so the kill lands while bytes are still in flight: at ~6 MB
        // of a 8 MB segment, ~2 s of transfer remain on loopback.
        // Cross ~1.5 MB of the 8 MB part: ~3.5 s of transfer remain, enough
        // for the control channel to confirm the kill before completion.
        val killThreshold = minOf(1_500_000L, segSize)
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (seg.exists() && seg.length() > killThreshold && workManager.getWorkInfoById(id).get().state == WorkInfo.State.RUNNING) break
            Thread.sleep(10)
        }
        assertTrue("E2E-6: partial segment needed before server kill", seg.exists() && seg.length() > killThreshold)

        // Ask the harness to kill the server, wait for confirmation.
        val ack = controlExchange("PARTIAL ${seg.length()}")
        assertEquals("E2E-6: control channel must confirm kill", "KILLED", ack)

        // Worker must observe the failure and retry (backoff), then fail after max attempts, without crashing.
        // Observed worst case: 3 backoffs (10 s, 20 s, 40 s) + 4 x 30 s connect timeouts = ~160 s.
        var sawRetry = false
        val settleDeadline = System.currentTimeMillis() + 270_000
        var info = workManager.getWorkInfoById(id).get()
        while (System.currentTimeMillis() < settleDeadline) {
            if (info.state == WorkInfo.State.RUNNING) {
                // after first failure it goes back to ENQUEUED for backoff retry
            }
            if (sawRetry && info.state == WorkInfo.State.FAILED) break
            if (info.state == WorkInfo.State.ENQUEUED) sawRetry = true
            Thread.sleep(500)
            info = workManager.getWorkInfoById(id).get()
        }
        assertTrue("E2E-6: retry/backoff must be observable (sawRetry or runAttemptCount)", sawRetry || info.runAttemptCount >= 2)
        assertEquals("E2E-6: final state must be FAILED (max retries)", WorkInfo.State.FAILED, info.state)

        // Room must remain coherent: queue entry still present with FAILED status
        val queue = repo.db.queuedInstallDao().getByReleaseName(releaseName)
        assertNotNull("E2E-6: queue entry must survive", queue)
        assertEquals(InstallStatus.FAILED, InstallStatus.fromStringOrNull(queue!!.status))

        // Ask harness to restart the server, then verify clean recovery via a fresh attempt.
        val ready = controlExchange("RESTART")
        assertEquals("E2E-6: control channel must confirm restart", "READY", ready)
        workManager.pruneWork()
        repo.addToQueue(releaseName, InstallStatus.QUEUED)
        repo.enqueueDownload(releaseName, false, false, baseUri, passwordB64)
        val recovered = settleAfterEnqueue(releaseName)
        assertEquals("E2E-6: recovery after server restart must succeed", WorkInfo.State.SUCCEEDED, recovered.state)
    }

    /** E2E-8: monetization fully stubbed (local MockWebServer), no dependency on vrhub.sunshine-aio.com. */
    @Test
    fun test05_monetizationStub() = runBlocking {
        val mock = MockWebServer()
        mock.start()
        mock.enqueue(MockResponse().setResponseCode(200).setBody("{\"tier\":\"standard\",\"valid\":false}"))
        val service = Retrofit.Builder()
            .baseUrl(mock.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StatsApiService::class.java)
        com.vrhub.data.NetworkModule.replaceStatsApiService(service)

        val response = com.vrhub.data.NetworkModule.statsApiService.getUserTier("anonymous")
        assertTrue("E2E-8: stub must serve locally", response.isSuccessful)
        val recorded = mock.requestCount
        assertTrue("E2E-8: request must have hit the local stub", recorded > 0)
        mock.shutdown()
    }

    /** E2E-1: every parameter comes from e2e.env through runner arguments. */
    @Test
    fun test00_configFromEnvArgs() {
        val host = requireArg("host")
        val port = requireArg("port")
        assertEquals("E2E-1: baseUri must match e2e.env host/port", "http://$host:$port", baseUri)
        assertEquals("E2E-1: controlHost must match e2e.env host", host, controlHost)
        val decoded = String(android.util.Base64.decode(passwordB64, android.util.Base64.NO_WRAP))
        assertEquals("E2E-1: password arg must decode to e2e.env E2E_PASSWORD", requireArg("passwordPlain"), decoded)
        assertTrue("E2E-1: release must be provided", releaseName.isNotBlank())
    }
}
