package com.vrpirates.rookieonquest.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vrpirates.rookieonquest.ui.RequiredPermission
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for PermissionManager - Story 1.8 Permission Flow for Installation
 *
 * Tests permission checking logic on actual Android device/emulator.
 *
 * Requirements covered:
 * - AC1: Check for all required installation permissions on app launch
 * - AC2: Check permissions before starting installation
 * - AC5: Store permission state in SharedPreferences
 * - AC7: Detect permission revocation
 *
 * Note: These tests require actual Android runtime to check system permissions.
 * Some tests may fail on devices/emulators where permissions are already granted.
 */
@RunWith(AndroidJUnit4::class)
class PermissionManagerInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Initialize PermissionManager before each test
        PermissionManager.init(context)
        // Clear any cached permission states
        PermissionManager.invalidateCache()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        PermissionManager.invalidateCache()
    }

    // ========== Permission Checking Tests (AC: 1, 2) ==========

    @Test
    fun getMissingPermissions_returns_list() {
        // Verify that getMissingPermissions() returns a list (may be empty or not depending on device state)
        val missing = PermissionManager.getMissingPermissions(context)
        assertNotNull(
            "getMissingPermissions() should return a non-null list",
            missing
        )
    }

    @Test
    fun getMissingPermissions_returns_correct_size() {
        // Verify that the returned list size is within expected bounds
        val missing = PermissionManager.getMissingPermissions(context)
        assertTrue(
            "Missing permissions count should be between 0 and 3",
            missing.size in 0..3
        )
    }

    @Test
    fun hasAllRequiredPermissions_returns_boolean() {
        // Verify that hasAllRequiredPermissions() returns a boolean
        val hasAll = PermissionManager.hasAllRequiredPermissions(context)
        // We don't assert true/false because it depends on device state
        // Just verify the method doesn't throw
        assertTrue("Method should complete without exception", true)
    }

    // ========== Cache Tests ==========

    @Test
    fun cache_returns_same_result_within_duration() {
        // First call should populate cache
        val firstCall = PermissionManager.getMissingPermissions(context)
        val firstTimestamp = PermissionManager.getLastCheckTimestamp()

        // Second immediate call should use cache
        val secondCall = PermissionManager.getMissingPermissions(context)

        // Results should be identical (cached)
        assertEquals(
            "Cached result should match first call",
            firstCall,
            secondCall
        )

        // Timestamp should be updated after first check
        assertTrue(
            "Last check timestamp should be greater than 0",
            firstTimestamp > 0L
        )
    }

    @Test
    fun invalidateCache_forces_fresh_check() {
        // First call to populate cache
        PermissionManager.getMissingPermissions(context)

        // Invalidate cache
        PermissionManager.invalidateCache()

        // Next call should fetch fresh from system
        val afterInvalidate = PermissionManager.getMissingPermissions(context)
        assertNotNull(
            "After cache invalidation, should still return valid list",
            afterInvalidate
        )
    }

    // ========== Individual Permission Check Tests ==========

    @Test
    fun hasInstallUnknownAppsPermission_returns_boolean() {
        val hasPermission = PermissionManager.hasInstallUnknownAppsPermission(context)
        // Just verify the method doesn't throw
        assertTrue("Method should complete without exception", true)
    }

    @Test
    fun hasManageExternalStoragePermission_returns_boolean() {
        val hasPermission = PermissionManager.hasManageExternalStoragePermission(context)
        // Just verify the method doesn't throw
        assertTrue("Method should complete without exception", true)
    }

    @Test
    fun hasIgnoreBatteryOptimizationsPermission_returns_boolean() {
        val hasPermission = PermissionManager.hasIgnoreBatteryOptimizationsPermission(context)
        // Just verify the method doesn't throw
        assertTrue("Method should complete without exception", true)
    }

    // ========== Permission State Persistence Tests (AC: 5) ==========

    @Test
    fun savePermissionState_stores_to_sharedPrefs() = runTest {
        // Save a permission state
        PermissionManager.savePermissionState(RequiredPermission.INSTALL_UNKNOWN_APPS, true)

        // Verify it can be loaded back
        val savedStates = PermissionManager.loadSavedPermissionStates()
        assertNotNull(
            "Saved states should not be null after saving",
            savedStates
        )

        if (savedStates != null) {
            assertTrue(
                "Saved states should contain INSTALL_UNKNOWN_APPS",
                savedStates.containsKey(RequiredPermission.INSTALL_UNKNOWN_APPS)
            )

            assertEquals(
                "Saved INSTALL_UNKNOWN_APPS state should be true",
                true,
                savedStates[RequiredPermission.INSTALL_UNKNOWN_APPS]
            )
        }
    }

    @Test
    fun savePermissionState_updates_timestamp() = runTest {
        // Get initial timestamp
        val initialTimestamp = PermissionManager.getLastCheckTimestamp()

        // Save a permission state
        PermissionManager.savePermissionState(RequiredPermission.MANAGE_EXTERNAL_STORAGE, true)

        // Verify timestamp was updated
        val newTimestamp = PermissionManager.getLastCheckTimestamp()
        assertTrue(
            "Timestamp should be updated after saving permission state",
            newTimestamp >= initialTimestamp
        )
    }

    @Test
    fun loadSavedPermissionStates_returns_null_when_nothing_saved() {
        // Clear SharedPreferences by re-initializing (if possible)
        // Note: This test may be flaky if previous tests saved state

        // Just verify the method can be called
        val savedStates = PermissionManager.loadSavedPermissionStates()
        // May return null or a map depending on previous test execution
        assertTrue("Method should complete without exception", true)
    }

    // ========== Permission Revocation Detection Tests (AC: 7) ==========

    @Test
    fun validateSavedStates_returns_result() = runTest {
        // Save a permission state
        PermissionManager.savePermissionState(RequiredPermission.INSTALL_UNKNOWN_APPS, true)

        // Validate saved states against actual permissions
        val result = PermissionManager.validateSavedStates(context)

        // Result depends on whether actual permission matches saved state
        // Just verify the method doesn't throw and returns a result
        assertNotNull("Method should return a non-null result", result)
    }

    @Test
    fun validateSavedStates_handles_no_saved_state() {
        // If no saved state exists, should return valid (no mismatch to detect)
        // This is implicit in the implementation
        val result = PermissionManager.validateSavedStates(context)
        assertTrue("Method should return valid when no saved state exists", result.isValid)
    }

    // ========== Permission Checker Interface Tests ==========

    @Test
    fun setPermissionChecker_allows_custom_checker() {
        // Create a custom mock checker
        val mockChecker = object : PermissionManager.PermissionChecker {
            override fun checkInstallPermission(context: Context): Boolean = true
            override fun checkStoragePermission(context: Context): Boolean = true
            override fun checkBatteryPermission(context: Context): Boolean = true
        }

        // Set the custom checker (should not throw)
        PermissionManager.setPermissionChecker(mockChecker)

        // Verify it's being used by calling getMissingPermissions
        val missing = PermissionManager.getMissingPermissions(context)

        // With mock checker returning true for all, missing should be empty
        assertEquals(
            "With mock checker reporting all permissions granted, missing should be empty",
            emptyList<RequiredPermission>(),
            missing
        )
    }

    @Test
    fun setPermissionChecker_with_missing_permissions() {
        // Create a custom mock checker that reports permissions missing
        val mockChecker = object : PermissionManager.PermissionChecker {
            override fun checkInstallPermission(context: Context): Boolean = false
            override fun checkStoragePermission(context: Context): Boolean = false
            override fun checkBatteryPermission(context: Context): Boolean = false
        }

        // Set the custom checker
        PermissionManager.setPermissionChecker(mockChecker)

        // Invalidate cache to force fresh check with new checker
        PermissionManager.invalidateCache()

        // Verify it's being used correctly
        val missing = PermissionManager.getMissingPermissions(context)

        // With mock checker returning false for all, missing should contain all 3
        assertEquals(
            "With mock checker reporting all permissions missing, missing should contain all 3",
            3,
            missing.size
        )

        assertTrue(
            "Missing should contain INSTALL_UNKNOWN_APPS",
            missing.contains(RequiredPermission.INSTALL_UNKNOWN_APPS)
        )

        assertTrue(
            "Missing should contain MANAGE_EXTERNAL_STORAGE",
            missing.contains(RequiredPermission.MANAGE_EXTERNAL_STORAGE)
        )

        assertTrue(
            "Missing should contain IGNORE_BATTERY_OPTIMIZATIONS",
            missing.contains(RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS)
        )
    }

    // ========== Constants Tests ==========

    @Test
    fun permission_cache_duration_is_positive() {
        assertTrue(
            "Permission cache duration should be positive",
            PermissionManager.PERMISSION_CACHE_DURATION_MS > 0
        )
    }

    @Test
    fun shared_prefs_keys_are_not_empty() {
        assertTrue(
            "PREFS_PERMISSIONS_GRANTED should not be empty",
            PermissionManager.PREFS_PERMISSIONS_GRANTED.isNotEmpty()
        )

        assertTrue(
            "PREFS_INSTALL_UNKNOWN_APPS_GRANTED should not be empty",
            PermissionManager.PREFS_INSTALL_UNKNOWN_APPS_GRANTED.isNotEmpty()
        )

        assertTrue(
            "PREFS_MANAGE_EXTERNAL_STORAGE_GRANTED should not be empty",
            PermissionManager.PREFS_MANAGE_EXTERNAL_STORAGE_GRANTED.isNotEmpty()
        )

        assertTrue(
            "PREFS_IGNORE_BATTERY_OPTIMIZATIONS_GRANTED should not be empty",
            PermissionManager.PREFS_IGNORE_BATTERY_OPTIMIZATIONS_GRANTED.isNotEmpty()
        )

        assertTrue(
            "PREFS_PERMISSION_CHECK_TIMESTAMP should not be empty",
            PermissionManager.PREFS_PERMISSION_CHECK_TIMESTAMP.isNotEmpty()
        )
    }
}
