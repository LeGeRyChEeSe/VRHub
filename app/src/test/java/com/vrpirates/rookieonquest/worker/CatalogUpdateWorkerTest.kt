package com.vrpirates.rookieonquest.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.vrpirates.rookieonquest.data.Constants
import com.vrpirates.rookieonquest.data.MainRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogUpdateWorkerTest {

    private lateinit var worker: CatalogUpdateWorker
    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val repository: MainRepository = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        worker = CatalogUpdateWorker(context, workerParams)
        
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } just Runs
    }

    @org.junit.After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `doWorkInternal returns success and updates prefs when update available`() = runTest {
        // Given
        coEvery { repository.getRemoteCatalogInfo() } returns "new_date"
        coEvery { repository.isCatalogUpdateAvailable("new_date") } returns true
        coEvery { repository.calculateUpdateCountFromMeta() } returns 5

        // When
        val result = worker.doWorkInternal(repository)

        // Then
        assertTrue(result is ListenableWorker.Result.Success)
        verify { editor.putBoolean(CatalogUpdateWorker.KEY_UPDATE_AVAILABLE, true) }
        verify { editor.putInt(Constants.PreferenceKeys.CATALOG_UPDATE_GAME_COUNT, 5) }
        verify { editor.apply() }
    }

    @Test
    fun `doWorkInternal returns success and updates prefs when no update available`() = runTest {
        // Given
        coEvery { repository.getRemoteCatalogInfo() } returns "same_date"
        coEvery { repository.isCatalogUpdateAvailable("same_date") } returns false

        // When
        val result = worker.doWorkInternal(repository)

        // Then
        assertTrue(result is ListenableWorker.Result.Success)
        verify { editor.putBoolean(CatalogUpdateWorker.KEY_UPDATE_AVAILABLE, false) }
        verify { editor.putInt(Constants.PreferenceKeys.CATALOG_UPDATE_GAME_COUNT, 0) }
        verify { editor.apply() }
    }

    @Test
    fun `doWorkInternal returns retry on repository exception`() = runTest {
        // Given
        coEvery { repository.getRemoteCatalogInfo() } throws Exception("Network error")

        // When
        val result = worker.doWorkInternal(repository)

        // Then
        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
