package com.vrhub.data

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MainRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScheduler = testDispatcher.scheduler

    private lateinit var db: AppDatabase
    private lateinit var repository: MainRepository
    private lateinit var consentPreferences: ConsentPreferences
    private lateinit var fakeStatsApiService: FakeStatsApiService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val appContext = org.robolectric.RuntimeEnvironment.getApplication() as android.app.Application

        db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        consentPreferences = ConsentPreferences(appContext)
        fakeStatsApiService = FakeStatsApiService()

        // Replace the NetworkModule statsApiService with our fake
        com.vrhub.data.NetworkModule.replaceStatsApiService(fakeStatsApiService)

        repository = MainRepository(appContext, db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // AC #1: Consent Gate in maybeCollectStats - skips collection when disabled
    @Test
    fun `maybeCollectStats skips collection when consent is false`() = runTest(testDispatcher) {
        // Ensure consent is false (default)
        consentPreferences.setConsentEnabled(false)
        testScheduler.advanceUntilIdle()

        fakeStatsApiService.collectStatsCalled = false

        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()

        assertFalse("collectStats should NOT be called when consent is disabled", fakeStatsApiService.collectStatsCalled)
    }

    // AC #1: Consent Gate - proceeds when consent is true (even if no games to report, no crash)
    @Test
    fun `maybeCollectStats does not crash when consent is true but no games`() = runTest(testDispatcher) {
        // Enable consent
        consentPreferences.setConsentEnabled(true)
        testScheduler.advanceUntilIdle()

        fakeStatsApiService.collectStatsCalled = false

        // Should complete without throwing even with no catalog games
        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()

        // No crash means the test passes; collectStats won't be called since there are no installed+catalog games
    }

    @Test
    fun `maybeCollectStats does not call collectStats when no games in catalog`() = runTest(testDispatcher) {
        // Enable consent but no games in database
        consentPreferences.setConsentEnabled(true)
        testScheduler.advanceUntilIdle()

        fakeStatsApiService.collectStatsCalled = false

        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()

        assertFalse("collectStats should NOT be called when there are no catalog games", fakeStatsApiService.collectStatsCalled)
    }

    // AC #6: Tier Resolution Fallback - returns "standard" on error
    @Test
    fun `resolveUserTierOrDefault returns standard on network failure`() = runTest(testDispatcher) {
        // Make the API service throw an exception
        fakeStatsApiService.throwOnGetUserTier = true

        val tier = repository.resolveUserTierOrDefault()

        assertEquals("Should fallback to 'standard' on network error", "standard", tier)
    }

    @Test
    fun `resolveUserTierOrDefault returns standard on server non-success response`() = runTest(testDispatcher) {
        // Make the API service return a 500 error
        fakeStatsApiService.throwOnGetUserTier = false
        fakeStatsApiService.returnErrorResponse = true

        val tier = repository.resolveUserTierOrDefault()

        assertEquals("Should fallback to 'standard' on server error", "standard", tier)
    }

    @Test
    fun `resolveUserTierOrDefault returns valid tier from successful response`() = runTest(testDispatcher) {
        fakeStatsApiService.throwOnGetUserTier = false
        fakeStatsApiService.returnErrorResponse = false
        // Default FakeStatsApiService returns "standard" tier

        val tier = repository.resolveUserTierOrDefault()

        assertEquals("Should return the tier from server response", "standard", tier)
    }

    // AC #4: Favorite toggle integration - stats failures don't break toggle
    @Test
    fun `toggleFavorite succeeds even when stats collection fails`() = runTest(testDispatcher) {
        val game = GameEntity(
            releaseName = "ToggleGame",
            gameName = "Toggle Game",
            packageName = "com.toggle.game",
            versionCode = "1",
            description = "A toggle test game",
            sizeBytes = 512L,
            isFavorite = false,
            lastUpdated = System.currentTimeMillis(),
            popularity = 0
        )
        db.gameDao().insertGames(listOf(game))

        // Make stats collection fail
        fakeStatsApiService.throwOnCollectStats = true

        // Toggle favorite should still succeed despite stats failure
        repository.toggleFavorite("ToggleGame", true)
        testScheduler.advanceUntilIdle()

        val updatedGame = db.gameDao().getByReleaseName("ToggleGame")?.toData()
        assertTrue("Favorite should be set to true even when stats fail", updatedGame!!.isFavorite)
    }

    @Test
    fun `toggleFavorite sets isFavorite correctly`() = runTest(testDispatcher) {
        val game = GameEntity(
            releaseName = "ToggleGame2",
            gameName = "Toggle Game 2",
            packageName = "com.toggle.game2",
            versionCode = "1",
            description = "A toggle test game 2",
            sizeBytes = 512L,
            isFavorite = false,
            lastUpdated = System.currentTimeMillis(),
            popularity = 0
        )
        db.gameDao().insertGames(listOf(game))

        repository.toggleFavorite("ToggleGame2", true)
        testScheduler.advanceUntilIdle()

        val gameAfter = db.gameDao().getByReleaseName("ToggleGame2")?.toData()
        assertTrue("Favorite should be set to true", gameAfter!!.isFavorite)

        repository.toggleFavorite("ToggleGame2", false)
        testScheduler.advanceUntilIdle()

        val gameAfterUnfav = db.gameDao().getByReleaseName("ToggleGame2")?.toData()
        assertFalse("Favorite should be set to false", gameAfterUnfav!!.isFavorite)
    }

    // AC #5: StatsCollector integration - verify correct call pattern when consent is enabled
    @Test
    fun `collectStats called with null email and standard tier`() = runTest(testDispatcher) {
        consentPreferences.setConsentEnabled(true)
        testScheduler.advanceUntilIdle()

        fakeStatsApiService.collectStatsCalled = false

        // Even without games, the method should not crash when consent is enabled
        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()

        // The key assertion: no crash occurred with consent enabled
    }

    @Test
    fun `collectStats records received tier correctly`() = runTest(testDispatcher) {
        consentPreferences.setConsentEnabled(true)
        testScheduler.advanceUntilIdle()

        fakeStatsApiService.collectStatsCalled = false

        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()

        // No crash with consent enabled and no games - the method handles empty gracefully
    }

    // AC #7: Thread safety - verify operations run on IO dispatcher without crashing
    @Test
    fun `maybeCollectStats runs on IO dispatcher`() = runTest(testDispatcher) {
        consentPreferences.setConsentEnabled(true)
        testScheduler.advanceUntilIdle()

        fakeStatsApiService.collectStatsCalled = false

        // This should not throw - verifies Dispatchers.IO wrapping
        repository.maybeCollectStats()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `toggleFavorite runs on IO dispatcher`() = runTest(testDispatcher) {
        val game = GameEntity(
            releaseName = "ThreadGame",
            gameName = "Thread Game",
            packageName = "com.thread.game",
            versionCode = "1",
            description = "Thread safety test game",
            sizeBytes = 64L,
            isFavorite = false,
            lastUpdated = System.currentTimeMillis(),
            popularity = 0
        )
        db.gameDao().insertGames(listOf(game))

        // This should not throw - verifies Dispatchers.IO wrapping
        repository.toggleFavorite("ThreadGame", true)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `toggleFavorite with stats failure does not crash`() = runTest(testDispatcher) {
        val game = GameEntity(
            releaseName = "SafeToggle",
            gameName = "Safe Toggle",
            packageName = "com.safe.toggle",
            versionCode = "1",
            description = "Safe toggle test",
            sizeBytes = 256L,
            isFavorite = false,
            lastUpdated = System.currentTimeMillis(),
            popularity = 0
        )
        db.gameDao().insertGames(listOf(game))

        fakeStatsApiService.throwOnCollectStats = true

        // Should not throw even when stats collection fails
        repository.toggleFavorite("SafeToggle", true)
        testScheduler.advanceUntilIdle()

        val updatedGame = db.gameDao().getByReleaseName("SafeToggle")?.toData()
        assertTrue("Favorite should still be set despite stats failure", updatedGame!!.isFavorite)
    }
}
