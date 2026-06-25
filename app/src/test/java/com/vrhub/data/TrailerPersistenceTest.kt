package com.vrhub.data

import androidx.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 11.2 — trailerUrl persistence (AC1/AC6).
 *
 * Verifies the new column round-trips through Room and that the dedicated DAO setter
 * (used by the per-game fallback in getGameRemoteInfo) updates it without touching other fields.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TrailerPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: GameDao

    @Before
    fun setUp() {
        val appContext = org.robolectric.RuntimeEnvironment.getApplication() as android.app.Application
        db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.gameDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleGame(release: String, trailer: String? = null) = GameEntity(
        releaseName = release,
        gameName = "Game $release",
        packageName = "com.example.$release",
        versionCode = "1",
        trailerUrl = trailer
    )

    @Test
    fun `trailerUrl round-trips through insert and read`() = runTest {
        val game = sampleGame("Beat Saber v1", trailer = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        dao.insertGames(listOf(game))

        val loaded = dao.getByReleaseName("Beat Saber v1")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", loaded?.trailerUrl)
    }

    @Test
    fun `trailerUrl defaults to null when not provided`() = runTest {
        dao.insertGames(listOf(sampleGame("No Trailer v1")))

        val loaded = dao.getByReleaseName("No Trailer v1")
        assertNull(loaded?.trailerUrl)
    }

    @Test
    fun `updateTrailer persists url without altering other metadata`() = runTest {
        val game = sampleGame("Pistol Whip v1").copy(
            description = "A rhythm shooter",
            screenshotUrlsJson = "https://host/a.jpg|https://host/b.jpg"
        )
        dao.insertGames(listOf(game))

        dao.updateTrailer("Pistol Whip v1", "https://youtu.be/dQw4w9WgXcQ")

        val loaded = dao.getByReleaseName("Pistol Whip v1")
        assertEquals("https://youtu.be/dQw4w9WgXcQ", loaded?.trailerUrl)
        // Other metadata must be untouched by the targeted update.
        assertEquals("A rhythm shooter", loaded?.description)
        assertEquals("https://host/a.jpg|https://host/b.jpg", loaded?.screenshotUrlsJson)
    }

    @Test
    fun `entity mappers carry trailerUrl in both directions`() = runTest {
        val data = sampleGame("Mapper v1", trailer = "https://youtu.be/dQw4w9WgXcQ").toData()
        assertEquals("https://youtu.be/dQw4w9WgXcQ", data.trailerUrl)

        val roundTripped = data.toEntity()
        assertEquals("https://youtu.be/dQw4w9WgXcQ", roundTripped.trailerUrl)
    }
}
