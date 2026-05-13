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
 * @param timestamp Unix timestamp in milliseconds (System.currentTimeMillis())
 */
data class StatsCollectRequest(
    val games: List<GameStat>,
    val tier: String,
    val timestamp: Long
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
 */
data class ConsentRequest(
    val consent: Boolean
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