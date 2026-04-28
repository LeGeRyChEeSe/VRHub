package com.vrhub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InstallHistoryEntity)

    @Delete
    suspend fun delete(entity: InstallHistoryEntity)

    @Query("DELETE FROM install_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM install_history")
    suspend fun deleteAll()

    /**
     * Returns a Flow of all installation history entries.
     * The list is sorted by the installation timestamp in descending order (newest first).
     */
    @Query("SELECT * FROM install_history ORDER BY installedAt DESC")
    fun getAllFlow(): Flow<List<InstallHistoryEntity>>

    /**
     * Search and filter history entries.
     * 
     * NOTE: The :query parameter MUST be pre-escaped by the caller (ViewModel) 
     * for LIKE special characters (%, _, \). This query uses the ESCAPE '\' clause.
     * 
     * @param query Search query for game name or package name (pre-escaped)
     * @param status Status filter (COMPLETED or FAILED), or null for all
     */
    @Query("""
        SELECT * FROM install_history 
        WHERE (:query IS NULL OR gameName LIKE '%' || :query || '%' ESCAPE '\' OR packageName LIKE '%' || :query || '%' ESCAPE '\')
        AND (:status IS NULL OR status = :status)
        ORDER BY installedAt DESC
    """)
    fun searchAndFilterFlow(query: String?, status: InstallStatus?): Flow<List<InstallHistoryEntity>>

    /**
     * Search and filter history entries with a limit for pagination.
     */
    /**
     * Search and filter history entries with a limit for pagination and dynamic sorting.
     */
    @Query("""
        SELECT * FROM install_history 
        WHERE (:query IS NULL OR gameName LIKE '%' || :query || '%' ESCAPE '\' OR packageName LIKE '%' || :query || '%' ESCAPE '\')
        AND (:status IS NULL OR status = :status)
        AND (:minTimestamp IS NULL OR installedAt >= :minTimestamp)
        ORDER BY 
            CASE WHEN :sortMode = 'DATE_DESC' THEN installedAt END DESC,
            CASE WHEN :sortMode = 'DATE_ASC' THEN installedAt END ASC,
            CASE WHEN :sortMode = 'NAME_ASC' THEN gameName END ASC,
            CASE WHEN :sortMode = 'NAME_DESC' THEN gameName END DESC,
            CASE WHEN :sortMode = 'SIZE_DESC' THEN fileSizeBytes END DESC,
            CASE WHEN :sortMode = 'DURATION_DESC' THEN downloadDurationMs END DESC
        LIMIT :limit
    """)
    fun searchAndFilterFlowWithLimitAndSort(
        query: String?, 
        status: InstallStatus?, 
        minTimestamp: Long?,
        limit: Int, 
        sortMode: String
    ): Flow<List<InstallHistoryEntity>>

    @Query("""
        SELECT * FROM install_history 
        WHERE (:query IS NULL OR gameName LIKE '%' || :query || '%' ESCAPE '\' OR packageName LIKE '%' || :query || '%' ESCAPE '\')
        AND (:status IS NULL OR status = :status)
        AND (:minTimestamp IS NULL OR installedAt >= :minTimestamp)
        ORDER BY installedAt DESC
        LIMIT :limit
    """)
    fun searchAndFilterFlowWithLimit(
        query: String?, 
        status: InstallStatus?, 
        minTimestamp: Long?,
        limit: Int
    ): Flow<List<InstallHistoryEntity>>

    /**
     * Paginated search and filter.
     * @param query Search query for game name or package name
     * @param status Status filter (COMPLETED or FAILED), or null for all
     * @param minTimestamp Minimum installation timestamp, or null for all
     * @param limit Number of items to return
     * @param offset Number of items to skip
     */
    @Query("""
        SELECT * FROM install_history 
        WHERE (:query IS NULL OR gameName LIKE '%' || :query || '%' ESCAPE '\' OR packageName LIKE '%' || :query || '%' ESCAPE '\')
        AND (:status IS NULL OR status = :status)
        AND (:minTimestamp IS NULL OR installedAt >= :minTimestamp)
        ORDER BY installedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchAndFilterPaginated(
        query: String?, 
        status: InstallStatus?, 
        minTimestamp: Long?,
        limit: Int, 
        offset: Int
    ): List<InstallHistoryEntity>

    /**
     * Get count of filtered entries.
     */
    @Query("""
        SELECT COUNT(*) FROM install_history 
        WHERE (:query IS NULL OR gameName LIKE '%' || :query || '%' ESCAPE '\' OR packageName LIKE '%' || :query || '%' ESCAPE '\')
        AND (:status IS NULL OR status = :status)
        AND (:minTimestamp IS NULL OR installedAt >= :minTimestamp)
    """)
    suspend fun getFilteredCount(query: String?, status: InstallStatus?, minTimestamp: Long?): Int

    @Query("SELECT * FROM install_history ORDER BY installedAt DESC")
    suspend fun getAll(): List<InstallHistoryEntity>

    @Query("SELECT COUNT(*) FROM install_history WHERE status = :status")
    suspend fun getCountByStatus(status: InstallStatus): Int

    @Query("SELECT AVG(downloadDurationMs) FROM install_history WHERE status = :status")
    suspend fun getAverageDuration(status: InstallStatus): Long

    @Query("SELECT SUM(fileSizeBytes) FROM install_history WHERE status = :status")
    suspend fun getTotalDownloadedSize(status: InstallStatus): Long

    @Query("SELECT gameName, COUNT(*) as count FROM install_history GROUP BY gameName ORDER BY count DESC LIMIT :limit")
    suspend fun getMostInstalledGames(limit: Int): List<GameCount>

    @Query("SELECT errorMessage, COUNT(*) as count FROM install_history WHERE errorMessage IS NOT NULL AND errorMessage != '' GROUP BY errorMessage ORDER BY count DESC LIMIT :limit")
    suspend fun getErrorSummary(limit: Int): List<ErrorCount>

    @Query("SELECT COUNT(*) FROM install_history WHERE releaseName = :releaseName AND createdAt = :createdAt")
    suspend fun countByReleaseAndCreatedAt(releaseName: String, createdAt: Long): Int
}

data class GameCount(
    val gameName: String,
    val count: Int
)

data class ErrorCount(
    val errorMessage: String,
    val count: Int
)
