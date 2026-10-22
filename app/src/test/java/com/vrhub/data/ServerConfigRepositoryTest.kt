package com.vrhub.data

import androidx.test.core.app.ApplicationProvider
import com.vrhub.logic.CatalogUtils
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ServerConfigRepositoryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/403") -> MockResponse().setResponseCode(403).setBody("Forbidden")
                    path.startsWith("/401") -> MockResponse().setResponseCode(401).setBody("Unauthorized")
                    path.startsWith("/500") -> MockResponse().setResponseCode(500).setBody("Server error")
                    path.startsWith("/ok") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"baseUri":"http://localhost:8080/","password":"secret123","extraKeys":{"metaUrl":"http://localhost:8080/meta.7z"}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchJsonConfig403ReturnsExplicitUnsupportedPublicSourceException() = runTest {
        val repo = ServerConfigRepository(ApplicationProvider.getApplicationContext())
        val result = repo.fetchJsonConfig(server.url("/403/config.json").toString())
        val error = result.exceptionOrNull()
        assertTrue(error is UnsupportedPublicSourceException)
        val message = error?.message ?: ""
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("Public catalog hosts are not supported"))
        assertTrue(message.contains("self-hosted"))
    }

    @Test
    fun fetchJsonConfig401ReturnsExplicitUnsupportedPublicSourceException() = runTest {
        val repo = ServerConfigRepository(ApplicationProvider.getApplicationContext())
        val result = repo.fetchJsonConfig(server.url("/401/config.json").toString())
        val error = result.exceptionOrNull()
        assertTrue(error is UnsupportedPublicSourceException)
        val message = error?.message ?: ""
        assertTrue(message.contains("HTTP 401"))
        assertTrue(message.contains("Public catalog hosts are not supported"))
        assertTrue(message.contains("self-hosted"))
    }

    @Test
    fun fetchJsonConfig500KeepsGenericNetworkException() = runTest {
        val repo = ServerConfigRepository(ApplicationProvider.getApplicationContext())
        val result = repo.fetchJsonConfig(server.url("/500/config.json").toString())
        val error = result.exceptionOrNull()
        assertTrue(error is NetworkException)
        assertTrue(!(error is UnsupportedPublicSourceException))
    }

    @Test
    fun fetchJsonConfig200ValidConfigSucceeds() = runTest {
        val repo = ServerConfigRepository(ApplicationProvider.getApplicationContext())
        val result = repo.fetchJsonConfig(server.url("/ok/config.json").toString())
        assertTrue(result.isSuccess)
        val config = result.getOrNull()
        assertNotNull(config)
        assertTrue(config?.baseUri == "http://localhost:8080/")
        assertTrue(config?.password == "secret123")
    }

    @Test
    fun testConnection403SurfacesExplicitMessage() = runTest {
        val config = ServerConfig(baseUri = server.url("/403/").toString(), password = "pw")
        val result = testConnection(config)
        assertTrue(result is TestResult.ConnectionError)
        val message = (result as TestResult.ConnectionError).message
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("Public catalog hosts are not supported"))
        assertTrue(message.contains("self-hosted"))
    }

    @Test
    fun testConnection401SurfacesExplicitMessage() = runTest {
        val config = ServerConfig(baseUri = server.url("/401/").toString(), password = "pw")
        val result = testConnection(config)
        assertTrue(result is TestResult.ConnectionError)
        val message = (result as TestResult.ConnectionError).message
        assertTrue(message.contains("HTTP 401"))
        assertTrue(message.contains("self-hosted"))
    }

    @Test
    fun testPassword403SurfacesExplicitMessage() = runTest {
        val config = ServerConfig(baseUri = server.url("/403/").toString(), password = "pw")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = testPassword(config, context)
        assertTrue(result is TestResult.ConnectionError)
        val message = (result as TestResult.ConnectionError).message
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("Public catalog hosts are not supported"))
        assertTrue(message.contains("self-hosted"))
    }

    @Test
    fun downloadFile403ThrowsUnsupportedPublicSourceException() = runTest {
        val target = File.createTempFile("vrhub_dl_test", ".bin")
        try {
            val error = runCatching {
                CatalogUtils.downloadFile(server.url("/403/meta.7z").toString(), target)
            }.exceptionOrNull()
            assertTrue(error is UnsupportedPublicSourceException)
            val message = error?.message ?: ""
            assertTrue(message.contains("HTTP 403"))
            assertTrue(message.contains("self-hosted"))
        } finally {
            target.delete()
        }
    }

    @Test
    fun unsupportedPublicSourceMessageContainsCodeAndGuidance() {
        val message = unsupportedPublicSourceMessage(403)
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("Public catalog hosts are not supported by VRHub"))
        assertTrue(message.contains("self-hosted"))
    }

    @Test
    fun unsupportedPublicSourceExceptionIsNonRetryable() {
        val error = UnsupportedPublicSourceException(403)
        assertTrue(error is NonRetryableDownloadException)
        assertTrue(error.message!!.contains("HTTP 403"))
    }
}
