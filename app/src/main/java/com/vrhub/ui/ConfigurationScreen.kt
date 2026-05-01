package com.vrhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrhub.BuildConfig
import com.vrhub.R

/**
 * Legal disclaimer text shown on the configuration screen.
 */
private val DISCLAIMER_TEXT = """
    VRHub is a neutral tool for managing VR game installations. The app does not provide, host, or endorse any game content. You are solely responsible for the servers you configure and the content you access through them.
""".trimIndent()

/**
 * Configuration screen composable.
 * Shown on first launch when no server configuration exists,
 * or when user wants to modify existing configuration from Settings.
 *
 * @param viewModel The configuration view model
 * @param onConfigSaved Callback when configuration is successfully saved
 * @param onCancel Callback when user presses back to cancel editing
 * @param isEditing Whether this is editing existing configuration (vs first-time setup)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    viewModel: ConfigurationViewModel = viewModel(),
    onConfigSaved: () -> Unit = {},
    onCancel: () -> Unit = {},
    isEditing: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load existing config when editing - only once when first opened
    LaunchedEffect(isEditing) {
        if (isEditing && !uiState.hasLoadedConfig) {
            viewModel.loadForEditing()
        }
    }

    // Navigate away when config is saved
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onConfigSaved()
        }
    }

    // Handle back button - only for first launch (not edit mode since we have TopAppBar back button)
    if (!isEditing) {
        BackHandler {
            onCancel()
        }
    }

    // VRHub Branding Header
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VRHub",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // DEBUG badge for dev flavor
                    if (BuildConfig.APPLICATION_ID.endsWith(".debug")) {
                        Text(
                            text = " DEBUG",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.branding_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top bar with back button for edit mode
        if (isEditing) {
            TopAppBar(
                title = { Text("Server Configuration") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Legal Disclaimer Card
        DisclaimerCard()

        Spacer(modifier = Modifier.height(24.dp))

        // Input Mode Tabs
        TabRow(
            selectedTabIndex = if (uiState.inputMode == InputMode.JSON_URL) 0 else 1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = uiState.inputMode == InputMode.JSON_URL,
                onClick = { viewModel.setInputMode(InputMode.JSON_URL) },
                text = { Text("JSON URL") }
            )
            Tab(
                selected = uiState.inputMode == InputMode.MANUAL_KV,
                onClick = { viewModel.setInputMode(InputMode.MANUAL_KV) },
                text = { Text("Manual Entry") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input fields based on mode
        when (uiState.inputMode) {
            InputMode.JSON_URL -> JsonUrlInput(
                url = uiState.jsonUrl,
                password = uiState.jsonPassword,
                onUrlChange = { viewModel.setJsonUrl(it) },
                onPasswordChange = { viewModel.setJsonPassword(it) }
            )
            InputMode.MANUAL_KV -> ManualKvInput(
                kvPairs = uiState.kvPairs,
                onAddPair = { viewModel.addKeyValuePair() },
                onUpdatePair = { index, key, value -> viewModel.updateKeyValuePair(index, key, value) },
                onRemovePair = { viewModel.removeKeyValuePair(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error/Success messages
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        uiState.successMessage?.let { success ->
            Text(
                text = success,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.testConfiguration() },
                modifier = Modifier.weight(1f),
                enabled = when (uiState.inputMode) {
                    InputMode.JSON_URL -> uiState.jsonUrl.isNotBlank() && !uiState.isTesting
                    InputMode.MANUAL_KV -> {
                        val hasBaseUri = uiState.kvPairs.any { it.key.equals("baseUri", ignoreCase = true) && it.value.isNotBlank() }
                        val hasPassword = uiState.kvPairs.any { it.key.equals("password", ignoreCase = true) && it.value.isNotBlank() }
                        hasBaseUri && hasPassword && !uiState.isTesting
                    }
                }
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("TEST")
                }
            }

            Button(
                onClick = {
                    val config = uiState.testedConfig ?: return@Button
                    viewModel.saveConfiguration(config)
                },
                modifier = Modifier.weight(1f),
                enabled = uiState.isSaveEnabled && !uiState.isLoading && !uiState.isSaved
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("SAVE")
                }
            }

            // SKIP button for debug builds only
            if (BuildConfig.APPLICATION_ID.endsWith(".debug")) {
                OutlinedButton(
                    onClick = {
                        viewModel.skipConfiguration()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading
                ) {
                    Text("SKIP")
                }
            }
        }
    }
}

/**
 * Card displaying the legal disclaimer.
 */
@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column {
                Text(
                    text = "Disclaimer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = DISCLAIMER_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * JSON URL input section.
 */
@Composable
private fun JsonUrlInput(
    url: String,
    password: String,
    onUrlChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("JSON Configuration URL") },
            placeholder = { Text("https://example.com/config.json") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            maxLines = 1
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password (optional)") },
            placeholder = { Text("Override server password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            maxLines = 1
        )
    }
}

/**
 * Manual key-value pair input section.
 */
@Composable
private fun ManualKvInput(
    kvPairs: List<KeyValuePair>,
    onAddPair: () -> Unit,
    onUpdatePair: (index: Int, key: String, value: String) -> Unit,
    onRemovePair: (index: Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        kvPairs.forEachIndexed { index, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = pair.key,
                    onValueChange = { onUpdatePair(index, it, pair.value) },
                    label = { Text("Key") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    maxLines = 1
                )
                OutlinedTextField(
                    value = pair.value,
                    onValueChange = { onUpdatePair(index, pair.key, it) },
                    label = { Text("Value") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    maxLines = 1
                )
                if (kvPairs.size > 1) {
                    IconButton(onClick = { onRemovePair(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove"
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onAddPair,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Add Key")
        }
    }
}
