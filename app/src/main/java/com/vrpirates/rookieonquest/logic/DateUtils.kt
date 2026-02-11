package com.vrpirates.rookieonquest.logic

object DateUtils {
    /**
     * Formats a timestamp into a human-readable "time ago" string.
     * 
     * @param timestamp The timestamp to format in milliseconds
     * @return String representation like "Just now", "5m ago", "2h ago", or "3d ago"
     */
    fun formatTimeAgo(timestamp: Long): String {
        if (timestamp <= 0) return "Never"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> "${diff / 86400000}d ago"
        }
    }
}
