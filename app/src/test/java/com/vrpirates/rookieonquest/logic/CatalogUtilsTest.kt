package com.vrpirates.rookieonquest.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogUtilsTest {

    @Test
    fun `isUpdateAvailable returns true when dates differ and count is positive`() {
        val available = CatalogUtils.isUpdateAvailable(
            remoteLastModified = "new_date",
            savedLastModified = "old_date",
            currentItemCount = 10
        )
        assertTrue(available)
    }

    @Test
    fun `isUpdateAvailable returns false when dates are equal`() {
        val available = CatalogUtils.isUpdateAvailable(
            remoteLastModified = "same_date",
            savedLastModified = "same_date",
            currentItemCount = 10
        )
        assertFalse(available)
    }

    @Test
    fun `isUpdateAvailable returns false when current count is zero`() {
        // Should be false because first sync is not an "update" for the banner
        val available = CatalogUtils.isUpdateAvailable(
            remoteLastModified = "new_date",
            savedLastModified = "old_date",
            currentItemCount = 0
        )
        assertFalse(available)
    }

    @Test
    fun `isUpdateAvailable returns false when remote date is null`() {
        val available = CatalogUtils.isUpdateAvailable(
            remoteLastModified = null,
            savedLastModified = "old_date",
            currentItemCount = 10
        )
        assertFalse(available)
    }

    @Test
    fun `isUpdateAvailable returns true when saved date is null but remote is present and count is positive`() {
        val available = CatalogUtils.isUpdateAvailable(
            remoteLastModified = "new_date",
            savedLastModified = null,
            currentItemCount = 10
        )
        assertTrue(available)
    }
}
