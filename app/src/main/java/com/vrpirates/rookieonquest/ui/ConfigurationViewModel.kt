package com.vrpirates.rookieonquest.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrpirates.rookieonquest.data.InvalidUrlException
import com.vrpirates.rookieonquest.data.JsonParseException
import com.vrpirates.rookieonquest.data.MissingKeysException
import com.vrpirates.rookieonquest.data.NetworkException
import com.vrpirates.rookieonquest.data.ServerConfig
import com.vrpirates.rookieonquest.data.ServerConfigRepository
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
    val kvPairs: List<KeyValuePair> = listOf(KeyValuePair("", "")),
    val isTesting: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val testedConfig: ServerConfig? = null
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
        val isValidFormat = url.isNotBlank() && (
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        )
        _uiState.value = _uiState.value.copy(
            jsonUrl = url,
            isSaveEnabled = isValidFormat,
            isSaved = false,
            errorMessage = null,
            successMessage = null,
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
     * - JSON_URL mode: fetches JSON from URL and validates structure
     * - MANUAL_KV mode: validates locally that baseUri and password keys exist and are valid
     */
    fun testConfiguration() {
        testJob?.cancel()
        // Capture state AFTER cancel to avoid stale state
        val currentState = _uiState.value
        val inputMode = currentState.inputMode
        val jsonUrl = currentState.jsonUrl
        val kvPairs = currentState.kvPairs

        testJob = viewModelScope.launch {
            currentCoroutineContext().ensureActive()
            _uiState.value = _uiState.value.copy(
                isTesting = true,
                errorMessage = null,
                successMessage = null,
                isSaveEnabled = false,
                testedConfig = null
            )

            currentCoroutineContext().ensureActive()

            val result = when (inputMode) {
                InputMode.JSON_URL -> repository.fetchJsonConfig(jsonUrl)
                InputMode.MANUAL_KV -> validateManualConfig(kvPairs)
            }

            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { config ->
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        successMessage = "Configuration is valid",
                        isSaveEnabled = true,
                        testedConfig = config
                    )
                },
                onFailure = { error ->
                    val errorMessage = when (error) {
                        is InvalidUrlException -> "Invalid URL format"
                        is NetworkException -> error.message ?: "Network error"
                        is JsonParseException -> "Server returned invalid JSON"
                        is MissingKeysException -> "Configuration missing required keys"
                        is IllegalArgumentException -> error.message ?: "Invalid configuration"
                        else -> "Test failed: ${error.message}"
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
        saveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, isSaveEnabled = false)
            try {
                currentCoroutineContext().ensureActive()
                repository.saveConfig(configToSave)
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
     * Load existing configuration if available.
     */
    fun loadExistingConfig(): ServerConfig? {
        return repository.loadConfig()
    }
}
