package com.vrhub.network

/**
 * Static configuration for game server.
 * Values are loaded from BuildConfig (local.properties).
 */
data class PublicConfig(
    val baseUri: String,
    val password64: String
)