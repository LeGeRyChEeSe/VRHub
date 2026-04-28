package com.vrhub.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrhub.BuildConfig
import com.vrhub.data.GameData
import com.vrhub.data.ServerConfig
import com.vrhub.data.ServerConfigRepository
import com.vrhub.data.Constants
import com.vrhub.data.InstallUtils
import com.vrhub.data.PermissionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import com.vrhub.data.MainRepository
import com.vrhub.network.UpdateInfo
import com.vrhub.network.UpdateService
import com.vrhub.network.GitHubApiResult
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import androidx.work.WorkInfo
import com.vrhub.data.NetworkModule
import com.vrhub.worker.DownloadWorker
import com.vrhub.R
import com.vrhub.ui.animation.AnimationState

sealed class MainEvent {
    data class Uninstall(val packageName: String) : MainEvent()
    data class InstallApk(val apkFile: File) : MainEvent()
    object RequestInstallPermission : MainEvent()
    object RequestStoragePermission : MainEvent()
    object RequestIgnoreBatteryOptimizations : MainEvent()
    data class ShowUpdatePopup(val updateInfo: UpdateInfo) : MainEvent()
    data class ShowMessage(val message: String) : MainEvent()
    data class CopyLogs(val logs: String) : MainEvent()

    // Event to open system settings for a specific permission
    data class OpenPermissionSettings(val permission: RequiredPermission) : MainEvent()
}

/**
 * Required permissions for game installation and management.
 *
 * **Note:** [MANAGE_EXTERNAL_STORAGE] represents storage access permission across Android versions:
 * - Android 11+ (API 30+): Maps to `Manifest.permission.MANAGE_EXTERNAL_STORAGE` system permission
 * - Android 10 (API 29): Maps to `WRITE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE` system permissions
 *
 * This abstraction allows the permission system to work consistently across Android versions
 * while handling the underlying implementation differences in [PermissionManager].
 *
 * @see PermissionManager.checkStoragePermission
 */
enum class RequiredPermission {
    /** Permission to install APK files from unknown sources (API 26+) */
    INSTALL_UNKNOWN_APPS,

    /**
     * Permission to access external storage for OBB files and game data.
     * On Android 11+ this is MANAGE_EXTERNAL_STORAGE.
     * On Android 10 this is WRITE_EXTERNAL_STORAGE + READ_EXTERNAL_STORAGE.
     */
    MANAGE_EXTERNAL_STORAGE,

    /** Permission to ignore battery optimizations for background downloads */
    IGNORE_BATTERY_OPTIMIZATIONS
}

enum class FilterStatus {
    ALL,
    FAVORITES,
    INSTALLED,
    DOWNLOADED,
    UPDATE_AVAILABLE,
    NEW,
    LOCAL_INSTALLS
}

enum class SortMode {
    NAME_ASC,
    NAME_DESC,
    LAST_UPDATED,
    SIZE,
    POPULARITY
}

enum class HistorySortMode {
    DATE_DESC, // Default (Newest first)
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DURATION_DESC
}

enum class HistoryDateFilter {
    ALL,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_3_MONTHS
}

data class HistoryStats(
    val successRate: Float,
    val averageDurationMs: Long,
    val totalDownloadedBytes: Long,
    val topGames: List<String>,
    val errorSummary: Map<String, Int> = emptyMap()
)

enum class InstallStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE
}

enum class InstallTaskStatus {
    QUEUED, DOWNLOADING, EXTRACTING, INSTALLING, PENDING_INSTALL, PAUSED, BLOCKED_BY_PERMISSIONS, COMPLETED, FAILED, LOCAL_VERIFYING, SHELVED
}

fun InstallTaskStatus.isProcessing(): Boolean = when (this) {
    InstallTaskStatus.DOWNLOADING,
    InstallTaskStatus.EXTRACTING,
    InstallTaskStatus.INSTALLING,
    InstallTaskStatus.LOCAL_VERIFYING -> true
    InstallTaskStatus.QUEUED,
    InstallTaskStatus.PENDING_INSTALL,
    InstallTaskStatus.PAUSED,
    InstallTaskStatus.BLOCKED_BY_PERMISSIONS,
    InstallTaskStatus.COMPLETED,
    InstallTaskStatus.FAILED,
    InstallTaskStatus.SHELVED -> false
}

/**
 * Maps a game name's first character to its alphabet group character.
 * Used for both sorting order and alphabet navigation to ensure consistency.
 *
 * Rules:
 * - '_' prefix → '_' group (comes first in sort)
 * - Digits (0-9) → '#' group (comes second in sort)
 * - Letters → uppercase letter group
 * - Empty/null → '_' group
 *
 * @param gameName The full game name to extract the group character from
 * @return The mapped character for alphabet grouping
 */
fun getAlphabetGroupChar(gameName: String): Char {
    val firstChar = gameName.trim().firstOrNull()?.uppercaseChar() ?: '_'
    return when {
        firstChar == '_' -> '_'
        firstChar.isDigit() -> '#'
        else -> firstChar
    }
}

/**
 * Returns the sort priority for alphabet groups.
 * Lower values sort first.
 *
 * @param groupChar The alphabet group character from getAlphabetGroupChar()
 * @return Sort priority (0 = '_', 1 = '#', 2 = letters)
 */
fun getAlphabetSortPriority(groupChar: Char): Int = when (groupChar) {
    '_' -> 0
    '#' -> 1
    else -> 2
}

@Immutable
data class InstallTaskState(
    val releaseName: String,
    val gameName: String,
    val packageName: String,
    val status: InstallTaskStatus = InstallTaskStatus.QUEUED,
    val progress: Float = 0f,
    val message: String? = null,
    val currentSize: String? = null,
    val totalSize: String? = null,
    val isDownloadOnly: Boolean = false,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long? = null,
    val isLocalInstall: Boolean = false,
    val error: String? = null
)

/**
 * Maps Room InstallStatus (from data layer) to UI InstallTaskStatus.
 *
 * ARCHITECTURAL NOTE: These mappers intentionally couple the data and UI layers.
 * This is acceptable because:
 * 1. Both enums represent the same domain concept (installation state)
 * 2. The mapping is simple and unlikely to change independently
 * 3. Introducing an intermediate layer would add complexity without benefit
 *
 * If the data layer statuses need to diverge significantly from UI statuses,
 * consider moving these mappers to a dedicated StatusMapper object in
 * the data package to improve testability and separation of concerns.
 *
 * Special cases:
 * - COPYING_OBB (data) → INSTALLING (UI): OBB copying is a sub-phase of installation
 *   and should not be visible as a distinct state to users
 */
fun com.vrhub.data.InstallStatus.toTaskStatus(): InstallTaskStatus {
    return when (this) {
        com.vrhub.data.InstallStatus.QUEUED -> InstallTaskStatus.QUEUED
        com.vrhub.data.InstallStatus.DOWNLOADING -> InstallTaskStatus.DOWNLOADING
        com.vrhub.data.InstallStatus.EXTRACTING -> InstallTaskStatus.EXTRACTING
        com.vrhub.data.InstallStatus.COPYING_OBB -> InstallTaskStatus.INSTALLING // OBB copy is sub-phase of install
        com.vrhub.data.InstallStatus.INSTALLING -> InstallTaskStatus.INSTALLING
        com.vrhub.data.InstallStatus.PENDING_INSTALL -> InstallTaskStatus.PENDING_INSTALL // Map pending install state
        com.vrhub.data.InstallStatus.PAUSED -> InstallTaskStatus.PAUSED
        com.vrhub.data.InstallStatus.COMPLETED -> InstallTaskStatus.COMPLETED
        com.vrhub.data.InstallStatus.FAILED -> InstallTaskStatus.FAILED
        com.vrhub.data.InstallStatus.LOCAL_VERIFYING -> InstallTaskStatus.LOCAL_VERIFYING
        com.vrhub.data.InstallStatus.SHELVED -> InstallTaskStatus.SHELVED
    }
}

/**
 * Maps UI InstallTaskStatus to Room InstallStatus (for saving to database).
 *
 * NOTE: This is the reverse mapping of toTaskStatus(). Since the UI has fewer
 * states than the data layer (no COPYING_OBB), this mapping is lossy in reverse.
 * INSTALLING in UI maps to INSTALLING in data, never to COPYING_OBB.
 *
 * This asymmetry is intentional - COPYING_OBB is set directly by MainRepository,
 * not through this mapper, ensuring correct state representation during OBB operations.
 */
fun InstallTaskStatus.toDataStatus(): com.vrhub.data.InstallStatus {
    return when (this) {
        InstallTaskStatus.QUEUED -> com.vrhub.data.InstallStatus.QUEUED
        InstallTaskStatus.DOWNLOADING -> com.vrhub.data.InstallStatus.DOWNLOADING
        InstallTaskStatus.EXTRACTING -> com.vrhub.data.InstallStatus.EXTRACTING
        InstallTaskStatus.INSTALLING -> com.vrhub.data.InstallStatus.INSTALLING
        InstallTaskStatus.PENDING_INSTALL -> com.vrhub.data.InstallStatus.PENDING_INSTALL // Map to PENDING_INSTALL in data layer
        InstallTaskStatus.PAUSED -> com.vrhub.data.InstallStatus.PAUSED
        InstallTaskStatus.BLOCKED_BY_PERMISSIONS -> com.vrhub.data.InstallStatus.PAUSED
        InstallTaskStatus.COMPLETED -> com.vrhub.data.InstallStatus.COMPLETED
        InstallTaskStatus.FAILED -> com.vrhub.data.InstallStatus.FAILED
        InstallTaskStatus.LOCAL_VERIFYING -> com.vrhub.data.InstallStatus.LOCAL_VERIFYING
        InstallTaskStatus.SHELVED -> com.vrhub.data.InstallStatus.SHELVED
    }
}

/**
 * Converts QueuedInstallEntity from Room to UI InstallTaskState
 * Uses pre-fetched game metadata from cache to avoid N+1 queries
 * Added missingPermissions parameter for visual feedback.
 *
 * @param context Android context for localized status messages
 * @param gameDataCache Pre-loaded map of releaseName -> GameData (from batch query)
 * @param missingPermissions List of currently missing permissions for visual feedback
 */
fun com.vrhub.data.QueuedInstallEntity.toInstallTaskState(
    context: Context,
    gameDataCache: Map<String, com.vrhub.data.GameData>,
    missingPermissions: List<RequiredPermission> = emptyList()
): InstallTaskState {
    val gameData = gameDataCache[releaseName]
    val statusEnum = statusEnum.toTaskStatus()

    // Check if task is blocked by missing critical permissions
    // Battery optimization permission is optional and doesn't block installation
    val missingCritical = missingPermissions.filter {
        it != RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS
    }

    // Distinguish between user-paused and permission-blocked
    val isBlockedByPermissions = (statusEnum == InstallTaskStatus.PAUSED || statusEnum == InstallTaskStatus.BLOCKED_BY_PERMISSIONS) &&
        missingCritical.isNotEmpty()

    val finalStatus = if (isBlockedByPermissions) InstallTaskStatus.BLOCKED_BY_PERMISSIONS else statusEnum

    // Generate status message based on current state using localized strings
    val message = when (finalStatus) {
        InstallTaskStatus.QUEUED -> context.getString(R.string.status_queued)
        InstallTaskStatus.DOWNLOADING -> context.getString(R.string.status_downloading)
        InstallTaskStatus.EXTRACTING -> context.getString(R.string.status_extracting)
        InstallTaskStatus.INSTALLING -> context.getString(R.string.status_installing)
        InstallTaskStatus.PENDING_INSTALL -> context.getString(R.string.status_pending_install)
        InstallTaskStatus.PAUSED -> context.getString(R.string.status_paused)
        InstallTaskStatus.BLOCKED_BY_PERMISSIONS -> context.getString(R.string.status_blocked_permissions)
        InstallTaskStatus.COMPLETED -> context.getString(R.string.status_completed)
        InstallTaskStatus.FAILED -> context.getString(R.string.status_failed)
        InstallTaskStatus.LOCAL_VERIFYING -> context.getString(R.string.status_local_verifying)
        InstallTaskStatus.SHELVED -> context.getString(R.string.status_shelved)
    }

    // Force 100% progress for tasks waiting for installation (Story 1.13 UI Fix)
    // Extraction ends at 92%, but for user clarity, SHELVED/PENDING_INSTALL should show 100%
    val finalProgress = when (finalStatus) {
        InstallTaskStatus.SHELVED, InstallTaskStatus.PENDING_INSTALL -> 1.0f
        else -> progress
    }

    // Format size strings
    val currentSize = downloadedBytes?.let { InstallUtils.formatBytes(it) }
    val totalSizeStr = totalBytes?.let { InstallUtils.formatBytes(it) }

    return InstallTaskState(
        releaseName = releaseName,
        gameName = gameData?.gameName ?: releaseName,
        packageName = gameData?.packageName ?: "",
        status = finalStatus,
        progress = finalProgress,
        message = message,
        currentSize = currentSize,
        totalSize = totalSizeStr,
        isDownloadOnly = isDownloadOnly,
        totalBytes = totalBytes ?: 0L,
        downloadedBytes = downloadedBytes,
        isLocalInstall = isLocalInstall,
        error = null // Error state is transient, not persisted
    )
}

data class InstallState(
    val isInstalling: Boolean = false,
    val packageName: String? = null,
    val gameName: String? = null,
    val message: String? = null,
    val progress: Float = 0f,
    val currentSize: String? = null,
    val totalSize: String? = null
)

@Immutable
data class GameItemState(
    val name: String,
    val version: String,
    val installedVersion: String? = null,
    val packageName: String,
    val releaseName: String,
    val iconFile: File?,
    val installStatus: InstallStatus = InstallStatus.NOT_INSTALLED,
    val queueStatus: InstallTaskStatus? = null,
    val isFirstInQueue: Boolean = false,
    val isDownloaded: Boolean = false,
    val isFavorite: Boolean = false,
    val size: String? = null,
    val sizeBytes: Long = 0L,
    val description: String? = null,
    val screenshotUrls: List<String>? = null,
    val lastUpdated: Long = 0L,
    val popularity: Int = 0
)

/**
 * Permission flow state for UI.
 *
 * Tracks the current state of permission requests and provides reactive updates
 * to the UI during the permission request flow.
 *
 * @param isActive Whether a permission flow is currently active
 * @param currentPermission The permission currently being requested (null if not in flow)
 * @param pendingGameInstall The releaseName of game waiting for permissions (null if none)
 * @param allGranted Whether all required permissions have been granted
 */
@Immutable
data class PermissionFlowState(
    val isActive: Boolean = false,
    val currentPermission: RequiredPermission? = null,
    val pendingGameInstall: String? = null,
    val allGranted: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    private val repository = MainRepository(application)
    private val configRepository = ServerConfigRepository(application)
    private val prefs = application.getSharedPreferences(com.vrhub.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedFilter = MutableStateFlow(FilterStatus.ALL)
    val selectedFilter: StateFlow<FilterStatus> = _selectedFilter

    private val _sortMode = MutableStateFlow(SortMode.NAME_ASC)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _events = MutableSharedFlow<MainEvent>()
    val events = _events.asSharedFlow()

    private val _installedPackages = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _downloadedReleases = MutableStateFlow<Set<String>>(emptySet())

    private val _missingPermissions = MutableStateFlow<List<RequiredPermission>?>(null)
    val missingPermissions: StateFlow<List<RequiredPermission>?> = _missingPermissions

    // Permission Flow State Management
    private val _permissionFlowState = MutableStateFlow(PermissionFlowState())
    val permissionFlowState: StateFlow<PermissionFlowState> = _permissionFlowState

    // Permission Revoked Dialog State
    // Shows dialog when a previously granted permission is revoked
    private val _showRevokedDialog = MutableStateFlow<List<RequiredPermission>>(emptyList())
    val showRevokedDialog: StateFlow<List<RequiredPermission>> = _showRevokedDialog

    // Track if permission flow is active to prevent duplicate requests
    private var isPermissionFlowActive = false
        set(value) {
            field = value
            _permissionFlowState.value = _permissionFlowState.value.copy(isActive = value)
        }

    // Store game user wants to install while requesting permissions
    private var pendingInstallAfterPermissions: String? = null
        set(value) {
            field = value
            _permissionFlowState.value = _permissionFlowState.value.copy(pendingGameInstall = value)
        }

    private val _visibleIndices = MutableStateFlow<List<Int>>(emptyList())
    private val priorityUpdateChannel = Channel<Unit>(Channel.CONFLATED)

    private val _isUpdateCheckInProgress = MutableStateFlow(true)
    val isUpdateCheckInProgress: StateFlow<Boolean> = _isUpdateCheckInProgress

    private val _isUpdateDialogShowing = MutableStateFlow(false)
    val isUpdateDialogShowing: StateFlow<Boolean> = _isUpdateDialogShowing

    private val _isUpdateDownloading = MutableStateFlow(false)
    val isUpdateDownloading: StateFlow<Boolean> = _isUpdateDownloading

    private val _isCatalogUpdateAvailable = MutableStateFlow(prefs.getBoolean("catalog_update_available", false))
    val isCatalogUpdateAvailable: StateFlow<Boolean> = _isCatalogUpdateAvailable

    private val _catalogUpdateCount = MutableStateFlow(prefs.getInt("catalog_update_count", 0))
    val catalogUpdateCount: StateFlow<Int> = _catalogUpdateCount

    private val _catalogSyncProgress = MutableStateFlow<Float?>(null)
    val catalogSyncProgress: StateFlow<Float?> = _catalogSyncProgress

    private val _updateProgress = MutableStateFlow("")
    val updateProgress: StateFlow<String> = _updateProgress

    private val _keepApks = MutableStateFlow(prefs.getBoolean("keep_apks", false))
    val keepApks: StateFlow<Boolean> = _keepApks

    private val _isAppVisible = MutableStateFlow(false)

    // Queue Management (v2.5.0 - Room DB as source of truth)
    // Single StateFlow derived from Room DB - installQueue IS the source of truth for UI
    // Internal code uses installQueue.value for reads; Room DB updates propagate automatically
    // OPTIMIZATION: Uses batch query to fetch all game metadata in 1 DB call instead of N+1
    @OptIn(ExperimentalCoroutinesApi::class)
    val installQueue: StateFlow<List<InstallTaskState>> = repository.getAllQueuedInstalls()
        .combine(_missingPermissions) { entities, missing ->
            entities to (missing ?: emptyList())
        }
        .flatMapLatest { (entities, missing) ->
            flow {
                if (entities.isEmpty()) {
                    emit(emptyList())
                    return@flow
                }

                // BATCH QUERY: Fetch all game metadata in a single DB call (avoids N+1 problem)
                val releaseNames = entities.map { it.releaseName }
                val gameDataCache = repository.getGamesByReleaseNames(releaseNames)

                // Convert entities using cached metadata (O(1) lookup per entity)
                val tasks = entities.mapNotNull { entity ->
                    runCatching {
                        val context = getApplication<Application>()
                        entity.toInstallTaskState(context, gameDataCache, missing)
                    }.getOrElse {
                        Log.e(TAG, "Failed to convert entity to task state: ${entity.releaseName}", it)
                        null
                    }
                }
                emit(tasks)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Exposes the animation state derived from installQueue for UI stickman animations.
     * This StateFlow emits animation states that can be observed by the UI layer.
     */
    val animationState: StateFlow<AnimationState> = installQueue
        .map { queue ->
            // Find the first active task in the queue
            val activeTask = queue.firstOrNull { task ->
                task.status == InstallTaskStatus.DOWNLOADING ||
                task.status == InstallTaskStatus.EXTRACTING ||
                task.status == InstallTaskStatus.INSTALLING ||
                task.status == InstallTaskStatus.PAUSED ||
                task.status == InstallTaskStatus.BLOCKED_BY_PERMISSIONS
            }

            when {
                // No active tasks - distinguish between empty queue and completed tasks
                activeTask == null -> {
                    if (queue.isEmpty()) {
                        AnimationState.Idle(AnimationState.IdleReason.NO_ACTIVE_TASK)
                    } else {
                        // Queue has tasks but none are active - check if all completed
                        val hasCompletedTasks = queue.any { it.status == InstallTaskStatus.COMPLETED }
                        if (hasCompletedTasks) {
                            AnimationState.Idle(AnimationState.IdleReason.ALL_TASKS_COMPLETED)
                        } else {
                            AnimationState.Idle(AnimationState.IdleReason.NO_ACTIVE_TASK)
                        }
                    }
                }

                // Map InstallTaskStatus to AnimationState
                // PAUSED - user-initiated pause
                activeTask.status == InstallTaskStatus.PAUSED -> {
                    AnimationState.Paused(AnimationState.PausedReason.USER_REQUESTED)
                }

                // BLOCKED_BY_PERMISSIONS - treat as error/paused state
                activeTask.status == InstallTaskStatus.BLOCKED_BY_PERMISSIONS -> {
                    AnimationState.Paused(AnimationState.PausedReason.ERROR)
                }

                activeTask.status == InstallTaskStatus.DOWNLOADING -> {
                    AnimationState.Downloading(activeTask.progress)
                }

                activeTask.status == InstallTaskStatus.EXTRACTING -> {
                    AnimationState.Extracting(activeTask.progress)
                }

                activeTask.status == InstallTaskStatus.INSTALLING -> {
                    AnimationState.Installing(activeTask.progress)
                }

                // Terminal or non-active states - return to idle
                // QUEUED, PENDING_INSTALL, COMPLETED, FAILED, LOCAL_VERIFYING, SHELVED
                else -> AnimationState.Idle(AnimationState.IdleReason.NO_ACTIVE_TASK)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnimationState.Idle())

    private val _showInstallOverlay = MutableStateFlow(false)
    val showInstallOverlay: StateFlow<Boolean> = _showInstallOverlay

    private val _viewedReleaseName = MutableStateFlow<String?>(null)
    val viewedReleaseName: StateFlow<String?> = _viewedReleaseName

    private val _historyQuery = MutableStateFlow("")
    val historyQuery: StateFlow<String> = _historyQuery

    private val _historyStatusFilter = MutableStateFlow<com.vrhub.data.InstallStatus?>(null)
    val historyStatusFilter: StateFlow<com.vrhub.data.InstallStatus?> = _historyStatusFilter

    private val _historySortMode = MutableStateFlow(HistorySortMode.DATE_DESC)
    val historySortMode: StateFlow<HistorySortMode> = _historySortMode

    private val _historyDateFilter = MutableStateFlow(HistoryDateFilter.ALL)
    val historyDateFilter: StateFlow<HistoryDateFilter> = _historyDateFilter

    private val _historyLimit = MutableStateFlow(50)

    val installHistory: StateFlow<List<com.vrhub.data.InstallHistoryEntity>> =
        combine(_historyQuery, _historyStatusFilter, _historyLimit, _historySortMode, _historyDateFilter) { query, status, limit, sort, dateFilter ->
            val queryToUse = if (query.isBlank()) null else query
            val minTimestamp = when (dateFilter) {
                HistoryDateFilter.ALL -> null
                HistoryDateFilter.LAST_7_DAYS -> {
                    java.time.LocalDate.now()
                        .minusDays(7)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
                HistoryDateFilter.LAST_30_DAYS -> {
                    java.time.LocalDate.now()
                        .minusDays(30)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
                HistoryDateFilter.LAST_3_MONTHS -> {
                    java.time.LocalDate.now()
                        .minusMonths(3)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
            }
            repository.db.installHistoryDao().searchAndFilterFlowWithLimitAndSort(
                queryToUse,
                status,
                minTimestamp,
                limit,
                sort.name
            )
        }
        .flatMapLatest { it }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyStats: StateFlow<HistoryStats?> = installHistory
        .flatMapLatest { _ -> // Re-calculate when history changes
            flow {
                try {
                    val dao = repository.db.installHistoryDao()
                    val successCount = dao.getCountByStatus(com.vrhub.data.InstallStatus.COMPLETED)
                    val failedCount = dao.getCountByStatus(com.vrhub.data.InstallStatus.FAILED)
                    val totalCount = successCount + failedCount

                    if (totalCount == 0) {
                        emit(null)
                        return@flow
                    }

                    val avgDuration = dao.getAverageDuration(com.vrhub.data.InstallStatus.COMPLETED)
                    val totalSize = dao.getTotalDownloadedSize(com.vrhub.data.InstallStatus.COMPLETED)
                    val topGames = dao.getMostInstalledGames(3)
                    val errorSummary = dao.getErrorSummary(5)

                    emit(HistoryStats(
                        successRate = if (totalCount > 0) successCount.toFloat() / totalCount else 0f,
                        averageDurationMs = avgDuration,
                        totalDownloadedBytes = totalSize,
                        topGames = topGames.map { it.gameName },
                        errorSummary = errorSummary.associate { it.errorMessage to it.count }
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to calculate history stats", e)
                    emit(null) // Emit null on error instead of crashing the flow
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val canLoadMoreHistory: StateFlow<Boolean> = installHistory.map { history ->
        // Only allow loading more if we have at least 'limit' items and haven't reached a reasonable cap
        history.size >= _historyLimit.value && _historyLimit.value < Constants.MAX_HISTORY_LIMIT
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * Updates the history search query with validation.
     * Limits query length to 100 characters AFTER escaping to prevent performance/memory issues.
     *
     * NOTE: This method is responsible for escaping LIKE special characters (%, _, \)
     * to prevent SQL injection or unexpected pattern matching. The DAO provides the
     * corresponding ESCAPE '\' clause.
     */
    fun setHistoryQuery(query: String) {
        // Escape LIKE special characters (%, _, \) using \ as escape character
        val escapedQuery = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        // Truncate to 100 characters AFTER escaping to ensure DB query size is capped
        val validatedQuery = if (escapedQuery.length > 100) {
            var truncated = escapedQuery.take(100)
            // If we truncated in the middle of an escape sequence (trailing odd number of backslashes)
            // we must remove the trailing backslash to avoid Room error.
            var backslashCount = 0
            for (i in truncated.length - 1 downTo 0) {
                if (truncated[i] == '\\') backslashCount++ else break
            }
            if (backslashCount % 2 != 0) {
                truncated = truncated.dropLast(1)
            }
            truncated
        } else escapedQuery

        if (_historyQuery.value != validatedQuery) {
            _historyQuery.value = validatedQuery
            _historyLimit.value = 50 // Reset pagination
        }
    }

    fun setHistoryStatusFilter(status: com.vrhub.data.InstallStatus?) {
        if (_historyStatusFilter.value != status) {
            _historyStatusFilter.value = status
            _historyLimit.value = Constants.HISTORY_PAGE_SIZE // Reset pagination
        }
    }

    fun setHistorySortMode(mode: HistorySortMode) {
        if (_historySortMode.value != mode) {
            _historySortMode.value = mode
            _historyLimit.value = Constants.HISTORY_PAGE_SIZE // Reset pagination
        }
    }

    fun setHistoryDateFilter(filter: HistoryDateFilter) {
        if (_historyDateFilter.value != filter) {
            _historyDateFilter.value = filter
            _historyLimit.value = Constants.HISTORY_PAGE_SIZE // Reset pagination
        }
    }

    fun loadMoreHistory() {
        // Increment limit by HISTORY_PAGE_SIZE, but cap to prevent excessive memory usage
        if (_historyLimit.value < Constants.MAX_HISTORY_LIMIT) {
            _historyLimit.value += Constants.HISTORY_PAGE_SIZE
        } else {
            // Notify user that maximum history limit has been reached
            viewModelScope.launch {
                _events.emit(MainEvent.ShowMessage("Maximum history limit reached (${Constants.MAX_HISTORY_LIMIT} items)"))
            }
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.db.installHistoryDao().deleteById(id)
                _events.emit(MainEvent.ShowMessage("Entry deleted from history"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete history entry", e)
                _events.emit(MainEvent.ShowMessage("Failed to delete entry"))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.db.installHistoryDao().deleteAll()
                _events.emit(MainEvent.ShowMessage("Installation history cleared"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear history", e)
                _events.emit(MainEvent.ShowMessage("Failed to clear history"))
            }
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _events.emit(MainEvent.ShowMessage(message))
        }
    }

    fun exportHistory(format: String = "txt") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _events.emit(MainEvent.ShowMessage("Exporting history ($format)..."))
                val (filePath, scannerSuccess) = repository.exportHistory(format)
                
                if (scannerSuccess) {
                    _events.emit(MainEvent.ShowMessage("History exported to: $filePath"))
                } else {
                    _events.emit(MainEvent.ShowMessage("Exported to $filePath (may take a moment to appear in file picker)"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export history", e)
                _events.emit(MainEvent.ShowMessage("Failed to export history: ${e.message}"))
            }
        }
    }

    private var queueProcessorJob: Job? = null
    private var currentTaskJob: Job? = null
    private var activeReleaseName: String? = null

    // Map of releaseName -> observation Job to prevent coroutine accumulation
    // When observeDownloadWork is called for a releaseName that's already being observed,
    // the previous Job is cancelled before starting a new one. This prevents memory leaks
    // and duplicate processing that could occur on app restart or task resumption.
    private val downloadObserverJobs = mutableMapOf<String, Job>()

    // Map of task completion signals, keyed by releaseName.
    // Each CompletableDeferred signals when that task's extraction/installation is complete.
    // Allows runTask() to suspend until the full install pipeline finishes.
    //
    // Changed from mutableMapOf to ConcurrentHashMap for thread-safety
    // Multiple coroutines may access this map concurrently (queue processor, download observers, cancellation)
    //
    // This design supports:
    // 1. Task-specific signal management (no state collision between tasks)
    // 2. Clean cancellation on pause/cancel without affecting other tasks
    // 3. Easy lookup and cleanup for any task by releaseName
    // 4. Future extensibility if parallel task execution is ever needed
    private val taskCompletionSignals = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    // Channel to signal when a new task has been added to the queue.
    // Changed from CONFLATED to RENDEZVOUS to prevent signal loss
    // This solves the race condition where startQueueProcessor() exits before
    // the StateFlow has emitted the newly inserted task from Room DB.
    // RENDEZVOUS ensures no signals are lost during rapid task additions,
    // with backpressure handling via trySend() which returns false if buffer is full.
    private val taskAddedSignal = Channel<Unit>(Channel.RENDEZVOUS)

    // Mutex to prevent concurrent executions of verifyPendingInstallations
    // Prevents race condition when multiple triggers
    // (app startup, resume, user action) attempt verification simultaneously
    private val verificationMutex = Mutex()

    // Prevents race condition in permission checks when multiple triggers
    // (app startup, resume, user action) attempt permission verification simultaneously
    private val permissionCheckMutex = Mutex()

    // Duplicate isPermissionFlowActive declaration removed
    private var previousMissingCount = 0
    // Added flag to track if permission denial message was shown
    // This prevents race conditions where multiple onAppResume events could trigger
    // false "Permission denied" messages. The flag is reset when permission flow starts.
    private var permissionDenialShown = false
    private var refreshJob: Job? = null
    private var sizeFetchJob: Job? = null

    private val updateService = NetworkModule.updateService
    private val githubReleaseService = NetworkModule.githubReleaseService

    // Use shared OkHttpClient from NetworkModule (singleton)
    private val okHttpClient = NetworkModule.okHttpClient

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "catalog_update_available" || key == "catalog_update_count") {
            Log.d(TAG, "SharedPreferences changed: $key, triggering real-time UI update")
            // Optimize: Update StateFlows directly instead of calling checkCatalogUpdate()
            // to avoid redundant preference reads and potential race conditions (Finding 8)
            viewModelScope.launch {
                when (key) {
                    "catalog_update_available" -> _isCatalogUpdateAvailable.value = prefs.getBoolean(key, false)
                    "catalog_update_count" -> _catalogUpdateCount.value = prefs.getInt(key, 0)
                }
            }
        }
    }

    private val _allGames = repository.getAllGamesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filterCounts: StateFlow<Map<FilterStatus, Int>> = combine(
        _allGames,
        _installedPackages,
        _downloadedReleases
    ) { list, installed, downloaded ->
        val counts = mutableMapOf<FilterStatus, Int>()
        counts[FilterStatus.ALL] = list.size

        var favoriteCount = 0
        var installedCount = 0
        var updateCount = 0
        var downloadedCount = 0
        var newCount = 0
        var localInstallCount = 0

        val lastSync = prefs.getLong("last_catalog_sync_time", 0L)
        val queue = installQueue.value

        list.forEach { game ->
            val installedVersion = installed[game.packageName]
            val catalogVersion = game.versionCode.toLongOrNull() ?: 0L

            if (game.isFavorite) {
                favoriteCount++
            }

            if (installedVersion != null) {
                installedCount++
                if (catalogVersion > installedVersion) {
                    updateCount++
                }
            }

            if (downloaded.contains(game.releaseName)) {
                downloadedCount++
            }

            if (game.lastUpdated > lastSync && lastSync != 0L) {
                newCount++
            }

            val task = queue.find { it.releaseName == game.releaseName }
            if (task?.status == InstallTaskStatus.SHELVED || task?.status == InstallTaskStatus.PENDING_INSTALL) {
                localInstallCount++
            }
        }

        counts[FilterStatus.FAVORITES] = favoriteCount
        counts[FilterStatus.INSTALLED] = installedCount
        counts[FilterStatus.DOWNLOADED] = downloadedCount
        counts[FilterStatus.UPDATE_AVAILABLE] = updateCount
        counts[FilterStatus.NEW] = newCount
        counts[FilterStatus.LOCAL_INSTALLS] = localInstallCount
        counts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @Suppress("UNCHECKED_CAST")
    val games: StateFlow<List<GameItemState>> = combine(
        _allGames,
        _searchQuery,
        _installedPackages,
        _downloadedReleases,
        _selectedFilter,
        _sortMode,
        installQueue
    ) { args ->
        val list = args[0] as List<GameData>
        val query = args[1] as String
        val installed = args[2] as Map<String, Long>
        val downloaded = args[3] as Set<String>
        val filter = args[4] as FilterStatus
        val sort = args[5] as SortMode
        val queue = args[6] as List<InstallTaskState>

        val firstInQueue = queue.firstOrNull()?.releaseName
        val lastSync = prefs.getLong("last_catalog_sync_time", 0L)

        val filteredList = list.filter { game ->
            val installedVersion = installed[game.packageName]
            val catalogVersion = game.versionCode.toLongOrNull() ?: 0L
            val status = when {
                installedVersion == null -> InstallStatus.NOT_INSTALLED
                catalogVersion > installedVersion -> InstallStatus.UPDATE_AVAILABLE
                else -> InstallStatus.INSTALLED
            }
            val isDownloaded = downloaded.contains(game.releaseName)

            val matchesQuery = query.isBlank() ||
                game.gameName.contains(query, ignoreCase = true) ||
                game.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                FilterStatus.FAVORITES -> game.isFavorite
                FilterStatus.INSTALLED -> status != InstallStatus.NOT_INSTALLED
                FilterStatus.DOWNLOADED -> isDownloaded
                FilterStatus.UPDATE_AVAILABLE -> status == InstallStatus.UPDATE_AVAILABLE
                FilterStatus.NEW -> game.lastUpdated > lastSync && lastSync != 0L
                FilterStatus.LOCAL_INSTALLS -> {
                    val task = queue.find { it.releaseName == game.releaseName }
                    task?.status == InstallTaskStatus.SHELVED || task?.status == InstallTaskStatus.PENDING_INSTALL
                }
                FilterStatus.ALL -> true
            }

            matchesQuery && matchesFilter
        }

        val sortedList = when (sort) {
            SortMode.NAME_ASC -> filteredList.sortedWith(compareBy<GameData> {
                getAlphabetSortPriority(getAlphabetGroupChar(it.gameName))
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.gameName })
            SortMode.NAME_DESC -> filteredList.sortedByDescending { it.gameName }
            SortMode.LAST_UPDATED -> filteredList.sortedByDescending { it.lastUpdated }
            SortMode.SIZE -> filteredList.sortedByDescending { it.sizeBytes ?: 0L }
            SortMode.POPULARITY -> filteredList.sortedByDescending { it.popularity }
        }

        sortedList.map { game ->
            val installedVersion = installed[game.packageName]
            val catalogVersion = game.versionCode.toLongOrNull() ?: 0L

            val status = when {
                installedVersion == null -> InstallStatus.NOT_INSTALLED
                catalogVersion > installedVersion -> InstallStatus.UPDATE_AVAILABLE
                else -> InstallStatus.INSTALLED
            }

            val isDownloaded = downloaded.contains(game.releaseName)
            val queueTask = queue.find { it.releaseName == game.releaseName }

            val iconLocations = listOf(
                File(repository.iconsDir, "${game.packageName}.png"),
                File(repository.iconsDir, "${game.packageName}.jpg"),
                File(repository.thumbnailsDir, "${game.packageName}.png"),
                File(repository.thumbnailsDir, "${game.packageName}.jpg"),
                File(repository.thumbnailsDir, "${game.packageName}.jpeg")
            )

            val iconFile = iconLocations.find { it.exists() }

            GameItemState(
                name = game.gameName,
                version = game.versionCode,
                installedVersion = installedVersion?.toString(),
                packageName = game.packageName,
                releaseName = game.releaseName,
                iconFile = iconFile,
                installStatus = status,
                queueStatus = queueTask?.status,
                isFirstInQueue = game.releaseName == firstInQueue,
                isDownloaded = isDownloaded,
                isFavorite = game.isFavorite,
                size = if (game.sizeBytes != null && game.sizeBytes > 0) InstallUtils.formatBytes(game.sizeBytes) else if (game.sizeBytes == -1L) "Error" else null,
                sizeBytes = game.sizeBytes ?: 0L,
                description = game.description,
                screenshotUrls = game.screenshotUrls,
                lastUpdated = game.lastUpdated,
                popularity = game.popularity
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alphabetInfo: StateFlow<Pair<List<Char>, Map<Char, Int>>> = games
        .map { list ->
            val chars = mutableListOf<Char>()
            val charToIndex = mutableMapOf<Char, Int>()
            list.forEachIndexed { index, game ->
                val mappedChar = getAlphabetGroupChar(game.name)
                if (!charToIndex.containsKey(mappedChar)) {
                    chars.add(mappedChar)
                    charToIndex[mappedChar] = index
                }
            }
            chars to charToIndex
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Char>() to emptyMap<Char, Int>())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Composite Install State for legacy UI support
    val installState: StateFlow<InstallState> = installQueue.map { queue ->
        val active = queue.find { it.status.isProcessing() }
        if (active != null) {
            InstallState(
                isInstalling = true,
                packageName = active.packageName,
                gameName = active.gameName,
                message = active.message,
                progress = active.progress,
                currentSize = active.currentSize,
                totalSize = active.totalSize
            )
        } else {
            InstallState(isInstalling = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstallState())

        init {
            // Register listener for catalog updates
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            // Perform immediate check to ensure initial state consistency (Story 4.3 Round 14 Fix)
            checkCatalogUpdate()
    
            // Initialize PermissionManager with application context
            com.vrhub.data.PermissionManager.init(getApplication())
        // Schedule background catalog update check
        scheduleCatalogUpdateWorker()

        // Load and validate saved permission states on startup
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                // Validate saved states against actual permissions (detect revocation)
                // Use ValidationResult to distinguish between revocation and manual grant
                val validationResult = com.vrhub.data.PermissionManager.validateSavedStates(context)

                if (!validationResult.isValid) {
                    // Check if permissions were revoked (granted -> denied)
                    if (validationResult.revokedPermissions.isNotEmpty()) {
                        // Permissions were revoked, show revocation dialog
                        pendingInstallAfterPermissions = null
                        _showRevokedDialog.value = validationResult.revokedPermissions
                    }
                    // Note: If permissions were manually granted (denied -> granted),
                    // we don't need to show anything, just update the state
                }

                // validateSavedStates() already detects permission changes and updates state
                // The UI state will be updated when user interacts with the app or on resume
                // checkPermissions() is called from onAppResume() when needed
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing permission state", e)
            }
        }

        // Run v2.4.0 queue migration first (if needed)
        viewModelScope.launch {
            try {
                val migratedCount = repository.migrateLegacyQueueIfNeeded()
                if (migratedCount > 0) {
                    Log.i(TAG, "Migrated $migratedCount items from v2.4.0 queue")
                    _events.emit(MainEvent.ShowMessage("Restored $migratedCount queued items from previous version"))
                } else if (migratedCount < 0) {
                    // Migration returned error code (-1)
                    Log.w(TAG, "Migration returned error code: $migratedCount")
                    _events.emit(MainEvent.ShowMessage("Note: Could not restore previous download queue"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed, continuing with empty queue", e)
                _events.emit(MainEvent.ShowMessage("Note: Could not restore previous download queue"))
            }
        }

        viewModelScope.launch {
            _isUpdateCheckInProgress.value = true
            Log.d(TAG, "Startup: Starting background initialization")
            try {
                // Trigger initial catalog sync in parallel if database is empty
                // This ensures the catalog starts loading immediately without waiting for update check
                launch {
                    val gameCount = repository.db.gameDao().getCount()
                    if (gameCount == 0) {
                        Log.i(TAG, "Startup: Catalog is empty, triggering auto-refresh in parallel")
                        refreshData()
                    } else {
                        Log.d(TAG, "Startup: Catalog already has $gameCount games")
                    }
                }

                // Add timeout to prevent hanging on update check if network is slow/unreachable
                Log.d(TAG, "Startup: Checking for app updates...")
                val updateAvailable = withTimeoutOrNull(5000) { checkForAppUpdates() } ?: false
                Log.d(TAG, "Startup: Update check complete, available: $updateAvailable")

                if (!updateAvailable) {
                    refreshInstalledPackages()
                    refreshDownloadedReleases()
                    startMetadataFetchLoop()
                    repository.verifyAndCleanupInstalls()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Startup: Error during initialization", e)
            } finally {
                _isUpdateCheckInProgress.value = false
                Log.d(TAG, "Startup: Background initialization complete")
            }
        }

        // Auto-refresh downloaded and installed status when catalog is loaded or changes
        viewModelScope.launch {
            _allGames.collect { games ->
                if (games.isNotEmpty()) {
                    refreshDownloadedReleases(games)
                    refreshInstalledPackages()
                }
            }
        }

        // Resume observation of any in-progress WorkManager downloads after app restart
        viewModelScope.launch {
            resumeActiveDownloadObservations()
        }
    }

    /**
     * Resumes observation of any in-progress downloads after app restart.
     * WorkManager automatically re-enqueues work on device reboot/process death,
     * but we need to re-observe the WorkInfo to handle completion/failure.
     *
     * CRITICAL: Also handles zombie states (EXTRACTING/INSTALLING) that can occur
     * when the app is killed during post-download processing.
     *
     * Recovery strategies:
     * - EXTRACTING with completed marker: Reset to QUEUED to be processed sequentially by queue processor
     * - EXTRACTING/INSTALLING without marker: Reset to QUEUED for full retry
     * - DOWNLOADING/QUEUED: Resume WorkManager observation or re-enqueue
     *
     * CONCURRENCY FIX: All zombie tasks are reset to QUEUED and processed by the queue processor
     * one at a time, preventing parallel extractions that would violate sequential queue logic.
     */
    private suspend fun resumeActiveDownloadObservations() {
        // CRITICAL FIX: Use repository.getAllQueuedInstalls().first() instead of StateFlow
        // to ensure we wait for DB emission regardless of time.
        //
        // The StateFlow (installQueue) is created with stateIn(..., emptyList()) and uses
        // flowOn(Dispatchers.IO), which creates a race condition window where the StateFlow
        // hasn't received Room's initial emission yet. Using .first() on the repository Flow
        // guarantees we wait for Room to emit, whether the queue is empty or has tasks.
        //
        // This fix prevents the startup race condition where resumeActiveDownloadObservations
        // would exit early thinking the queue is empty, when Room simply hasn't emitted yet.
        val activeQueue = repository.getAllQueuedInstalls()
            .first() // Wait for Room DB to emit (blocks until first emission from Room)
            .mapNotNull { entity ->
                // Convert QueuedInstallEntity to InstallTaskState
                runCatching {
                    val gameData = repository.getGameByReleaseName(entity.releaseName)
                    val context = getApplication<Application>()
                    entity.toInstallTaskState(
                        context = context,
                        gameDataCache = mapOf(entity.releaseName to (gameData ?: return@mapNotNull null)),
                        missingPermissions = _missingPermissions.value ?: emptyList()
                    )
                }.getOrElse {
                    Log.e(TAG, "Failed to convert entity during resume: ${entity.releaseName}", it)
                    null
                }
            }

        // Handle zombie states first (EXTRACTING/INSTALLING stuck after app kill)
        // CRITICAL: Reset ALL zombies to QUEUED to ensure sequential processing via queue processor
        // This prevents the concurrency bug where multiple zombies could trigger parallel extractions
        val zombieTasks = activeQueue.filter {
            it.status == InstallTaskStatus.EXTRACTING || it.status == InstallTaskStatus.INSTALLING
        }

        if (zombieTasks.isNotEmpty()) {
            Log.w(TAG, "Found ${zombieTasks.size} zombie task(s) stuck in EXTRACTING/INSTALLING state - resetting to QUEUED")
            for (task in zombieTasks) {
                handleZombieTaskRecovery(task)
            }
        }

        // Handle PENDING_INSTALL tasks (waiting for user, but app was killed)
        // Move them to SHELVED so they appear in Local Installs tab and don't block the UI queue
        val pendingInstalls = activeQueue.filter { it.status == InstallTaskStatus.PENDING_INSTALL }
        if (pendingInstalls.isNotEmpty()) {
            Log.i(TAG, "Found ${pendingInstalls.size} task(s) in PENDING_INSTALL - shelving them")
            for (task in pendingInstalls) {
                repository.updateQueueStatus(task.releaseName, com.vrhub.data.InstallStatus.SHELVED)
            }
        }

        // Discover orphaned staged APKs (Story 1.13 Review Fix)
        // These are APKs in externalFilesDir that are not in our queue.
        // They are added as SHELVED so they appear in Local Installs.
        discoverOrphanedStagedApks()

        // Now handle downloading/queued tasks
        val inProgressTasks = activeQueue.filter {
            it.status == InstallTaskStatus.DOWNLOADING || it.status == InstallTaskStatus.QUEUED
        }

        if (inProgressTasks.isEmpty() && zombieTasks.isEmpty()) {
            Log.d(TAG, "No in-progress downloads to resume observation for")
            return
        }

        Log.i(TAG, "Resuming observation for ${inProgressTasks.size} active download(s)")

        for (task in inProgressTasks) {
            // Check if WorkManager has an active work for this task
            val isWorkActive = repository.isDownloadWorkActive(task.releaseName)

            if (isWorkActive) {
                // Resume observation of this work
                Log.d(TAG, "Resuming observation for: ${task.releaseName}")
                observeDownloadWork(task.releaseName, task.gameName)
            } else {
                // Work not found in WorkManager - either completed/failed while app was dead
                // or was never enqueued. Check Room DB status and decide action.
                Log.d(TAG, "No active work found for: ${task.releaseName}, re-enqueueing")

                if (task.status == InstallTaskStatus.DOWNLOADING) {
                    // Was downloading but work is gone - reset to QUEUED and restart
                    repository.updateQueueStatus(task.releaseName, com.vrhub.data.InstallStatus.QUEUED)
                }
            }
        }

        // Start queue processor to pick up any QUEUED tasks (including reset zombies)
        startQueueProcessor()
    }

    /**
     * Scans the staged APKs directory for orphaned APKs and adds them to the queue as SHELVED.
     * This ensures that games ready for installation but missing from the queue (e.g., after DB clear)
     * are discovered and displayed in the "Ready to Install" view.
     *
     * Orphaned APKs are identified by matching the filename (packageName.apk) against the catalog.
     * If a match is found and the APK is valid, it is added to the queue with SHELVED status.
     *
     * This method is called during app initialization (resumeActiveDownloadObservations).
     *
     * **Usage Example:**
     * ```kotlin
     * // Manually trigger discovery of orphaned APKs (e.g., after manual file transfer)
     * viewModelScope.launch {
     *     discoverOrphanedStagedApks()
     * }
     * ```
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun discoverOrphanedStagedApks() {
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val dir = context.getExternalFilesDir(null) ?: return@withContext
                val apkFiles = dir.listFiles { file -> file.extension == "apk" } ?: return@withContext

                Log.d(TAG, "Scanning for orphaned staged APKs in ${dir.absolutePath}")

                for (file in apkFiles) {
                    val packageName = file.nameWithoutExtension
                    
                    // Find game in catalog to get its releaseName
                    val game = repository.getGameByPackageName(packageName)
                    if (game != null) {
                        // CRITICAL: Refresh queue snapshot inside the loop to prevent race condition (Story 1.13 Review Fix)
                        // This ensures we don't add duplicate entries if multiple orphaned APKs for same release exist
                        // or if the queue changes during discovery.
                        val currentQueue = repository.getAllQueuedInstalls().first()
                        val existing = currentQueue.find { it.releaseName == game.releaseName }
                        
                        if (existing == null) {
                            // Not in queue - check if it's a valid APK for this game (verifying integrity)
                            val expectedVersion = game.versionCode.toLongOrNull()
                            if (repository.isValidApkFile(file, packageName, expectedVersion)) {
                                Log.i(TAG, "Discovered orphaned staged APK for ${game.gameName} ($packageName), adding to queue as SHELVED")
                                repository.addToQueue(
                                    releaseName = game.releaseName,
                                    status = com.vrhub.data.InstallStatus.SHELVED,
                                    isDownloadOnly = false
                                )
                            } else {
                                // Add debug logging for invalid orphaned APKs (Story 1.13 Review Fix)
                                Log.d(TAG, "Skipping orphaned APK $packageName: integrity check failed (invalid APK or version mismatch)")
                            }
                        } else {
                            Log.d(TAG, "Skipping orphaned APK $packageName: already in queue with status ${existing.status}")
                        }
                    } else {
                        Log.d(TAG, "Skipping orphaned APK $packageName: package not found in catalog")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to discover orphaned staged APKs", e)
            }
        }
    }

    /**
     * Handles recovery of a zombie task (EXTRACTING/INSTALLING state after app kill).
     *
     * CRITICAL: ALL zombie tasks are reset to QUEUED to ensure sequential processing.
     * This prevents the concurrency bug where multiple zombies could trigger parallel extractions.
     *
     * The queue processor will pick up QUEUED tasks one at a time and:
     * - For tasks with staged APK in externalFilesDir: Skip extraction, proceed to installation
     * - For tasks with completed extraction: Skip download and proceed to installation
     * - For tasks without extraction: Full restart via WorkManager
     *
     * Enhanced: Checks for staged APK in externalFilesDir to skip extraction phase entirely.
     */
    private suspend fun handleZombieTaskRecovery(task: InstallTaskState) = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val tempInstallRoot = File(context.filesDir, "install_temp")

        // Compute hash for temp directory (matches MainRepository logic)
        val hash = com.vrhub.data.CryptoUtils.md5(task.releaseName + "\n")
        val gameTempDir = File(tempInstallRoot, hash)
        val extractionMarker = File(gameTempDir, "extraction_done.marker")
        val extractionDir = File(gameTempDir, "extracted")

        // Check for staged APK in externalFilesDir (already extracted and ready for install)
        // Uses packageName.apk naming convention to prevent cross-contamination between installation tasks
        // Validates APK integrity (including package name and version match) using repository helper
        val game = repository.getGameByReleaseName(task.releaseName)
        val expectedVersion = game?.versionCode?.toLongOrNull()
        val stagedApk = repository.getValidStagedApk(task.packageName, expectedVersion)

        // Log recovery state for debugging
        when {
            stagedApk != null -> {
                Log.i(TAG, "Zombie recovery: staged APK found (${stagedApk.name}) for ${task.releaseName}, will launch installer directly")
            }
            extractionMarker.exists() && extractionDir.exists() -> {
                Log.i(TAG, "Zombie recovery: extraction complete for ${task.releaseName}, will resume at installation phase")
            }
            gameTempDir.exists() && gameTempDir.listFiles()?.isNotEmpty() == true -> {
                Log.i(TAG, "Zombie recovery: partial download found for ${task.releaseName}, will resume download")
            }
            else -> {
                Log.i(TAG, "Zombie recovery: no recoverable state for ${task.releaseName}, full restart")
            }
        }

        // ALWAYS reset to QUEUED - queue processor handles sequential execution
        // This fixes the concurrency bug where multiple handleDownloadSuccess() calls
        // could run in parallel if we called it directly here
        repository.updateQueueStatus(task.releaseName, com.vrhub.data.InstallStatus.QUEUED)
    }

    fun toggleFavorite(releaseName: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(releaseName, isFavorite)
        }
    }

    fun refreshInstalledPackages() {
        viewModelScope.launch {
            try {
                val installed = withContext(Dispatchers.IO) {
                    repository.getInstalledPackagesMap()
                }
                _installedPackages.value = installed
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh installed packages", e)
            }
        }
    }

    fun refreshDownloadedReleases(gamesOverride: List<GameData>? = null) {
        viewModelScope.launch {
            try {
                val downloaded = withContext(Dispatchers.IO) {
                    val dir = repository.downloadsDir
                    if (!dir.exists()) return@withContext emptySet<String>()

                    val games = gamesOverride ?: _allGames.value
                    if (games.isEmpty()) return@withContext emptySet<String>()

                    val releaseNamesWithFolders = dir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()

                    games.filter { game ->
                        val safeDirName = game.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                        releaseNamesWithFolders.contains(safeDirName)
                    }.map { it.releaseName }.toSet()
                }
                _downloadedReleases.value = downloaded
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh downloaded releases", e)
            }
        }
    }

    private suspend fun checkForAppUpdates(): Boolean {
        // Try GitHub API first (primary), fall back to Netlify only on 404 (repo transferred/deleted)
        val githubResult = checkGitHubReleasesWithBackoff()

        return when (githubResult) {
            is GitHubApiResult.Success -> {
                val currentVersion = BuildConfig.VERSION_NAME
                val latestClean = githubResult.updateInfo.version.lowercase().removePrefix("v")
                val currentClean = currentVersion.lowercase().removePrefix("v")
                if (isVersionNewer(latestClean, currentClean)) {
                    _isUpdateDialogShowing.value = true
                    _events.emit(MainEvent.ShowUpdatePopup(githubResult.updateInfo))
                    true
                } else {
                    false
                }
            }
            is GitHubApiResult.NotFound -> {
                // 404 from GitHub = repo transferred/deleted - fall back to Netlify
                Log.w(TAG, "GitHub repo not found (404), falling back to Netlify update service")
                checkNetlifyUpdatesWithFallback()
            }
            is GitHubApiResult.RateLimited -> {
                Log.w(TAG, "GitHub API rate limited (403)")
                _error.value = "Update check failed: Too many requests to GitHub. Please wait a few minutes before trying again."
                false
            }
            is GitHubApiResult.ServerError -> {
                Log.w(TAG, "GitHub API server error (${githubResult.code}), retrying with backoff")
                _error.value = "Update check failed: GitHub is temporarily unavailable. Please try again later."
                false
            }
            is GitHubApiResult.ClientError -> {
                Log.w(TAG, "GitHub API client error (${githubResult.code})")
                _error.value = "Update check failed: Request rejected by GitHub (${githubResult.code}). Please try again later."
                false
            }
            is GitHubApiResult.NetworkError -> {
                Log.w(TAG, "GitHub API network error: ${githubResult.message}")
                _error.value = "Update check failed: Network error. Please check your internet connection and try again."
                false
            }
        }
    }

    /**
     * Checks GitHub Releases API with retry and exponential backoff.
     *
     * IMPORTANT: Rate limits (403) and temporary errors (5xx, IOException) trigger retry with backoff
     * on GitHub API, NOT fallback to Netlify. Netlify fallback is ONLY for 404 (repo transferred/deleted).
     *
     * @return [GitHubApiResult] indicating success or specific error type
     */
    private suspend fun checkGitHubReleasesWithBackoff(): GitHubApiResult {
        val githubOwner = com.vrhub.data.Constants.GITHUB_OWNER
        val githubRepo = com.vrhub.data.Constants.GITHUB_REPO

        var currentDelay = 1000L
        val maxRetries = com.vrhub.data.Constants.UPDATE_MAX_RETRIES

        repeat(maxRetries) { attempt ->
            try {
                val release = githubReleaseService.getLatestRelease(githubOwner, githubRepo, "VRHub/1.0")
                // Successfully got GitHub release - convert to UpdateInfo
                val updateInfo = convertGitHubReleaseToUpdateInfo(release)
                return GitHubApiResult.Success(updateInfo)
            } catch (e: retrofit2.HttpException) {
                when (e.code()) {
                    403 -> {
                        // Rate limited - should NOT fall back to Netlify, retry with backoff
                        val remaining = e.response()?.raw()?.header("X-RateLimit-Remaining")?.toIntOrNull()
                        val resetTime = e.response()?.raw()?.header("X-RateLimit-Reset")
                        Log.w(TAG, "GitHub API rate limited (403), remaining: $remaining, retrying in ${currentDelay}ms...")
                        if (attempt < maxRetries - 1) {
                            kotlinx.coroutines.delay(currentDelay)
                            currentDelay *= 2
                        } else {
                            return GitHubApiResult.RateLimited(remaining ?: 0, resetTime)
                        }
                    }
                    404 -> {
                        // Not found - repo transferred/deleted = fall back to Netlify
                        return GitHubApiResult.NotFound("Repository not found on GitHub")
                    }
                    in 500..599 -> {
                        // Server error - retry with backoff, NOT fallback
                        Log.w(TAG, "GitHub API server error (${e.code()}), retrying in ${currentDelay}ms...")
                        if (attempt < maxRetries - 1) {
                            kotlinx.coroutines.delay(currentDelay)
                            currentDelay *= 2
                        } else {
                            return GitHubApiResult.ServerError(e.code(), "Server error")
                        }
                    }
                    else -> {
                        // Other client errors (4xx except 403/404) - retry with backoff
                        Log.w(TAG, "GitHub API client error (${e.code()}), retrying in ${currentDelay}ms...")
                        if (attempt < maxRetries - 1) {
                            kotlinx.coroutines.delay(currentDelay)
                            currentDelay *= 2
                        } else {
                            return GitHubApiResult.ClientError(e.code(), e.message() ?: "Client error")
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                // Network error - retry with backoff, NOT fallback
                Log.w(TAG, "GitHub API network error (${e.message}), retrying in ${currentDelay}ms...")
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                } else {
                    return GitHubApiResult.NetworkError(e.message ?: "Network error")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected GitHub API error: ${e.message}")
                return GitHubApiResult.NetworkError(e.message ?: "Unknown error")
            }
        }
        return GitHubApiResult.NetworkError("Max retries exhausted")
    }

    /**
     * Converts a GitHub release response to [UpdateInfo].
     * The APK asset is expected to be named: VRHub_{version}.apk
     */
    private fun convertGitHubReleaseToUpdateInfo(release: com.vrhub.network.GitHubReleaseResponse): UpdateInfo {
        // Find the APK asset (should be named VRHub_{version}.apk)
        val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
            ?: throw IllegalStateException("No APK asset found in GitHub release ${release.tagName}. Please ensure the release includes an APK file.")
        val downloadUrl = apkAsset.browserDownloadUrl
        val version = release.tagName.removePrefix("v")
        val changelog = release.body ?: "Release $version - See GitHub release for details"
        val timestamp = release.publishedAt ?: java.time.Instant.now().toString()

        return UpdateInfo(
            version = version,
            changelog = changelog,
            downloadUrl = downloadUrl,
            checksum = "", // GitHub doesn't provide checksum in API response - could be in release body
            timestamp = timestamp
        )
    }

    /**
     * Falls back to Netlify update service when GitHub repo is not found (404).
     * This handles the case where the repo was transferred to a GitHub organization.
     */
    private suspend fun checkNetlifyUpdatesWithFallback(): Boolean {
        var currentDelay = 1000L
        val maxRetries = com.vrhub.data.Constants.UPDATE_MAX_RETRIES

        repeat(maxRetries) { attempt ->
            try {
                val date = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
                val secret = com.vrhub.data.Constants.VRHUB_UPDATE_SECRET
                val signature = com.vrhub.data.CryptoUtils.hmacSha256(date, secret)

                val latest = updateService.checkUpdate(signature, date)
                val currentVersion = BuildConfig.VERSION_NAME

                val latestClean = latest.version.lowercase().removePrefix("v")
                val currentClean = currentVersion.lowercase().removePrefix("v")

                return if (isVersionNewer(latestClean, currentClean)) {
                    _isUpdateDialogShowing.value = true
                    _events.emit(MainEvent.ShowUpdatePopup(latest))
                    true
                } else {
                    false
                }
            } catch (e: retrofit2.HttpException) {
                // Distinguish between server errors (5xx) and client errors (4xx)
                val isServerError = e.code() >= 500 && e.code() < 600
                val isClientError = e.code() >= 400 && e.code() < 500 && e.code() != 403
                // 404 is permanent - endpoint not found should not be retried
                val isNotFoundError = e.code() == 404
                // 408 = Request Timeout - should not retry
                val isTimeoutError = e.code() == 408
                // 429 = Too Many Requests - should not retry, will resolve with time
                val isRateLimitError = e.code() == 429

                if (e.code() == 403) {
                    Log.w(TAG, "Update check forbidden (403): Possible clock skew.")
                    _error.value = "Update check failed: Your Quest's system clock may be out of sync. This happens if the device was off for a long time. Please go to Settings -> System -> Date & Time and toggle 'Set time automatically' off and on again."
                    return false
                } else if (isNotFoundError) {
                    // 404 is permanent - don't retry
                    Log.w(TAG, "Update check endpoint not found (404).")
                    _error.value = "Update check failed: Update service not found. Please update the app manually."
                    return false
                } else if (isTimeoutError) {
                    // 408 is retryable but with longer delay - handled in final error
                    Log.w(TAG, "Update check timeout (408).")
                    _error.value = "Update check failed: The request timed out. Please try again later."
                    return false
                } else if (isRateLimitError) {
                    // 429 is retryable but will resolve with time
                    Log.w(TAG, "Update check rate limited (429).")
                    _error.value = "Update check failed: Too many requests. Please wait a few minutes before trying again."
                    return false
                } else if (isClientError && attempt < maxRetries - 1) {
                    Log.w(TAG, "Update check failed client error (${e.code()}), retrying in ${currentDelay}ms...")
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                } else if (isServerError && attempt < maxRetries - 1) {
                    Log.w(TAG, "Update check failed server error (${e.code()}), retrying in ${currentDelay}ms...")
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                } else {
                    Log.w(TAG, "Update check failed (${e.code()}) after $maxRetries attempts.")
                    _error.value = if (isServerError) {
                        "Update check failed: The update server is temporarily unavailable. Please try again later."
                    } else if (isClientError) {
                        "Update check failed: Request rejected by server (${e.code()}). Please try again later."
                    } else {
                        "Update check failed: Unable to connect to the update server. Please check your internet connection and try again."
                    }
                    return false
                }
            } catch (e: java.io.IOException) {
                // Distinguish between transient and permanent network failures
                // Permanent failures (connection refused, unknown host) don't warrant retries
                val isTransientError = e.message?.let { msg ->
                    // Transient errors that may succeed on retry
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("reset", ignoreCase = true) ||
                    msg.contains("unreachable", ignoreCase = true) ||
                    msg.contains("temporarily", ignoreCase = true)
                } ?: false

                if (isTransientError && attempt < maxRetries - 1) {
                    Log.w(TAG, "Update check transient network error (${e.message}), retrying in ${currentDelay}ms...")
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                } else {
                    // Permanent error (e.g., connection refused) or retries exhausted
                    Log.w(TAG, "Update check failed after $maxRetries attempts: ${e.message}")
                    _error.value = "Update check failed: Network error. Please check your internet connection and try again."
                    return false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error checking for updates: ${e.message}")
                return false
            }
        }
        return false
    }

    /**
     * Compares two version strings to determine if the latest version is newer.
     *
     * This implements SemVer-like comparison following official SemVer precedence rules:
     * - Pre-release versions have lower precedence than normal versions (2.5.0-rc < 2.5.0)
     * - When comparing pre-release identifiers: numeric identifiers are compared numerically,
     *   alphanumeric identifiers are compared ASCII-sort (e.g., rc.1 < rc.2 < rc.10)
     * - Build metadata (e.g., +build.1) is ignored in comparisons
     *
     * @param latest The latest version string (e.g., "2.5.0", "2.5.0-rc.1")
     * @param current The current version string to compare against
     * @return true if latest is newer than current
     */
    private fun isVersionNewer(latest: String, current: String): Boolean {
        // Simple SemVer-like comparison
        val latestBase = latest.split('-')[0]
        val currentBase = current.split('-')[0]

        val latestParts = latestBase.split('.').mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val currentParts = currentBase.split('.').mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }

        // If version bases are identical, compare pre-release tags
        val latestHasPre = latest.contains('-')
        val currentHasPre = current.contains('-')

        // A version without pre-release is newer than one with pre-release
        if (!latestHasPre && currentHasPre) return true
        if (latestHasPre && !currentHasPre) return false

        // Both have pre-release tags - compare them using SemVer rules
        if (latestHasPre && currentHasPre) {
            return comparePreRelease(latest.substringAfter('-'), current.substringAfter('-')) > 0
        }

        return false
    }

    /**
     * Compares two pre-release identifiers according to SemVer precedence rules.
     * Numeric identifiers are compared numerically, non-numeric are compared ASCII-sort.
     */
    private fun comparePreRelease(latest: String, current: String): Int {
        val latestIdentifiers = latest.split('.').toMutableList()
        val currentIdentifiers = current.split('.').toMutableList()

        val maxIdentifiers = maxOf(latestIdentifiers.size, currentIdentifiers.size)

        for (i in 0 until maxIdentifiers) {
            val latestId = latestIdentifiers.getOrElse(i) { "" }
            val currentId = currentIdentifiers.getOrElse(i) { "" }

            val latestIsNumeric = latestId.all { it.isDigit() }
            val currentIsNumeric = currentId.all { it.isDigit() }

            when {
                latestIsNumeric && currentIsNumeric -> {
                    // Both numeric - compare numerically
                    // Use safe null handling to prevent NPE on large numbers that overflow Int
                    val latestNum = latestId.toLongOrNull() ?: Long.MAX_VALUE
                    val currentNum = currentId.toLongOrNull() ?: Long.MAX_VALUE
                    val result = latestNum compareTo currentNum
                    if (result != 0) return result
                }
                latestIsNumeric && !currentIsNumeric -> {
                    // Numeric has lower precedence than non-numeric in SemVer
                    return -1
                }
                !latestIsNumeric && currentIsNumeric -> {
                    return 1
                }
                else -> {
                    // Both non-numeric - compare ASCII-sort
                    val result = latestId.compareTo(currentId, ignoreCase = true)
                    if (result != 0) return result
                }
            }
        }

        return 0
    }

    fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isUpdateDownloading.value = true
                _isUpdateDialogShowing.value = false

                val context = getApplication<Application>()
                val targetFile = File(context.getExternalFilesDir(null), "vrhub_update.apk")
                
                // Use specialized timeouts for update downloads (larger files)
                val updateClient = okHttpClient.newBuilder()
                    .connectTimeout(com.vrhub.data.Constants.UPDATE_CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(com.vrhub.data.Constants.UPDATE_READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val initialDownloaded = if (targetFile.exists()) targetFile.length() else 0L

                // Resolve downloadUrl: support absolute URLs, protocol-relative URLs, and relative paths
                // - Absolute URLs (http://, https://): used as-is
                // - Protocol-relative URLs (//example.com): resolved to https://
                // - Relative paths (/path): resolved against SECURE_UPDATE_BASE_URL
                val resolvedDownloadUrl = when {
                    updateInfo.downloadUrl.startsWith("http://") ||
                    updateInfo.downloadUrl.startsWith("https://") -> {
                        // Absolute URL - use as-is
                        updateInfo.downloadUrl
                    }
                    updateInfo.downloadUrl.startsWith("//") -> {
                        // Protocol-relative URL (e.g., "//sunshine-aio.com/path") - add https:
                        "https:" + updateInfo.downloadUrl
                    }
                    else -> {
                        // Relative path - resolve against base URL (strip trailing slash from base)
                        val baseUrl = com.vrhub.data.Constants.SECURE_UPDATE_BASE_URL.trimEnd('/')
                        baseUrl + updateInfo.downloadUrl
                    }
                }
                Log.d(TAG, "Resolved download URL: $resolvedDownloadUrl")

                val requestBuilder = Request.Builder().url(resolvedDownloadUrl)
                
                // Add Range header for resumable downloads if partial file exists
                if (initialDownloaded > 0) {
                    requestBuilder.addHeader("Range", "bytes=$initialDownloaded-")
                }
                
                val request = requestBuilder.build()
                updateClient.newCall(request).execute().use { response ->
                    val isResume = response.code == 206
                    
                    if (!response.isSuccessful && response.code != 416) {
                        throw Exception("HTTP ${response.code}")
                    }

                    if (response.code == 416) {
                        // Range Not Satisfiable: file might already be complete or server-side changed
                        Log.i(TAG, "Update download: 416 Range Not Satisfiable. Verifying existing file.")
                    } else {
                        val body = response.body ?: throw Exception("Empty response body")
                        val contentLength = body.contentLength()
                        val totalBytes = if (isResume) contentLength + initialDownloaded else contentLength

                        // Check available disk space before downloading
                        if (totalBytes > 0) {
                            val downloadDir = targetFile.parentFile
                            if (downloadDir != null) {
                                val stat = android.os.StatFs(downloadDir.path)
                                val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                                // Require totalBytes + 50MB buffer for safety
                                // Buffer accounts for: filesystem overhead, potential partial writes,
                                // and ensures space for extraction temp files during APK install
                                val requiredBytes = totalBytes + (50 * 1024 * 1024)
                                if (availableBytes < requiredBytes) {
                                    val availableMb = availableBytes / (1024 * 1024)
                                    val requiredMb = requiredBytes / (1024 * 1024)
                                    throw Exception("Insufficient storage: ${availableMb}MB available, ${requiredMb}MB required for update")
                                }
                            }
                        }

                        // Open in append mode if resuming, otherwise overwrite
                        java.io.FileOutputStream(targetFile, isResume).use { output ->
                            com.vrhub.data.DownloadUtils.downloadWithProgress(
                                inputStream = body.byteStream(),
                                outputStream = output,
                                initialDownloaded = if (isResume) initialDownloaded else 0L,
                                totalBytes = totalBytes,
                                isCancelled = { !(_isUpdateDownloading.value) },
                                onProgress = { _, _, progress ->
                                    _updateProgress.value = "Downloading update: ${(progress * 100).toInt()}%"
                                }
                            )
                        }
                    }
                }

                _updateProgress.value = "Verifying integrity: 0%"
                val actualChecksum = com.vrhub.data.CryptoUtils.sha256(targetFile) { progress ->
                    _updateProgress.value = "Verifying integrity: ${(progress * 100).toInt()}%"
                }
                
                if (updateInfo.checksum.isNullOrEmpty() || !actualChecksum.equals(updateInfo.checksum, ignoreCase = true)) {
                    targetFile.delete()
                    throw Exception("Integrity check failed: Checksum mismatch or missing. The file may be corrupted.")
                }

                _updateProgress.value = "Launching installer..."
                withContext(Dispatchers.Main) {
                    if (!_isAppVisible.value) {
                        _updateProgress.value = "Update ready. Please return to the app."
                        _isAppVisible.first { it }
                    }
                    _events.emit(MainEvent.InstallApk(targetFile))
                    _isUpdateDownloading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update error", e)
                _error.value = "Update failed: ${e.message}"
                _isUpdateDownloading.value = false
                onUpdateDialogDismissed()
            }
        }
    }

    fun onUpdateDialogDismissed() {
        if (_isUpdateDownloading.value) return
        _isUpdateDialogShowing.value = false
        checkPermissions()
        refreshInstalledPackages()
        refreshDownloadedReleases()
        startMetadataFetchLoop()
    }

    fun setVisibleIndices(indices: List<Int>) {
        if (_visibleIndices.value != indices) {
            _visibleIndices.value = indices
            priorityUpdateChannel.trySend(Unit)
        }
    }

    fun setAppVisibility(visible: Boolean) {
        if (_isAppVisible.value != visible) {
            _isAppVisible.value = visible
            if (visible) {
                priorityUpdateChannel.trySend(Unit)
                refreshInstalledPackages()
                refreshDownloadedReleases()
                viewModelScope.launch {
                    repository.verifyAndCleanupInstalls()
                }
                // Check if a catalog update was found in background
                checkCatalogUpdate()
            }
        }
    }

    private fun checkCatalogUpdate() {
        val available = prefs.getBoolean("catalog_update_available", false)
        val count = prefs.getInt("catalog_update_count", 0)
        if (_isCatalogUpdateAvailable.value != available) {
            _isCatalogUpdateAvailable.value = available
        }
        if (_catalogUpdateCount.value != count) {
            _catalogUpdateCount.value = count
        }
    }

    fun dismissCatalogUpdate() {
        // Update UI state immediately for responsive feedback
        _isCatalogUpdateAvailable.value = false
        _catalogUpdateCount.value = 0
        
        viewModelScope.launch(Dispatchers.IO) {
            com.vrhub.logic.CatalogUtils.catalogSyncMutex.withLock {
                prefs.edit().apply {
                    putBoolean("catalog_update_available", false)
                    putInt("catalog_update_count", 0)
                    apply()
                }
            }
        }
    }

    fun syncCatalogNow() {
        // Hiding the banner immediately provides better UX response.
        // The background flags will be cleared by repository upon successful sync.
        _isCatalogUpdateAvailable.value = false
        refreshData()
    }

    private fun scheduleCatalogUpdateWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        // Rationale for 6 hours: Provides a balance between timely updates and battery/data efficiency.
        // Mirrors typically sync daily, so 4 checks per day ensures users see changes within hours
        // without excessive background activity. (Finding 7)
        val request = androidx.work.PeriodicWorkRequestBuilder<com.vrhub.worker.CatalogUpdateWorker>(
            6, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10L,
                TimeUnit.SECONDS
            )
            .build()

        androidx.work.WorkManager.getInstance(getApplication())
            .enqueueUniquePeriodicWork(
                "catalog_update_check",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startMetadataFetchLoop() {
        sizeFetchJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                if (!_isAppVisible.value) {
                    _isAppVisible.first { it }
                }

                val currentGames = _allGames.value
                val currentSearch = _searchQuery.value
                if (currentGames.isEmpty()) {
                    priorityUpdateChannel.receive()
                    continue
                }

                val needsData = currentGames.filter { it.sizeBytes == null || (it.description == null && it.screenshotUrls == null) }
                if (needsData.isEmpty()) {
                    priorityUpdateChannel.receive()
                    continue
                }

                val visible = _visibleIndices.value
                val filteredGames = games.value

                val prioritizedPackages = visible.mapNotNull { index ->
                    filteredGames.getOrNull(index)?.packageName
                }.toSet()

                val searchResultPackages = if (currentSearch.isNotEmpty()) {
                    filteredGames.map { it.packageName }.toSet()
                } else null

                val candidates = if (searchResultPackages != null) {
                    needsData.filter { searchResultPackages.contains(it.packageName) }
                } else {
                    needsData
                }

                val target = candidates.find { prioritizedPackages.contains(it.packageName) }

                if (target != null) {
                    try {
                        repository.getGameRemoteInfo(target)
                    } catch (e: Exception) {
                        delay(2000)
                    }

                    select<Unit> {
                        priorityUpdateChannel.onReceive { }
                        onTimeout(100.milliseconds) { }
                    }
                } else {
                    priorityUpdateChannel.receive()
                }
            }
        }
    }

    /**
     * Manually trigger an update check from UI
     */
    fun checkForUpdatesManually() {
        viewModelScope.launch {
            try {
                _isUpdateCheckInProgress.value = true
                val updateAvailable = checkForAppUpdates()
                if (!updateAvailable) {
                    _events.emit(MainEvent.ShowMessage("You are on the latest version"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manual update check failed", e)
                _events.emit(MainEvent.ShowMessage("Update check failed: ${e.message}"))
            } finally {
                _isUpdateCheckInProgress.value = false
            }
        }
    }

    fun refreshData() {
        if (refreshJob?.isActive == true) return
        if (_isUpdateDialogShowing.value || _isUpdateDownloading.value) return

        refreshJob = viewModelScope.launch {
            val context = getApplication<Application>()
            // Synchronize permission check with permissionCheckMutex
            // to prevent race conditions with checkPermissions() when updating _missingPermissions
            val missing = permissionCheckMutex.withLock {
                com.vrhub.data.PermissionManager.getMissingPermissions(context)
            }
            _missingPermissions.value = missing

            // Allow catalog to load even without permissions
            // Only installation should be blocked, not browsing
            // Permissions are checked in installGame() method

            val now = System.currentTimeMillis()
            _isRefreshing.value = true
            _catalogSyncProgress.value = 0f
            _error.value = null
            try {
                // Shared Mutex coordination: Protect the entire synchronization flow to prevent
                // concurrent database writes or metadata race conditions between the manual
                // UI-triggered sync and the background CatalogUpdateWorker.
                com.vrhub.logic.CatalogUtils.catalogSyncMutex.withLock {
                    withContext(Dispatchers.IO) {
                        // 1. Load saved config and set as active for this sync
                        val savedConfig = configRepository.loadConfig()
                        if (savedConfig == null || !savedConfig.isValid()) {
                            _catalogSyncProgress.value = -1f // Signal error
                            _events.emit(MainEvent.ShowMessage("No server configuration. Please configure server in Settings."))
                            return@withContext
                        }
                        repository.setActiveConfig(savedConfig)
                        val baseUri = savedConfig.baseUri

                        // 2. Sync Catalog first to have the latest games list
                        repository.syncCatalog(baseUri) { progress ->
                            _catalogSyncProgress.value = progress
                        }

                        // 3. Refresh statuses now that we have the latest catalog
                        // We get the fresh games list directly to be immediate
                        val freshGames = repository.getAllGamesFlow().first()

                        val installed = repository.getInstalledPackagesMap()
                        _installedPackages.value = installed
                        refreshDownloadedReleases(freshGames)

                        // Store current sync time after successful sync
                        prefs.edit().putLong("last_catalog_sync_time", now).apply()
                    }
                }
                priorityUpdateChannel.trySend(Unit)
            } catch (e: Exception) {
                com.vrhub.data.LogUtils.e(TAG, "Refresh error", e)
                _error.value = "Error: ${e.message}"
            } finally {
                _isRefreshing.value = false
                _catalogSyncProgress.value = null
            }
        }
    }

    /**
     * Check permissions and update state.
     * Uses permissionCheckMutex to prevent concurrent executions from multiple triggers.
     *
     * @param fromResume true if called from onAppResume() (handles denial logic)
     * Refactored to use separate handler methods for better code clarity.
     */
    fun checkPermissions(fromResume: Boolean = false) {
        if (_isUpdateDialogShowing.value || _isUpdateDownloading.value) return

        viewModelScope.launch {
            // Use Mutex to prevent concurrent permission checks
            // Multiple triggers (startup, resume, user action) could cause overlapping checks
            permissionCheckMutex.withLock {
                val context = getApplication<Application>()
                // Use PermissionManager instead of getMissingPermissionsList()
                val missing = com.vrhub.data.PermissionManager.getMissingPermissions(context)

                val newCount = missing.size
                val previousState = _missingPermissions.value
                val previouslyHadAllPermissions = previousState?.isEmpty() == true

                // Route to appropriate handler based on current state
                when {
                    isPermissionFlowActive -> handleActivePermissionFlow(missing, fromResume, context)
                    previouslyHadAllPermissions && newCount > 0 -> handlePermissionRevocation(missing)
                    else -> handleNormalPermissionUpdate(missing)
                }
            }
        }
    }

    /**
     * Handle permission state changes during active permission flow.
     * Manages concurrent grant/revoke scenarios where user might grant one permission
     * but revoke another during the permission flow.
     *
     * @param missing Current list of missing permissions from system
     * @param fromResume true if called from onAppResume() (handles denial logic)
     * @param context Application context for string resources
     */
    private suspend fun handleActivePermissionFlow(
        missing: List<RequiredPermission>,
        fromResume: Boolean,
        context: Application
    ) {
        val previouslyMissing = _missingPermissions.value ?: emptyList()
        val previousCount = previouslyMissing.size
        val newCount = missing.size

        // Detect newly granted permissions (were missing, now granted)
        val newlyGranted = previouslyMissing.filter { it !in missing }

        // Detect newly revoked permissions (were granted, now missing)
        val newlyRevoked = missing.filter { it !in previouslyMissing && it in RequiredPermission.entries }

        when {
            // Permission state changed - save changes and update flow
            newlyGranted.isNotEmpty() || newlyRevoked.isNotEmpty() -> {
                handlePermissionStateChanges(newlyGranted, newlyRevoked, missing)
                val flowCompleted = checkAndCompletePermissionFlow(missing, context)
                if (!flowCompleted) {
                    requestNextPermission()
                }
            }
            // All critical permissions just granted (no changes detected)
            missing.filter { it != RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS }.isEmpty() &&
                newCount != previousCount -> {
                completePermissionFlow(missing, context)
            }
            // No progress made - user may have denied permission
            newCount == previousCount && fromResume && !permissionDenialShown -> {
                handlePermissionDenial(missing)
            }
            // Otherwise, just update state and continue flow
            else -> {
                _missingPermissions.value = missing
                previousMissingCount = newCount
            }
        }
    }

    /**
     * Save permission state changes (granted/revoked) during active flow.
     *
     * @param newlyGranted Permissions that were granted since last check
     * @param newlyRevoked Permissions that were revoked since last check
     * @param missing Current list of missing permissions
     */
    private suspend fun handlePermissionStateChanges(
        newlyGranted: List<RequiredPermission>,
        newlyRevoked: List<RequiredPermission>,
        missing: List<RequiredPermission>
    ) {
        // Save newly granted permissions
        for (permission in newlyGranted) {
            com.vrhub.data.PermissionManager.savePermissionState(permission, true)
            com.vrhub.data.LogUtils.i(TAG, "Permission granted: $permission")
        }

        // Handle newly revoked permissions
        if (newlyRevoked.isNotEmpty()) {
            for (permission in newlyRevoked) {
                com.vrhub.data.PermissionManager.savePermissionState(permission, false)
                com.vrhub.data.LogUtils.i(TAG, "Permission revoked during flow: $permission")
            }
            // Clear pending install if critical permissions were revoked
            val hasRevokedCritical = newlyRevoked.any {
                it != RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS
            }
            if (hasRevokedCritical) {
                pendingInstallAfterPermissions = null
            }
        }

        _missingPermissions.value = missing
        previousMissingCount = missing.size
    }

    /**
     * Check if all critical permissions are granted and complete flow if so.
     *
     * @param missing Current list of missing permissions
     * @param context Application context for string resources
     * @return true if flow was completed, false otherwise
     */
    private suspend fun checkAndCompletePermissionFlow(
        missing: List<RequiredPermission>,
        context: Application
    ): Boolean {
        val missingCritical = missing.filter {
            it != RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS
        }

        if (missingCritical.isEmpty()) {
            completePermissionFlow(missing, context)
            return true
        }
        return false
    }

    /**
     * Complete the permission flow and retry pending installation.
     *
     * @param missing Current list of missing permissions (may contain optional permissions)
     * @param context Application context for string resources
     */
    private suspend fun completePermissionFlow(
        missing: List<RequiredPermission>,
        context: Application
    ) {
        isPermissionFlowActive = false
        _missingPermissions.value = missing // May still contain battery optimization
        _permissionFlowState.value = PermissionFlowState(allGranted = true)

        // Save all states
        saveAllPermissionStates(missing)

        // Auto-retry pending install
        pendingInstallAfterPermissions?.let { releaseName ->
            val hasBatteryOpt = !missing.contains(RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS)
            val message = if (hasBatteryOpt) {
                context.getString(R.string.msg_permissions_granted)
            } else {
                context.getString(R.string.msg_critical_permissions_granted)
            }
            _events.emit(MainEvent.ShowMessage(message))
            installGame(releaseName)
            pendingInstallAfterPermissions = null
        }

        refreshData()
    }

    /**
     * Handle permission revocation when user had all permissions,
     * now missing some. Shows revocation dialog and clears pending install.
     *
     * @param missing List of revoked permissions
     */
    private fun handlePermissionRevocation(missing: List<RequiredPermission>) {
        com.vrhub.data.LogUtils.i(TAG, "Permission revoked detected: $missing")
        // Show revocation dialog for all missing permissions
        _showRevokedDialog.value = missing
        // Clear pending install since permissions were revoked
        pendingInstallAfterPermissions = null
    }

    /**
     * Handle normal permission update when flow is not active.
     * Updates state and refreshes data if all permissions are now granted.
     *
     * @param missing Current list of missing permissions
     */
    private fun handleNormalPermissionUpdate(
        missing: List<RequiredPermission>
    ) {
        _missingPermissions.value = missing
        previousMissingCount = missing.size
        _permissionFlowState.value = _permissionFlowState.value.copy(
            allGranted = missing.isEmpty()
        )

        // Refresh data if permissions were just granted and game list is empty
        if (missing.isEmpty() && _allGames.value.isEmpty()) {
            refreshData()
        }
    }

    /**
     * Dismiss the permission revoked dialog.
     * Called when user clicks "Later" in the dialog.
     * Updated to clear the list of revoked permissions.
     */
    fun dismissRevokedDialog() {
        _showRevokedDialog.value = emptyList()
    }

    /**
     * Open system settings for the revoked permission.
     * Called when user clicks "Open Settings" in the revocation dialog.
     * Opens settings for the first revoked permission in the list.
     */
    fun openPermissionSettings(permission: RequiredPermission) {
        viewModelScope.launch {
            _events.emit(MainEvent.OpenPermissionSettings(permission))
        }
        // Dismiss dialog after launching settings
        _showRevokedDialog.value = emptyList()
    }

    /**
     * Called when app resumes (e.g. returning from settings).
     * Handles permission cache invalidation and state sync.
     *
     * Race condition fixed by using permissionCheckMutex
     * to prevent concurrent permission checks when verifyPendingInstallations()
     * is also called during app resume.
     */
    fun onAppResume() {
        // Invalidate cache to ensure fresh check from system
        com.vrhub.data.PermissionManager.invalidateCache()
        // Trigger check with fromResume=true to handle denial logic
        checkPermissions(fromResume = true)
    }

    /**
     * Save permission states when all permissions are granted.
     * Called when permission flow completes successfully.
     * Changed to suspend function since it calls savePermissionState
     * which is now suspend to prevent UI thread blocking.
     * Use for loop for sequential saves to prevent race conditions.
     * Wrap loop in withContext(Dispatchers.IO) to ensure strict
     * sequential execution on a single-threaded dispatcher context.
     */
    private suspend fun saveAllPermissionStates(missing: List<RequiredPermission>) = withContext(Dispatchers.IO) {
        for (permission in RequiredPermission.entries) {
            val granted = !missing.contains(permission)
            com.vrhub.data.PermissionManager.savePermissionState(permission, granted)
        }
    }

    /**
     * Handle permission denial by the user.
     * Extracted from checkPermissions for better code clarity.
     *
     * @param missing Current list of missing permissions from system
     */
    private suspend fun handlePermissionDenial(missing: List<RequiredPermission>) {
        // Use permissionDenialShown flag to prevent false denial messages
        // from multiple onAppResume events. Only show denial message once per permission flow.
        // User returned from settings without granting - treat as Denial
        val currentMissing = _missingPermissions.value ?: emptyList()
        val deniedPermission = currentMissing.firstOrNull { it in missing }
        com.vrhub.data.LogUtils.i(TAG, "Permission denied by user: $deniedPermission")

        // Show user-friendly error message
        val permissionName = getPermissionDisplayName(deniedPermission)
        _events.emit(MainEvent.ShowMessage(
            getApplication<Application>().getString(R.string.perm_denial_message, permissionName)
        ))

        // Mark that we've shown denial message for this flow
        permissionDenialShown = true

        // Cancel permission flow but keep pending install for retry (better UX)
        // User can click install again to restart permission flow
        isPermissionFlowActive = false
        _permissionFlowState.value = PermissionFlowState()
        // Note: pendingInstallAfterPermissions is preserved for later retry
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        priorityUpdateChannel.trySend(Unit)
    }

    fun setFilter(filter: FilterStatus) {
        _selectedFilter.value = filter
        priorityUpdateChannel.trySend(Unit)
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        priorityUpdateChannel.trySend(Unit)
    }

    /**
     * Start the permission request flow.
     * Sets up the flow state and requests the first missing permission.
     * Reset permissionDenialShown flag when starting a new flow
     * to prevent false denial messages from previous flows.
     */
    fun startPermissionFlow() {
        isPermissionFlowActive = true
        permissionDenialShown = false  // Reset denial tracking for new flow

        // Set the first missing permission as current
        val next = _missingPermissions.value?.firstOrNull()
        _permissionFlowState.value = _permissionFlowState.value.copy(
            currentPermission = next
        )

        requestNextPermission()
    }

    /**
     * Request the next permission in the flow.
     * Emits the appropriate event based on the current missing permission.
     * Updates the permission flow state to track which permission is being requested.
     *
     * Enhanced to allow installation to proceed when only critical
     * permissions are granted, even if optional IGNORE_BATTERY_OPTIMIZATIONS is denied.
     */
    fun requestNextPermission() {
        // Check if we have critical permissions remaining
        val missingCritical = _missingPermissions.value?.filter {
            it != RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS
        } ?: emptyList()

        if (missingCritical.isEmpty()) {
            // All critical permissions granted - flow is complete
            // IGNORE_BATTERY_OPTIMIZATIONS may still be missing, but that's OK
            isPermissionFlowActive = false
            _permissionFlowState.value = _permissionFlowState.value.copy(
                currentPermission = null,
                allGranted = true
            )

            // Auto-retry pending install if all critical permissions granted
            pendingInstallAfterPermissions?.let { releaseName ->
                viewModelScope.launch {
                    val context = getApplication<Application>()
                    val hasBatteryOpt = _missingPermissions.value?.contains(RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS) == false
                    val message = if (hasBatteryOpt) {
                        context.getString(R.string.msg_permissions_granted)
                    } else {
                        context.getString(R.string.msg_critical_permissions_granted)
                    }
                    _events.emit(MainEvent.ShowMessage(message))
                    installGame(releaseName)
                }
                pendingInstallAfterPermissions = null
            }

            return
        }

        // Get the next critical permission to request (skip battery optimization if it's the only one left)
        val next = _missingPermissions.value?.firstOrNull { permission ->
            // Only request critical permissions during the flow
            permission != RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS ||
            _missingPermissions.value?.size == 1 // Only request battery if it's the ONLY missing permission
        }

        if (next == null) {
            // No critical permissions remaining to request
            isPermissionFlowActive = false
            _permissionFlowState.value = _permissionFlowState.value.copy(
                currentPermission = null,
                allGranted = true
            )
            return
        }

        // Update current permission in state
        _permissionFlowState.value = _permissionFlowState.value.copy(
            currentPermission = next
        )

        viewModelScope.launch {
            when (next) {
                RequiredPermission.INSTALL_UNKNOWN_APPS -> _events.emit(MainEvent.RequestInstallPermission)
                RequiredPermission.MANAGE_EXTERNAL_STORAGE -> _events.emit(MainEvent.RequestStoragePermission)
                RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> _events.emit(MainEvent.RequestIgnoreBatteryOptimizations)
            }
        }
    }

    /**
     * Get display name for a permission.
     * Extracted hardcoded strings to strings.xml for internationalization.
     */
    private fun getPermissionDisplayName(permission: RequiredPermission?): String {
        val context = getApplication<Application>()
        return when (permission) {
            RequiredPermission.INSTALL_UNKNOWN_APPS -> context.getString(R.string.perm_name_install)
            RequiredPermission.MANAGE_EXTERNAL_STORAGE -> context.getString(R.string.perm_name_storage)
            RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> context.getString(R.string.perm_name_battery)
            null -> context.getString(R.string.perm_overlay_subtitle)
        }
    }

    /**
     * Cancel the permission flow.
     * Called when user cancels the permission request.
     */
    fun cancelPermissionFlow() {
        isPermissionFlowActive = false
        _permissionFlowState.value = PermissionFlowState()
        pendingInstallAfterPermissions = null
    }

    // Queue Management Methods
    fun isGameInCatalog(releaseName: String): Boolean {
        return _allGames.value.any { it.releaseName == releaseName }
    }

    /**
     * Install a game with permission checking.
     *
     * @param releaseName The release name to install
     * @param downloadOnly If true, only download without installing APK
     *
     * Permission flow:
     * 1. Check if all CRITICAL permissions are granted (install + storage)
     * 2. If permissions missing: start permission flow, store pending install
     * 3. If permissions granted: proceed with normal installation
     * 4. After permission flow completes: automatically retry install
     *
     * Changed to use hasCriticalPermissions() instead of
     * hasAllRequiredPermissions() to make IGNORE_BATTERY_OPTIMIZATIONS optional.
     */
    fun installGame(releaseName: String, downloadOnly: Boolean = false) {
        // Use PermissionManager to check CRITICAL permissions only
        val context = getApplication<Application>()
        val hasAllPermissions = com.vrhub.data.PermissionManager.hasCriticalPermissions(context)

        if (!hasAllPermissions) {
            // Permissions missing, start permission flow
            pendingInstallAfterPermissions = releaseName
            startPermissionFlow()

            // Show user-friendly message
            viewModelScope.launch {
                _events.emit(MainEvent.ShowMessage("Please grant required permissions to install games"))
            }
            return
        }

        // Check if releaseName is empty
        if (releaseName.isEmpty()) {
            showOverlay()
            return
        }

        // Find game in catalog
        val game = _allGames.value.find { it.releaseName == releaseName }
        if (game == null) {
            // Catalog not yet loaded or game not found
            viewModelScope.launch {
                if (_allGames.value.isEmpty()) {
                    _events.emit(MainEvent.ShowMessage("Please wait for the catalog to load"))
                } else {
                    _events.emit(MainEvent.ShowMessage("Game not found in catalog"))
                }
            }
            return
        }

        // Check if already in queue
        val existingTask = installQueue.value.find { it.releaseName == releaseName }
        if (existingTask != null) {
            val isFirst = installQueue.value.firstOrNull()?.releaseName == releaseName
            when {
                // FAILED tasks: Promote to front and relaunch (retry UX)
                existingTask.status == InstallTaskStatus.FAILED -> {
                    promoteTask(releaseName)
                    viewModelScope.launch {
                        _events.emit(MainEvent.ShowMessage("Retrying ${game.gameName}..."))
                    }
                }
                // PENDING_INSTALL or SHELVED tasks - user wants to retry/complete installation
                // This happens when download completed but installation failed or was postponed
                existingTask.status == InstallTaskStatus.PENDING_INSTALL || existingTask.status == InstallTaskStatus.SHELVED -> {
                    // Promote to front and resume installation phase
                    promoteTask(releaseName)
                    viewModelScope.launch {
                        _events.emit(MainEvent.ShowMessage("Resuming installation for ${game.gameName}..."))
                    }
                }
                // PAUSED tasks: Promote and Resume
                existingTask.status == InstallTaskStatus.PAUSED -> {
                    promoteTask(releaseName)
                }
                // QUEUED tasks: If not first, promote it to prioritize
                existingTask.status == InstallTaskStatus.QUEUED && !isFirst -> {
                    promoteTask(releaseName)
                    viewModelScope.launch {
                        _events.emit(MainEvent.ShowMessage("Prioritizing ${game.gameName}..."))
                    }
                }
                // Actively processing task: just show the overlay
                existingTask.status.isProcessing() -> {
                    _viewedReleaseName.value = releaseName
                    _showInstallOverlay.value = true
                }
                // All other cases: Already in queue
                else -> {
                    viewModelScope.launch {
                        _events.emit(MainEvent.ShowMessage("${game.gameName} is already in the queue"))
                    }
                }
            }
            return
        }

        // Add to Room DB (which will update StateFlow via Flow observer)
        viewModelScope.launch {
            try {
                repository.addToQueue(
                    releaseName = game.releaseName,
                    status = com.vrhub.data.InstallStatus.QUEUED,
                    isDownloadOnly = downloadOnly
                )

                // CRITICAL: Signal that a task was added AFTER Room insert completes.
                // This solves the race condition where startQueueProcessor() would check
                // installQueue.value before the StateFlow had emitted the new task.
                taskAddedSignal.trySend(Unit)

                val isShowing = _showInstallOverlay.value
                val alreadyProcessing = installQueue.value.any { it.status.isProcessing() }

                if (!isShowing && !alreadyProcessing) {
                    _viewedReleaseName.value = releaseName
                    _showInstallOverlay.value = true
                } else {
                    _events.emit(MainEvent.ShowMessage("${game.gameName} added to queue"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add game to queue", e)
                _events.emit(MainEvent.ShowMessage("Failed to add ${game.gameName} to queue"))
            }
        }

        startQueueProcessor()
    }

    fun showOverlay() {
        val currentQueue = installQueue.value
        if (currentQueue.isEmpty()) return

        val taskToView = _viewedReleaseName.value?.let { v -> currentQueue.find { it.releaseName == v } }?.releaseName
            ?: currentQueue.find { it.status.isProcessing() }?.releaseName
            ?: currentQueue.firstOrNull()?.releaseName

        if (taskToView != null) {
            _viewedReleaseName.value = taskToView
            _showInstallOverlay.value = true
        }
    }

    /**
     * Starts the queue processor that sequentially processes QUEUED tasks.
     *
     * FIX for race condition (Story 1.10):
     * The previous implementation would immediately exit if no QUEUED tasks were found
     * in installQueue.value. This caused a race condition because:
     * 1. installGame() inserts task to Room DB (async)
     * 2. startQueueProcessor() called immediately after
     * 3. installQueue.value hasn't emitted the new task yet (StateFlow delay from Room)
     * 4. Processor exits thinking there's no work to do
     *
     * Solution: Use taskAddedSignal channel to wait for task insertion confirmation
     * before exiting. The processor now waits for either:
     * - A task to appear in the StateFlow, OR
     * - A signal that a task was added (so it should recheck the StateFlow)
     * - A timeout (to avoid indefinite waiting if something goes wrong)
     */
    private fun startQueueProcessor() {
        if (queueProcessorJob?.isActive == true) return

        queueProcessorJob = viewModelScope.launch {
            try {
                while (isActive) {
                    val nextTask = installQueue.value.find { it.status == InstallTaskStatus.QUEUED }

                    if (nextTask != null) {
                        // Task found - process it
                        runTask(nextTask)
                    } else {
                        // No QUEUED task found in StateFlow.
                        // This could be because:
                        // 1. Queue is genuinely empty → should exit
                        // 2. Room insert completed but StateFlow hasn't emitted yet → wait for signal
                        // 3. All tasks are in non-QUEUED states (PAUSED, etc.) → should exit

                        // Wait for either:
                        // - taskAddedSignal (new task was inserted)
                        // - timeout (to recheck or exit if genuinely empty)
                        val signalReceived = select<Boolean> {
                            taskAddedSignal.onReceive { true }
                            onTimeout(500.milliseconds) { false }
                        }

                        if (signalReceived) {
                            // Signal received - a task was just added. Wait for StateFlow to emit
                            // the new task before rechecking. This is more reactive than a fixed delay.
                            //
                            // REACTIVE WAIT: Use withTimeoutOrNull(200) with installQueue.first()
                            // to wait for QUEUED tasks to appear. If they appear within 200ms, proceed
                            // immediately. Otherwise, loop back and recheck (handles edge cases).
                            //
                            // 200ms timeout is conservative - actual StateFlow propagation is typically <50ms.
                            // The timeout prevents indefinite waiting if something goes wrong.
                            val taskAppeared = withTimeoutOrNull(200) {
                                installQueue.first { it.any { t -> t.status == InstallTaskStatus.QUEUED } }
                                true
                            } ?: false

                            if (taskAppeared) {
                                // Task appeared in StateFlow - proceed to process it
                                continue
                            }
                            // Timeout without task appearing - loop back and recheck queue state
                            continue
                        }

                        // Timeout - recheck the queue
                        val hasQueuedTasks = installQueue.value.any { it.status == InstallTaskStatus.QUEUED }
                        if (!hasQueuedTasks) {
                            // No QUEUED tasks after waiting - genuinely empty, exit processor
                            Log.d(TAG, "Queue processor: No QUEUED tasks found, exiting")
                            break
                        }
                        // else: There are QUEUED tasks, loop back and process them
                    }
                }
            } finally {
                queueProcessorJob = null
            }
        }
    }

    /**
     * Runs a download task using WorkManager for background execution.
     * WorkManager ensures the download survives app kill and device reboot.
     * Progress and status updates flow from DownloadWorker → Room DB → StateFlow → UI.
     *
     * CRITICAL: This function suspends until the ENTIRE pipeline completes (download + extraction + installation).
     * This prevents the queue processor from starting the next task while extraction is in progress,
     * which would cause concurrent I/O operations and violate the sequential queue contract.
     *
     * OPTIMIZATION: If extraction is already complete (zombie recovery case),
     * skip WorkManager entirely and proceed directly to installation.
     *
     * NOTE ON FILE EXISTENCE CHECKS:
     * The file checks here (stagedApk, extractionMarker, extractionDir) are NOT redundant with
     * handleZombieTaskRecovery() because:
     * 1. handleZombieTaskRecovery() only runs at app startup (in resumeActiveDownloadObservations)
     * 2. runTask() is called for BOTH zombie-recovered tasks AND fresh new tasks
     * 3. A task could be added while the app is running, then the app is killed during extraction,
     *    then the app restarts and processes the task again - these checks handle that case
     * 4. These checks enable smart resumption at the furthest completed stage
     */
    private suspend fun runTask(task: InstallTaskState) {
        val game = _allGames.value.find { it.releaseName == task.releaseName } ?: return

        // Double check status hasn't changed to PAUSED before we start
        val latestTask = installQueue.value.find { it.releaseName == task.releaseName }
        if (latestTask == null || latestTask.status != InstallTaskStatus.QUEUED) return

        // Auto-switch view if nothing is being viewed or the viewed task is not active
        val currentViewed = installQueue.value.find { it.releaseName == _viewedReleaseName.value }
        if (currentViewed == null || !currentViewed.status.isProcessing()) {
            _viewedReleaseName.value = task.releaseName
        }

        // Initialize active task state before enqueuing work
        activeReleaseName = task.releaseName

        // Check if installation can be resumed from various recovery points
        // Use withContext(Dispatchers.IO) to avoid blocking main thread with file I/O
        val context = getApplication<Application>()

        // Check CRITICAL permissions before processing queue task
        // This prevents tasks from starting if critical permissions were revoked
        // Battery optimization permission is optional and doesn't block installation
        if (!PermissionManager.hasCriticalPermissions(context)) {
            Log.w(TAG, "Permissions missing for ${task.releaseName}, blocking queue processing")
            updateTaskStatus(task.releaseName, InstallTaskStatus.PAUSED)
            _events.emit(MainEvent.ShowMessage("Permissions required. Grant them in Settings to continue."))
            return
        }

        // FAST TRACK FLOW (Story 1.12): Auto-detect local files and skip to installation
        // Check if valid local files exist before starting WorkManager
        // This skips the download and extraction phases entirely.
        // Fallback to standard flow if discovery fails due to permissions (AC: 7)
        val hasLocal = try {
            repository.hasLocalInstallFiles(task.releaseName)
        } catch (e: Exception) {
            Log.w(TAG, "Fast Track check failed for ${task.releaseName}, falling back to standard flow", e)
            false
        }
        if (hasLocal) {
            Log.i(TAG, "Fast Track: Local files found for ${task.releaseName}")
            updateTaskStatus(task.releaseName, InstallTaskStatus.LOCAL_VERIFYING)

            // Mark as local install in database for history tracking (Story 1.12)
            repository.updateLocalInstallStatus(task.releaseName, true)

            // Create task-specific completion signal
            val completionSignal = CompletableDeferred<Unit>()
            taskCompletionSignals[task.releaseName] = completionSignal

            try {
                // Ensure temp directory exists and create extraction marker (Story 1.12)
                // This leverages existing installation logic and enables zombie recovery
                withContext(Dispatchers.IO) {
                    val tempInstallRoot = File(context.filesDir, "install_temp")
                    val hash = com.vrhub.data.CryptoUtils.md5(task.releaseName + "\n")
                    val gameTempDir = File(tempInstallRoot, hash)
                    if (!gameTempDir.exists()) gameTempDir.mkdirs()
                    File(gameTempDir, "extraction_done.marker").createNewFile()
                }

                // Call installation phase directly using skipRemoteVerification
                // This will use the existing files in the download directory
                currentTaskJob = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val apkFile = repository.installGame(
                            game = game,
                            keepApk = _keepApks.value,
                            downloadOnly = task.isDownloadOnly,
                            skipRemoteVerification = true,
                            skipStorageCheck = true
                        ) { message, progress, current, total ->
                            updateTaskProgress(task.releaseName, progress, current, total)
                            
                            // Update status based on message if needed
                            val status = when {
                                message.contains("Installing", ignoreCase = true) -> InstallTaskStatus.INSTALLING
                                message.contains("OBB", ignoreCase = true) -> InstallTaskStatus.INSTALLING
                                message.contains("Launching", ignoreCase = true) -> InstallTaskStatus.INSTALLING
                                else -> InstallTaskStatus.LOCAL_VERIFYING
                            }
                            
                            val currentTask = installQueue.value.find { it.releaseName == task.releaseName }
                            if (currentTask?.status != status) {
                                updateTaskStatus(task.releaseName, status)
                            }
                        }

                        if (task.isDownloadOnly) {
                            updateTaskStatus(task.releaseName, InstallTaskStatus.COMPLETED)
                            delay(1000)
                            repository.archiveTask(task.releaseName, com.vrhub.data.InstallStatus.COMPLETED)
                            withContext(Dispatchers.Main) {
                                updateOverlayAfterTaskComplete(task.releaseName)
                                refreshDownloadedReleases()
                            }
                        } else {
                            if (apkFile != null && apkFile.exists()) {
                                withContext(Dispatchers.Main) {
                                    _events.emit(MainEvent.InstallApk(apkFile))
                                }
                            }
                            updateTaskStatus(task.releaseName, InstallTaskStatus.PENDING_INSTALL)
                            withContext(Dispatchers.Main) {
                                updateOverlayAfterTaskComplete(task.releaseName)
                                refreshInstalledPackages()
                            }
                        }
                    } catch (e: CancellationException) {
                        updateTaskStatus(task.releaseName, InstallTaskStatus.PAUSED)
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Fast Track failed for ${task.releaseName}", e)
                        updateTaskStatus(task.releaseName, InstallTaskStatus.FAILED)
                        withContext(Dispatchers.Main) {
                            _events.emit(MainEvent.ShowMessage("Fast Track failed: ${e.message}"))
                        }
                    } finally {
                        if (activeReleaseName == task.releaseName) {
                            activeReleaseName = null
                        }
                        completionSignal.complete(Unit)
                    }
                }
                
                // Wait for the fast track installation to complete
                completionSignal.await()
                return
            } finally {
                taskCompletionSignals.remove(task.releaseName)
            }
        }

        // AC: 7 (Story 1.12) - Show message if fallback occurred but files were potentially present
        withContext(Dispatchers.IO) {
            val safeDirName = task.releaseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val gameDir = File(repository.downloadsDir, safeDirName)
            if (gameDir.exists() && gameDir.isDirectory && gameDir.list()?.isNotEmpty() == true) {
                withContext(Dispatchers.Main) {
                    _events.emit(MainEvent.ShowMessage("Local files invalid or incomplete. Falling back to download."))
                }
            }
        }

        // 2. NORMAL FLOW / RESUMPTION FLOW
        // Moved signal creation after early returns to prevent signal leakage.
        // Create task-specific completion signal - will be completed when extraction/installation finishes
        // This ensures the queue processor waits for the FULL pipeline before starting next task
        val completionSignal = CompletableDeferred<Unit>()
        taskCompletionSignals[task.releaseName] = completionSignal

        try {
            val recoveryState = withContext(Dispatchers.IO) {
                val tempInstallRoot = File(context.filesDir, "install_temp")
                val hash = com.vrhub.data.CryptoUtils.md5(task.releaseName + "\n")
                val gameTempDir = File(tempInstallRoot, hash)
                val extractionMarker = File(gameTempDir, "extraction_done.marker")
                val extractionDir = File(gameTempDir, "extracted")

                // Check for staged APK in externalFilesDir (ready for install, extraction already done)
                // Uses packageName.apk naming convention to prevent cross-contamination between installation tasks
                // Validates APK integrity (including package name and version match) using repository helper
                val expectedVersion = game.versionCode.toLongOrNull()
                val stagedApk = repository.getValidStagedApk(task.packageName, expectedVersion)

                Triple(stagedApk, extractionMarker.exists() && extractionDir.exists(), gameTempDir)
            }
            val (stagedApk, isExtractionComplete, _) = recoveryState

            if (stagedApk != null) {
                // APK is already staged - skip extraction entirely, go directly to APK install
                Log.i(TAG, "Staged APK found for ${task.releaseName}, launching installer directly")
                updateTaskStatus(task.releaseName, InstallTaskStatus.INSTALLING)
                _events.emit(MainEvent.InstallApk(stagedApk))

                // Set PENDING_INSTALL instead of COMPLETED
                // Installation is non-blocking - we need to verify via PackageManager later.
                // Do NOT cleanup or remove from queue - verification handles that.
                updateTaskStatus(task.releaseName, InstallTaskStatus.PENDING_INSTALL)

                // Do NOT cleanup or remove from queue here
                // The task must persist with PENDING_INSTALL status until verifyPendingInstallations()
                // confirms the package is installed via PackageManager.
                // Cleanup will happen after successful verification in checkInstallationStatusSilent().
                progressThrottleMap.remove(task.releaseName)
                totalBytesWrittenSet.remove(task.releaseName)

                withContext(Dispatchers.Main) {
                    updateOverlayAfterTaskComplete(task.releaseName)
                    refreshInstalledPackages()
                }

                if (activeReleaseName == task.releaseName) {
                    activeReleaseName = null
                }
                taskCompletionSignals[task.releaseName]?.complete(Unit)
                return
            }

            if (isExtractionComplete) {
                // Zombie Recovery - skip download/extraction, go directly to installation
                Log.i(TAG, "Zombie Recovery: Extraction complete for ${task.releaseName}, starting installation at 94%")
                try {
                    runInstallationPhase(task, game)
                    // Wait for installation to complete before returning
                    taskCompletionSignals[task.releaseName]?.await()
                    return
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Zombie Recovery failed for ${task.releaseName} (${e.message}) - falling back to full download")
                    // If installation from extracted failed, fall back to standard download flow
                    // Continue to WorkManager enqueuing below
                }
            }
            Log.i(TAG, "Enqueueing WorkManager download for: ${task.releaseName}")

            // Load server config to pass to DownloadWorker
            val savedConfig = configRepository.loadConfig()
            val serverBaseUri = savedConfig?.baseUri ?: ""
            val serverPassword = savedConfig?.password ?: ""

            // Enqueue download via WorkManager - survives process death
            repository.enqueueDownload(
                releaseName = task.releaseName,
                isDownloadOnly = task.isDownloadOnly,
                keepApk = _keepApks.value,
                serverBaseUri = serverBaseUri,
                serverPassword = serverPassword
            )

            // Observe WorkManager status and sync with our UI/queue state
            observeDownloadWork(task.releaseName, task.gameName)

            // CRITICAL: Wait for the FULL pipeline to complete (download + extraction + installation)
            // This prevents the queue processor from starting the next task prematurely
            //
            // Adaptive timeout calculation:
            // - Base: 5 minutes minimum (for small games and overhead)
            // - Scale: 2 minutes per 500 MB (accounts for download + extraction on Quest hardware)
            // - Cap: 6 hours maximum (for very large games like 100GB+)
            // Note: Previous 2-hour cap was insufficient for games >60GB
            // Code Review Fix (Item 5): Increased from 1 min/500MB to 2 min/500MB for Quest VR hardware
            // Quest headsets may have slower storage and CPU compared to typical Android devices
            val fileSizeMb = task.totalBytes / (1024 * 1024)
            val baseTimeoutMinutes = 5L
            val scaledMinutes = fileSizeMb / 250 // 2 minutes per 500 MB (1 min per 250 MB)
            val timeoutMinutes = (baseTimeoutMinutes + scaledMinutes).coerceIn(5, 360)
            val timeoutMs = timeoutMinutes * 60 * 1000L

            Log.d(TAG, "Task timeout for ${task.releaseName}: ${timeoutMinutes}min (size: ${fileSizeMb}MB)")

            // Added try-finally to ensure taskCompletionSignals are always cleaned up,
            // preventing queue processor stall in case of exceptions (e.g., SecurityException from copyDataToSdcard)
            try {
                withTimeoutOrNull(timeoutMs) {
                    taskCompletionSignals[task.releaseName]?.await()
                } ?: run {
                    // Timeout reached - worker likely failed silently
                    Log.e(TAG, "Task completion timeout for ${task.releaseName} after ${timeoutMinutes}min - marking as FAILED")
                    updateTaskStatus(task.releaseName, InstallTaskStatus.FAILED)
                    _events.emit(MainEvent.ShowMessage("Installation timed out for ${task.gameName}"))
                }
            } catch (e: CancellationException) {
                // Task was cancelled (pause/cancel) - propagate cancellation
                Log.d(TAG, "Task completion signal cancelled for ${task.releaseName}")
                throw e
            }
        } finally {
            // CRITICAL: Always clear active task state if it's still this task
            if (activeReleaseName == task.releaseName) {
                activeReleaseName = null
            }
            // CRITICAL: Always remove the signal from the map to prevent memory leaks
            // and ensure the queue processor can continue even if an unexpected exception occurred
            // The signal may have already been completed and removed by observeDownloadWork or runInstallationPhase
            // but we ensure it's cleaned up here as well for safety
            taskCompletionSignals.remove(task.releaseName)
        }
    }

    /**
     * Observes WorkManager work status and handles completion/failure.
     * Room DB updates from DownloadWorker propagate to UI via existing StateFlow.
     * This method handles post-completion cleanup and APK installation trigger.
     *
     * CONCURRENCY FIX: Manages observer Jobs in downloadObserverJobs map to prevent
     * coroutine accumulation. If an observer already exists for this releaseName,
     * it's cancelled before starting a new one. Jobs are cleaned up on terminal states.
     */
    private fun observeDownloadWork(releaseName: String, gameName: String) {
        // Cancel any existing observer for this releaseName to prevent accumulation
        downloadObserverJobs[releaseName]?.cancel()

        val job = viewModelScope.launch {
            repository.getDownloadWorkInfoFlow(releaseName)
                .collect { workInfoList ->
                    val workInfo = workInfoList.firstOrNull() ?: return@collect

                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> {
                            Log.d(TAG, "Work ENQUEUED: $releaseName (constraints not met)")
                        }
                        WorkInfo.State.RUNNING -> {
                            Log.d(TAG, "Work RUNNING: $releaseName")
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            Log.i(TAG, "Work SUCCEEDED: $releaseName")
                            handleDownloadSuccess(releaseName)
                        }
                        WorkInfo.State.FAILED -> {
                            val errorMessage = workInfo.outputData.getString(
                                DownloadWorker.KEY_STATUS
                            ) ?: "Download failed"
                            Log.e(TAG, "Work FAILED: $releaseName - $errorMessage")
                            handleDownloadFailure(releaseName, gameName, errorMessage)

                            // CRITICAL: Clear activeReleaseName BEFORE signaling completion (Story 1.13 Review Fix)
                            if (activeReleaseName == releaseName) {
                                activeReleaseName = null
                            }

                            // Signal completion so queue processor can continue to next task
                            taskCompletionSignals[releaseName]?.complete(Unit)
                            taskCompletionSignals.remove(releaseName)
                        }
                        WorkInfo.State.CANCELLED -> {
                            Log.d(TAG, "Work CANCELLED: $releaseName")
                            // Status already updated to PAUSED by pauseInstall() or cancelInstall()
                            // Signal cancellation so queue processor can handle it
                            taskCompletionSignals[releaseName]?.cancel()
                            taskCompletionSignals.remove(releaseName)
                        }
                        WorkInfo.State.BLOCKED -> {
                            Log.d(TAG, "Work BLOCKED: $releaseName")
                        }
                    }

                    // Terminal state - stop observing and clean up
                    if (workInfo.state.isFinished) {
                        // Clear active task state if it's still this task
                        if (activeReleaseName == releaseName) {
                            activeReleaseName = null
                        }
                        // Remove from observer jobs map to allow garbage collection
                        downloadObserverJobs.remove(releaseName)
                        return@collect
                    }
                }
        }

        // Store the job in the map for lifecycle management
        downloadObserverJobs[releaseName] = job
    }

    /**
     * Zombie Recovery - Run installation phase only (OBB + APK staging).
     * This method is called when extraction is already complete (extraction_done.marker exists).
     * It skips download/extraction and starts installation at 94% progress.
     */
    private suspend fun runInstallationPhase(task: InstallTaskState, game: GameData) {
        Log.i(TAG, "Starting installation phase for ${task.releaseName}")

        // Update status to INSTALLING
        updateTaskStatus(task.releaseName, InstallTaskStatus.INSTALLING)

        currentTaskJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Call repository.installFromExtracted which only does OBB + staging
                val apkFile = repository.installFromExtracted(
                    game = game
                ) { _, progress, current, total ->
                    updateTaskProgress(task.releaseName, progress, current, total)
                }

                // APK is ready for installation
                if (apkFile != null && apkFile.exists()) {
                    Log.i(TAG, "APK ready for installation: ${apkFile.absolutePath}")
                    withContext(Dispatchers.Main) {
                        _events.emit(MainEvent.InstallApk(apkFile))
                    }
                }

                // Set status to PENDING_INSTALL after launching installer
                // (waiting for user to complete installation in system installer)
                updateTaskStatus(task.releaseName, InstallTaskStatus.PENDING_INSTALL)

                // Do NOT remove from queue
                // The task must persist with PENDING_INSTALL status in DB so it can be:
                // 1. Restored after app restart
                // 2. Verified via verifyPendingInstallations()
                // 3. Cleaned up only after successful PackageManager verification
                // Queue processor will skip PENDING_INSTALL tasks (they're not in QUEUED status).
                progressThrottleMap.remove(task.releaseName)
                totalBytesWrittenSet.remove(task.releaseName)

                withContext(Dispatchers.Main) {
                    updateOverlayAfterTaskComplete(task.releaseName)
                    refreshInstalledPackages()
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Installation cancelled for ${task.releaseName}")
                updateTaskStatus(task.releaseName, InstallTaskStatus.PAUSED)
                taskCompletionSignals[task.releaseName]?.cancel()
                taskCompletionSignals.remove(task.releaseName)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Installation failed for ${task.releaseName}", e)
                updateTaskStatus(task.releaseName, InstallTaskStatus.FAILED)
                withContext(Dispatchers.Main) {
                    _events.emit(MainEvent.ShowMessage("Installation failed: ${e.message}"))
                }
            } finally {
                if (activeReleaseName == task.releaseName) {
                    activeReleaseName = null
                }
                // Signal task completion to allow queue processor to continue
                taskCompletionSignals[task.releaseName]?.complete(Unit)
                taskCompletionSignals.remove(task.releaseName)
            }
        }
    }

    private suspend fun handleDownloadSuccess(releaseName: String) {
        refreshDownloadedReleases()

        val task = installQueue.value.find { it.releaseName == releaseName }
        val game = _allGames.value.find { it.releaseName == releaseName }

        // Helper function to signal task completion (allows queue processor to continue)
        fun signalTaskComplete() {
            taskCompletionSignals[releaseName]?.complete(Unit)
            taskCompletionSignals.remove(releaseName)
        }

        // If download-only mode, move files from temp cache to public Downloads folder
        if (task?.isDownloadOnly == true) {
            Log.i(TAG, "Download-only mode: moving files to Downloads for $releaseName")

            if (game != null) {
                // Update status to show we're finalizing
                updateTaskStatus(releaseName, InstallTaskStatus.EXTRACTING)

                currentTaskJob = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // Use installGame with downloadOnly=true to move files to Downloads folder
                        // This handles extraction, saving to Downloads, and proper cleanup
                        repository.installGame(
                            game = game,
                            keepApk = _keepApks.value,
                            downloadOnly = true, // Moves to Downloads without launching installer
                            skipRemoteVerification = true // Files already verified by WorkManager
                        ) { _, progress, current, total ->
                            // The repository already scales progress (0-0.8 download, 0.8-1.0 extraction)
                            // So we use it directly instead of re-scaling which would cause jumps
                            updateTaskProgress(releaseName, progress, current, total)
                        }

                        updateTaskStatus(releaseName, InstallTaskStatus.COMPLETED)
                        delay(1000)

                        // Archive completed task to history
                        val archived = repository.archiveTask(releaseName, com.vrhub.data.InstallStatus.COMPLETED)
                        if (!archived) {
                            withContext(Dispatchers.Main) {
                                _events.emit(MainEvent.ShowMessage("Failed to archive task to history. It will remain in queue."))
                            }
                        }
                        progressThrottleMap.remove(releaseName)
                        totalBytesWrittenSet.remove(releaseName)

                        withContext(Dispatchers.Main) {
                            updateOverlayAfterTaskComplete(releaseName)
                            refreshDownloadedReleases()
                        }

                    } catch (e: CancellationException) {
                        Log.d(TAG, "Download-only finalization cancelled for $releaseName")
                        updateTaskStatus(releaseName, InstallTaskStatus.PAUSED)
                        // Signal cancellation so queue processor can handle it
                        taskCompletionSignals[releaseName]?.cancel()
                        taskCompletionSignals.remove(releaseName)
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Download-only finalization failed for $releaseName", e)
                        updateTaskStatus(releaseName, InstallTaskStatus.FAILED)
                        withContext(Dispatchers.Main) {
                            _events.emit(MainEvent.ShowMessage("Failed to save download: ${e.message}"))
                        }
                    } finally {
                        // CRITICAL: Clear activeReleaseName BEFORE signaling completion (Story 1.13 Review Fix)
                        // This prevents a race condition where the queue processor might see the old
                        // activeReleaseName when starting the next task.
                        if (activeReleaseName == releaseName) {
                            activeReleaseName = null
                        }
                        signalTaskComplete()
                    }
                }
            } else {
                Log.e(TAG, "Game not found for download-only: $releaseName")
                // Fallback: archive as failed
                try {
                    repository.archiveTask(releaseName, com.vrhub.data.InstallStatus.FAILED, "Game not found in catalog")
                    progressThrottleMap.remove(releaseName)
                    totalBytesWrittenSet.remove(releaseName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to archive task", e)
                }
                updateOverlayAfterTaskComplete(releaseName)
                signalTaskComplete()
            }
            return
        }

        // Full install mode: trigger extraction and installation via legacy flow
        if (game != null) {
            Log.i(TAG, "Download complete, starting extraction/installation for: $releaseName")

            // Update status to EXTRACTING before starting
            updateTaskStatus(releaseName, InstallTaskStatus.EXTRACTING)

            // Run extraction and installation logic (skip remote verification since WorkManager already downloaded)
            currentTaskJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val apkFile = repository.installGame(
                        game = game,
                        keepApk = _keepApks.value,
                        downloadOnly = false,
                        skipRemoteVerification = true // Skip HEAD requests - files already verified by WorkManager
                                        ) { message, progress, current, total ->
                                            // The repository already scales progress (0-0.8 download, 0.8-1.0 extraction)
                                            // So we use it directly instead of re-scaling which would cause jumps
                                            val adjustedProgress = progress

                                            // Determine status from message
                                            val status = when {
                                                message.contains("Extract", ignoreCase = true) -> InstallTaskStatus.EXTRACTING
                                                message.contains("Installing", ignoreCase = true) -> InstallTaskStatus.INSTALLING
                                                message.contains("OBB", ignoreCase = true) -> InstallTaskStatus.INSTALLING
                                                message.contains("Launching", ignoreCase = true) -> InstallTaskStatus.INSTALLING
                                                else -> InstallTaskStatus.EXTRACTING
                                            }

                                            updateTaskProgress(releaseName, adjustedProgress, current, total)
                        // Update status if changed
                        val currentTask = installQueue.value.find { it.releaseName == releaseName }
                        if (currentTask?.status != status) {
                            updateTaskStatus(releaseName, status)
                        }
                    }

                    // APK is ready for installation
                    if (apkFile != null && apkFile.exists()) {
                        Log.i(TAG, "APK ready for installation: ${apkFile.absolutePath}")
                        withContext(Dispatchers.Main) {
                            _events.emit(MainEvent.InstallApk(apkFile))
                        }
                    }

                    // Set status to PENDING_INSTALL after launching installer
                    // (waiting for user to complete installation in system installer)
                    updateTaskStatus(releaseName, InstallTaskStatus.PENDING_INSTALL)

                    // DO NOT clean up temp files yet - wait for verification
                    // Cleanup will happen after checkInstallationStatus() confirms installation

                    // CRITICAL FIX: Do NOT remove from queue - PENDING_INSTALL must persist in DB
                    // so it can be restored after app restart and verified.
                    // The task stays in the queue with PENDING_INSTALL status until verification completes.
                    // Queue processor will skip PENDING_INSTALL tasks (they're not processing).
                    progressThrottleMap.remove(releaseName)
                    totalBytesWrittenSet.remove(releaseName)

                    withContext(Dispatchers.Main) {
                        updateOverlayAfterTaskComplete(releaseName)
                        refreshInstalledPackages()
                    }

                } catch (e: CancellationException) {
                    Log.d(TAG, "Installation cancelled for $releaseName")
                    updateTaskStatus(releaseName, InstallTaskStatus.PAUSED)
                    // Signal cancellation so queue processor can handle it
                    taskCompletionSignals[releaseName]?.cancel()
                    taskCompletionSignals.remove(releaseName)
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Installation failed for $releaseName", e)
                    updateTaskStatus(releaseName, InstallTaskStatus.FAILED)
                    withContext(Dispatchers.Main) {
                        _events.emit(MainEvent.ShowMessage("Installation failed: ${e.message}"))
                    }
                } finally {
                    // CRITICAL: Signal task completion to allow queue processor to continue
                    signalTaskComplete()
                    if (activeReleaseName == releaseName) {
                        activeReleaseName = null
                    }
                }
            }
        } else {
            Log.e(TAG, "Game not found in catalog for installation: $releaseName")
            updateTaskStatus(releaseName, InstallTaskStatus.FAILED)
            _events.emit(MainEvent.ShowMessage("Installation failed: Game not found in catalog"))
            signalTaskComplete()
        }
    }

    private fun updateOverlayAfterTaskComplete(releaseName: String) {
        // Update overlay view
        if (installQueue.value.isEmpty()) {
            _showInstallOverlay.value = false
            _viewedReleaseName.value = null
        } else if (_viewedReleaseName.value == releaseName) {
            val remaining = installQueue.value
            val nextActive = remaining.find { it.status.isProcessing() }
            _viewedReleaseName.value = nextActive?.releaseName ?: remaining.firstOrNull()?.releaseName
        }
    }

    private suspend fun handleDownloadFailure(releaseName: String, gameName: String, errorMessage: String) {
        // Status already updated by DownloadWorker
        progressThrottleMap.remove(releaseName)

        if (errorMessage.contains("Insufficient storage space", ignoreCase = true)) {
            _events.emit(MainEvent.ShowMessage("STORAGE ERROR: $errorMessage"))
        } else {
            _events.emit(MainEvent.ShowMessage("Failed to download $gameName: $errorMessage"))
        }

        // Archive failed task to history
        val archived = repository.archiveTask(releaseName, com.vrhub.data.InstallStatus.FAILED, errorMessage)
        if (!archived) {
            Log.e(TAG, "Failed to archive failed task $releaseName")
        }
    }

    /**
     * Updates task status in Room DB. Status updates are NOT throttled because:
     * 1. State transitions (QUEUED→DOWNLOADING→EXTRACTING→INSTALLING→COMPLETED) must be immediate
     *    for correct UI display and queue processor logic
     * 2. Status changes are infrequent (5-7 per task) vs progress updates (hundreds per second)
     * 3. Delayed status updates could cause race conditions in pause/resume/cancel operations
     * 4. UI relies on status to show correct action buttons (Pause vs Resume, etc.)
     *
     * Terminal states (COMPLETED, FAILED) use NonCancellable context to ensure they persist
     * even if the ViewModel is cleared during the write operation.
     *
     * Contrast with updateTaskProgress() which IS throttled to max 1 update per 500ms.
     */
    private fun updateTaskStatus(releaseName: String, status: InstallTaskStatus) {
        // Terminal states must persist even if ViewModel/coroutine is cancelled
        val isTerminalState = status == InstallTaskStatus.COMPLETED || status == InstallTaskStatus.FAILED
        val context = if (isTerminalState) NonCancellable else viewModelScope.coroutineContext

        // Persist to Room DB immediately - NO THROTTLING (see doc above)
        viewModelScope.launch(context) {
            try {
                repository.updateQueueStatus(releaseName, status.toDataStatus())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update task status in DB", e)
                // Notify user of the failure so they're aware the state may be inconsistent
                _events.emit(MainEvent.ShowMessage("Failed to save queue state. Please restart the app if issues persist."))
            }
        }
    }

    // Throttling state for DB progress updates (NOT for status - see updateTaskStatus doc)
    private val progressThrottleMap = mutableMapOf<String, Long>()

    // Tracks tasks where totalBytes has already been written to DB (optimization to avoid redundant writes)
    private val totalBytesWrittenSet = mutableSetOf<String>()

    /**
     * Updates task progress in Room DB with throttling to prevent excessive I/O.
     * Progress updates happen every 64KB of download (~hundreds/second), so we limit
     * DB writes to max once every 500ms per task. Completion (progress >= 1.0) always updates.
     *
     * Optimization: After the first write that includes totalBytes, subsequent writes
     * skip totalBytes since it's constant throughout the download.
     */
    private fun updateTaskProgress(releaseName: String, progress: Float, current: Long, total: Long) {
        // Check throttle BEFORE launching coroutine to avoid memory pressure
        val now = System.currentTimeMillis()
        val lastUpdate = progressThrottleMap[releaseName] ?: 0L
        val shouldUpdate = (now - lastUpdate) >= com.vrhub.data.Constants.PROGRESS_THROTTLE_MS || progress >= 1.0f

        if (!shouldUpdate) return // Early exit - no coroutine launched

        progressThrottleMap[releaseName] = now

        // Check if totalBytes has already been written for this task
        val hasTotalBeenWritten = totalBytesWrittenSet.contains(releaseName)
        val shouldWriteTotal = !hasTotalBeenWritten && total > 0

        // Mark as written if we're about to write totalBytes for the first time
        if (shouldWriteTotal) {
            totalBytesWrittenSet.add(releaseName)
        }

        // Only now launch coroutine for DB persistence
        viewModelScope.launch {
            try {
                repository.updateQueueProgress(
                    releaseName = releaseName,
                    progress = progress,
                    downloadedBytes = if (current > 0) current else null,
                    totalBytes = if (total > 0) total else null,
                    skipTotalBytes = hasTotalBeenWritten // Skip if already written
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update task progress in DB", e)
            }
        }
    }

    fun pauseInstall(releaseName: String) {
        val task = installQueue.value.find { it.releaseName == releaseName } ?: return
        if (task.status.isProcessing() || task.status == InstallTaskStatus.QUEUED) {
            updateTaskStatus(releaseName, InstallTaskStatus.PAUSED)
            progressThrottleMap.remove(releaseName) // Clean up throttling state on pause
            totalBytesWrittenSet.remove(releaseName) // Reset so totalBytes is written on resume

            // Cancel WorkManager work
            repository.cancelDownloadWork(releaseName)

            // Clean up observer job immediately (don't wait for WorkInfo terminal state)
            downloadObserverJobs[releaseName]?.cancel()
            downloadObserverJobs.remove(releaseName)

            // Clean up completion signal
            taskCompletionSignals[releaseName]?.cancel()
            taskCompletionSignals.remove(releaseName)

            if (releaseName == activeReleaseName) {
                currentTaskJob?.cancel()
                activeReleaseName = null
            }
        }
    }

    fun resumeInstall(releaseName: String) {
        // If it's the first in queue, just start it
        val isFirst = installQueue.value.firstOrNull()?.releaseName == releaseName
        if (isFirst) {
            _viewedReleaseName.value = releaseName
            updateTaskStatus(releaseName, InstallTaskStatus.QUEUED)
            startQueueProcessor()
        } else {
            // If not first, promote it to front which will also set status to QUEUED and start processor
            promoteTask(releaseName)
        }
    }

    fun cancelInstall(releaseName: String) {
        if (installQueue.value.none { it.releaseName == releaseName }) return

        // Cancel WorkManager work first
        repository.cancelDownloadWork(releaseName)

        // Clean up observer job immediately (don't wait for WorkInfo terminal state)
        downloadObserverJobs[releaseName]?.cancel()
        downloadObserverJobs.remove(releaseName)

        // Clean up completion signal
        taskCompletionSignals[releaseName]?.cancel()
        taskCompletionSignals.remove(releaseName)

        if (releaseName == activeReleaseName) {
            currentTaskJob?.cancel()
            activeReleaseName = null
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.cleanupInstall(releaseName)
            // Also clean up staged APK if we have the package name
            installQueue.value.find { it.releaseName == releaseName }?.packageName?.let { pkg ->
                repository.deleteStagedApk(pkg)
            }
            
            // Remove from Room DB
            repository.removeFromQueue(releaseName)
            progressThrottleMap.remove(releaseName) // Clean up throttling state
            totalBytesWrittenSet.remove(releaseName) // Clean up totalBytes tracking
        }

        val remaining = installQueue.value
        if (_viewedReleaseName.value == releaseName) {
            _viewedReleaseName.value = remaining.find { it.status.isProcessing() }?.releaseName
                ?: remaining.firstOrNull()?.releaseName
        }

        if (remaining.isEmpty()) {
            _showInstallOverlay.value = false
            _viewedReleaseName.value = null
        } else {
            // Restart processor just in case it stopped
            startQueueProcessor()
        }
    }

    fun promoteTask(releaseName: String) {
        val currentQueue = installQueue.value
        val task = currentQueue.find { it.releaseName == releaseName } ?: return

        if (task.status == InstallTaskStatus.QUEUED || task.status == InstallTaskStatus.PAUSED || 
            task.status == InstallTaskStatus.FAILED || task.status == InstallTaskStatus.SHELVED ||
            task.status == InstallTaskStatus.PENDING_INSTALL) {
            // Pause current processing task if any
            val previousActive = activeReleaseName
            if (previousActive != null && previousActive != releaseName) {
                updateTaskStatus(previousActive, InstallTaskStatus.PAUSED)
                // Cancel WorkManager work for previous active task
                repository.cancelDownloadWork(previousActive)
                currentTaskJob?.cancel()
            }

            // Reorder queue in Room DB atomically: promote to front AND set status to QUEUED
            // Using single atomic transaction to prevent partial state updates
            viewModelScope.launch {
                try {
                    if (task.status != InstallTaskStatus.QUEUED) {
                        // Use atomic operation that promotes AND updates status in one transaction
                        repository.promoteInQueueAndSetStatus(releaseName, com.vrhub.data.InstallStatus.QUEUED)
                    } else {
                        // Already QUEUED, just promote position
                        repository.promoteInQueue(releaseName)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to promote task in DB", e)
                    _events.emit(MainEvent.ShowMessage("Failed to promote task. Please try again."))
                }
            }

            _viewedReleaseName.value = releaseName

            // Restart processor to pick up the new top task
            queueProcessorJob?.cancel()
            queueProcessorJob = null
            startQueueProcessor()
        }
    }

    /**
     * Moves a task to SHELVED status.
     * Shelved tasks are ready to install but out of the active queue.
     * They are displayed in the "Ready to Install" filter.
     *
     * @param releaseName The unique release name of the game to shelve
     */
    fun shelveTask(releaseName: String) {
        viewModelScope.launch {
            Log.i(TAG, "Shelving task: $releaseName")
            repository.updateQueueStatus(releaseName, com.vrhub.data.InstallStatus.SHELVED)
            withContext(Dispatchers.Main) {
                updateOverlayAfterTaskComplete(releaseName)
            }
        }
    }

    fun setFocusedTask(releaseName: String) {
        _viewedReleaseName.value = releaseName
        _showInstallOverlay.value = true
    }

    fun hideInstallOverlay() {
        _showInstallOverlay.value = false
    }

    fun toggleKeepApks() {
        val newValue = !_keepApks.value
        _keepApks.value = newValue
        prefs.edit().putBoolean("keep_apks", newValue).apply()
    }

    fun uninstallGame(packageName: String) {
        viewModelScope.launch {
            _events.emit(MainEvent.Uninstall(packageName))
        }
    }

    fun deleteDownloadedGame(releaseName: String) {
        viewModelScope.launch {
            try {
                repository.deleteDownloadedGame(releaseName)
                refreshDownloadedReleases()
                _events.emit(MainEvent.ShowMessage("Download deleted"))
            } catch (e: Exception) {
                _events.emit(MainEvent.ShowMessage("Failed to delete download: ${e.message}"))
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                val freed = repository.clearCache()
                _events.emit(MainEvent.ShowMessage("Cache cleared, freed ${InstallUtils.formatBytes(freed)}"))
            } catch (e: Exception) {
                _events.emit(MainEvent.ShowMessage("Failed to clear cache: ${e.message}"))
            }
        }
    }

    fun exportDiagnostics(toFile: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _events.emit(MainEvent.ShowMessage("Collecting logs..."))
                val process = Runtime.getRuntime().exec("logcat -d")
                val logs = process.inputStream.bufferedReader().use { it.readText() }

                if (toFile) {
                    val fileName = repository.saveLogs(logs)
                    _events.emit(MainEvent.ShowMessage("Logs saved to Download/VRHub/$fileName"))
                } else {
                    withContext(Dispatchers.Main) {
                        _events.emit(MainEvent.CopyLogs(logs))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs", e)
                _events.emit(MainEvent.ShowMessage("Failed to collect logs: ${e.message}"))
            }
        }
    }

    /**
     * Verify all PENDING_INSTALL tasks automatically.
     * This method should be called when app returns to foreground (onResume in MainActivity).
     *
     * It scans the install queue for tasks with PENDING_INSTALL status and verifies each one.
     * Successfully verified installations are marked COMPLETED and cleaned up.
     *
     * Consolidates UI messages to prevent Snackbar spam during batch verification.
     */
    fun verifyPendingInstallations() {
        viewModelScope.launch(Dispatchers.IO) {
            // Use Mutex to prevent concurrent executions
            // Multiple triggers (startup, resume, user action) could cause overlapping verification
            verificationMutex.withLock {
                try {
                // Get all PENDING_INSTALL tasks from the queue
                val pendingTasks = installQueue.value.filter { it.status == InstallTaskStatus.PENDING_INSTALL }

                // Note: Staged APK cleanup when user cancels installation
                // If user cancels the system installation, the staged APK remains in externalFilesDir
                // This is acceptable because:
                // 1. The APK will be cleaned up on next app restart via verifyAndCleanupInstalls()
                // 2. User can retry installation by clicking "Verify" button
                // 3. APK is named by packageName, so reinstall simply overwrites
                // 4. Storage impact is limited (one APK per pending installation)
                // To implement age-based cleanup, we'd need to track when status became PENDING_INSTALL

                if (pendingTasks.isEmpty()) {
                    Log.d(TAG, "No pending installations to verify")
                    return@withLock
                }

                Log.i(TAG, "Found ${pendingTasks.size} pending installation(s) to verify")

                // Track verification results to consolidate UI messages
                // Added failure tracking and detailed logging
                var verifiedCount = 0
                var pendingCount = 0
                var failedCount = 0
                val failedGames = mutableListOf<String>()

                // Verify each pending installation (silently - no individual messages)
                // Add delay between installations to prevent UI confusion
                // Multiple system installer dialogs opening simultaneously can overwhelm users
                for ((index, task) in pendingTasks.withIndex()) {
                    val game = games.value.find { it.releaseName == task.releaseName }
                    if (game != null) {
                        Log.d(TAG, "Verifying pending installation: ${task.releaseName}")

                        // If this is not the first task and verification will succeed, add delay
                        // This prevents multiple installer dialogs from opening simultaneously
                        if (index > 0) {
                            delay(2000) // 2 second delay between installations
                        }

                        val wasVerified = checkInstallationStatusSilent(game.packageName, game.version, task.releaseName)
                        if (wasVerified) {
                            verifiedCount++
                        } else {
                            // Story 1.13: Move to SHELVED if installation not complete
                            // CRITICAL: Validate staged APK before shelving (Story 1.13 Review Fix)
                            // If the APK is corrupted or missing, mark as FAILED to prevent stuck tasks.
                            val expectedVersion = game.version.toLongOrNull()
                            val stagedApk = repository.getValidStagedApk(game.packageName, expectedVersion)
                            
                            if (stagedApk != null) {
                                Log.d(TAG, "Installation not complete for ${task.releaseName} - shelving valid APK")
                                repository.updateQueueStatus(task.releaseName, com.vrhub.data.InstallStatus.SHELVED)
                                pendingCount++
                            } else {
                                Log.e(TAG, "Installation not complete and staged APK invalid/missing for ${task.releaseName} - marking as FAILED")
                                repository.updateQueueStatus(task.releaseName, com.vrhub.data.InstallStatus.FAILED)
                                repository.cleanupInstall(task.releaseName)
                                repository.archiveTask(task.releaseName, com.vrhub.data.InstallStatus.FAILED, "Staged APK corrupted or missing")
                                failedCount++
                                failedGames.add(task.gameName)
                            }
                        }
                    } else {
                        Log.w(TAG, "Game not found in catalog for pending installation: ${task.releaseName}")
                        // Game removed from catalog? Track as failure and archive
                        failedCount++
                        failedGames.add(task.gameName)
                        repository.cleanupInstall(task.releaseName)
                        val archived = repository.archiveTask(task.releaseName, com.vrhub.data.InstallStatus.FAILED, "Game not found in catalog")
                        if (!archived) {
                            Log.e(TAG, "Failed to archive task ${task.releaseName} (game not found)")
                        }
                    }
                }

                // Log detailed failure info for diagnostics
                if (failedGames.isNotEmpty()) {
                    Log.e(TAG, "Verification failed for ${failedGames.size} game(s): ${failedGames.joinToString(", ")}")
                }

                // Show consolidated message instead of per-task messages
                // Include failure info in message when relevant
                if (verifiedCount > 0 || pendingCount > 0 || failedCount > 0) {
                    val message = when {
                        // All verified
                        verifiedCount > 0 && pendingCount == 0 && failedCount == 0 ->
                            if (verifiedCount == 1) "Installation verified successfully"
                            else "$verifiedCount installations verified successfully"
                        // All pending
                        verifiedCount == 0 && pendingCount > 0 && failedCount == 0 ->
                            if (pendingCount == 1) "Waiting for installation to complete..."
                            else "$pendingCount installations pending..."
                        // Some failed (include failure info)
                        failedCount > 0 -> {
                            val parts = mutableListOf<String>()
                            if (verifiedCount > 0) parts.add("$verifiedCount verified")
                            if (pendingCount > 0) parts.add("$pendingCount pending")
                            parts.add("$failedCount failed")
                            parts.joinToString(", ")
                        }
                        // Mixed verified and pending
                        else -> "$verifiedCount verified, $pendingCount pending"
                    }
                    withContext(Dispatchers.Main) {
                        _events.emit(MainEvent.ShowMessage(message))
                        if (verifiedCount > 0) {
                            refreshInstalledPackages()
                        }
                    }
                }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to verify pending installations", e)
                }
            } // end verificationMutex.withLock
        }
    }

    /**
     * Post-Install Verification - Check if installation was successful.
     * This method should be called when app returns to foreground or user clicks "Verify".
     * It checks PackageManager for installed package and verifies version matches catalog.
     *
     * @param packageName The package name to verify
     * @param catalogVersionCode The version code from catalog (String to handle Room schema)
     * @param releaseName The release name for cleanup if verification succeeds
     */
    fun checkInstallationStatus(packageName: String, catalogVersionCode: String, releaseName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wasVerified = checkInstallationStatusSilent(packageName, catalogVersionCode, releaseName)
            withContext(Dispatchers.Main) {
                if (wasVerified) {
                    _events.emit(MainEvent.ShowMessage("Installation verified successfully"))
                    refreshInstalledPackages()
                } else {
                    _events.emit(MainEvent.ShowMessage("Waiting for installation to complete..."))
                }
            }
        }
    }

    /**
     * Silent version of checkInstallationStatus for batch verification.
     * Does not emit UI messages - caller is responsible for consolidated messaging.
     *
     * @return true if installation was successfully verified, false if still pending or failed
     */
    private suspend fun checkInstallationStatusSilent(
        packageName: String,
        catalogVersionCode: String,
        releaseName: String
    ): Boolean {
        return try {
            Log.i(TAG, "Checking installation status for $packageName (catalog version: $catalogVersionCode)")

            // Query PackageManager for installed package
            val pm = getApplication<Application>().packageManager
            val packageInfo = try {
                pm.getPackageInfo(packageName, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Package $packageName not found in PackageManager")
                null
            }

            if (packageInfo == null) {
                Log.d(TAG, "Package $packageName not installed yet")
                return false
            }

            // Parse catalog versionCode (String) to Long for comparison
            // Handle edge cases: empty string, malformed, null -> 0L
            val catalogVersion = catalogVersionCode.toLongOrNull() ?: 0L

            // Get installed version (handle both legacy and long version codes)
            val installedVersion = packageInfo.longVersionCode

            Log.d(TAG, "Version comparison for $packageName: catalog=$catalogVersion, installed=$installedVersion")

            // Use >= comparison instead of strict equality
            // This allows verification to succeed if user installs a newer version than catalog
            if (installedVersion >= catalogVersion) {
                Log.i(TAG, "Installation verification successful for $packageName (installed version $installedVersion >= catalog $catalogVersion)")

                // Mark task as COMPLETED before cleanup
                updateTaskStatus(releaseName, InstallTaskStatus.COMPLETED)

                // Clean up temp files and staged APK after successful verification
                repository.cleanupInstall(releaseName)
                repository.deleteStagedApk(packageName)

                // Archive completed task to history
                repository.archiveTask(releaseName, com.vrhub.data.InstallStatus.COMPLETED)

                true
            } else {
                Log.w(TAG, "Version too old for $packageName: installed $installedVersion < catalog $catalogVersion")
                // Installed version is older than catalog - user may have restored from backup
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check installation status for $packageName", e)
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
