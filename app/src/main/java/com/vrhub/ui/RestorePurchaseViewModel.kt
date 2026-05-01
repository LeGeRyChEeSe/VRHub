package com.vrhub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrhub.data.NetworkModule
import com.vrhub.data.ServerConfigRepository
import com.vrhub.network.InitRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the restore purchase screen.
 */
data class RestorePurchaseState(
    val email: String = "",
    val isLoading: Boolean = false,
    val isSent: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the restore purchase flow.
 * Handles calling /init to send a restore magic link to the user's email.
 */
class RestorePurchaseViewModel(application: Application) : AndroidViewModel(application) {

    private val configRepository = ServerConfigRepository(application)

    private val _uiState = MutableStateFlow(RestorePurchaseState())
    val uiState: StateFlow<RestorePurchaseState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun sendRestoreLink() {
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
                val request = InitRequest(email)
                val response = NetworkModule.monetizationApi.initEmail(request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSent = true)
                    configRepository.saveMonetizationEmail(email)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = when (response.code()) {
                        429 -> "Too many requests. Please wait before trying again."
                        else -> errorBody ?: "Failed to send restore link"
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
}
