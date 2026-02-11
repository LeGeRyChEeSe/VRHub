package com.vrpirates.rookieonquest.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vrpirates.rookieonquest.data.Constants
import com.vrpirates.rookieonquest.data.LogUtils
import com.vrpirates.rookieonquest.data.MainRepository
import com.vrpirates.rookieonquest.data.PermissionManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private val repository: MainRepository = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkStatic(Log::class)
        mockkObject(LogUtils)
        mockkObject(PermissionManager)
        
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0

        every { LogUtils.d(any(), any()) } just Runs
        every { LogUtils.i(any(), any()) } just Runs
        every { LogUtils.e(any(), any(), any()) } just Runs
        
        every { PermissionManager.init(any()) } just Runs
        every { PermissionManager.validateSavedStates(any()) } returns PermissionManager.ValidationResult(true)

        every { application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } just Runs
        every { editor.commit() } returns true
        
        // Mocking repo flows
        every { repository.getAllQueuedInstalls() } returns MutableStateFlow(emptyList())
        every { repository.getAllGamesFlow() } returns MutableStateFlow(emptyList())

        viewModel = MainViewModel(application, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
        unmockkObject(LogUtils)
        unmockkObject(PermissionManager)
    }

    @Test
    fun `checkForCatalogUpdate updates state when update available`() = runTest {
        // Given
        val date = "Mon, 10 Feb 2026 12:00:00 GMT"
        coEvery { repository.getRemoteCatalogInfo() } returns date
        coEvery { repository.isCatalogUpdateAvailable(date) } returns true
        
        // Start collecting the flow in backgroundScope to keep it active
        backgroundScope.launch { viewModel.isCatalogUpdateAvailable.collect {} }

        // When
        viewModel.checkForCatalogUpdate()
        
        // Advance until idle to ensure launch block finishes and combine emits
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.isCatalogUpdateAvailable.value)
        verify { editor.putBoolean(Constants.PreferenceKeys.CATALOG_UPDATE_AVAILABLE, true) }
    }

    @Test
    fun `checkForCatalogUpdate updates state when no update available`() = runTest {
        // Given
        val date = "Mon, 10 Feb 2026 12:00:00 GMT"
        coEvery { repository.getRemoteCatalogInfo() } returns date
        coEvery { repository.isCatalogUpdateAvailable(date) } returns false
        
        backgroundScope.launch { viewModel.isCatalogUpdateAvailable.collect {} }

        // When
        viewModel.checkForCatalogUpdate()
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.isCatalogUpdateAvailable.value)
        verify { editor.putBoolean(Constants.PreferenceKeys.CATALOG_UPDATE_AVAILABLE, false) }
    }

    @Test
    fun `dismissCatalogUpdate updates prefs and re-emits state`() = runTest {
        // Given
        // Initial state
        // We need to trigger checkForCatalogUpdate first to set it to true if we want to test re-emission
        // but re-emission just sets it to itself.
        
        // When
        viewModel.dismissCatalogUpdate()

        // Then
        verify { editor.putLong(Constants.PreferenceKeys.CATALOG_UPDATE_DISMISSED_TIME, any()) }
        verify { editor.commit() }
    }
}
