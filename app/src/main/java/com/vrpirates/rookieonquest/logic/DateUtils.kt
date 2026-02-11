package com.vrpirates.rookieonquest.logic

import android.content.Context
import com.vrpirates.rookieonquest.R

object DateUtils {
    /**
     * Formats a timestamp into a human-readable "time ago" string.
     * 
     * @param context Android context for string resources
     * @param timestamp The timestamp to format in milliseconds
     * @return String representation like "Just now", "5m ago", "2h ago", or "3d ago"
     */
    fun formatTimeAgo(context: Context, timestamp: Long): String {
        if (timestamp <= 0) return context.getString(R.string.time_never)
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> context.getString(R.string.time_just_now)
            diff < 3600000 -> context.getString(R.string.time_minutes_ago, diff / 60000)
            diff < 86400000 -> context.getString(R.string.time_hours_ago, diff / 3600000)
            else -> context.getString(R.string.time_days_ago, diff / 86400000)
        }
    }
}
