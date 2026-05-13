package com.vrhub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of ConsentPreferences for unit testing.
 * Allows controlling the consent state programmatically.
 */
class FakeConsentPreferences(initialState: Boolean) : ConsentPreferencesInterface {
    private val _consentEnabled = MutableStateFlow(initialState)
    override val consentEnabled: Flow<Boolean> = _consentEnabled.asStateFlow()

    override suspend fun setConsentEnabled(enabled: Boolean) {
        _consentEnabled.value = enabled
    }
}