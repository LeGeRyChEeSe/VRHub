package com.vrhub.data

import com.vrhub.network.resolveTier
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TierResolver utility and UserTierResponse handling.
 * Tests Story 1.3 AC #1-5: Tier resolution with fallback to "standard"
 */
class TierResolverTest {

    @Test
    fun `resolveTier returns standard when tier is null`() {
        assertEquals("standard", resolveTier(null))
    }

    @Test
    fun `resolveTier returns standard when tier is empty`() {
        assertEquals("standard", resolveTier(""))
    }

    @Test
    fun `resolveTier returns standard when tier is blank`() {
        assertEquals("standard", resolveTier("   "))
    }

    @Test
    fun `resolveTier returns supporter when tier is supporter`() {
        assertEquals("supporter", resolveTier("supporter"))
    }

    @Test
    fun `resolveTier returns lucky when tier is lucky`() {
        assertEquals("lucky", resolveTier("lucky"))
    }

    @Test
    fun `resolveTier returns standard for unknown tier`() {
        assertEquals("standard", resolveTier("unknown"))
    }

    @Test
    fun `resolveTier returns standard when tier is standard`() {
        assertEquals("standard", resolveTier("standard"))
    }
}