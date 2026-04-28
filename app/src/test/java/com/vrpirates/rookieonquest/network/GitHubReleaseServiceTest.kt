package com.vrpirates.rookieonquest.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Unit tests for GitHubReleaseService.
 *
 * Tests cover:
 * - Successful release fetch
 * - 404 Not Found (repo transferred/deleted)
 * - 403 Rate Limiting
 * - 5xx Server Errors
 * - Network failures
 * - Response parsing
 */
class GitHubReleaseServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: GitHubReleaseService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        service = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubReleaseService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun getLatestRelease_sendsCorrectUserAgent_andParsesResponse() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "tag_name": "v2.5.0",
                "name": "VRHub v2.5.0",
                "body": "## Changelog\n- Fix X\n- Add Y",
                "html_url": "https://github.com/LeGeRyChEeSe/VRHub/releases/tag/v2.5.0",
                "assets": [
                    {
                        "name": "VRHub_2.5.0.apk",
                        "browser_download_url": "https://github.com/.../VRHub_2.5.0.apk",
                        "size": 57000000
                    }
                ],
                "published_at": "2026-02-15T05:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        // When
        val result = service.getLatestRelease("VRHub/1.0")

        // Then
        val request = mockWebServer.takeRequest()
        assertEquals("VRHub/1.0", request.getHeader("User-Agent"))
        assertEquals("/repos/{owner}/{repo}/releases/latest", request.path)

        assertEquals("v2.5.0", result.tagName)
        assertEquals("VRHub v2.5.0", result.name)
        assertEquals("## Changelog\n- Fix X\n- Add Y", result.body)
        assertEquals(1, result.assets.size)
        assertEquals("VRHub_2.5.0.apk", result.assets[0].name)
        assertEquals("https://github.com/.../VRHub_2.5.0.apk", result.assets[0].browserDownloadUrl)
        assertEquals(57000000, result.assets[0].size)
        assertEquals("2026-02-15T05:00:00Z", result.publishedAt)
    }

    @Test
    fun getLatestRelease_handles404NotFound() {
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            try {
                service.getLatestRelease("VRHub/1.0")
                fail("Expected HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(404, e.code())
            }
        }
    }

    @Test
    fun getLatestRelease_handles403RateLimit() {
        runBlocking {
            // Simulate rate limit response with rate limit headers
            val response = MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1708012345")
            mockWebServer.enqueue(response)

            try {
                service.getLatestRelease("VRHub/1.0")
                fail("Expected HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(403, e.code())
                // Verify rate limit headers are present
                val remaining = e.response()?.raw()?.header("X-RateLimit-Remaining")
                assertEquals("0", remaining)
            }
        }
    }

    @Test
    fun getLatestRelease_handles500ServerError() {
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            try {
                service.getLatestRelease("VRHub/1.0")
                fail("Expected HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(500, e.code())
            }
        }
    }

    @Test
    fun getLatestRelease_handlesNetworkFailure() {
        runBlocking {
            // Shutdown server to trigger IOException
            mockWebServer.shutdown()

            try {
                service.getLatestRelease("VRHub/1.0")
                fail("Expected IOException")
            } catch (e: java.io.IOException) {
                // Success
            }
        }
    }

    @Test
    fun getLatestRelease_handlesMissingAssetFields() = runBlocking {
        // Given - response with minimal asset info
        val jsonResponse = """
            {
                "tag_name": "v2.5.0",
                "name": "VRHub v2.5.0",
                "body": null,
                "html_url": null,
                "assets": [],
                "published_at": null
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        // When
        val result = service.getLatestRelease("VRHub/1.0")

        // Then
        assertEquals("v2.5.0", result.tagName)
        assertEquals("VRHub v2.5.0", result.name)
        assertEquals(null, result.body)
        assertEquals(0, result.assets.size)
        assertEquals(null, result.publishedAt)
    }

    @Test
    fun getLatestRelease_handlesMultipleAssets() = runBlocking {
        // Given - response with multiple assets (source, apk, etc.)
        val jsonResponse = """
            {
                "tag_name": "v2.5.0",
                "name": "VRHub v2.5.0",
                "body": "Release notes",
                "assets": [
                    {
                        "name": "source.tar.gz",
                        "browser_download_url": "https://github.com/.../source.tar.gz",
                        "size": 1000000
                    },
                    {
                        "name": "VRHub_2.5.0.apk",
                        "browser_download_url": "https://github.com/.../VRHub_2.5.0.apk",
                        "size": 57000000
                    }
                ],
                "published_at": "2026-02-15T05:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        // When
        val result = service.getLatestRelease("VRHub/1.0")

        // Then
        assertEquals(2, result.assets.size)
        // Assets are in order from server (typically source first, then APK)
        assertTrue(result.assets.any { it.name == "VRHub_2.5.0.apk" })
    }
}
