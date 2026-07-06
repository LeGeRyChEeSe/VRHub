package com.vrhub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrhub.data.AppearancePreferences
import com.vrhub.ui.theme.AppearanceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes persisted [AppearanceSettings] to Compose and applies updates.
 */
class AppearanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppearancePreferences(application)

    val settings: StateFlow<AppearanceSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppearanceSettings())

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch {
            repository.update(transform)
        }
    }

    fun reset() {
        viewModelScope.launch {
            repository.reset()
        }
    }
}
