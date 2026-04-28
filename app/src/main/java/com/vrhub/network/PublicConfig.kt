package com.vrhub.network

/**
 * Static configuration for VRPirates server.
 * Values are loaded from BuildConfig (local.properties).
 */
data class PublicConfig(
    val baseUri: String,
    val password64: String
)