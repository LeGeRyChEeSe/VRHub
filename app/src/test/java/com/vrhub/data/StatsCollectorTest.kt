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
 * Tests Story 3.3 AC #1, #2 and Story 8.6 (gameName transmission).
 */
@RunWith(RobolectricTestRunner::class)
class StatsCollectorTest {

    @Test
    fun `collectStats with no consent does not call API`() = runTest {
        val mockConsent = mockk<ConsentPreferencesInterface>()
        every { mockConsent.consentEnabled } returns flowOf(false)

        val mockApi = mockk<StatsApiService>()
        coEvery { mockApi.collectStats(any<StatsCollectRequest>()) } returns Response.success(StatsCollectResponse(message = "ok"))

        val collector = StatsCollector(mockApi, mockConsent)
        collector.collectStats(null, mapOf("com.game.app" to Pair(false, null)), "standard")

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
            "com.game.app1" to Pair(true, null as String?),
            "com.game.app2" to Pair(false, null as String?)
        )
        collector.collectStats(null, games, "lucky")

        assertTrue("Request should be captured", requestSlot.isCaptured)
        val capturedRequest = requestSlot.captured
        assertEquals("Should receive 2 games", 2, capturedRequest.games.size)
        assertEquals("Tier should be lucky", "lucky", capturedRequest.tier)
        assertNull("Email should be null when not provided", capturedRequest.email)
        assertEquals("com.game.app1", capturedRequest.games[0].packageName)
        assertTrue(capturedRequest.games[0].isFavorite)
        assertEquals("com.game.app2", capturedRequest.games[1].packageName)
        assertFalse(capturedRequest.games[1].isFavorite)
        // gameName is null when not provided (backward compat)
        assertNull(capturedRequest.games[0].gameName)
        assertNull(capturedRequest.games[1].gameName)
    }

    @Test
    fun `collectStats transmits gameName when provided`() = runTest {
        val mockConsent = mockk<ConsentPreferencesInterface>()
        every { mockConsent.consentEnabled } returns flowOf(true)

        val mockApi = mockk<StatsApiService>()
        val requestSlot = slot<StatsCollectRequest>()

        coEvery { mockApi.collectStats(capture(requestSlot)) } returns Response.success(StatsCollectResponse(message = "ok"))

        val collector = StatsCollector(mockApi, mockConsent)

        val games = mapOf(
            "com.beatgames.beatsaber" to Pair(true, "Beat Saber"),
            "com.camouflaj.pistolwhip" to Pair(false, null as String?)
        )
        collector.collectStats(null, games, "standard")

        val capturedRequest = requestSlot.captured
        val beatSaber = capturedRequest.games.first { it.packageName == "com.beatgames.beatsaber" }
        val pistolWhip = capturedRequest.games.first { it.packageName == "com.camouflaj.pistolwhip" }

        assertEquals("gameName should be transmitted when provided", "Beat Saber", beatSaber.gameName)
        assertNull("gameName should be null when not provided (old client compat)", pistolWhip.gameName)
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
        collector.collectStats(null, mapOf("com.game.app" to Pair(false, null)), "standard")

        assertTrue("403 handled gracefully", true)
    }
}
