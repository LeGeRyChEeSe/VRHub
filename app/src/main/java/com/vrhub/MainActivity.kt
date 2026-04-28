package com.vrhub

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.vrhub.ui.*
import com.vrhub.data.ServerConfigRepository
import com.vrhub.ui.ConfigurationScreen
import com.vrhub.ui.ConfigurationViewModel
import com.vrhub.ui.components.CatalogUpdateBanner
import com.vrhub.ui.theme.VRHubTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VRHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenWrapper()
                }
            }
        }
    }
}

/**
 * Wrapper composable that decides whether to show the ConfigurationScreen
 * or the main catalog screen based on whether a valid configuration exists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreenWrapper() {
    val context = LocalContext.current
    var configKey by remember { mutableStateOf(0) }
    val configRepository = remember(configKey) { ServerConfigRepository(context) }
    val hasValidConfig = try {
        configRepository.hasValidConfig()
    } catch (e: Exception) {
        false
    }

    if (!hasValidConfig) {
        ConfigurationScreen(
            onConfigSaved = {
                // Increment key to force recomposition and re-read of config
                configKey++
            },
            onCancel = {}
        )
    } else {
        MainScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val games by viewModel.games.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val installQueue by viewModel.installQueue.collectAsState()
    val showInstallOverlay by viewModel.showInstallOverlay.collectAsState()
    val viewedReleaseName by viewModel.viewedReleaseName.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val filterCounts by viewModel.filterCounts.collectAsState()
    val missingPermissions by viewModel.missingPermissions.collectAsState()
    val alphabetInfo by viewModel.alphabetInfo.collectAsState()
    val keepApks by viewModel.keepApks.collectAsState()
    val isCatalogUpdateAvailable by viewModel.isCatalogUpdateAvailable.collectAsState()
    val catalogUpdateCount by viewModel.catalogUpdateCount.collectAsState()
    val catalogSyncProgress by viewModel.catalogSyncProgress.collectAsState()

    // Navigation state
    var currentScreen by remember { mutableStateOf("catalog") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val isUpdateCheckInProgress by viewModel.isUpdateCheckInProgress.collectAsState()
    val isUpdateDownloading by viewModel.isUpdateDownloading.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()
    val configurationViewModel: ConfigurationViewModel = viewModel()

    val listState = rememberLazyListState()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showUpdateDialogState by remember { mutableStateOf<com.vrhub.network.UpdateInfo?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var gameToDelete by remember { mutableStateOf<GameItemState?>(null) }
    var taskToCancel by remember { mutableStateOf<String?>(null) }

    // Permission request dialog state
    var showPermissionDialog by remember { mutableStateOf<RequiredPermission?>(null) }

    // Permission revoked dialog state
    val showRevokedDialog by viewModel.showRevokedDialog.collectAsState()

    LaunchedEffect(listState, staggeredGridState) {
        snapshotFlow {
            if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) listState.layoutInfo.visibleItemsInfo.map { it.index }
            else staggeredGridState.layoutInfo.visibleItemsInfo.map { it.index }
        }
        .collect { indices ->
            viewModel.setVisibleIndices(indices)
        }
    }

    // Story 1.7: Note - verifyPendingInstallations() is called in ON_RESUME lifecycle event
    // which covers both app startup and return from installer. No separate LaunchedEffect needed.

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MainEvent.Uninstall -> {
                    try {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.fromParts("package", event.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to uninstall: ${e.message}")
                    }
                }
                is MainEvent.InstallApk -> {
                    try {
                        val authority = "${context.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, event.apkFile)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to launch installer: ${e.message}")
                    }
                }
                is MainEvent.RequestInstallPermission -> {
                    // Story 1.8: Show permission dialog first, then launch intent
                    showPermissionDialog = RequiredPermission.INSTALL_UNKNOWN_APPS
                }
                is MainEvent.RequestStoragePermission -> {
                    // Story 1.8: Show permission dialog first, then launch intent
                    showPermissionDialog = RequiredPermission.MANAGE_EXTERNAL_STORAGE
                }
                is MainEvent.RequestIgnoreBatteryOptimizations -> {
                    // Story 1.8: Show permission dialog first, then launch intent
                    showPermissionDialog = RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS
                }
                is MainEvent.ShowUpdatePopup -> {
                    showUpdateDialogState = event.updateInfo
                }
                is MainEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is MainEvent.CopyLogs -> {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("VRHub Logs", event.logs)
                        clipboard.setPrimaryClip(clip)
                        snackbarHostState.showSnackbar("Logs copied to clipboard")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to copy logs: ${e.message}")
                    }
                }
                is MainEvent.OpenPermissionSettings -> {
                    // Open system settings for the specific permission
                    // Add explanatory message for Android 10 (API 29) storage permission
                    // since users need to manually find "Files and media" in settings
                    try {
                        // For Android 10 storage permission, show instructions first
                        if (event.permission == RequiredPermission.MANAGE_EXTERNAL_STORAGE &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            // Launch coroutine to show message then open settings
                            // Use CoroutineScope since we're not in a ViewModel context
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                snackbarHostState.showSnackbar(
                                    message = "In Settings: Tap 'Permissions' → Enable 'Files and media'",
                                    duration = SnackbarDuration.Long
                                )
                                // Brief pause to let user read the message, then open settings
                                kotlinx.coroutines.delay(1500)
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        } else {
                            val intent = when (event.permission) {
                                RequiredPermission.INSTALL_UNKNOWN_APPS -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    } else null
                                }
                                RequiredPermission.MANAGE_EXTERNAL_STORAGE -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    } else null
                                }
                                RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    } else null
                                }
                            }
                            intent?.let { context.startActivity(it) }
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to open settings: ${e.message}")
                    }
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Handle app resume (check permissions, sync flow)
                    // Consolidated into single call to avoid race conditions
                    viewModel.onAppResume()

                    viewModel.setAppVisibility(true)
                    // Story 1.7: Verify pending installations when app returns to foreground
                    // This handles the case where user installed APK in system installer
                    // and is now returning to the app to complete the flow
                    viewModel.verifyPendingInstallations()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setAppVisibility(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth > 800.dp

        when {
            isUpdateCheckInProgress -> LoadingScreen("Checking for updates...")
            isUpdateDownloading -> {
                BackHandler(enabled = true) { /* Block back button during update */ }
                InstallationOverlay(
                    activeTask = InstallTaskState(releaseName = "update", gameName = "VRHub Update", packageName = "", status = InstallTaskStatus.DOWNLOADING, message = updateProgress, progress = -1f),
                    onCancel = {},
                    onPause = {},
                    onResume = {},
                    onBackground = {}
                )
            }
            showUpdateDialogState != null -> {
                BackHandler(enabled = true) { /* Block back button during update dialog */ }
                UpdateOverlay(
                    updateInfo = showUpdateDialogState!!,
                    onDismiss = {
                        showUpdateDialogState = null
                        viewModel.onUpdateDialogDismissed()
                    },
                    onConfirm = {
                        viewModel.downloadAndInstallUpdate(showUpdateDialogState!!)
                        showUpdateDialogState = null
                    }
                )
            }
            missingPermissions == null -> LoadingScreen("Checking permissions...")
            showConfigDialog -> {
                ConfigurationScreen(
                    viewModel = configurationViewModel,
                    isEditing = true,
                    onConfigSaved = {
                        Log.d("ConfigDebug", "onConfigSaved called")
                        showConfigDialog = false
                        configurationViewModel.resetLoadState()
                        viewModel.refreshData()
                    },
                    onCancel = {
                        Log.d("ConfigDebug", "onCancel called")
                        configurationViewModel.resetLoadState()
                        showConfigDialog = false
                    }
                )
            }
            showSettingsDialog -> {
                SettingsDialog(
                    keepApks = keepApks,
                    onToggleKeepApks = { viewModel.toggleKeepApks() },
                    onExportDiagnostics = { viewModel.exportDiagnostics(toFile = false) },
                    onSaveDiagnostics = { viewModel.exportDiagnostics(toFile = true) },
                    onClearCache = { viewModel.clearCache() },
                    missingPermissions = missingPermissions ?: emptyList(),
                    onPermissionClick = { viewModel.openPermissionSettings(it) },
                    onServerConfigClick = {
                        showSettingsDialog = false
                        showConfigDialog = true
                    },
                    onDismiss = { showSettingsDialog = false }
                )
            }
            else -> {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen == "catalog",
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color(0xFF121212),
                            drawerContentColor = Color.White,
                            drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                        ) {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "VRHUB",
                                modifier = Modifier.padding(horizontal = 28.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp,
                                color = Color.White
                            )
                            Spacer(Modifier.height(24.dp))
                            NavigationDrawerItem(
                                label = { Text("Catalog", fontWeight = FontWeight.Bold) },
                                selected = currentScreen == "catalog" && selectedFilter != FilterStatus.LOCAL_INSTALLS,
                                onClick = {
                                    currentScreen = "catalog"
                                    viewModel.setFilter(FilterStatus.ALL)
                                    coroutineScope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Apps, null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.filter_local_installs), fontWeight = FontWeight.Bold) },
                                selected = currentScreen == "catalog" && selectedFilter == FilterStatus.LOCAL_INSTALLS,
                                onClick = {
                                    currentScreen = "catalog"
                                    viewModel.setFilter(FilterStatus.LOCAL_INSTALLS)
                                    coroutineScope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.DownloadDone, null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Installation History", fontWeight = FontWeight.Bold) },
                                selected = currentScreen == "history",
                                onClick = {
                                    currentScreen = "history"
                                    coroutineScope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.History, null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            Spacer(Modifier.weight(1f))
                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.05f))
                            NavigationDrawerItem(
                                label = { Text("Check for Updates", fontWeight = FontWeight.Bold) },
                                selected = false,
                                onClick = {
                                    viewModel.checkForUpdatesManually()
                                    coroutineScope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Update, null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Settings", fontWeight = FontWeight.Bold) },
                                selected = false,
                                onClick = {
                                    showSettingsDialog = true
                                    coroutineScope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Settings, null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Version ${BuildConfig.VERSION_NAME}",
                                modifier = Modifier
                                    .padding(horizontal = 28.dp)
                                    .alpha(0.5f),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                ) {
                    if (currentScreen == "history") {
                        BackHandler { currentScreen = "catalog" }
                        InstallHistoryScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "catalog" }
                        )
                    } else {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                CustomTopBar(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    selectedFilter = selectedFilter,
                                    onFilterChange = { viewModel.setFilter(it) },
                                    sortMode = sortMode,
                                    onSortChange = { viewModel.setSortMode(it) },
                                    filterCounts = filterCounts,
                                    onSettingsClick = { showSettingsDialog = true },
                                    onRefreshClick = { viewModel.refreshData() },
                                    onNavigationClick = { coroutineScope.launch { drawerState.open() } },
                                    isRefreshing = isRefreshing,
                                    isInstalling = installQueue.any {
                                        it.status != InstallTaskStatus.COMPLETED &&
                                        it.status != InstallTaskStatus.FAILED &&
                                        it.status != InstallTaskStatus.PAUSED &&
                                        it.status != InstallTaskStatus.BLOCKED_BY_PERMISSIONS &&
                                        it.status != InstallTaskStatus.SHELVED
                                    } || isUpdateDownloading,
                                    permissionsMissing = !missingPermissions.isNullOrEmpty()
                                )
                            },
                            containerColor = Color.Black,
                            bottomBar = {
                                AnimatedVisibility(
                                    visible = !showInstallOverlay && installQueue.isNotEmpty(),
                                    enter = slideInVertically(initialOffsetY = { it }),
                                    exit = slideOutVertically(targetOffsetY = { it })
                                ) {
                                    BottomQueueBar(
                                        queue = installQueue,
                                        onClick = { viewModel.showOverlay() }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize().animateContentSize()) {
                                    AnimatedVisibility(
                                        visible = isCatalogUpdateAvailable,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        CatalogUpdateBanner(
                                            updateCount = catalogUpdateCount,
                                            onSyncNow = { viewModel.syncCatalogNow() },
                                            onDismiss = { viewModel.dismissCatalogUpdate() }
                                        )
                                    }

                                    Row(modifier = Modifier.weight(1f)) {
                                        if (games.isNotEmpty() && searchQuery.isEmpty() && selectedFilter == FilterStatus.ALL && sortMode == SortMode.NAME_ASC) {
                                        AlphabetIndexer(
                                            alphabetInfo = alphabetInfo,
                                            onLetterClick = { index ->
                                                coroutineScope.launch {
                                                    if (isWide) staggeredGridState.scrollToItem(index)
                                                    else listState.scrollToItem(index)
                                                }
                                            }
                                        )
                                        Box(
                                            modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.1f))
                                        )
                                    }

                                    if (isRefreshing && games.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            LoadingScreen("Loading Catalog...")
                                        }
                                    } else if (error != null && games.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            ErrorScreen(error!!, onRetry = { viewModel.refreshData() })
                                        }
                                    } else if (isWide) {
                                        LazyVerticalStaggeredGrid(
                                            columns = StaggeredGridCells.Fixed(3),
                                            state = staggeredGridState,
                                            contentPadding = PaddingValues(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalItemSpacing = 8.dp,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        ) {
                                            items(games, key = { it.releaseName }) { game ->
                                                GameListItem(
                                                    game = game,
                                                    onInstallClick = { viewModel.installGame(game.releaseName) },
                                                    onUninstallClick = { viewModel.uninstallGame(game.packageName) },
                                                    onDownloadOnlyClick = { viewModel.installGame(game.releaseName, downloadOnly = true) },
                                                    onDeleteDownloadClick = { gameToDelete = game },
                                                    onResumeClick = { viewModel.resumeInstall(game.releaseName) },
                                                    onToggleFavorite = { viewModel.toggleFavorite(game.releaseName, it) },
                                                    isGridItem = true,
                                                    permissionsMissing = missingPermissions?.isNotEmpty() == true,
                                                    modifier = Modifier.animateItemPlacement()
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            state = listState,
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        ) {
                                            items(games, key = { it.releaseName }) { game ->
                                                GameListItem(
                                                    game = game,
                                                    onInstallClick = { viewModel.installGame(game.releaseName) },
                                                    onUninstallClick = { viewModel.uninstallGame(game.packageName) },
                                                    onDownloadOnlyClick = { viewModel.installGame(game.releaseName, downloadOnly = true) },
                                                    onDeleteDownloadClick = { gameToDelete = game },
                                                    onResumeClick = { viewModel.resumeInstall(game.releaseName) },
                                                    onToggleFavorite = { viewModel.toggleFavorite(game.releaseName, it) },
                                                    permissionsMissing = missingPermissions?.isNotEmpty() == true,
                                                    modifier = Modifier.animateItemPlacement()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                                if (isRefreshing && games.isNotEmpty()) {
                                    SyncingOverlay(catalogSyncProgress, catalogUpdateCount)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showInstallOverlay) {
            val taskToShow = installQueue.find { it.releaseName == viewedReleaseName }
                ?: installQueue.find { it.status.isProcessing() }
                ?: installQueue.firstOrNull()

            if (taskToShow != null) {
                QueueManagerOverlay(
                    queue = installQueue,
                    viewedReleaseName = taskToShow.releaseName,
                    onTaskClick = { viewModel.setFocusedTask(it) },
                    onCancel = { taskToCancel = it },
                    onPause = { viewModel.pauseInstall(it) },
                    onResume = { viewModel.resumeInstall(it) },
                    onPromote = { viewModel.promoteTask(it) },
                    onClose = { viewModel.hideInstallOverlay() }
                )
            }
        }

        if (gameToDelete != null) {
            AlertDialog(
                onDismissRequest = { gameToDelete = null },
                title = { Text(stringResource(R.string.dialog_delete_download_title)) },
                text = { Text(stringResource(R.string.dialog_delete_download_msg, gameToDelete?.name ?: "")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            gameToDelete?.let { viewModel.deleteDownloadedGame(it.releaseName) }
                            gameToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679))
                    ) {
                        Text(stringResource(R.string.btn_delete), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { gameToDelete = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (taskToCancel != null) {
            val taskName = installQueue.find { it.releaseName == taskToCancel }?.gameName ?: taskToCancel
            AlertDialog(
                onDismissRequest = { taskToCancel = null },
                title = { Text("Cancel Download?") },
                text = { Text("Are you sure you want to cancel the download of $taskName? This will delete any partially downloaded files.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            taskToCancel?.let { viewModel.cancelInstall(it) }
                            taskToCancel = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679))
                    ) {
                        Text("CANCEL DOWNLOAD", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskToCancel = null }) {
                        Text("KEEP")
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Permission Request Dialog
        if (showPermissionDialog != null) {
            PermissionRequestDialog(
                permission = showPermissionDialog!!,
                onGrant = {
                    // Launch the appropriate intent based on permission type
                    when (showPermissionDialog!!) {
                        RequiredPermission.INSTALL_UNKNOWN_APPS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback: Try to open app settings page
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        }
                        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }
                        }
                        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    }
                    // Clear the dialog state after launching intent
                    showPermissionDialog = null
                },
                onCancel = {
                    // User cancelled the permission request
                    viewModel.cancelPermissionFlow()
                    showPermissionDialog = null
                },
                onDismiss = {
                    // Dialog was dismissed (e.g., back button)
                    viewModel.cancelPermissionFlow()
                    showPermissionDialog = null
                }
            )
        }

        // Permission Revoked Dialog
        // Updated to handle list of revoked permissions
        if (showRevokedDialog.isNotEmpty()) {
            PermissionRevokedDialog(
                permissions = showRevokedDialog,
                onOpenSettings = {
                    // Open settings for the first revoked permission
                    viewModel.openPermissionSettings(showRevokedDialog.first())
                },
                onDismiss = {
                    viewModel.dismissRevokedDialog()
                }
            )
        }
    }
}

@Composable
fun SetupLayout(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.secondary,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    isScrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(iconColor.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (isScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isScrollable) Arrangement.Top else Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(48.dp))

                content()
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = iconColor)
                ) {
                    Text(primaryButtonText, fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (iconColor == Color.White) Color.Black else Color.White)
                }

                if (secondaryButtonText != null && onSecondaryClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onSecondaryClick,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text(secondaryButtonText, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionOverlay(
    missingPermissions: List<RequiredPermission>,
    onGrantClick: () -> Unit
) {
    SetupLayout(
        title = stringResource(R.string.perm_overlay_title),
        subtitle = stringResource(R.string.perm_overlay_subtitle),
        icon = Icons.Default.Security,
        iconColor = MaterialTheme.colorScheme.secondary,
        primaryButtonText = stringResource(R.string.perm_overlay_grant_button),
        onPrimaryClick = onGrantClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            missingPermissions.forEach { permission ->
                val (title, description, icon) = when (permission) {
                    RequiredPermission.INSTALL_UNKNOWN_APPS -> Triple(
                        stringResource(R.string.perm_install_unknown_apps_title),
                        stringResource(R.string.perm_install_unknown_apps_desc),
                        Icons.Default.SystemUpdate
                    )
                    RequiredPermission.MANAGE_EXTERNAL_STORAGE -> Triple(
                        stringResource(R.string.perm_manage_storage_title),
                        stringResource(R.string.perm_manage_storage_desc),
                        Icons.Default.Storage
                    )
                    RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> Triple(
                        stringResource(R.string.perm_battery_optimization_title),
                        stringResource(R.string.perm_battery_optimization_desc),
                        Icons.Default.BatteryChargingFull
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(description, color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateOverlay(
    updateInfo: com.vrhub.network.UpdateInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    SetupLayout(
        title = stringResource(R.string.update_available_title),
        subtitle = stringResource(R.string.update_available_desc),
        icon = Icons.Default.NewReleases,
        iconColor = Color(0xFF3498db),
        primaryButtonText = stringResource(R.string.btn_update_now),
        onPrimaryClick = onConfirm,
        secondaryButtonText = stringResource(R.string.btn_later),
        onSecondaryClick = onDismiss,
        isScrollable = true
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF3498db), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Version ${updateInfo.version}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = parseMarkdown(updateInfo.changelog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun ResponsiveTitle(text: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val maxWidth = maxWidth
        // Fallback to shorter title if width is extremely constrained (Quest 2/3 narrow profiles)
        val displayTitle = if (maxWidth < 110.dp) "VRHUB" else text
        
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Black
        )

        val fontSize = remember(displayTitle, maxWidth, density) {
            var currentSize = 11.sp
            while (currentSize > 4.sp) {
                val result = textMeasurer.measure(
                    text = displayTitle,
                    style = style.copy(
                        fontSize = currentSize,
                        letterSpacing = if (currentSize < 11.sp) (-0.5).sp else 0.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                if (result.size.width <= with(density) { maxWidth.toPx() }) {
                    break
                }
                currentSize = (currentSize.value - 0.5f).sp
            }
            currentSize
        }

        Text(
            text = displayTitle,
            style = style.copy(
                fontSize = fontSize,
                letterSpacing = if (fontSize < 11.sp) (-0.5).sp else 0.sp
            ),
            color = Color.White,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: FilterStatus,
    onFilterChange: (FilterStatus) -> Unit,
    sortMode: SortMode,
    onSortChange: (SortMode) -> Unit,
    filterCounts: Map<FilterStatus, Int>,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onNavigationClick: () -> Unit,
    isRefreshing: Boolean,
    isInstalling: Boolean,
    permissionsMissing: Boolean
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF121212),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                ResponsiveTitle(
                    text = "VRHUB",
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)", color = Color.White) },
                            onClick = { onSortChange(SortMode.NAME_ASC); showSortMenu = false },
                            leadingIcon = { if (sortMode == SortMode.NAME_ASC) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Last Updated", color = Color.White) },
                            onClick = { onSortChange(SortMode.LAST_UPDATED); showSortMenu = false },
                            leadingIcon = { if (sortMode == SortMode.LAST_UPDATED) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Size", color = Color.White) },
                            onClick = { onSortChange(SortMode.SIZE); showSortMenu = false },
                            leadingIcon = { if (sortMode == SortMode.SIZE) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Popularity", color = Color.White) },
                            onClick = { onSortChange(SortMode.POPULARITY); showSortMenu = false },
                            leadingIcon = { if (sortMode == SortMode.POPULARITY) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                    }
                }

                IconButton(onClick = onRefreshClick, enabled = !isInstalling) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isRefreshing) MaterialTheme.colorScheme.secondary else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onSettingsClick) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        if (permissionsMissing) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color(0xFFe74c3c), CircleShape)
                                    .border(1.dp, Color.Black, CircleShape)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search VR games...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == FilterStatus.ALL,
                        onClick = { onFilterChange(FilterStatus.ALL) },
                        label = { Text("All (${filterCounts[FilterStatus.ALL] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = Color.Black,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == FilterStatus.NEW,
                        onClick = { onFilterChange(FilterStatus.NEW) },
                        label = { Text("New (${filterCounts[FilterStatus.NEW] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFe74c3c),
                            selectedLabelColor = Color.White,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null,
                        leadingIcon = {
                             Icon(
                                Icons.Default.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedFilter == FilterStatus.NEW) Color.White else Color(0xFFe74c3c)
                            )
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == FilterStatus.FAVORITES,
                        onClick = { onFilterChange(FilterStatus.FAVORITES) },
                        label = { Text("Favorites (${filterCounts[FilterStatus.FAVORITES] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFf1c40f),
                            selectedLabelColor = Color.Black,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null,
                        leadingIcon = {
                             Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedFilter == FilterStatus.FAVORITES) Color.Black else Color(0xFFf1c40f)
                            )
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == FilterStatus.INSTALLED,
                        onClick = { onFilterChange(FilterStatus.INSTALLED) },
                        label = { Text("Installed (${filterCounts[FilterStatus.INSTALLED] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF3498db),
                            selectedLabelColor = Color.White,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == FilterStatus.DOWNLOADED,
                        onClick = { onFilterChange(FilterStatus.DOWNLOADED) },
                        label = { Text("Downloaded (${filterCounts[FilterStatus.DOWNLOADED] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2ecc71),
                            selectedLabelColor = Color.White,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == FilterStatus.LOCAL_INSTALLS,
                        onClick = { onFilterChange(FilterStatus.LOCAL_INSTALLS) },
                        label = { Text("${stringResource(R.string.filter_local_installs)} (${filterCounts[FilterStatus.LOCAL_INSTALLS] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = Color.Black,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null,
                        leadingIcon = {
                             Icon(
                                Icons.Default.DownloadDone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedFilter == FilterStatus.LOCAL_INSTALLS) Color.Black else MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == FilterStatus.UPDATE_AVAILABLE,
                        onClick = { onFilterChange(FilterStatus.UPDATE_AVAILABLE) },
                        label = { Text("Updates (${filterCounts[FilterStatus.UPDATE_AVAILABLE] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFf1c40f),
                            selectedLabelColor = Color.Black,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null
                    )
                }
            }
        }
    }
}

@Composable
fun SyncingOverlay(progress: Float? = null, updateCount: Int = 0) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (progress == -1f) Color(0xFFB00020) else Color(0xFF1A1A1A))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.catalog_syncing_title),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (progress != null && progress >= 0) {
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else if (progress != -1f) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val text = when {
                        progress == -1f -> stringResource(R.string.catalog_sync_failed)
                        progress != null && progress >= 0 -> {
                            if (updateCount > 0) {
                                stringResource(R.string.catalog_syncing_with_count, updateCount, (progress * 100).toInt())
                            } else {
                                stringResource(R.string.catalog_syncing_progress, (progress * 100).toInt())
                            }
                        }
                        else -> {
                            if (updateCount > 0) {
                                stringResource(R.string.catalog_syncing_with_count_simple, updateCount)
                            } else {
                                stringResource(R.string.catalog_syncing)
                            }
                        }
                    }
                    Text(text, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.secondary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFCF6679), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Try Again", color = Color.White)
        }
    }
}

@Composable
fun InstallationOverlay(
    activeTask: InstallTaskState,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onBackground: () -> Unit
) {
    val isAppUpdate = activeTask.releaseName == "update"
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val progress = if (activeTask.progress >= 0f) activeTask.progress else null

            Text(
                text = activeTask.gameName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            if (activeTask.isLocalInstall) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "FAST TRACK",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (progress != null) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(200.dp),
                        strokeWidth = 8.dp,
                        color = when (activeTask.status) {
                            InstallTaskStatus.PAUSED -> Color.Gray
                            InstallTaskStatus.BLOCKED_BY_PERMISSIONS -> MaterialTheme.colorScheme.error
                            InstallTaskStatus.SHELVED, InstallTaskStatus.PENDING_INSTALL -> Color(0xFF2ecc71)
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        if (activeTask.currentSize != null) {
                            Text(
                                text = "${activeTask.currentSize} / ${activeTask.totalSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(100.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 6.dp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Story 1.7 Code Review Fix: User-friendly status messages instead of raw enum names
            val statusMessage = activeTask.message ?: when (activeTask.status) {
                InstallTaskStatus.PENDING_INSTALL -> stringResource(R.string.status_pending_install)
                InstallTaskStatus.QUEUED -> stringResource(R.string.status_queued)
                InstallTaskStatus.DOWNLOADING -> stringResource(R.string.status_downloading)
                InstallTaskStatus.EXTRACTING -> stringResource(R.string.status_extracting)
                InstallTaskStatus.INSTALLING -> stringResource(R.string.status_installing)
                InstallTaskStatus.PAUSED -> stringResource(R.string.status_paused)
                InstallTaskStatus.BLOCKED_BY_PERMISSIONS -> stringResource(R.string.status_blocked_permissions)
                InstallTaskStatus.COMPLETED -> stringResource(R.string.status_completed)
                InstallTaskStatus.FAILED -> stringResource(R.string.status_failed)
                InstallTaskStatus.LOCAL_VERIFYING -> stringResource(R.string.status_local_verifying)
                InstallTaskStatus.SHELVED -> stringResource(R.string.status_shelved)
            }
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            if (activeTask.error != null) {
                Text(
                    text = activeTask.error,
                    color = Color(0xFFCF6679),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            if (!isAppUpdate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    if (activeTask.status == InstallTaskStatus.PAUSED ||
                        activeTask.status == InstallTaskStatus.FAILED ||
                        activeTask.status == InstallTaskStatus.BLOCKED_BY_PERMISSIONS) {
                        Button(
                            onClick = onResume,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ecc71)),
                            modifier = Modifier.height(56.dp).weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RESUME", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (activeTask.status.isProcessing()) {
                        OutlinedButton(
                            onClick = { onPause() },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp).weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PAUSE", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { onCancel() },
                        border = BorderStroke(1.dp, Color(0xFFCF6679).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp).weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFCF6679))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CANCEL", color = Color(0xFFCF6679), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onBackground,
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text("Run in background", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            } else {
                Text(
                    text = "Application update is mandatory. Please wait.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BottomQueueBar(queue: List<InstallTaskState>, onClick: () -> Unit) {
    val activeTask = queue.find { it.status.isProcessing() }
        ?: queue.firstOrNull() ?: return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color(0xFF1A1A1A),
        tonalElevation = 8.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = activeTask.progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = when (activeTask.status) {
                    InstallTaskStatus.PAUSED -> Color.Gray
                    InstallTaskStatus.BLOCKED_BY_PERMISSIONS -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                },
                trackColor = Color.Transparent
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (activeTask.status) {
                        InstallTaskStatus.PAUSED -> Icons.Default.Pause
                        InstallTaskStatus.BLOCKED_BY_PERMISSIONS -> Icons.Default.Lock
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = activeTask.gameName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(activeTask.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                if (queue.size > 1) {
                    Text(
                        text = " (+${queue.size - 1})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    keepApks: Boolean,
    onToggleKeepApks: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    onClearCache: () -> Unit,
    missingPermissions: List<RequiredPermission>,
    onPermissionClick: (RequiredPermission) -> Unit,
    onServerConfigClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Application",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("Server Configuration") },
                    supportingContent = { Text("Change server URL or key-value pairs") },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onServerConfigClick() }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("Keep APKs after install") },
                    supportingContent = { Text("Saved to Download/VRHub") },
                    trailingContent = { Switch(checked = keepApks, onCheckedChange = { onToggleKeepApks() }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Clear Download Cache") },
                    supportingContent = { Text("Delete temporary installation files") },
                    trailingContent = {
                        IconButton(onClick = onClearCache) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear cache", tint = Color(0xFFCF6679))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onClearCache() }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                RequiredPermission.entries.forEach { permission ->
                    val isMissing = missingPermissions.contains(permission)
                    val displayName = when (permission) {
                        RequiredPermission.INSTALL_UNKNOWN_APPS -> "Install Unknown Apps"
                        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> "All Files Access"
                        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> "Battery Optimization"
                    }

                    ListItem(
                        headlineContent = { Text(displayName) },
                        supportingContent = { Text(if (isMissing) "Action Required" else "Granted") },
                        trailingContent = {
                            if (isMissing) {
                                Button(
                                    onClick = { onPermissionClick(permission) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe74c3c))
                                ) {
                                    Text("GRANT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Granted",
                                    tint = Color(0xFF2ecc71),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("Export Diagnostics") },
                    supportingContent = { Text("Copy application logs to clipboard") },
                    trailingContent = {
                        IconButton(onClick = onExportDiagnostics) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs", tint = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onExportDiagnostics() }
                )

                ListItem(
                    headlineContent = { Text("Save Logs to Storage") },
                    supportingContent = { Text("Export txt file to Download/VRHub") },
                    trailingContent = {
                        IconButton(onClick = onSaveDiagnostics) {
                            Icon(Icons.Default.Save, contentDescription = "Save logs", tint = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSaveDiagnostics() }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

                Text(
                    text = "About",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("VRHub") },
                    supportingContent = { Text(stringResource(R.string.about_branding)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun AlphabetIndexer(
    alphabetInfo: Pair<List<Char>, Map<Char, Int>>,
    onLetterClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .width(40.dp)
            .background(Color.Black.copy(alpha = 0.5f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(alphabetInfo.first) { char ->
            key(char) {
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                val scale by animateFloatAsState(if (isHovered) 1.8f else 1f, label = "magnify")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            alphabetInfo.second[char]?.let { index ->
                                onLetterClick(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHovered) MaterialTheme.colorScheme.secondary else Color.Gray,
                        modifier = Modifier.scale(scale)
                    )
                }
            }
        }
    }
}

@Composable
fun QueueManagerOverlay(
    queue: List<InstallTaskState>,
    viewedReleaseName: String?,
    onTaskClick: (String) -> Unit,
    onCancel: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onPromote: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INSTALLATION QUEUE (${queue.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (queue.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No downloads in queue",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Browse the catalog and tap Install to add games",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(queue.size, key = { queue[it].releaseName }) { index ->
                        val task = queue[index]
                        val position = index + 1
                        val isViewed = task.releaseName == viewedReleaseName
                        val isProcessing = task.status.isProcessing()
                        val isPaused = task.status == InstallTaskStatus.PAUSED || task.status == InstallTaskStatus.BLOCKED_BY_PERMISSIONS
                        val isFailed = task.status == InstallTaskStatus.FAILED

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTaskClick(task.releaseName) },
                            color = if (isViewed) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(16.dp),
                            border = if (isViewed) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Position indicator badge
                                    Surface(
                                        color = if (isProcessing) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                "#$position",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isProcessing) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = task.gameName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (task.isLocalInstall) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "FAST TRACK",
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 8.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = if (isFailed && task.error != null) task.error else (task.message ?: task.status.name),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isFailed) Color(0xFFCF6679) else Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Action button (Prioritize/Resume/Pause)
                                        if (isProcessing) {
                                            IconButton(onClick = { onPause(task.releaseName) }) {
                                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White)
                                            }
                                        } else {
                                            // Task is not processing: could be QUEUED, PAUSED, FAILED, SHELVED, or PENDING_INSTALL
                                            val actionIcon = when {
                                                task.status == InstallTaskStatus.FAILED -> Icons.Default.Refresh
                                                task.status == InstallTaskStatus.SHELVED || task.status == InstallTaskStatus.PENDING_INSTALL -> Icons.Default.DownloadDone
                                                task.status == InstallTaskStatus.PAUSED -> Icons.Default.PlayArrow
                                                else -> Icons.Default.VerticalAlignTop // QUEUED
                                            }
                                            
                                            val actionTint = when {
                                                task.status == InstallTaskStatus.FAILED -> Color(0xFFF39C12)
                                                task.status == InstallTaskStatus.SHELVED || task.status == InstallTaskStatus.PENDING_INSTALL -> Color(0xFF2ecc71)
                                                task.status == InstallTaskStatus.PAUSED -> Color(0xFF2ecc71)
                                                else -> MaterialTheme.colorScheme.secondary // QUEUED
                                            }

                                            IconButton(onClick = { 
                                                if (task.status == InstallTaskStatus.QUEUED) onPromote(task.releaseName) 
                                                else onResume(task.releaseName) 
                                            }) {
                                                Icon(
                                                    imageVector = actionIcon, 
                                                    contentDescription = "Start", 
                                                    tint = actionTint
                                                )
                                            }
                                        }

                                        // Cancel button
                                        IconButton(onClick = { onCancel(task.releaseName) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = Color(0xFFCF6679))
                                        }
                                    }
                                }

                                if (isProcessing || (task.progress > 0 && task.status != InstallTaskStatus.COMPLETED)) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = task.progress.coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = if (isPaused) Color.Gray else MaterialTheme.colorScheme.secondary,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                    if (task.currentSize != null) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            if (task.status == InstallTaskStatus.SHELVED || task.status == InstallTaskStatus.PENDING_INSTALL) {
                                                Text(
                                                    text = "100%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF2ecc71),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "${task.currentSize} / ${task.totalSize}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("BACK TO CATALOG", fontWeight = FontWeight.Black)
            }
        }
    }
}

fun parseMarkdown(text: String) = buildAnnotatedString {
    val cleanText = text.replace(Regex("""^[\uFEFF\u200B\u200C\u200D\u200E\u200F]+"""), "").trim()
    val lines = cleanText.split(Regex("\\r?\\n"))

    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            append("\n")
            return@forEach
        }

        val headerMatch = Regex("""^#+\s*(.*)$""").find(trimmed)
        if (headerMatch != null) {
            val title = headerMatch.groupValues[1].replace("*", "").trim()
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)) {
                append(title)
            }
            append("\n\n")
            return@forEach
        }

        var content = trimmed
        if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("·") || trimmed.startsWith("•")) {
            append("  • ")
            content = trimmed.replace(Regex("^[-*·•]\\s*"), "")
        }

        val parts = content.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
        append("\n")
    }
}
