package com.vrpirates.rookieonquest.logic

/**
 * Utility functions for catalog management.
 */
object CatalogUtils {
    /**
     * Determines if a catalog update is available based on last-modified dates.
     * 
     * An update is considered available if:
     * 1. The remote date is provided and differs from the local saved date.
     * 2. The local database already contains games (otherwise it's an initial sync).
     *
     * ### Usage Examples:
     * ```kotlin
     * // Update available (dates differ, local DB has games)
     * isUpdateAvailable("Mon, 10 Feb 2026", "Sun, 09 Feb 2026", 100) // returns true
     * 
     * // No update available (dates match)
     * isUpdateAvailable("Mon, 10 Feb 2026", "Mon, 10 Feb 2026", 100) // returns false
     * 
     * // Initial sync (dates differ, but local DB is empty)
     * isUpdateAvailable("Mon, 10 Feb 2026", null, 0) // returns false
     * 
     * // Server error (remote date is null)
     * isUpdateAvailable(null, "Sun, 09 Feb 2026", 100) // returns false
     * ```
     *
     * @param remoteLastModified The Last-Modified date from the server (e.g., from HTTP HEAD)
     * @param savedLastModified The Last-Modified date stored locally in Preferences
     * @param currentItemCount The current number of games in the local database
     * @return true if an update is available (dates differ and local DB is not empty)
     */
    fun isUpdateAvailable(
        remoteLastModified: String?,
        savedLastModified: String?,
        currentItemCount: Int
    ): Boolean {
        if (remoteLastModified == null) return false
        
        // If we have no games, it's not an "update" for the banner, it's a first sync
        if (currentItemCount == 0) return false
        
        return remoteLastModified != savedLastModified
    }
}
