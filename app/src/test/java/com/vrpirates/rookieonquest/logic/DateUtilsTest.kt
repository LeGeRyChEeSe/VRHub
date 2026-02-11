package com.vrpirates.rookieonquest.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    @Test
    fun `formatTimeAgo returns Just now for recent timestamps`() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", DateUtils.formatTimeAgo(now - 30000)) // 30s ago
    }

    @Test
    fun `formatTimeAgo returns minutes for intervals under 1 hour`() {
        val now = System.currentTimeMillis()
        assertEquals("5m ago", DateUtils.formatTimeAgo(now - 5 * 60000))
        assertEquals("59m ago", DateUtils.formatTimeAgo(now - 59 * 60000))
    }

    @Test
    fun `formatTimeAgo returns hours for intervals under 1 day`() {
        val now = System.currentTimeMillis()
        assertEquals("2h ago", DateUtils.formatTimeAgo(now - 2 * 3600000))
        assertEquals("23h ago", DateUtils.formatTimeAgo(now - 23 * 3600000))
    }

    @Test
    fun `formatTimeAgo returns days for intervals over 1 day`() {
        val now = System.currentTimeMillis()
        assertEquals("1d ago", DateUtils.formatTimeAgo(now - 25 * 3600000))
        assertEquals("10d ago", DateUtils.formatTimeAgo(now - 10 * 86400000L))
    }

    @Test
    fun `formatTimeAgo returns Never for zero or negative timestamps`() {
        assertEquals("Never", DateUtils.formatTimeAgo(0))
        assertEquals("Never", DateUtils.formatTimeAgo(-1000))
    }

    @Test
    fun `formatTimeAgo handles future timestamps as Just now`() {
        // Current implementation: diff < 60000 -> "Just now"
        // If timestamp is in future, diff is negative, so it should be < 60000
        val now = System.currentTimeMillis()
        assertEquals("Just now", DateUtils.formatTimeAgo(now + 10000))
    }
}
