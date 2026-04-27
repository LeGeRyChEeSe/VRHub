package com.vrpirates.rookieonquest.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.net.URL

/**
 * Repository for managing server configuration persistence.
 * Stores server URL and password for catalog access.
 *
 * @param context Android context for accessing SharedPreferences
 */
class ServerConfigRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save the server configuration.
     * @param config The configuration to save
     */
    fun saveConfig(config: ServerConfig) {
        prefs.edit().putString(KEY_SERVER_CONFIG, ServerConfig.toJson(config)).apply()
    }

    /**
     * Load the server configuration.
     * @return The saved configuration, or null if none exists or is invalid
     */
    fun loadConfig(): ServerConfig? {
        val json = prefs.getString(KEY_SERVER_CONFIG, null) ?: return null
        return ServerConfig.fromJson(json)
    }

    /**
     * Check if a valid configuration exists.
     * @return true if a valid configuration is saved
     */
    fun hasValidConfig(): Boolean {
        val config = loadConfig()
        return config != null && config.isValid()
    }

    /**
     * Clear the saved configuration.
     */
    fun clearConfig() {
        prefs.edit().remove(KEY_SERVER_CONFIG).apply()
    }

    /**
     * Check if a configuration has ever been saved (even if now invalid).
     * @return true if a configuration exists (even if invalid)
     */
    fun hasConfig(): Boolean {
        return prefs.getString(KEY_SERVER_CONFIG, null) != null
    }

    /**
     * Fetch and validate JSON configuration from a URL.
     *
     * @param urlString The URL to fetch JSON config from
     * @return Result containing ServerConfig on success, or error with specific message
     */
    suspend fun fetchJsonConfig(urlString: String): Result<ServerConfig> = withContext(Dispatchers.IO) {
        // Validate URL format
        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            return@withContext Result.failure(InvalidUrlException("Invalid URL format"))
        }

        // Ensure URL has valid scheme
        val scheme = url.protocol
        if (scheme != "http" && scheme != "https") {
            return@withContext Result.failure(InvalidUrlException("Invalid URL format"))
        }

        // Build and execute request with timeout
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val response = try {
            NetworkModule.okHttpClient.newCall(request).await()
        } catch (e: java.io.IOException) {
            return@withContext Result.failure(NetworkException("Network error: ${e.message}"))
        }

        // Check response code
        if (!response.isSuccessful) {
            return@withContext Result.failure(NetworkException("Server returned error: ${response.code}"))
        }

        // Read response body
        val body = response.body?.string()
        if (body == null || body.length > MAX_BODY_LENGTH) {
            return@withContext Result.failure(JsonParseException("Server returned empty or oversized response"))
        }
        if (body.isBlank()) {
            return@withContext Result.failure(JsonParseException("Server returned empty response"))
        }

        // Parse JSON
        val jsonConfig: JsonConfigResponse
        try {
            jsonConfig = GSON.fromJson(body, JsonConfigResponse::class.java)
        } catch (e: JsonSyntaxException) {
            return@withContext Result.failure(JsonParseException("Server returned invalid JSON"))
        } catch (e: Exception) {
            return@withContext Result.failure(JsonParseException("Server returned invalid JSON"))
        }

        // Validate required keys
        if (jsonConfig.baseUri.isNullOrBlank()) {
            return@withContext Result.failure(MissingKeysException("Configuration missing required keys"))
        }
        if (jsonConfig.password.isNullOrBlank()) {
            return@withContext Result.failure(MissingKeysException("Configuration missing required keys"))
        }

        // Length validation to prevent memory pressure
        if (jsonConfig.baseUri.length > MAX_URI_LENGTH || jsonConfig.password.length > MAX_PASSWORD_LENGTH) {
            return@withContext Result.failure(JsonParseException("Configuration values too long"))
        }

        // Build ServerConfig with extra keys
        val extraKeys = mutableMapOf<String, String>()
        jsonConfig.extraKeys?.forEach { (key, value) ->
            if (key.isNotBlank() && key != "baseUri" && key != "password" && value.isNotBlank()) {
                extraKeys[key] = value
            }
        }

        Result.success(ServerConfig(
            baseUri = jsonConfig.baseUri,
            password = jsonConfig.password,
            extraKeys = extraKeys
        ))
    }

    companion object {
        private const val PREFS_NAME = "vrhub_server_config"
        private const val KEY_SERVER_CONFIG = "server_config"
        private const val MAX_URI_LENGTH = 2048
        private const val MAX_PASSWORD_LENGTH = 512
        private const val MAX_BODY_LENGTH = 1024 * 1024 // 1 MB
        private val GSON = Gson()
    }
}

/**
 * JSON response structure for remote configuration.
 */
private data class JsonConfigResponse(
    @SerializedName("baseUri")
    val baseUri: String?,
    @SerializedName("password")
    val password: String?,
    @SerializedName("extraKeys")
    val extraKeys: Map<String, String>?
)

/**
 * Exception for invalid URL format.
 */
class InvalidUrlException(message: String) : Exception(message)

/**
 * Exception for network errors.
 */
class NetworkException(message: String) : Exception(message)

/**
 * Exception for JSON parsing errors.
 */
class JsonParseException(message: String) : Exception(message)

/**
 * Exception for missing required configuration keys.
 */
class MissingKeysException(message: String) : Exception(message)

/**
 * Result of testing server configuration connectivity.
 */
sealed class TestResult {
    object Success : TestResult()
    data class ConnectionError(val message: String) : TestResult()
    data class Timeout(val seconds: Int) : TestResult()
    data class InvalidConfig(val message: String) : TestResult()
}

/**
 * Test the connectivity of a server configuration.
 *
 * @param config The server configuration to test
 * @return TestResult indicating success or specific failure reason
 */
suspend fun testConnection(config: ServerConfig): TestResult {
    return withContext(Dispatchers.IO) {
        val url = try {
            java.net.URL(config.baseUri)
        } catch (e: Exception) {
            return@withContext TestResult.InvalidConfig("Invalid URL format")
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val response = try {
            // Use a 10-second timeout as per AC #4
            withTimeoutOrNull(10_000) {
                NetworkModule.okHttpClient.newCall(request).await()
            }
        } catch (e: java.io.IOException) {
            return@withContext TestResult.ConnectionError(e.message ?: "Network error")
        } catch (e: Exception) {
            return@withContext TestResult.ConnectionError(e.message ?: "Connection failed")
        }

        if (response == null) {
            return@withContext TestResult.Timeout(10)
        }

        if (!response.isSuccessful) {
            return@withContext TestResult.ConnectionError("Server returned error: ${response.code}")
        }

        TestResult.Success
    }
}
