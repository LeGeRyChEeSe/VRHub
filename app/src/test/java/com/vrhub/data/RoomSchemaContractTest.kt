package com.vrhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomSchemaContractTest {
    private val schemaDir = resolveSchemaDir()

    private fun resolveSchemaDir(): File {
        val fromModule = File("schemas/com.vrhub.data.AppDatabase")
        val fromRoot = File("app/schemas/com.vrhub.data.AppDatabase")
        return if (fromModule.isDirectory) fromModule else fromRoot
    }

    private val historicalVersions = listOf(2, 4, 5, 6, 7)

    private fun versionFieldOf(file: File): Int? {
        val text = file.readText()
        val match = Regex("\"version\"\\s*:\\s*(\\d+)").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    @Test
    fun schemaFilesExistForAllVersions() {
        for (version in historicalVersions) {
            val file = File(schemaDir, "$version.json")
            assertTrue("Schema file missing: ${file.path} (expected app/schemas/com.vrhub.data.AppDatabase/$version.json)", file.isFile)
        }
    }

    @Test
    fun schemaVersionMatchesFilename() {
        for (version in historicalVersions) {
            val file = File(schemaDir, "$version.json")
            assertTrue("Schema file missing: ${file.path}", file.isFile)
            val actualVersion = versionFieldOf(file)
            assertEquals("Version field in ${file.name} must match its filename", version, actualVersion)
        }
    }
}
