package com.vrhub.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for SQLITE_MAX_VARIABLE_NUMBER (999 on Android < 12).
 *
 * GameDao call sites must chunk lists that become `IN (:names)` clauses.
 * `getGamesByReleaseNames` was violating the documented contract when the
 * queue exceeded the chunk size (single unbounded IN call).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GameDaoChunkingTest {

    private val appContext = org.robolectric.RuntimeEnvironment.getApplication() as android.app.Application

    @Test
    fun `getGamesByReleaseNames chunks calls above the SQL variable limit`() = runBlocking {
        val dao = mockk<GameDao>()
        val db = mockk<AppDatabase>()
        every { db.gameDao() } returns dao

        val chunks = mutableListOf<List<String>>()
        val slot = slot<List<String>>()
        coEvery { dao.getByReleaseNames(capture(slot)) } answers {
            chunks.add(slot.captured)
            slot.captured.map {
                GameEntity(
                    releaseName = it,
                    gameName = "game",
                    packageName = "com.game",
                    versionCode = "1"
                )
            }
        }

        val repository = MainRepository(appContext, db)
        val names = (1..600).map { "release-$it" }

        val result = repository.getGamesByReleaseNames(names)

        assertEquals(600, result.size)
        assertTrue(
            "Each IN(...) call must stay under SQLITE_MAX_VARIABLE_NUMBER (chunk size 500 per GameDao contract); got calls: ${chunks.map { it.size }}",
            chunks.all { it.size <= 500 }
        )
        assertEquals(2, chunks.size)
        assertEquals(500, chunks[0].size)
        assertEquals(100, chunks[1].size)
    }

    @Test
    fun `getGamesByReleaseNames returns empty map for empty list without touching dao`() = runBlocking {
        val dao = mockk<GameDao>(relaxed = true)
        val db = mockk<AppDatabase>()
        every { db.gameDao() } returns dao

        val repository = MainRepository(appContext, db)
        val result = repository.getGamesByReleaseNames(emptyList())

        assertEquals(emptyMap<String, GameData>(), result)
    }
}
