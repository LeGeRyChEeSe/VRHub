package com.vrhub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vrhub_settings"
)

class ConsentPreferences(private val context: Context) {

    private val dataStore = context.settingsDataStore

    companion object {
        val CONSENT_ENABLED = booleanPreferencesKey("consent_enabled")
    }

    val consentEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[CONSENT_ENABLED] ?: false
        }

    suspend fun setConsentEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CONSENT_ENABLED] = enabled
        }
    }
}