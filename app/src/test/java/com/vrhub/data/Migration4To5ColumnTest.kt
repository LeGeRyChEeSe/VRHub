package com.vrhub.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression test for the 4 -> 5 migration path.
 *
 * QueuedInstallEntity gained `downloadStartedAt` at schema v5 (story 1.9,
 * commit a645e0b) but MIGRATION_4_5 never added the column: a device sitting
 * at Room v4 produced a schema mismatch on upgrade and was silently wiped by
 * fallbackToDestructiveMigration instead of being migrated.
 *
 * The test drives the real migration against the v4 schema as exported
 * (app/schemas) and compares the resulting table against the entity-required
 * columns from the v5 export.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class Migration4To5ColumnTest {

    private val context = org.robolectric.RuntimeEnvironment.getApplication()
    private val schemasDir = resolveSchemasDir()

    private fun resolveSchemasDir(): File {
        val candidates = listOf(
            File("app/schemas/com.vrhub.data.AppDatabase"),
            File("schemas/com.vrhub.data.AppDatabase")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Room schema exports not found (expected app/schemas/com.vrhub.data.AppDatabase)")
    }

    private fun entityColumns(version: Int, tableName: String): Set<String> {
        val json = Gson().fromJson(File(schemasDir, "$version.json").readText(), JsonObject::class.java)
        val entity = json.getAsJsonObject("database").getAsJsonArray("entities")
            .map { it.asJsonObject }
            .firstOrNull { it.get("tableName").asString == tableName }
            ?: error("Entity $tableName not found in schema v$version")
        return entity.getAsJsonArray("fields").map { it.asJsonObject.get("columnName").asString }.toSet()
    }

    @Test
    fun `migration 4 to 5 produces the full entity column set for install_queue`() {
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-4-5-test")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val db = factory.create(configuration).writableDatabase

        // Recreate install_queue exactly as the exported v4 schema declares it.
        val ddl = Gson().fromJson(File(schemasDir, "4.json").readText(), JsonObject::class.java)
            .getAsJsonObject("database").getAsJsonArray("entities")
            .map { it.asJsonObject }
            .firstOrNull { it.get("tableName").asString == "install_queue" }
            ?: error("install_queue entity not found in schema v4")

        val columnDefs = ddl.getAsJsonArray("fields").map { field ->
            val f = field.asJsonObject
            val notNull = if (f.get("notNull").asBoolean) " NOT NULL" else ""
            val default = if (f.has("defaultValue") && !f.get("defaultValue").isJsonNull)
                " DEFAULT ${f.get("defaultValue").asString}" else ""
            "${f.get("columnName").asString} ${f.get("affinity").asString}$notNull$default"
        }
        db.execSQL("CREATE TABLE install_queue (${columnDefs.joinToString(", ")}, PRIMARY KEY (releaseName))")
        db.execSQL("INSERT INTO install_queue (releaseName, status, progress, queuePosition, createdAt, lastUpdatedAt, isDownloadOnly) VALUES ('g4', 'QUEUED', 0, 1, 1, 1, 0)")

        AppDatabase.MIGRATION_4_5.migrate(db)

        val actual = mutableListOf<String>()
        db.query("PRAGMA table_info(install_queue)").use { cursor ->
            while (cursor.moveToNext()) actual.add(cursor.getString(1))
        }

        val required = entityColumns(5, "install_queue")
        val missing = required - actual.toSet()
        assertEquals(
            "install_queue after 4 -> 5 must match the entity column set (schema v5); missing: $missing",
            emptySet<String>(),
            missing
        )

        db.query("SELECT releaseName FROM install_queue WHERE releaseName = 'g4'").use { cursor ->
            assertTrue("Queue row must survive the migration", cursor.moveToFirst())
        }

        db.close()
    }
}
