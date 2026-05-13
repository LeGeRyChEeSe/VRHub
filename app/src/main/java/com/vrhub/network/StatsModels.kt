package com.vrhub.network

import com.google.gson.annotations.SerializedName

/**
 * API models for VRHub stats collection backend.
 * Handles anonymous game statistics and user consent management.
 */

/**
 * POST /stats/collect - Send anonymous game stats
 * @param games List of installed games with favorite status
 * @param tier User tier (standard/supporter/lucky)
 * @param email User email (only for supporter/lucky tier, null for standard)
 */
data class StatsCollectRequest(
    val games: List<GameStat>,
    val tier: String,
    val email: String? = null
)

data class GameStat(
    @SerializedName("package_name")
    val packageName: String?,
    @SerializedName("is_favorite")
    val isFavorite: Boolean
)

data class StatsCollectResponse(
    val message: String?
)

/**
 * POST /stats/consent - Update user consent preference
 * @param enabled Whether consent is enabled
 * @param email User email (only for supporter/lucky tier, null for standard)
 */
data class ConsentRequest(
    val enabled: Boolean,
    val email: String? = null
)

data class ConsentResponse(
    val message: String?,
    val consent: Boolean
)

/**
 * GET /user/tier - Get user tier by email
 * Note: email is passed as query parameter for backend routing.
 * The server associates the user via session/auth token, not the email itself.
 */
data class UserTierResponse(
    val email: String,
    val tier: String?,
    val status: String
)

/**
 * Resolves the user tier from API response with fallback to "standard".
 * @param tier The tier from API response (may be null or empty)
 * @return "standard" if tier is null/empty, otherwise the tier value
 */
fun resolveTier(tier: String?): String {
    return when {
        tier.isNullOrBlank() -> "standard"
        tier in listOf("standard", "supporter", "lucky") -> tier
        else -> "standard" // Unknown tier, fallback to standard
    }
}