package com.vrpirates.rookieonquest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallHistoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: InstallHistoryDao
    private lateinit var gameDao: GameDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.installHistoryDao()
        gameDao = database.gameDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertTestGame(releaseName: String) {
        gameDao.insertGames(listOf(
            GameEntity(
                releaseName = releaseName,
                gameName = "Test Game",
                packageName = "com.test.game",
                versionCode = "1"
            )
        ))
    }

    @Test
    fun insertAndRetrieve_returnsCorrectEntity() = runTest {
        val releaseName = "TestGame-v1.0"
        insertTestGame(releaseName)
        
        val entity = InstallHistoryEntity(
            releaseName = releaseName,
            gameName = "Test Game",
            packageName = "com.test.game",
            installedAt = 1000L,
            downloadDurationMs = 5000L,
            fileSizeBytes = 1000000L,
            status = InstallStatus.COMPLETED
        )
        dao.insert(entity)

        val result = dao.getAllFlow().first()
        assertEquals(1, result.size)
        assertEquals(entity.releaseName, result[0].releaseName)
        assertEquals(entity.status, result[0].status)
    }

    @Test
    fun searchAndFilter_worksCorrectly() = runTest {
        insertTestGame("Game1")
        insertTestGame("Game2")
        
        val e1 = InstallHistoryEntity(
            releaseName = "Game1",
            gameName = "Action Pack",
            packageName = "com.action",
            installedAt = 1000L,
            downloadDurationMs = 5000L,
            fileSizeBytes = 1000000L,
            status = InstallStatus.COMPLETED
        )
        val e2 = InstallHistoryEntity(
            releaseName = "Game2",
            gameName = "Puzzle Fun",
            packageName = "com.puzzle",
            installedAt = 2000L,
            downloadDurationMs = 3000L,
            fileSizeBytes = 500000L,
            status = InstallStatus.FAILED
        )
        dao.insert(e1)
        dao.insert(e2)

        // Test search
        val searchResult = dao.searchAndFilterFlow("Action", null).first()
        assertEquals(1, searchResult.size)
        assertEquals("Game1", searchResult[0].releaseName)

        // Test status filter
        val filterResult = dao.searchAndFilterFlow(null, InstallStatus.FAILED).first()
        assertEquals(1, filterResult.size)
        assertEquals("Game2", filterResult[0].releaseName)

        // Test search + filter
        val bothResult = dao.searchAndFilterFlow("Puzzle", InstallStatus.FAILED).first()
        assertEquals(1, bothResult.size)
        
        val noneResult = dao.searchAndFilterFlow("Puzzle", InstallStatus.COMPLETED).first()
        assertEquals(0, noneResult.size)
    }

    @Test
    fun deleteById_removesCorrectEntry() = runTest {
        insertTestGame("Game1")
        val e = InstallHistoryEntity(
            releaseName = "Game1",
            gameName = "Game 1",
            packageName = "com.game1",
            installedAt = 1000L,
            downloadDurationMs = 5000L,
            fileSizeBytes = 1000000L,
            status = InstallStatus.COMPLETED
        )
        dao.insert(e)
        
        val inserted = dao.getAllFlow().first()[0]
        dao.deleteById(inserted.id)
        
        val result = dao.getAllFlow().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun deleteAll_works() = runTest {
        insertTestGame("Game1")
        insertTestGame("Game2")
        dao.insert(InstallHistoryEntity(releaseName = "Game1", gameName = "G1", packageName = "p1", installedAt = 1, downloadDurationMs = 1, fileSizeBytes = 1, status = InstallStatus.COMPLETED))
        dao.insert(InstallHistoryEntity(releaseName = "Game2", gameName = "G2", packageName = "p2", installedAt = 2, downloadDurationMs = 2, fileSizeBytes = 2, status = InstallStatus.COMPLETED))
        
        assertEquals(2, dao.getAllFlow().first().size)
        dao.deleteAll()
        assertEquals(0, dao.getAllFlow().first().size)
    }

    @Test
    fun archiveTask_directTesting_worksCorrectly() = runTest {
        val releaseName = "ArchiveTest"
        val createdAt = 1000L
        val downloadStartedAt = 1500L
        val totalBytes = 5000L
        
        insertTestGame(releaseName)
        
        val queuedInstallDao = database.queuedInstallDao()
        val queueEntry = QueuedInstallEntity(
            releaseName = releaseName,
            status = InstallStatus.DOWNLOADING,
            progress = 0.5f,
            downloadedBytes = 2500L,
            totalBytes = totalBytes,
            queuePosition = 0,
            createdAt = createdAt,
            lastUpdatedAt = 1600L,
            downloadStartedAt = downloadStartedAt
        )
        queuedInstallDao.insert(queueEntry)

        // Test using MainRepository directly (refactored to accept db)
        val repository = MainRepository(ApplicationProvider.getApplicationContext(), database)
        val success = repository.archiveTask(releaseName, InstallStatus.COMPLETED)
        
        assertTrue("archiveTask should return true", success)

        // Verify results
        val history = dao.getAllFlow().first()
        assertEquals(1, history.size)
        assertEquals(releaseName, history[0].releaseName)
        assertEquals(createdAt, history[0].createdAt)
        // Duration should be roughly (now - downloadStartedAt)
        assertTrue(history[0].downloadDurationMs >= 0)

        val queue = queuedInstallDao.getAll()
        assertTrue("Queue should be empty after archiving", queue.isEmpty())

        // Test duplicate prevention
        val repeatSuccess = repository.archiveTask(releaseName, InstallStatus.COMPLETED)
        // Should return false because it's no longer in the queue
        assertTrue("archiveTask should return false for non-existent task", !repeatSuccess)
        
        val historyAfterDuplicate = dao.getAllFlow().first()
        assertEquals(1, historyAfterDuplicate.size)
    }
}
