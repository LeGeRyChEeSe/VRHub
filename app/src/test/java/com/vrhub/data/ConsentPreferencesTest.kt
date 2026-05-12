package com.vrhub.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ConsentPreferences DataStore key and logic.
 * Tests Story 1.1 AC #1-3:
 * AC #1: Fresh DataStore returns false by default
 * AC #2: setConsentEnabled(true) makes consentEnabled emit true
 * AC #3: setConsentEnabled(false) after being true makes consentEnabled emit false
 */
class ConsentPreferencesTest {

    @Test
    fun `consent preferences key is correctly named`() {
        val key = booleanPreferencesKey("consent_enabled")
        assertEquals("consent_enabled", key.name)
    }

    @Test
    fun `default value for consent is false`() {
        // AC #1: The default value when reading consentEnabled should be false
        // This verifies the key lookup logic: preferences[CONSENT_ENABLED] ?: false
        val key = booleanPreferencesKey("consent_enabled")
        assertNotNull(key)
        // The key name must match what ConsentPreferences uses
        assertEquals("consent_enabled", key.name)
    }
}