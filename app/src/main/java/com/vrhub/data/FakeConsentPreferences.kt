package com.vrhub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake implementation of ConsentPreferencesInterface for unit testing.
 * Uses in-memory MutableStateFlow instead of actual DataStore.
 */
class FakeConsentPreferences : ConsentPreferencesInterface {

    private val _consentEnabled = MutableStateFlow(false)
    private val _hasSeenConsentDialog = MutableStateFlow(false)

    override val consentEnabled: Flow<Boolean> = _consentEnabled
    override val hasSeenConsentDialog: Flow<Boolean> = _hasSeenConsentDialog

    override suspend fun setConsentEnabled(enabled: Boolean) {
        _consentEnabled.value = enabled
    }

    override suspend fun setHasSeenConsentDialog(shown: Boolean) {
        _hasSeenConsentDialog.value = shown
    }
}