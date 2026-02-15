package com.vrpirates.rookieonquest.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Model representing application update information returned by the secure gateway.
 */
data class UpdateInfo(
    /** Semantic version string (e.g., "2.1.2") */
    @SerializedName("version") val version: String,
    /** Markdown formatted list of changes in this version */
    @SerializedName("changelog") val changelog: String,
    /** Direct HTTPS URL to download the update APK */
    @SerializedName("downloadUrl") val downloadUrl: String,
    /** SHA-256 hex-encoded checksum of the update APK for integrity verification */
    @SerializedName("checksum") val checksum: String,
    /** ISO-8601 UTC timestamp of the release */
    @SerializedName("timestamp") val timestamp: String
)

/**
 * Retrofit service for checking application updates via the secure gateway.
 *
 * Requests to this service must be authenticated using HMAC-SHA256 request signing.
 * The signature is computed using the ROOKIE_UPDATE_SECRET and the current UTC timestamp.
 */
interface UpdateService {
    /**
     * Checks for the latest available version of the application.
     *
     * @param signature HMAC-SHA256 signature of the [date] header value
     * @param date Current UTC timestamp in ISO-8601 format (e.g., "2026-02-15T12:00:00Z")
     * @return [UpdateInfo] containing metadata about the latest version
     * @throws retrofit2.HttpException for non-2xx responses (e.g., 403 Forbidden if signature is invalid or clock skew is too large)
     * @throws java.io.IOException for network failures or connection timeouts
     * @throws com.google.gson.JsonParseException if the server response is not valid JSON or doesn't match UpdateInfo structure
     */
    @GET(".netlify/functions/check-update")
    suspend fun checkUpdate(
        @Header("X-Rookie-Signature") signature: String,
        @Header("X-Rookie-Date") date: String
    ): UpdateInfo
}
