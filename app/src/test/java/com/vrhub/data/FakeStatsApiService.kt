package com.vrhub.data

import com.vrhub.network.ConsentRequest
import com.vrhub.network.ConsentResponse
import com.vrhub.network.GameStat
import com.vrhub.network.StatsCollectRequest
import com.vrhub.network.StatsCollectResponse
import com.vrhub.network.StatsApiService
import com.vrhub.network.UserTierResponse
import retrofit2.Response

/**
 * Fake implementation of StatsApiService for unit testing.
 * Records method calls and captures request data for verification.
 */
class FakeStatsApiService : StatsApiService {
    var collectStatsCalled = false
    var receivedGames: List<GameStat>? = null
    var receivedTier: String? = null
    var updateConsentCalled = false
    var updateConsentValue: Boolean? = null

    override suspend fun collectStats(request: StatsCollectRequest): Response<StatsCollectResponse> {
        collectStatsCalled = true
        receivedGames = request.games
        receivedTier = request.tier
        return Response.success(StatsCollectResponse(message = "ok"))
    }

    override suspend fun updateConsent(request: ConsentRequest): Response<ConsentResponse> {
        updateConsentCalled = true
        updateConsentValue = request.consent
        return Response.success(ConsentResponse(message = "ok", consent = request.consent))
    }

    override suspend fun getUserTier(email: String): Response<UserTierResponse> {
        return Response.success(UserTierResponse(email = email, tier = "standard", status = "ok"))
    }
}