package com.vrpirates.rookieonquest.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for SQL LIKE escaping logic in MainViewModel (Story 1.9).
 * 
 * These tests verify that special characters in search queries are correctly escaped
 * to prevent SQL injection or unexpected pattern matching in Room/SQLite.
 * 
 * ESCAPE character is ''.
 */
class HistorySearchEscapingTest {

    /**
     * Replicates the escaping logic from MainViewModel.kt:561-572.
     */
    private fun escapeQuery(query: String): String {
        val validatedQuery = if (query.length > 100) query.take(100) else query
        // Escape LIKE special characters (%, _, \) using \ as escape character
        // Order matters: \ must be replaced first
        return validatedQuery
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    @Test
    fun `test basic escaping`() {
        assertEquals("game\\%1", escapeQuery("game%1"))
        assertEquals("my\\_game", escapeQuery("my_game"))
        assertEquals("folder\\\\file", escapeQuery("folder\\file"))
    }

    @Test
    fun `test mixed special characters`() {
        assertEquals("test\\%\\_\\\\stuff", escapeQuery("test%_\\stuff"))
    }

    @Test
    fun `test multiple identical special characters`() {
        assertEquals("100\\% pure \\% match", escapeQuery("100% pure % match"))
        assertEquals("\\_\\_private\\_\\_", escapeQuery("__private__"))
        assertEquals("\\\\\\\\network\\\\path", escapeQuery("\\\\network\\path"))
    }

    @Test
    fun `test no special characters`() {
        assertEquals("Beat Saber", escapeQuery("Beat Saber"))
        assertEquals("v1.2.3", escapeQuery("v1.2.3"))
    }

    @Test
    fun `test empty and short queries`() {
        assertEquals("", escapeQuery(""))
        assertEquals("a", escapeQuery("a"))
        assertEquals("\\%", escapeQuery("%"))
    }

    @Test
    fun `test length limit validation`() {
        val longQuery = "a".repeat(110)
        val result = escapeQuery(longQuery)
        assertEquals(100, result.length)
        assertEquals("a".repeat(100), result)
    }

    @Test
    fun `test length limit with escaping`() {
        // 50 percent signs -> 100 characters after escaping
        val query = "%".repeat(50)
        val result = escapeQuery(query)
        assertEquals(100, result.length)
        assertEquals("\\%".repeat(50), result)
        
        // 100 percent signs -> truncated to 100 before escaping -> 200 characters after escaping
        val longQuery = "%".repeat(150)
        val resultLong = escapeQuery(longQuery)
        assertEquals(200, resultLong.length)
        assertEquals("\\%".repeat(100), resultLong)
    }

    @Test
    fun `test backslash sequences (SQL injection protection)`() {
        // Testing sequences mentioned in review finding
        assertEquals("\\\\\\%", escapeQuery("\\%"))
        assertEquals("\\\\\\\\", escapeQuery("\\\\"))
        assertEquals("\\\\\\_\\%", escapeQuery("\\_%"))
    }
}
