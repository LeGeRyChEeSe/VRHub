package com.vrhub.data

import kotlinx.coroutines.flow.Flow

/**
 * Interface for consent preferences functionality.
 * Used for dependency injection and testing fakes.
 */
interface ConsentPreferencesInterface {
    val consentEnabled: Flow<Boolean>
    val hasSeenConsentDialog: Flow<Boolean>
    suspend fun setConsentEnabled(enabled: Boolean)
    suspend fun setHasSeenConsentDialog(shown: Boolean)
}