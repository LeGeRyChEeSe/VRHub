package com.vrhub.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.URI

/**
 * Server configuration data class.
 * Stores the server URL and password needed to access the VR game catalog.
 *
 * @param baseUri The base URL of the VR game catalog server
 * @param password The password for extracting archives from the server
 * @param extraKeys Additional key-value pairs for extensibility (optional server-specific settings)
 * @param monetizationUrl The URL of the monetization backend (default: https://vrhub.sunshine-aio.com)
 * @param kofiVerificationToken The Ko-fi verification token for webhook authentication
 */
data class ServerConfig(
    @SerializedName("baseUri")
    val baseUri: String,
    @SerializedName("password")
    val password: String,
    val extraKeys: Map<String, String> = emptyMap(),
    @SerializedName("monetizationUrl")
    val monetizationUrl: String? = null,
    @SerializedName("kofiVerificationToken")
    val kofiVerificationToken: String? = null
) {
    /**
     * Check if this configuration is valid (has non-empty, non-whitespace baseUri and password,
     * and baseUri is a valid http/https URI).
     */
    fun isValid(): Boolean {
        if (baseUri.isBlank() || password.isBlank()) return false
        if (baseUri.isNotBlank() && baseUri.trim() != baseUri) return false
        if (password.isNotBlank() && password.trim() != password) return false
        val uri = try { URI(baseUri) } catch (e: Exception) { return false }
        val scheme = uri.scheme
        return scheme == "http" || scheme == "https"
    }

    companion object {
        private val GSON = Gson()

        /**
         * Serialize to JSON string for storage.
         */
        fun toJson(config: ServerConfig): String = GSON.toJson(config)

        /**
         * Deserialize from JSON string.
         */
        fun fromJson(json: String): ServerConfig? {
            return try {
                GSON.fromJson(json, ServerConfig::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
