package com.vrhub.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Story 11.2 — unit tests for YouTube trailer URL parsing.
 *
 * Pure JVM tests (no Android/Robolectric): TrailerUtils has no platform dependencies.
 */
class TrailerUtilsTest {

    // --- watch URLs -----------------------------------------------------------------------

    @Test
    fun `extracts id from standard watch url`() {
        assertEquals("dQw4w9WgXcQ", TrailerUtils.extractYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extracts id from watch url without www`() {
        assertEquals("dQw4w9WgXcQ", TrailerUtils.extractYouTubeId("https://youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extracts id from watch url with extra params`() {
        assertEquals(
            "dQw4w9WgXcQ",
            TrailerUtils.extractYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s&list=PLabc")
        )
    }

    @Test
    fun `extracts id when v param is not first`() {
        assertEquals(
            "dQw4w9WgXcQ",
            TrailerUtils.extractYouTubeId("https://www.youtube.com/watch?list=PLabc&v=dQw4w9WgXcQ")
        )
    }

    // --- youtu.be short URLs --------------------------------------------------------------

    @Test
    fun `extracts id from youtu_be short url`() {
        assertEquals("dQw4w9WgXcQ", TrailerUtils.extractYouTubeId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `extracts id from youtu_be with timestamp param`() {
        assertEquals("dQw4w9WgXcQ", TrailerUtils.extractYouTubeId("https://youtu.be/dQw4w9WgXcQ?t=42"))
    }

    // --- embed / shorts -------------------------------------------------------------------

    @Test
    fun `extracts id from embed url`() {
        assertEquals("dQw4w9WgXcQ", TrailerUtils.extractYouTubeId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `extracts id from shorts url`() {
        assertEquals("dQw4w9WgXcQ", TrailerUtils.extractYouTubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun `extracts id with underscores and hyphens`() {
        assertEquals("a_b-C1d2E3f", TrailerUtils.extractYouTubeId("https://www.youtube.com/watch?v=a_b-C1d2E3f"))
    }

    // --- invalid / edge cases -------------------------------------------------------------

    @Test
    fun `returns null for null input`() {
        assertNull(TrailerUtils.extractYouTubeId(null))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(TrailerUtils.extractYouTubeId("   "))
    }

    @Test
    fun `returns null for non youtube url`() {
        assertNull(TrailerUtils.extractYouTubeId("https://vimeo.com/123456789"))
    }

    @Test
    fun `returns null for too short id`() {
        assertNull(TrailerUtils.extractYouTubeId("https://www.youtube.com/watch?v=short"))
    }

    @Test
    fun `returns null for garbage string`() {
        assertNull(TrailerUtils.extractYouTubeId("not a url at all"))
    }

    // --- isValidVideoId -------------------------------------------------------------------

    @Test
    fun `isValidVideoId accepts exactly 11 url-safe chars`() {
        assertTrue(TrailerUtils.isValidVideoId("dQw4w9WgXcQ"))
        assertTrue(TrailerUtils.isValidVideoId("a_b-C1d2E3f"))
    }

    @Test
    fun `isValidVideoId rejects wrong length and bad chars`() {
        assertFalse(TrailerUtils.isValidVideoId(null))
        assertFalse(TrailerUtils.isValidVideoId(""))
        assertFalse(TrailerUtils.isValidVideoId("short"))
        assertFalse(TrailerUtils.isValidVideoId("dQw4w9WgXcQextra"))
        assertFalse(TrailerUtils.isValidVideoId("dQw4w9WgX!Q"))
    }
}
