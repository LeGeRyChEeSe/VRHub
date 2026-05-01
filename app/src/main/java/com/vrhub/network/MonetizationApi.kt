package com.vrhub.network

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit service interface for VRHub monetization backend.
 */
interface MonetizationApi {

    /**
     * POST /init - Create pending user, send magic link email
     */
    @POST("/init")
    suspend fun initEmail(@Body request: InitRequest): Response<InitResponse>

    /**
     * GET /verify?token=UUID - Validate magic link, redirect to Ko-fi
     * Note: Returns 302 redirect, not JSON
     */
    @GET("/verify")
    suspend fun verifyToken(@Query("token") token: String): Response<Unit>

    /**
     * POST /webhook/kofi - Process Ko-fi purchase webhook
     */
    @POST("/webhook/kofi")
    suspend fun webhookKofi(@Body request: WebhookRequest): Response<WebhookResponse>

    /**
     * GET /validate?email= - Check if email has valid license
     */
    @GET("/validate")
    suspend fun validateEmail(@Query("email") email: String): Response<ValidateResponse>

    /**
     * GET /health - Server health check
     */
    @GET("/health")
    suspend fun healthCheck(): Response<HealthResponse>

    /**
     * POST /pending/cleanup - Delete expired pending entries (internal)
     */
    @POST("/pending/cleanup")
    suspend fun cleanupPending(): Response<CleanupResponse>
}