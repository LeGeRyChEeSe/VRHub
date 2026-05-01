package com.vrhub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.vrhub.data.NetworkModule
import com.vrhub.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the debug monetization panel.
 */
data class DebugPanelState(
    val email: String = "",
    val token: String = "",
    val verificationToken: String = "",
    val response: String = "",
    val isLoading: Boolean = false,
    val isMinimized: Boolean = false,
    val isVisible: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for the debug monetization panel.
 * Handles API calls to the monetization backend for testing.
 */
class DebugMonetizationViewModel : ViewModel() {

    private val _state = MutableStateFlow(DebugPanelState())
    val state: StateFlow<DebugPanelState> = _state.asStateFlow()

    private val gson = Gson()

    fun updateEmail(email: String) {
        _state.value = _state.value.copy(email = email, error = null)
    }

    fun updateToken(token: String) {
        _state.value = _state.value.copy(token = token, error = null)
    }

    fun updateVerificationToken(token: String) {
        _state.value = _state.value.copy(verificationToken = token, error = null)
    }

    fun testInit() {
        val email = _state.value.email.trim()
        if (email.isBlank()) {
            _state.value = _state.value.copy(error = "Email is required")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val request = InitRequest(email)
                val response = NetworkModule.monetizationApi.initEmail(request)
                val body = if (response.isSuccessful) {
                    response.body()?.let { gson.toJson(it) } ?: "{\"message\": \"ok\"}"
                } else {
                    "Error ${response.code()}: ${response.errorBody()?.string() ?: "Unknown error"}"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = formatJson(body)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun testVerify() {
        val token = _state.value.token.trim()
        if (token.isBlank()) {
            _state.value = _state.value.copy(error = "Token is required")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = NetworkModule.monetizationApi.verifyToken(token)
                val body = if (response.isSuccessful) {
                    "Success: Redirected to Ko-fi"
                } else {
                    "Error ${response.code()}: ${response.errorBody()?.string() ?: "Unknown error"}"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = body
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun testWebhookSupporter() {
        testWebhook("supporter")
    }

    fun testWebhookLucky() {
        testWebhook("lucky")
    }

    private fun testWebhook(tier: String) {
        val email = _state.value.email.trim()
        val verificationToken = _state.value.verificationToken.trim()

        if (email.isBlank()) {
            _state.value = _state.value.copy(error = "Email is required")
            return
        }
        if (verificationToken.isBlank()) {
            _state.value = _state.value.copy(error = "Ko-fi verification token is required")
            return
        }

        val directLinkCode = when (tier) {
            "supporter" -> "44b9877f37"
            "lucky" -> "vrhub-lucky"
            else -> return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val request = WebhookRequest(
                    verificationToken = verificationToken,
                    type = "Shop Order",
                    email = email,
                    shopItems = listOf(ShopItem(directLinkCode = directLinkCode))
                )
                val response = NetworkModule.monetizationApi.webhookKofi(request)
                val body = if (response.isSuccessful) {
                    response.body()?.let { gson.toJson(it) } ?: "{\"message\": \"success\"}"
                } else {
                    "Error ${response.code()}: ${response.errorBody()?.string() ?: "Unknown error"}"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = formatJson(body)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun testValidate() {
        val email = _state.value.email.trim()
        if (email.isBlank()) {
            _state.value = _state.value.copy(error = "Email is required")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = NetworkModule.monetizationApi.validateEmail(email)
                val body = if (response.isSuccessful) {
                    response.body()?.let { gson.toJson(it) } ?: "{}"
                } else {
                    "Error ${response.code()}: ${response.errorBody()?.string() ?: "Unknown error"}"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = formatJson(body)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun testHealth() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = NetworkModule.monetizationApi.healthCheck()
                val body = if (response.isSuccessful) {
                    response.body()?.let { gson.toJson(it) } ?: "{}"
                } else {
                    "Error ${response.code()}"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = formatJson(body)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun toggleMinimized() {
        _state.value = _state.value.copy(isMinimized = !_state.value.isMinimized)
    }

    fun toggleVisibility() {
        _state.value = _state.value.copy(isVisible = !_state.value.isVisible)
    }

    fun clearResponse() {
        _state.value = _state.value.copy(response = "", error = null)
    }

    private fun formatJson(jsonString: String): String {
        return try {
            val jsonElement = gson.fromJson(jsonString, Any::class.java)
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            jsonString
        }
    }
}