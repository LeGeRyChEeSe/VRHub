package com.vrhub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceLayoutTest {

    @Test
    fun `ROUNDED card corner matches the original hardcoded 12dp radius`() {
        assertEquals(RoundedCornerShape(12.dp), cardCornerShape(CardCorner.ROUNDED))
    }

    @Test
    fun `every CardCorner preset maps to a distinct shape`() {
        val shapes = CardCorner.entries.map { cardCornerShape(it) }.toSet()
        assertEquals(CardCorner.entries.size, shapes.size)
    }

    @Test
    fun `COMFORTABLE spacing factor is 1x, matching original hardcoded paddings`() {
        assertEquals(1f, Density.COMFORTABLE.spacingFactor())
    }

    @Test
    fun `COMPACT spacing factor is smaller than COMFORTABLE`() {
        assert(Density.COMPACT.spacingFactor() < Density.COMFORTABLE.spacingFactor())
    }
}
