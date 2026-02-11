package com.vrpirates.rookieonquest.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate4To5() {
        // Create database with version 4
        var db = helper.createDatabase(TEST_DB, 4)
        
        // Insert test data into install_queue (v4 schema)
        db.execSQL("""
            INSERT INTO install_queue (releaseName, status, progress, downloadedBytes, totalBytes, queuePosition, createdAt, lastUpdatedAt, isDownloadOnly)
            VALUES ('test-game-v4', 'QUEUED', 0.0, 0, 1024, 1, 123456789, 123456789, 0)
        """.trimIndent())
        db.close()

        // Open and validate with version 5
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5)

        // Verify data in install_queue is preserved
        db.query("SELECT releaseName FROM install_queue WHERE releaseName = 'test-game-v4'").use { cursor ->
            assertTrue("Data in install_queue should be preserved after migration", cursor.moveToFirst())
        }

        // Test CASCADE DELETE (AC Review fix)
        // 1. Insert a game into games table (required for FK)
        db.execSQL("""
            INSERT INTO games (releaseName, gameName, packageName, versionCode)
            VALUES ('test-game-cascade', 'Test Game', 'com.test.game', '1')
        """.trimIndent())
        
        // 2. Insert history entry referencing the game
        db.execSQL("""
            INSERT INTO install_history (releaseName, gameName, packageName, installedAt, downloadDurationMs, fileSizeBytes, status, createdAt)
            VALUES ('test-game-cascade', 'Test Game', 'com.test.game', 1600000000000, 5000, 1024, 'COMPLETED', 1600000000000)
        """.trimIndent())
        
        // 3. Delete the game
        db.execSQL("DELETE FROM games WHERE releaseName = 'test-game-cascade'")
        
        // 4. Verify history entry is automatically removed via CASCADE DELETE
        db.query("SELECT * FROM install_history WHERE releaseName = 'test-game-cascade'").use { cursor ->
            assertFalse("History entry should be removed by CASCADE DELETE when parent game is deleted", cursor.moveToFirst())
        }

        // Verify table exists
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='install_history'").use { cursor ->
            assertTrue("Table install_history should exist", cursor.moveToFirst())
        }

        // Verify indexes exist and check uniqueness
        db.query("PRAGMA index_list('install_history')").use { cursor ->
            val indices = mutableMapOf<String, Boolean>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val isUnique = cursor.getInt(2) == 1
                indices[name] = isUnique
            }
            assertTrue("Index index_install_history_releaseName should exist", indices.containsKey("index_install_history_releaseName"))
            assertTrue("Index index_install_history_status should exist", indices.containsKey("index_install_history_status"))
            assertTrue("Index index_install_history_installedAt should exist", indices.containsKey("index_install_history_installedAt"))
            assertTrue("Index index_install_history_releaseName_createdAt should exist", indices.containsKey("index_install_history_releaseName_createdAt"))
            assertTrue("Index index_install_history_releaseName_createdAt should be UNIQUE", indices["index_install_history_releaseName_createdAt"] == true)
        }

        // Verify columns exist
        db.query("PRAGMA table_info(install_history)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(1))
            }
            assertTrue("Column id should exist", columns.contains("id"))
            assertTrue("Column releaseName should exist", columns.contains("releaseName"))
            assertTrue("Column gameName should exist", columns.contains("gameName"))
            assertTrue("Column packageName should exist", columns.contains("packageName"))
            assertTrue("Column status should exist", columns.contains("status"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create database with version 2 (legacy)
        var db = helper.createDatabase(TEST_DB, 2)
        db.close()

        // Run all migrations to version 5
        db = helper.runMigrationsAndValidate(
            TEST_DB, 
            5, 
            true, 
            AppDatabase.MIGRATION_2_3, 
            AppDatabase.MIGRATION_3_4, 
            AppDatabase.MIGRATION_4_5
        )
    }
}
