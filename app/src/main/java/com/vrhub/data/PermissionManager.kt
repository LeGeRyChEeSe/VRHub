package com.vrhub.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import com.vrhub.BuildConfig
import com.vrhub.ui.RequiredPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Permission Manager for handling installation-related permissions.
 *
 * **Responsibilities:**
 * - Check permission states (INSTALL_UNKNOWN_APPS, MANAGE_EXTERNAL_STORAGE, IGNORE_BATTERY_OPTIMIZATIONS)
 * - Cache permission states for 30 seconds to avoid excessive PackageManager calls
 * - Persist permission state to SharedPreferences
 * - Validate saved permission states against actual system permissions
 *
 * **Story:** 1.8 - Permission Flow for Installation
 *
 * **Thread-safety:** All public methods are thread-safe and can be called from any thread.
 *
 * **Testing:** The [PermissionChecker] interface allows injection of mock permission checks
 * for unit testing without Android context.
 *
 * @see com.vrhub.ui.RequiredPermission
 * @see PermissionChecker
 */
object PermissionManager {
    private const val TAG = "PermissionManager"

    // SharedPreferences keys
    // Made public for testing
    const val PREFS_PERMISSIONS_GRANTED = "permissions_granted"
    const val PREFS_INSTALL_UNKNOWN_APPS_GRANTED = "install_unknown_apps_granted"
    const val PREFS_MANAGE_EXTERNAL_STORAGE_GRANTED = "manage_external_storage_granted"
    const val PREFS_IGNORE_BATTERY_OPTIMIZATIONS_GRANTED = "ignore_battery_optimizations_granted"
    const val PREFS_PERMISSION_CHECK_TIMESTAMP = "permission_check_timestamp"

    // Cache duration: 30 seconds
    // Made public for testing
    const val PERMISSION_CACHE_DURATION_MS = 30_000L

    // Thread-safety lock object for synchronized access to shared state
    private val stateLock = Any()

    // Single-threaded dispatcher for sequential permission state writes
    // This prevents race conditions when multiple threads call savePermissionState simultaneously
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Cached permission states
    // @Volatile ensures writes are immediately visible to all threads
    @Volatile
    private var cachedPermissionStates: Map<RequiredPermission, Boolean>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    // SharedPreferences instance (lazy-loaded)
    // @Volatile ensures writes are immediately visible to all threads
    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * Permission checker interface for testability.
     *
     * This interface allows dependency injection of permission checking logic,
     * enabling unit tests to mock system permission states without requiring
     * Android context or runtime environment.
     *
     * **Usage in tests:**
     * ```kotlin
     * val mockChecker = object : PermissionChecker {
     *     override fun checkInstallPermission(context: Context) = true
     *     override fun checkStoragePermission(context: Context) = false
     *     override fun checkBatteryPermission(context: Context) = true
     * }
     * PermissionManager.setPermissionChecker(mockChecker)
     * ```
     */
    interface PermissionChecker {
        /**
         * Check if INSTALL_UNKNOWN_APPS permission is granted.
         * @param context Android context
         * @return true if permission is granted
         */
        fun checkInstallPermission(context: Context): Boolean

        /**
         * Check if MANAGE_EXTERNAL_STORAGE permission is granted.
         * @param context Android context
         * @return true if permission is granted
         */
        fun checkStoragePermission(context: Context): Boolean

        /**
         * Check if IGNORE_BATTERY_OPTIMIZATIONS permission is granted.
         * @param context Android context
         * @return true if permission is granted
         */
        fun checkBatteryPermission(context: Context): Boolean
    }

    /**
     * Default permission checker implementation using Android system APIs.
     */
    private var permissionChecker: PermissionChecker = object : PermissionChecker {
        override fun checkInstallPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        }

        override fun checkStoragePermission(context: Context): Boolean {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Android 11+: MANAGE_EXTERNAL_STORAGE required
                    // Maps to RequiredPermission.MANAGE_EXTERNAL_STORAGE enum value
                    Environment.isExternalStorageManager()
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    // Android 10 (API 29): WRITE_EXTERNAL_STORAGE required for OBB access
                    // Even with scoped storage, OBB files need legacy storage permission.
                    // With requestLegacyExternalStorage="true", WRITE permission grants both read and write.
                    // Maps to RequiredPermission.MANAGE_EXTERNAL_STORAGE enum value (abstracted).
                    val result = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED

                    if (!result) {
                        LogUtils.d(TAG, "API 29 storage check: WRITE_EXTERNAL_STORAGE not granted")
                    }

                    result
                }
                else -> true
            }
        }

        override fun checkBatteryPermission(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    /**
     * Inject a custom [PermissionChecker] for testing purposes.
     *
     * This method replaces the default system permission checker with a mock implementation,
     * allowing unit tests to control permission states without requiring Android runtime.
     *
     * **Note:** This method is annotated with `@VisibleForTesting` and should only be used
     * in test code. Production code should use the default system permission checker.
     *
     * @param checker Custom permission checker implementation
     */
    @androidx.annotation.VisibleForTesting
    fun setPermissionChecker(checker: PermissionChecker) {
        permissionChecker = checker
    }

    /**
     * Initialize PermissionManager with application context.
     * Must be called before any other method.
     *
     * Thread-safe initialization.
     *
     * @param context Application context
     */
    fun init(context: Context) {
        synchronized(stateLock) {
            if (prefs == null) {
                prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                LogUtils.d(TAG, "PermissionManager initialized")
            }
        }
    }

    /**
     * Check if all CRITICAL installation permissions are granted.
     * Critical permissions are those required for installation to succeed:
     * - INSTALL_UNKNOWN_APPS (required for APK installation)
     * - MANAGE_EXTERNAL_STORAGE (required for OBB file movement)
     *
     * IGNORE_BATTERY_OPTIMIZATIONS is NOT included as it's optional/nice-to-have.
     *
     * Uses cached results if available and not stale (within 30 seconds).
     *
     * @param context Application context
     * @return true if all critical permissions are granted, false otherwise
     */
    fun hasCriticalPermissions(context: Context): Boolean {
        val missing = getMissingCriticalPermissions(context)
        return missing.isEmpty()
    }

    /**
     * Check if all required installation permissions are granted.
     * This includes optional permissions like IGNORE_BATTERY_OPTIMIZATIONS.
     *
     * Uses cached results if available and not stale (within 30 seconds).
     *
     * @param context Application context
     * @return true if all permissions are granted, false otherwise
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        val missing = getMissingPermissions(context)
        return missing.isEmpty()
    }

    /**
     * Get list of missing (not granted) permissions.
     * Results are cached for 30 seconds to avoid excessive PackageManager calls.
     *
     * Thread-safe implementation to prevent race conditions when multiple
     * threads access cached state simultaneously.
     *
     * @param context Application context
     * @return List of RequiredPermission that are not granted
     */
    fun getMissingPermissions(context: Context): List<RequiredPermission> {
        // Use synchronized to access shared state
        synchronized(stateLock) {
            val now = System.currentTimeMillis()

            // Return cached results if available and not stale
            if (cachedPermissionStates != null && (now - cacheTimestamp) < PERMISSION_CACHE_DURATION_MS) {
                val missing = cachedPermissionStates?.filter { !it.value }?.keys?.toList() ?: emptyList()
                LogUtils.d(TAG, "Using cached permission states: ${missing.size} missing permissions")
                return missing
            }

            // Check permissions from system
            val permissionStates = mutableMapOf<RequiredPermission, Boolean>()
            val missing = mutableListOf<RequiredPermission>()

            try {
                // Check INSTALL_UNKNOWN_APPS permission (API 26+)
                val hasInstallPermission = permissionChecker.checkInstallPermission(context)
                permissionStates[RequiredPermission.INSTALL_UNKNOWN_APPS] = hasInstallPermission
                if (!hasInstallPermission) {
                    missing.add(RequiredPermission.INSTALL_UNKNOWN_APPS)
                }

                // Check MANAGE_EXTERNAL_STORAGE permission (API 30+)
                val hasStoragePermission = permissionChecker.checkStoragePermission(context)
                permissionStates[RequiredPermission.MANAGE_EXTERNAL_STORAGE] = hasStoragePermission
                if (!hasStoragePermission) {
                    missing.add(RequiredPermission.MANAGE_EXTERNAL_STORAGE)
                }

                // Check IGNORE_BATTERY_OPTIMIZATIONS permission
                val hasBatteryPermission = permissionChecker.checkBatteryPermission(context)
                permissionStates[RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS] = hasBatteryPermission
                if (!hasBatteryPermission) {
                    missing.add(RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS)
                }

                // Update cache (atomic write within Mutex lock)
                cachedPermissionStates = permissionStates
                cacheTimestamp = now

                LogUtils.d(TAG, "Permission check completed: ${missing.size} missing permissions: $missing")

            } catch (e: Exception) {
                LogUtils.e(TAG, "Error checking permissions", e)
                // On error, assume permissions are missing to be safe
                // Fixed API 29 handling - include API 29 for storage permission
                return listOf(
                    RequiredPermission.INSTALL_UNKNOWN_APPS,
                    RequiredPermission.MANAGE_EXTERNAL_STORAGE,
                    RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS
                ).filter { permission ->
                    when (permission) {
                        RequiredPermission.INSTALL_UNKNOWN_APPS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        // MANAGE_EXTERNAL_STORAGE is required on API 30+ (R)
                        // On API 29 (Q), we need WRITE_EXTERNAL_STORAGE instead
                        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> true
                    }
                }
            }

            return missing
        }
    }

    /**
     * Get list of missing CRITICAL (not granted) permissions.
     * Critical permissions are those required for installation to succeed:
     * - INSTALL_UNKNOWN_APPS (required for APK installation)
     * - MANAGE_EXTERNAL_STORAGE (required for OBB file movement)
     *
     * IGNORE_BATTERY_OPTIMIZATIONS is NOT checked as it's optional.
     *
     * Results are cached for 30 seconds to avoid excessive PackageManager calls.
     *
     * @param context Application context
     * @return List of RequiredPermission that are not granted (only critical ones)
     */
    fun getMissingCriticalPermissions(context: Context): List<RequiredPermission> {
        // Get all missing permissions and filter out battery optimization
        return getMissingPermissions(context).filter { it != RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS }
    }

    /**
     * Check if INSTALL_UNKNOWN_APPS permission is granted.
     * Does not use cache - always checks current system state.
     *
     * Refactored to use PermissionChecker interface for consistency.
     *
     * @param context Android context
     * @return true if permission is granted, false otherwise
     */
    fun hasInstallUnknownAppsPermission(context: Context): Boolean {
        return try {
            permissionChecker.checkInstallPermission(context)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking INSTALL_UNKNOWN_APPS permission", e)
            false
        }
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is granted.
     * Does not use cache - always checks current system state.
     *
     * Refactored to use PermissionChecker interface for consistency.
     *
     * @param context Android context
     * @return true if permission is granted, false otherwise
     */
    fun hasManageExternalStoragePermission(context: Context): Boolean {
        return try {
            permissionChecker.checkStoragePermission(context)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking MANAGE_EXTERNAL_STORAGE permission", e)
            false
        }
    }

    /**
     * Check if IGNORE_BATTERY_OPTIMIZATIONS permission is granted.
     * Does not use cache - always checks current system state.
     *
     * Refactored to use PermissionChecker interface for consistency.
     *
     * @param context Android context
     * @return true if permission is granted, false otherwise
     */
    fun hasIgnoreBatteryOptimizationsPermission(context: Context): Boolean {
        return try {
            permissionChecker.checkBatteryPermission(context)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking IGNORE_BATTERY_OPTIMIZATIONS permission", e)
            false
        }
    }

    /**
     * Save permission state to SharedPreferences.
     * Should be called after each permission grant/deny.
     *
     * Thread-safe implementation.
     * Use commit() instead of apply() for critical permission state writes.
     * Run commit() on background thread to avoid blocking caller while
     * still ensuring immediate persistence to prevent race conditions.
     * Use single-threaded dispatcher to prevent race conditions when
     * multiple threads call savePermissionState simultaneously. All writes execute sequentially
     * on the same thread, ensuring no concurrent writes to SharedPreferences.
     * Changed to suspend function and use withContext instead of runBlocking
     * to prevent UI thread blocking. This is a CRITICAL fix - runBlocking blocks the calling
     * thread, causing UI stutters and potential ANRs when called from viewModelScope.
     * Clarified that commit() is used for immediate persistence (synchronous disk write).
     * While commit() blocks the current thread, withContext(ioDispatcher) ensures this blocking
     * occurs on a background thread and not the UI thread.
     *
     * @param permission The permission that was granted/denied
     * @param granted true if granted, false if denied
     */
    suspend fun savePermissionState(permission: RequiredPermission, granted: Boolean) {
        // Collect values to save within synchronized block
        val prefsToUse = synchronized(stateLock) {
            val p = prefs
            if (p == null) {
                LogUtils.e(TAG, "PermissionManager not initialized. Call init() first. Permission state not saved.")
                return
            }
            p
        }

        // Use withContext to ensure the blocking commit() call 
        // happens on the background dispatcher, keeping the UI thread responsive.
        withContext(ioDispatcher) {
            try {
                // Use commit() for critical permission state to ensure immediate persistence
                // (synchronous disk write). This prevents race conditions where permission
                // state might not be saved if the app is killed immediately after.
                val committed = prefsToUse.edit().apply {
                    when (permission) {
                        RequiredPermission.INSTALL_UNKNOWN_APPS -> {
                            putBoolean(PREFS_INSTALL_UNKNOWN_APPS_GRANTED, granted)
                        }
                        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> {
                            putBoolean(PREFS_MANAGE_EXTERNAL_STORAGE_GRANTED, granted)
                        }
                        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> {
                            putBoolean(PREFS_IGNORE_BATTERY_OPTIMIZATIONS_GRANTED, granted)
                        }
                    }
                    putLong(PREFS_PERMISSION_CHECK_TIMESTAMP, System.currentTimeMillis())
                }.commit()

                if (!committed) {
                    LogUtils.e(TAG, "Failed to commit permission state for $permission")
                } else {
                    LogUtils.d(TAG, "Saved permission state: $permission = $granted")
                }

            } catch (e: Exception) {
                LogUtils.e(TAG, "Error saving permission state", e)
            }
        }
    }

    /**
     * Load saved permission states from SharedPreferences.
     * Returns null if no saved state exists.
     *
     * Thread-safe implementation.
     *
     * @return Map of permission to granted state, or null if not saved
     */
    fun loadSavedPermissionStates(): Map<RequiredPermission, Boolean>? {
        return synchronized(stateLock) {
            val sharedPreferences = prefs ?: run {
                LogUtils.e(TAG, "PermissionManager not initialized. Call init() first.")
                return@synchronized null
            }

            try {
                val timestamp = sharedPreferences.getLong(PREFS_PERMISSION_CHECK_TIMESTAMP, 0L)
                if (timestamp == 0L) {
                    LogUtils.d(TAG, "No saved permission state found")
                    return@synchronized null
                }

                val states = mapOf(
                    RequiredPermission.INSTALL_UNKNOWN_APPS to sharedPreferences.getBoolean(
                        PREFS_INSTALL_UNKNOWN_APPS_GRANTED,
                        false
                    ),
                    RequiredPermission.MANAGE_EXTERNAL_STORAGE to sharedPreferences.getBoolean(
                        PREFS_MANAGE_EXTERNAL_STORAGE_GRANTED,
                        false
                    ),
                    RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS to sharedPreferences.getBoolean(
                        PREFS_IGNORE_BATTERY_OPTIMIZATIONS_GRANTED,
                        false
                    )
                )

                LogUtils.d(TAG, "Loaded saved permission states from timestamp $timestamp: $states")
                states

            } catch (e: Exception) {
                LogUtils.e(TAG, "Error loading saved permission states", e)
                null
            }
        }
    }

    /**
     * Result of permission state validation.
     * Provides detailed information about permission state changes.
     *
     * @property isValid true if all saved states match actual states
     * @property revokedPermissions List of permissions that were revoked (granted -> denied)
     * @property grantedPermissions List of permissions that were granted (denied -> granted)
     */
    data class ValidationResult(
        val isValid: Boolean,
        val revokedPermissions: List<RequiredPermission> = emptyList(),
        val grantedPermissions: List<RequiredPermission> = emptyList()
    )

    /**
     * Validate saved permission states against actual system permissions.
     * Detects if user revoked permissions in system settings.
     *
     * Refactored to use PermissionChecker interface for consistency
     * and to enable proper unit testing.
     *
     * Enhanced to distinguish between manual grants and revocations.
     * Returns detailed ValidationResult with separate lists for revoked and granted permissions.
     *
     * @param context Application context
     * @return ValidationResult with detailed information about permission state changes
     */
    fun validateSavedStates(context: Context): ValidationResult {
        val savedStates = loadSavedPermissionStates() ?: return ValidationResult(
            isValid = true
        ) // No saved state to validate

        val actualStates = mutableMapOf<RequiredPermission, Boolean>()

        // Check actual system states using PermissionChecker interface for consistency
        try {
            actualStates[RequiredPermission.INSTALL_UNKNOWN_APPS] =
                permissionChecker.checkInstallPermission(context)
            actualStates[RequiredPermission.MANAGE_EXTERNAL_STORAGE] =
                permissionChecker.checkStoragePermission(context)
            actualStates[RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS] =
                permissionChecker.checkBatteryPermission(context)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking permissions during validation", e)
            return ValidationResult(isValid = false)
        }

        // Compare saved vs actual and categorize changes
        val revokedPermissions = mutableListOf<RequiredPermission>()
        val grantedPermissions = mutableListOf<RequiredPermission>()

        savedStates.forEach { (permission, savedGranted) ->
            val actualGranted = actualStates[permission] ?: false

            when {
                // Permission was granted before, now denied -> REVOKED
                savedGranted && !actualGranted -> {
                    revokedPermissions.add(permission)
                    Log.w(TAG, "Permission revoked detected: $permission (was granted, now denied)")
                }
                // Permission was denied before, now granted -> MANUALLY GRANTED
                !savedGranted && actualGranted -> {
                    grantedPermissions.add(permission)
                    LogUtils.i(TAG, "Permission manually granted detected: $permission (was denied, now granted)")
                }
            }
        }

        val isValid = revokedPermissions.isEmpty() && grantedPermissions.isEmpty()

        if (!isValid) {
            // Clear stale cache to force re-check
            invalidateCache()

            if (revokedPermissions.isNotEmpty()) {
                Log.w(TAG, "Permission revocations detected: $revokedPermissions")
            }
            if (grantedPermissions.isNotEmpty()) {
                LogUtils.i(TAG, "Permissions manually granted: $grantedPermissions")
            }
        }

        return ValidationResult(
            isValid = isValid,
            revokedPermissions = revokedPermissions,
            grantedPermissions = grantedPermissions
        )
    }

    /**
     * Invalidate the permission cache.
     * Forces a fresh permission check on next call to getMissingPermissions().
     * Should be called when permissions might have changed (e.g., returning from settings).
     *
     * Thread-safe implementation.
     * Documented synchronization with MainViewModel.
     *
     * **Cache Synchronization Strategy:**
     * This method clears the internal cache in PermissionManager. MainViewModel has its own
     * synchronization mechanism (permissionCheckMutex) to prevent concurrent permission checks.
     * The two work together:
     * 1. onAppResume() calls invalidateCache() to clear PermissionManager's cached state
     * 2. MainViewModel.permissionCheckMutex prevents race conditions during checkPermissions()
     * 3. This ensures fresh permission state without excessive system calls
     *
     * @see MainViewModel.onAppResume for cache invalidation trigger
     * @see MainViewModel.permissionCheckMutex for concurrent access prevention
     */
    fun invalidateCache() {
        synchronized(stateLock) {
            cachedPermissionStates = null
            cacheTimestamp = 0L
            LogUtils.d(TAG, "Permission cache invalidated")
        }
    }

    /**
     * Get the timestamp of the last permission check.
     *
     * Thread-safe implementation.
     *
     * @return Timestamp in milliseconds, or 0 if never checked
     */
    fun getLastCheckTimestamp(): Long {
        return synchronized(stateLock) {
            val sharedPreferences = prefs ?: return@synchronized 0L
            sharedPreferences.getLong(PREFS_PERMISSION_CHECK_TIMESTAMP, 0L)
        }
    }
}
