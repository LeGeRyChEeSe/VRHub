package com.vrhub.network

import retrofit2.Response
import retrofit2.http.*

interface StatsApiService {

    @POST("stats/collect")
    suspend fun collectStats(@Body request: StatsCollectRequest): Response<StatsCollectResponse>

    @POST("stats/consent")
    suspend fun updateConsent(@Body request: ConsentRequest): Response<ConsentResponse>

    /**
     * GET /user/tier - Get user tier by email
     * @param email User email for lookup (caller should validate format before calling)
     */
    @GET("user/tier")
    suspend fun getUserTier(@Query("email") email: String): Response<UserTierResponse>
}