package com.vrpirates.rookieonquest.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "install_history",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["releaseName"],
            childColumns = ["releaseName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["releaseName"]),
        Index(value = ["status"]),
        Index(value = ["installedAt"]),
        Index(value = ["releaseName", "createdAt"], unique = true)
    ]
)
data class InstallHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val releaseName: String,
    val gameName: String,
    val packageName: String,
    val installedAt: Long,
    val downloadDurationMs: Long,
    val fileSizeBytes: Long,
    val status: InstallStatus,
    val errorMessage: String? = null,
    /**
     * Creation timestamp of the original queue entry.
     * 
     * WARNING: The default value [System.currentTimeMillis] is only a safety fallback.
     * In production, this field MUST be populated from the source [QueuedInstallEntity.createdAt]
     * during the archiving process to preserve the actual task lifecycle history.
     * Failing to do so will result in incorrect duration calculations and lost history.
     */
    val createdAt: Long = System.currentTimeMillis(),
    val isLocalInstall: Boolean = false
)
