package com.vrhub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrhub.data.NetworkModule
import com.vrhub.data.ServerConfigRepository
import com.vrhub.network.InitRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the monetization email screen.
 */
data class MonetizationEmailState(
    val email: String = "",
    val isLoading: Boolean = false,
    val isSent: Boolean = false,
    val isUpgrading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the monetization email initiation flow.
 * Handles calling /init to send a magic link to the user's email.
 * Server determines if user is new (purchase) or existing (restore via login link).
 */
class MonetizationEmailViewModel(application: Application) : AndroidViewModel(application) {

    private val configRepository = ServerConfigRepository(application)

    private val _uiState = MutableStateFlow(MonetizationEmailState())
    val uiState: StateFlow<MonetizationEmailState> = _uiState.asStateFlow()

    /**
     * Get the saved monetization email from local storage.
     * Returns null if no email has been saved yet.
     */
    fun getSavedEmail(): String? {
        return configRepository.loadMonetizationEmail()
    }

    /**
     * Set an error for the upgrade flow (when savedEmail is null).
     */
    fun setUpgradeError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun sendMagicLink() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email is required")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(error = "Invalid email format")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                currentCoroutineContext().ensureActive()
                val request = InitRequest(email)
                val response = NetworkModule.monetizationApi.initEmail(request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSent = true)
                    configRepository.saveMonetizationEmail(email)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = when (response.code()) {
                        429 -> "Too many requests. Please wait before trying again."
                        else -> errorBody ?: "Failed to send magic link"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun resetToEmailInput() {
        _uiState.value = _uiState.value.copy(isSent = false, error = null)
    }

    /**
     * Send upgrade magic link to an existing email.
     * Used when Supporter wants to upgrade to Lucky.
     * Server sends magic link to the provided email, which redirects to Ko-fi for upgrade purchase.
     */
    fun sendUpgradeMagicLink(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "No email on file")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpgrading = true, error = null)
            try {
                currentCoroutineContext().ensureActive()
                val request = InitRequest(email)
                val response = NetworkModule.monetizationApi.initEmail(request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isUpgrading = false, isSent = true)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = when (response.code()) {
                        429 -> "Too many requests. Please wait before trying again."
                        else -> errorBody ?: "Failed to send upgrade link"
                    }
                    _uiState.value = _uiState.value.copy(isUpgrading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpgrading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }
}
