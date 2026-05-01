package com.vrhub.network

import com.google.gson.annotations.SerializedName

/**
 * API service for VRHub monetization backend (vrhub-monetization server).
 * Handles email verification, Ko-fi webhook processing, and license validation.
 */

/**
 * POST /init - Create pending user, send magic link
 */
data class InitRequest(
    val email: String
)

data class InitResponse(
    val message: String
)

/**
 * POST /webhook/kofi - Process Ko-fi purchase webhook
 */
data class WebhookRequest(
    @SerializedName("verification_token")
    val verificationToken: String,
    @SerializedName("type")
    val type: String = "Shop Order",
    val email: String,
    @SerializedName("shop_items")
    val shopItems: List<ShopItem>
)

data class ShopItem(
    @SerializedName("direct_link_code")
    val directLinkCode: String
)

data class WebhookResponse(
    val message: String
)

/**
 * GET /validate - Check if email has valid license
 */
data class ValidateResponse(
    val valid: Boolean,
    val tier: String?
)

/**
 * GET /health - Server health check
 */
data class HealthResponse(
    val status: String,
    val timestamp: String
)

/**
 * Generic error response
 */
data class ErrorResponse(
    val type: String?,
    val title: String?,
    val status: Int?,
    val detail: String?,
    val instance: String?
)

/**
 * Cleanup response
 */
data class CleanupResponse(
    val deleted: Long
)