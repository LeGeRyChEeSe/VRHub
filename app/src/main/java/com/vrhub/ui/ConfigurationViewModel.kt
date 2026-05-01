package com.vrhub.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrhub.data.InvalidUrlException
import com.vrhub.data.JsonParseException
import com.vrhub.data.MissingKeysException
import com.vrhub.data.NetworkException
import com.vrhub.data.ServerConfig
import com.vrhub.data.ServerConfigRepository
import com.vrhub.data.TestResult
import com.vrhub.data.testConnection
import com.vrhub.data.testPassword
import com.vrhub.logic.CatalogUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * UI state for the ConfigurationScreen.
 */
data class ConfigurationUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val inputMode: InputMode = InputMode.JSON_URL,
    val jsonUrl: String = "",
    val jsonPassword: String = "",
    val kvPairs: List<KeyValuePair> = listOf(KeyValuePair("", "")),
    val isTesting: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val testedConfig: ServerConfig? = null,
    val isEditing: Boolean = false,
    val hasLoadedConfig: Boolean = false
)

/**
 * Input mode for configuration.
 */
enum class InputMode {
    JSON_URL,
    MANUAL_KV
}

/**
 * A key-value pair for manual configuration entry.
 */
data class KeyValuePair(
    val key: String,
    val value: String
)

/**
 * ViewModel for the server configuration screen.
 * Manages configuration input, validation, testing, and saving.
 */
class ConfigurationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServerConfigRepository(application)

    private val _uiState = MutableStateFlow(ConfigurationUiState())
    val uiState: StateFlow<ConfigurationUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private var saveJob: Job? = null

    /**
     * Check if a valid configuration already exists.
     */
    val hasValidConfig: Boolean
        get() = repository.hasValidConfig()

    /**
     * Load existing configuration for editing.
     * Sets up the UI state with current config values.
     */
    fun loadForEditing() {
        // Only load if we haven't already loaded for this editing session
        if (_uiState.value.hasLoadedConfig) {
            return
        }
        val existingConfig = repository.loadConfig()
        val savedInputMode = repository.loadInputMode()
        if (existingConfig != null) {
            val extraKeys = existingConfig.extraKeys

            if (existingConfig.baseUri.isNotBlank()) {
                // Use saved input mode if available, otherwise detect from config structure
                val inputMode = when {
                    savedInputMode == "MANUAL_KV" -> InputMode.MANUAL_KV
                    savedInputMode == "JSON_URL" -> InputMode.JSON_URL
                    else -> {
                        // Fallback to detection logic for configs saved before inputMode was tracked
                        val looksLikeJsonUrl = existingConfig.baseUri.contains("/") && extraKeys.isEmpty()
                        if (looksLikeJsonUrl) InputMode.JSON_URL else InputMode.MANUAL_KV
                    }
                }
                if (inputMode == InputMode.JSON_URL) {
                    _uiState.value = _uiState.value.copy(
                        inputMode = InputMode.JSON_URL,
                        jsonUrl = existingConfig.baseUri,
                        isEditing = true,
                        hasLoadedConfig = true
                    )
                } else {
                    // Manual KV mode - reconstruct key-value pairs
                    val pairs = mutableListOf<KeyValuePair>()
                    pairs.add(KeyValuePair("baseUri", existingConfig.baseUri))
                    pairs.add(KeyValuePair("password", existingConfig.password))
                    extraKeys.forEach { (key, value) ->
                        pairs.add(KeyValuePair(key, value))
                    }
                    Log.d("ConfigVM", "loadForEditing: reconstructed pairs = $pairs")
                    _uiState.value = _uiState.value.copy(
                        inputMode = InputMode.MANUAL_KV,
                        kvPairs = if (pairs.isNotEmpty()) pairs else listOf(KeyValuePair("", "")),
                        isEditing = true,
                        hasLoadedConfig = true
                    )
                }
            }
        } else {
            Log.d("ConfigVM", "loadForEditing: no existing config found")
        }
    }

    /**
     * Reset the load flag so config can be reloaded on next open.
     */
    fun resetLoadState() {
        _uiState.value = _uiState.value.copy(
            hasLoadedConfig = false,
            isSaved = false,
            isLoading = false,
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Set the input mode (JSON URL or Manual KV).
     */
    fun setInputMode(mode: InputMode) {
        _uiState.value = _uiState.value.copy(
            inputMode = mode,
            isSaveEnabled = false,
            errorMessage = null,
            successMessage = null,
            testedConfig = null
        )
    }

    /**
     * Update the JSON URL input.
     */
    fun setJsonUrl(url: String) {
        val trimmed = url.trim()
        val schemeIndex = trimmed.indexOf("://")
        val isValidFormat = trimmed.isNotBlank() &&
            (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
            !trimmed.contains(" ") &&
            schemeIndex > 0 && trimmed.length > schemeIndex + 3
        _uiState.value = _uiState.value.copy(
            jsonUrl = trimmed,
            isSaveEnabled = isValidFormat,
            isSaved = false,
            errorMessage = null,
            successMessage = null,
            testedConfig = null
        )
    }

    /**
     * Update the JSON URL optional password override.
     */
    fun setJsonPassword(password: String) {
        _uiState.value = _uiState.value.copy(
            jsonPassword = password,
            isSaved = false,
            testedConfig = null
        )
    }

    /**
     * Add a new key-value pair.
     */
    fun addKeyValuePair() {
        val currentPairs = _uiState.value.kvPairs.toMutableList()
        currentPairs.add(KeyValuePair("", ""))
        _uiState.value = _uiState.value.copy(kvPairs = currentPairs)
    }

    /**
     * Update a key-value pair at the given index.
     */
    fun updateKeyValuePair(index: Int, key: String, value: String) {
        val currentPairs = _uiState.value.kvPairs.toMutableList()
        if (index in currentPairs.indices) {
            currentPairs[index] = KeyValuePair(key, value)
            _uiState.value = _uiState.value.copy(
                kvPairs = currentPairs,
                isSaveEnabled = false,
                isSaved = false,
                errorMessage = null,
                successMessage = null,
                testedConfig = null
            )
        }
    }

    /**
     * Remove a key-value pair at the given index.
     */
    fun removeKeyValuePair(index: Int) {
        val currentPairs = _uiState.value.kvPairs.toMutableList()
        if (index in currentPairs.indices && currentPairs.size > 1) {
            currentPairs.removeAt(index)
            _uiState.value = _uiState.value.copy(
                kvPairs = currentPairs,
                isSaveEnabled = false,
                isSaved = false,
                errorMessage = null,
                successMessage = null,
                testedConfig = null
            )
        }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Test the current configuration.
     * - JSON_URL mode: fetches JSON from URL and validates structure, then tests connection
     * - MANUAL_KV mode: validates locally that baseUri and password keys exist, then tests connection
     *
     * AC #1: Loading indicator shown during test
     * AC #2: "Configuration valid" shown on success, SAVE enabled
     * AC #3: "Connection failed: [error]" on connection error, SAVE disabled
     * AC #4: "Connection timeout" on timeout (>10s), SAVE disabled
     */
    fun testConfiguration() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            // Capture state after cancel — must be inside the launched block
            val currentState = _uiState.value
            val inputMode = currentState.inputMode
            val jsonUrl = currentState.jsonUrl
            val jsonPassword = currentState.jsonPassword
            val kvPairs = currentState.kvPairs

            currentCoroutineContext().ensureActive()
            _uiState.value = _uiState.value.copy(
                isTesting = true,
                errorMessage = null,
                successMessage = null,
                isSaveEnabled = false,
                testedConfig = null
            )

            currentCoroutineContext().ensureActive()

            // Step 1: Validate configuration (local validation)
            val validationResult = when (inputMode) {
                InputMode.JSON_URL -> repository.fetchJsonConfig(jsonUrl, jsonPassword)
                InputMode.MANUAL_KV -> validateManualConfig(kvPairs)
            }

            currentCoroutineContext().ensureActive()

            // If validation failed, show error immediately
            validationResult.fold(
                onSuccess = { config ->
                    // Step 2: Test connection to server with 10-second timeout
                    val connectionResult = testConnection(config)
                    currentCoroutineContext().ensureActive()
                    when (connectionResult) {
                        is TestResult.Success -> {
                            // Step 3: Validate password by attempting to extract meta.7z
                            val passwordResult = testPassword(config, getApplication())
                            currentCoroutineContext().ensureActive()
                            when (passwordResult) {
                                is TestResult.Success -> {
                                    _uiState.value = _uiState.value.copy(
                                        isTesting = false,
                                        successMessage = "Configuration valid",
                                        isSaveEnabled = true,
                                        testedConfig = config
                                    )
                                }
                                is TestResult.PasswordValidationFailed -> {
                                    _uiState.value = _uiState.value.copy(
                                        isTesting = false,
                                        errorMessage = "Invalid password: ${passwordResult.message}",
                                        isSaveEnabled = false,
                                        testedConfig = null
                                    )
                                }
                                else -> {
                                    // Should not happen for password validation, but handle it
                                    _uiState.value = _uiState.value.copy(
                                        isTesting = false,
                                        errorMessage = "Password validation failed: unexpected result",
                                        isSaveEnabled = false,
                                        testedConfig = null
                                    )
                                }
                            }
                        }
                        is TestResult.ConnectionError -> {
                            _uiState.value = _uiState.value.copy(
                                isTesting = false,
                                errorMessage = "Connection failed: ${connectionResult.message}",
                                isSaveEnabled = false,
                                testedConfig = null
                            )
                        }
                        is TestResult.Timeout -> {
                            _uiState.value = _uiState.value.copy(
                                isTesting = false,
                                errorMessage = "Connection timeout",
                                isSaveEnabled = false,
                                testedConfig = null
                            )
                        }
                        is TestResult.InvalidConfig -> {
                            _uiState.value = _uiState.value.copy(
                                isTesting = false,
                                errorMessage = "Invalid configuration: ${connectionResult.message}",
                                isSaveEnabled = false,
                                testedConfig = null
                            )
                        }
                        is TestResult.PasswordValidationFailed -> {
                            _uiState.value = _uiState.value.copy(
                                isTesting = false,
                                errorMessage = "Invalid password: ${connectionResult.message}",
                                isSaveEnabled = false,
                                testedConfig = null
                            )
                        }
                    }
                },
                onFailure = { error ->
                    val errorMessage = when (error) {
                        is InvalidUrlException -> "Invalid URL format"
                        is NetworkException -> error.message ?: "Network error"
                        is JsonParseException -> "Server returned invalid JSON"
                        is MissingKeysException -> "Configuration missing required keys"
                        is IllegalArgumentException -> error.message?.replace("\n", " ") ?: "Invalid configuration"
                        else -> "Test failed: ${error.message?.replace("\n", " ") ?: "unknown error"}"
                    }
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        errorMessage = errorMessage,
                        isSaveEnabled = false,
                        testedConfig = null
                    )
                }
            )
        }
    }

    /**
     * Validate manual KV configuration locally.
     * Checks that baseUri and password keys exist and are valid.
     */
    private fun validateManualConfig(kvPairs: List<KeyValuePair>): Result<ServerConfig> {
        val baseUri = kvPairs.find { it.key.equals("baseUri", ignoreCase = true) }?.value ?: ""
        val password = kvPairs.find { it.key.equals("password", ignoreCase = true) }?.value ?: ""

        val trimmedUri = baseUri.trim()
        val trimmedPassword = password.trim()

        if (trimmedUri.isBlank()) {
            return Result.failure(MissingKeysException("baseUri is required"))
        }
        if (trimmedPassword.isBlank()) {
            return Result.failure(MissingKeysException("password is required"))
        }

        // Validate baseUri format
        if (!trimmedUri.startsWith("http://", ignoreCase = true) && !trimmedUri.startsWith("https://", ignoreCase = true)) {
            return Result.failure(InvalidUrlException("Invalid URL format"))
        }

        val uri = try {
            java.net.URI(trimmedUri)
        } catch (e: Exception) {
            return Result.failure(InvalidUrlException("Invalid URL format"))
        }
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https") {
            return Result.failure(InvalidUrlException("Invalid URL format"))
        }

        // Extract extra keys (everything except baseUri and password)
        val extraKeys = kvPairs
            .filter {
                !it.key.equals("baseUri", ignoreCase = true) &&
                !it.key.equals("password", ignoreCase = true) &&
                it.key.isNotBlank() &&
                it.value.isNotBlank()
            }
            .associate { it.key to it.value }

        return Result.success(ServerConfig(
            baseUri = trimmedUri,
            password = trimmedPassword,
            extraKeys = extraKeys
        ))
    }

    /**
     * Save the configuration.
     */
    fun saveConfiguration(config: ServerConfig) {
        saveJob?.cancel()
        val configToSave = config
        val currentInputMode = _uiState.value.inputMode
        saveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, isSaveEnabled = false)
            try {
                currentCoroutineContext().ensureActive()
                repository.saveConfig(configToSave, currentInputMode.name)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaved = true,
                    successMessage = "Configuration saved",
                    isSaveEnabled = false
                )
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to save configuration: ${e.message}",
                    isSaveEnabled = false
                )
            }
        }
    }

    /**
     * Skip configuration for debug builds.
     * Saves a dummy config to allow the app to proceed without a real server.
     */
    fun skipConfiguration() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val dummyConfig = ServerConfig(
                    baseUri = "https://debug.invalid",
                    password = "DEBUG_SKIP",
                    extraKeys = emptyMap()
                )
                repository.saveConfig(dummyConfig, "DEBUG_SKIP")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaved = true,
                    successMessage = "Configuration skipped (debug)",
                    isSaveEnabled = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Skip failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Load existing configuration if available.
     */
    fun loadExistingConfig(): ServerConfig? {
        return repository.loadConfig()
    }
}
