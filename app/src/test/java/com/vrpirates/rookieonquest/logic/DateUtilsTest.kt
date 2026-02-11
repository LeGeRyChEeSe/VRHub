package com.vrpirates.rookieonquest.logic

import android.content.Context
import com.vrpirates.rookieonquest.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DateUtilsTest {
    private val context = mockk<Context>()

    @Before
    fun setup() {
        every { context.getString(R.string.time_never) } returns "Never"
        every { context.getString(R.string.time_just_now) } returns "Just now"
        every { context.getString(R.string.time_minutes_ago, any()) } answers { 
            val arg = if (it.invocation.args[1] is Array<*>) (it.invocation.args[1] as Array<*>)[0] else it.invocation.args[1]
            "${arg}m ago" 
        }
        every { context.getString(R.string.time_hours_ago, any()) } answers { 
            val arg = if (it.invocation.args[1] is Array<*>) (it.invocation.args[1] as Array<*>)[0] else it.invocation.args[1]
            "${arg}h ago" 
        }
        every { context.getString(R.string.time_days_ago, any()) } answers { 
            val arg = if (it.invocation.args[1] is Array<*>) (it.invocation.args[1] as Array<*>)[0] else it.invocation.args[1]
            "${arg}d ago" 
        }
    }

    @Test
    fun `formatTimeAgo returns Just now for recent timestamps`() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", DateUtils.formatTimeAgo(context, now - 30000)) // 30s ago
    }

    @Test
    fun `formatTimeAgo returns minutes for intervals under 1 hour`() {
        val now = System.currentTimeMillis()
        assertEquals("5m ago", DateUtils.formatTimeAgo(context, now - 5 * 60000))
        assertEquals("59m ago", DateUtils.formatTimeAgo(context, now - 59 * 60000))
    }

    @Test
    fun `formatTimeAgo returns hours for intervals under 1 day`() {
        val now = System.currentTimeMillis()
        assertEquals("2h ago", DateUtils.formatTimeAgo(context, now - 2 * 3600000))
        assertEquals("23h ago", DateUtils.formatTimeAgo(context, now - 23 * 3600000))
    }

    @Test
    fun `formatTimeAgo returns days for intervals over 1 day`() {
        val now = System.currentTimeMillis()
        assertEquals("1d ago", DateUtils.formatTimeAgo(context, now - 25 * 3600000))
        assertEquals("10d ago", DateUtils.formatTimeAgo(context, now - 10 * 86400000L))
    }

    @Test
    fun `formatTimeAgo returns Never for zero or negative timestamps`() {
        assertEquals("Never", DateUtils.formatTimeAgo(context, 0))
        assertEquals("Never", DateUtils.formatTimeAgo(context, -1000))
    }

    @Test
    fun `formatTimeAgo handles future timestamps as Just now`() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", DateUtils.formatTimeAgo(context, now + 10000))
    }
}
