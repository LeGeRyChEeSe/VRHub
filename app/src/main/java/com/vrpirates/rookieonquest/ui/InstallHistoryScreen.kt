package com.vrpirates.rookieonquest.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vrpirates.rookieonquest.data.DateTimeConstants
import com.vrpirates.rookieonquest.data.InstallHistoryEntity
import com.vrpirates.rookieonquest.data.InstallStatus
import com.vrpirates.rookieonquest.data.InstallUtils
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val history by viewModel.installHistory.collectAsState()
    val query by viewModel.historyQuery.collectAsState()
    val statusFilter by viewModel.historyStatusFilter.collectAsState()
    val sortMode by viewModel.historySortMode.collectAsState()
    val dateFilter by viewModel.historyDateFilter.collectAsState()
    val stats by viewModel.historyStats.collectAsState()
    val canLoadMore by viewModel.canLoadMoreHistory.collectAsState()
    
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    // Handle events from ViewModel (like export messages)
    LaunchedEffect(Unit) {
        try {
            viewModel.events.collect { event ->
                if (event is MainEvent.ShowMessage) {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        } catch (e: Exception) {
            Log.e("InstallHistoryScreen", "Error collecting events in history screen", e)
        }
    }

    // Trigger load more when reaching the end
    LaunchedEffect(listState, canLoadMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex: Int? ->
                if (canLoadMore && lastIndex != null && lastIndex >= history.size - 5) {
                    viewModel.loadMoreHistory()
                }
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Installation History", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (stats != null) {
                        IconButton(onClick = { showStats = !showStats }) {
                            Icon(if (showStats) Icons.Default.BarChart else Icons.Default.InsertChartOutlined, contentDescription = "Toggle Stats", tint = if (showStats) MaterialTheme.colorScheme.secondary else Color.White)
                        }
                    }
                    if (history.isNotEmpty() || query.isNotEmpty() || statusFilter != null || dateFilter != HistoryDateFilter.ALL) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date (Newest)", color = if (sortMode == HistorySortMode.DATE_DESC) MaterialTheme.colorScheme.secondary else Color.White) },
                                    onClick = { viewModel.setHistorySortMode(HistorySortMode.DATE_DESC); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.History, null, tint = if (sortMode == HistorySortMode.DATE_DESC) MaterialTheme.colorScheme.secondary else Color.Gray) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest)", color = if (sortMode == HistorySortMode.DATE_ASC) MaterialTheme.colorScheme.secondary else Color.White) },
                                    onClick = { viewModel.setHistorySortMode(HistorySortMode.DATE_ASC); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Schedule, null, tint = if (sortMode == HistorySortMode.DATE_ASC) MaterialTheme.colorScheme.secondary else Color.Gray) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)", color = if (sortMode == HistorySortMode.NAME_ASC) MaterialTheme.colorScheme.secondary else Color.White) },
                                    onClick = { viewModel.setHistorySortMode(HistorySortMode.NAME_ASC); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, null, tint = if (sortMode == HistorySortMode.NAME_ASC) MaterialTheme.colorScheme.secondary else Color.Gray) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size", color = if (sortMode == HistorySortMode.SIZE_DESC) MaterialTheme.colorScheme.secondary else Color.White) },
                                    onClick = { viewModel.setHistorySortMode(HistorySortMode.SIZE_DESC); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.SdStorage, null, tint = if (sortMode == HistorySortMode.SIZE_DESC) MaterialTheme.colorScheme.secondary else Color.Gray) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duration", color = if (sortMode == HistorySortMode.DURATION_DESC) MaterialTheme.colorScheme.secondary else Color.White) },
                                    onClick = { viewModel.setHistorySortMode(HistorySortMode.DURATION_DESC); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Timer, null, tint = if (sortMode == HistorySortMode.DURATION_DESC) MaterialTheme.colorScheme.secondary else Color.Gray) }
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Export History", tint = MaterialTheme.colorScheme.secondary)
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export as TXT", color = Color.White) },
                                    onClick = { viewModel.exportHistory("txt"); showExportMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Description, null, tint = Color.Gray) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as JSON", color = Color.White) },
                                    onClick = { viewModel.exportHistory("json"); showExportMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Code, null, tint = Color.Gray) }
                                )
                            }
                        }
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color(0xFFCF6679))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Stats Section
            if (showStats && stats != null) {
                HistoryStatsView(stats!!)
            }

            // Search and Filter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setHistoryQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search history...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setHistoryQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { viewModel.setHistoryStatusFilter(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = Color.Black,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null
                    )
                    FilterChip(
                        selected = statusFilter == InstallStatus.COMPLETED,
                        onClick = { viewModel.setHistoryStatusFilter(InstallStatus.COMPLETED) },
                        label = { Text("Success") },
                        leadingIcon = { if (statusFilter == InstallStatus.COMPLETED) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2ecc71),
                            selectedLabelColor = Color.White,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null
                    )
                    FilterChip(
                        selected = statusFilter == InstallStatus.FAILED,
                        onClick = { viewModel.setHistoryStatusFilter(InstallStatus.FAILED) },
                        label = { Text("Failed") },
                        leadingIcon = { if (statusFilter == InstallStatus.FAILED) Icon(Icons.Default.Error, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFe74c3c),
                            selectedLabelColor = Color.White,
                            labelColor = Color.Gray,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = null
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateFilterChip(
                        selected = dateFilter == HistoryDateFilter.ALL,
                        onClick = { viewModel.setHistoryDateFilter(HistoryDateFilter.ALL) },
                        label = "All Time"
                    )
                    DateFilterChip(
                        selected = dateFilter == HistoryDateFilter.LAST_7_DAYS,
                        onClick = { viewModel.setHistoryDateFilter(HistoryDateFilter.LAST_7_DAYS) },
                        label = "7 Days"
                    )
                    DateFilterChip(
                        selected = dateFilter == HistoryDateFilter.LAST_30_DAYS,
                        onClick = { viewModel.setHistoryDateFilter(HistoryDateFilter.LAST_30_DAYS) },
                        label = "30 Days"
                    )
                    DateFilterChip(
                        selected = dateFilter == HistoryDateFilter.LAST_3_MONTHS,
                        onClick = { viewModel.setHistoryDateFilter(HistoryDateFilter.LAST_3_MONTHS) },
                        label = "3 Months"
                    )
                }
            }

            if (history.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History, 
                            contentDescription = null, 
                            modifier = Modifier.size(80.dp), 
                            tint = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (query.isEmpty() && statusFilter == null) "No installation history yet" else "No matching records found",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history, key = { it.id }) { entry ->
                        HistoryItem(
                            entry = entry,
                            onDelete = { viewModel.deleteHistoryEntry(entry.id) },
                            onReinstall = { 
                                viewModel.installGame(entry.releaseName)
                                onBack() // Go back to main screen to see progress
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear History?") },
            text = { Text("This will permanently delete all installation history records.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679))
                ) {
                    Text("CLEAR ALL", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun HistoryItem(
    entry: InstallHistoryEntity,
    onDelete: () -> Unit,
    onReinstall: () -> Unit
) {
    val dateStr = remember(entry.installedAt) { 
        DateTimeConstants.HISTORY_DATE_FORMATTER.format(Instant.ofEpochMilli(entry.installedAt)) 
    }
    
    val durationStr = remember(entry.downloadDurationMs) {
        val seconds = entry.downloadDurationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusIcon = if (entry.status == InstallStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Error
                val statusColor = if (entry.status == InstallStatus.COMPLETED) Color(0xFF2ecc71) else Color(0xFFe74c3c)
                
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.gameName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconButton(onClick = onReinstall) {
                    Icon(Icons.Default.Refresh, contentDescription = "Re-install", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                }
                
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HistoryDetail(Icons.Default.CalendarToday, dateStr)
                HistoryDetail(Icons.Default.Timer, durationStr)
                HistoryDetail(Icons.Default.SdStorage, InstallUtils.formatBytes(entry.fileSizeBytes))
            }
            
            if (!entry.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color.Red.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFCF6679), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = entry.errorMessage,
                            color = Color(0xFFCF6679),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDetail(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = Color.Gray, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            selectedLabelColor = Color.White,
            labelColor = Color.Gray,
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = null
    )
}

@Composable
fun HistoryStatsView(stats: com.vrpirates.rookieonquest.ui.HistoryStats) {
    Surface(
        color = Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Success Rate",
                    value = "${(stats.successRate * 100).toInt()}%",
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFF2ecc71)
                )
                StatItem(
                    label = "Avg. Duration",
                    value = "${stats.averageDurationMs / 1000}s",
                    icon = Icons.Default.Timer,
                    color = MaterialTheme.colorScheme.secondary
                )
                StatItem(
                    label = "Total Size",
                    value = InstallUtils.formatBytes(stats.totalDownloadedBytes),
                    icon = Icons.Default.SdStorage,
                    color = Color.White
                )
            }
            
            if (stats.topGames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Most Installed Games:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stats.topGames.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (stats.errorSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Top Errors:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                stats.errorSummary.forEach { (error, count) ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFCF6679), RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (error.length > 40) error.take(40) + "..." else error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "x$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCF6679),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}