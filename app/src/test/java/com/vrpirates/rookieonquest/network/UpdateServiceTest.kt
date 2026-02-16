package com.vrpirates.rookieonquest.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UpdateServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: UpdateService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        service = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpdateService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun checkUpdate_sendsCorrectHeaders_andParsesResponse() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "version": "2.5.0",
                "changelog": "New features",
                "downloadUrl": "https://example.com/rookie.apk",
                "checksum": "abc123sha256",
                "timestamp": "2026-02-15T05:00:00Z"
            }
        """.trimIndent()
        
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))
        
        val signature = "test-signature"
        val date = "2026-02-15T05:00:00Z"

        // When
        val result = service.checkUpdate(signature, date)

        // Then
        val request = mockWebServer.takeRequest()
        assertEquals("test-signature", request.getHeader("X-Rookie-Signature"))
        assertEquals("2026-02-15T05:00:00Z", request.getHeader("X-Rookie-Date"))
        assertEquals("/api/check-update", request.path)
        
        assertEquals("2026-02-15T05:00:00Z", result.timestamp)
        assertEquals("2.5.0", result.version)
        assertEquals("https://example.com/rookie.apk", result.downloadUrl)
    }

    @Test
    fun checkUpdate_handles403Forbidden() {
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(403))
            try {
                service.checkUpdate("invalid", "now")
                org.junit.Assert.fail("Expected HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(403, e.code())
            }
        }
    }

    @Test
    fun checkUpdate_handlesInvalidJson() {
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody("not-json").setResponseCode(200))
            try {
                service.checkUpdate("sig", "date")
                org.junit.Assert.fail("Expected Gson exception")
            } catch (e: Exception) {
                val isGsonException = e is com.google.gson.JsonParseException || 
                                     e is com.google.gson.stream.MalformedJsonException ||
                                     e.cause is com.google.gson.stream.MalformedJsonException
                assert(isGsonException) { "Expected Gson exception, got ${e.javaClass.simpleName}" }
            }
        }
    }

    @Test
    fun checkUpdate_handlesMissingFields() = runBlocking {
        // Gson will set missing fields to null for String types even with 'val'
        val jsonResponse = """
            {
                "version": "2.5.0"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))
        
        val result = service.checkUpdate("sig", "date")
        assertEquals("2.5.0", result.version)
        assertEquals(null, result.downloadUrl)
        assertEquals(null, result.checksum)
    }

    @Test
    fun checkUpdate_handlesNetworkTimeout() {
        runBlocking {
            // MockWebServer doesn't have a direct "timeout" but we can simulate by not responding 
            // and setting a very short timeout on the client. 
            // Alternatively, enqueue a response with a delay.
            mockWebServer.enqueue(MockResponse()
                .setBody("{}")
                .setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS))
            
            val timeoutService = Retrofit.Builder()
                .baseUrl(mockWebServer.url("/"))
                .client(okhttp3.OkHttpClient.Builder()
                    .readTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UpdateService::class.java)

            try {
                timeoutService.checkUpdate("sig", "date")
                org.junit.Assert.fail("Expected SocketTimeoutException")
            } catch (e: java.net.SocketTimeoutException) {
                // Success
            }
        }
    }

    @Test
    fun checkUpdate_handlesNetworkFailure() {
        runBlocking {
            // Shutdown server to trigger IOException
            mockWebServer.shutdown()

            try {
                service.checkUpdate("sig", "date")
                org.junit.Assert.fail("Expected IOException")
            } catch (e: java.io.IOException) {
                // Success - test passed
            }
            // Note: No need to re-initialize mockWebServer here.
            // The @Before setup() method creates a fresh instance for each test automatically.
        }
    }
}
