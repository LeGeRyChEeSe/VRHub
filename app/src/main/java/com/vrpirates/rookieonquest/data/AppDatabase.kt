package com.vrpirates.rookieonquest.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GameEntity::class, QueuedInstallEntity::class, InstallHistoryEntity::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun queuedInstallDao(): QueuedInstallDao
    abstract fun installHistoryDao(): InstallHistoryDao

    companion object {
        private const val TAG = "AppDatabase"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    Log.i(TAG, "Starting migration 2 -> 3: Adding install_queue table")

                    // Create new install_queue table
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS install_queue (
                            releaseName TEXT PRIMARY KEY NOT NULL,
                            status TEXT NOT NULL,
                            progress REAL NOT NULL,
                            downloadedBytes INTEGER,
                            totalBytes INTEGER,
                            queuePosition INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            lastUpdatedAt INTEGER NOT NULL,
                            downloadStartedAt INTEGER
                        )
                    """.trimIndent())

                    // Create indexes for performance (removing redundant status-only index)
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_install_queue_queuePosition ON install_queue(queuePosition)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_install_queue_status_queuePosition ON install_queue(status, queuePosition)")

                    Log.i(TAG, "Migration 2 -> 3 completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Migration 2 -> 3 FAILED - User data (favorites) may be lost on fallback", e)
                    throw e // Re-throw to trigger fallback
                }
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    Log.i(TAG, "Starting migration 3 -> 4: Adding isDownloadOnly column to install_queue")

                    // Add isDownloadOnly column with default value false (0)
                    database.execSQL("ALTER TABLE install_queue ADD COLUMN isDownloadOnly INTEGER NOT NULL DEFAULT 0")

                    Log.i(TAG, "Migration 3 -> 4 completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Migration 3 -> 4 FAILED - Queue data may be lost on fallback", e)
                    throw e // Re-throw to trigger fallback
                }
            }
        }

        /**
         * Direct migration from v2 to v4 for multi-version jumps.
         * Creates the complete install_queue table schema in one step,
         * avoiding unnecessary ALTER TABLE operations.
         */
        internal val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    Log.i(TAG, "Starting migration 2 -> 4: Adding install_queue table with complete schema")

                    // Create install_queue table with ALL columns including isDownloadOnly
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS install_queue (
                            releaseName TEXT PRIMARY KEY NOT NULL,
                            status TEXT NOT NULL,
                            progress REAL NOT NULL,
                            downloadedBytes INTEGER,
                            totalBytes INTEGER,
                            queuePosition INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            lastUpdatedAt INTEGER NOT NULL,
                            downloadStartedAt INTEGER,
                            isDownloadOnly INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())

                    // Create indexes for performance
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_install_queue_queuePosition ON install_queue(queuePosition)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_install_queue_status_queuePosition ON install_queue(status, queuePosition)")

                    Log.i(TAG, "Migration 2 -> 4 completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Migration 2 -> 4 FAILED - User data (favorites) may be lost on fallback", e)
                    throw e // Re-throw to trigger fallback
                }
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    Log.i(TAG, "Starting migration 4 -> 5: Adding install_history table")

                    // Create install_history table
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS install_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            releaseName TEXT NOT NULL,
                            gameName TEXT NOT NULL,
                            packageName TEXT NOT NULL,
                            installedAt INTEGER NOT NULL,
                            downloadDurationMs INTEGER NOT NULL,
                            fileSizeBytes INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            errorMessage TEXT,
                            createdAt INTEGER NOT NULL,
                            FOREIGN KEY (releaseName) REFERENCES games(releaseName) ON DELETE CASCADE
                        )
                    """.trimIndent())

                    // Create indexes for performance
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_install_history_releaseName ON install_history(releaseName)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_install_history_status ON install_history(status)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_install_history_installedAt ON install_history(installedAt)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_install_history_releaseName_createdAt ON install_history(releaseName, createdAt)")

                    Log.i(TAG, "Migration 4 -> 5 completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Migration 4 -> 5 FAILED", e)
                    throw e
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rookie_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_2_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
