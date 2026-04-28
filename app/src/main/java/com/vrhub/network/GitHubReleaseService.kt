package com.vrhub.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * GitHub Releases API response model.
 * Represents the latest release from a GitHub repository.
 *
 * @property tagName The version tag (e.g., "v2.5.0")
 * @property name Release name/title
 * @property body Markdown body with changelog
 * @property htmlUrl URL to the release page
 * @property assets List of release assets
 * @property publishedAt ISO-8601 timestamp of release publication
 */
data class GitHubReleaseResponse(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>,
    @SerializedName("published_at") val publishedAt: String?
)

/**
 * GitHub release asset model.
 *
 * @property name Asset filename
 * @property browserDownloadUrl Direct download URL
 * @property size Size in bytes
 */
data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val size: Long
)

/**
 * Retrofit service for GitHub Releases API.
 *
 * Used as PRIMARY update checker - Netlify is fallback only for 404 (repo transferred/deleted).
 *
 * IMPORTANT: GitHub API rate limits (60 req/hour unauthenticated) without proper User-Agent.
 * We use "VRHub/1.0" as User-Agent to identify our app.
 *
 * @see <a href="https://docs.github.com/en/rest/releases/releases#get-the-latest-release">GitHub REST API - Latest Release</a>
 */
interface GitHubReleaseService {
    /**
     * Fetches the latest release from the configured GitHub repository.
     *
     * @param userAgent Custom User-Agent header to avoid rate limiting.
     *                  GitHub rate limits 60 req/hour for unauthenticated requests without proper User-Agent.
     * @return [GitHubReleaseResponse] containing release info and assets
     * @throws retrofit2.HttpException for non-2xx responses
     * @throws java.io.IOException for network failures
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("User-Agent") userAgent: String = "VRHub/1.0"
    ): GitHubReleaseResponse
}

/**
 * Result of a GitHub API check operation.
 * Distinguishes between different error types for proper handling.
 */
sealed class GitHubApiResult {
    /** Successful response with update info */
    data class Success(val updateInfo: UpdateInfo) : GitHubApiResult()

    /** Repository not found (404) - indicates repo was transferred/deleted */
    data class NotFound(val message: String) : GitHubApiResult()

    /** Rate limited (403) - should retry with backoff, NOT fallback to Netlify */
    data class RateLimited(val remaining: Int, val resetTime: String?) : GitHubApiResult()

    /** Server error (5xx) - should retry with backoff, NOT fallback to Netlify */
    data class ServerError(val code: Int, val message: String) : GitHubApiResult()

    /** Client error (4xx except 403/404) - should retry with backoff */
    data class ClientError(val code: Int, val message: String) : GitHubApiResult()

    /** Network or IO error - should retry with backoff, NOT fallback to Netlify */
    data class NetworkError(val message: String) : GitHubApiResult()
}
