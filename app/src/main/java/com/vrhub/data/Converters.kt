package com.vrhub.data

import androidx.room.TypeConverter

/**
 * Room TypeConverters for the application database.
 * Handles conversion between complex types (like Enums) and database-compatible types (like Strings).
 */
class Converters {
    @TypeConverter
    fun fromInstallStatus(value: InstallStatus?): String? = value?.name

    @TypeConverter
    fun toInstallStatus(value: String?): InstallStatus? = value?.let { InstallStatus.fromString(it) }
}
