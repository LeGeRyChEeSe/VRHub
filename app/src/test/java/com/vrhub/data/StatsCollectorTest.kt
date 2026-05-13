package com.vrhub.data

import com.vrhub.network.StatsCollectRequest
import com.vrhub.network.StatsCollectResponse
import com.vrhub.network.StatsApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

/**
 * Unit tests for StatsCollector using MockK for mocking.
 * Tests Story 3.3 AC #1, #2:
 * AC #1: Given consent=false, when collectStats() called, no API call recorded.
 * AC #2: Given consent=true and mock API service, when collectStats(games, "lucky") called,
 *        then mock service receives request with 2 games and tier "lucky".
 */
@RunWith(RobolectricTestRunner::class)
class StatsCollectorTest {

    @Test
    fun `collectStats with no consent does not call API`() = runTest {
        val mockConsent = mockk<ConsentPreferencesInterface>()
        every { mockConsent.consentEnabled } returns flowOf(false)

        val mockApi = mockk<StatsApiService>()
        // Set up mock but expect no calls since consent is false
        coEvery { mockApi.collectStats(any<StatsCollectRequest>()) } returns Response.success(StatsCollectResponse(message = "ok"))

        val collector = StatsCollector(mockApi, mockConsent)
        collector.collectStats(null, mapOf("com.game.app" to false), "standard")

        // Verify API was never called (AC1)
        coVerify(exactly = 0) { mockApi.collectStats(any<StatsCollectRequest>()) }
    }

    @Test
    fun `collectStats with consent sends correct data`() = runTest {
        val mockConsent = mockk<ConsentPreferencesInterface>()
        every { mockConsent.consentEnabled } returns flowOf(true)

        val mockApi = mockk<StatsApiService>()
        val requestSlot = slot<StatsCollectRequest>()

        coEvery { mockApi.collectStats(capture(requestSlot)) } returns Response.success(StatsCollectResponse(message = "ok"))

        val collector = StatsCollector(mockApi, mockConsent)

        val games = mapOf(
            "com.game.app1" to true,
            "com.game.app2" to false
        )
        collector.collectStats(null, games, "lucky")

        assertTrue("Request should be captured", requestSlot.isCaptured)
        val capturedRequest = requestSlot.captured
        assertEquals("Should receive 2 games", 2, capturedRequest.games.size)
        assertEquals("Tier should be lucky", "lucky", capturedRequest.tier)
        // Verify GameStat content (packageName and isFavorite values)
        assertEquals("com.game.app1", capturedRequest.games[0].packageName)
        assertTrue(capturedRequest.games[0].isFavorite)
        assertEquals("com.game.app2", capturedRequest.games[1].packageName)
        assertFalse(capturedRequest.games[1].isFavorite)
    }

    @Test
    fun `collectStats with no games does not call API even with consent`() = runTest {
        val mockConsent = mockk<ConsentPreferencesInterface>()
        every { mockConsent.consentEnabled } returns flowOf(true)

        val mockApi = mockk<StatsApiService>()
        var apiCalled = false

        coEvery { mockApi.collectStats(any<StatsCollectRequest>()) } answers {
            apiCalled = true
            Response.success(StatsCollectResponse(message = "ok"))
        }

        val collector = StatsCollector(mockApi, mockConsent)
        collector.collectStats(null, emptyMap(), "standard")

        assertFalse("API should not be called with no games", apiCalled)
    }

    @Test
    fun `collectStats handles 403 server rejection gracefully`() = runTest {
        val mockConsent = mockk<ConsentPreferencesInterface>()
        every { mockConsent.consentEnabled } returns flowOf(true)

        val mockApi = mockk<StatsApiService>()

        coEvery { mockApi.collectStats(any<StatsCollectRequest>()) } returns Response.error(403, "Forbidden".toResponseBody(null))

        val collector = StatsCollector(mockApi, mockConsent)

        // Should not throw exception - 403 is handled internally
        collector.collectStats(null, mapOf("com.game.app" to false), "standard")

        // Test passes if no exception is thrown
        assertTrue("403 handled gracefully", true)
    }
}