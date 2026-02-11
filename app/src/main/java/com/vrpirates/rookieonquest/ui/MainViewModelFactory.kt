package com.vrpirates.rookieonquest.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vrpirates.rookieonquest.data.MainRepository

/**
 * Factory for creating [MainViewModel] with its required dependencies.
 * 
 * This factory ensures that the correct [Application] context and [MainRepository]
 * are provided to the ViewModel, preventing initialization issues.
 * 
 * @param application The Android Application context
 * @param repository The MainRepository instance
 */
class MainViewModelFactory(
    private val application: Application,
    private val repository: MainRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
