package com.vrpirates.rookieonquest.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vrpirates.rookieonquest.network.VrpService
import com.vrpirates.rookieonquest.network.PublicConfig
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.ResponseBody
import retrofit2.Response

class MainRepositoryUpdateTest {

    private lateinit var repository: MainRepository
    private val context: Context = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val service: VrpService = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)

    @org.junit.Rule
    @JvmField
    val tempFolder = org.junit.rules.TemporaryFolder()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(NetworkModule)
        mockkStatic(android.os.Environment::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { android.os.Environment.getExternalStoragePublicDirectory(any()) } returns tempFolder.newFolder("downloads")
        every { NetworkModule.okHttpClient } returns mockk(relaxed = true)
        every { NetworkModule.retrofit } returns mockk(relaxed = true)

        val db: AppDatabase = mockk(relaxed = true)
        every { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
        every { context.filesDir } returns tempFolder.newFolder("files")
        
        repository = MainRepository(context, db, gameDao, service)
    }

    @org.junit.After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkObject(NetworkModule)
        unmockkStatic(android.os.Environment::class)
    }

    @Test
    fun `isCatalogUpdateAvailable returns true when dates differ and count is positive`() = runTest {
        // Given
        every { prefs.getString(Constants.PreferenceKeys.META_LAST_MODIFIED, "") } returns "old_date"
        coEvery { gameDao.getCount() } returns 10

        // When
        val available = repository.isCatalogUpdateAvailable("new_date")

        // Then
        assertTrue(available)
    }

    @Test
    fun `isCatalogUpdateAvailable returns false when dates are equal`() = runTest {
        // Given
        every { prefs.getString(Constants.PreferenceKeys.META_LAST_MODIFIED, "") } returns "same_date"
        coEvery { gameDao.getCount() } returns 10

        // When
        val available = repository.isCatalogUpdateAvailable("same_date")

        // Then
        assertFalse(available)
    }

    @Test
    fun `getRemoteCatalogInfo returns date from head request`() = runTest {
        // Given
        val config = PublicConfig(baseUri = "https://example.com", password64 = "cGFzcw==")
        coEvery { service.getPublicConfig() } returns config
        
        val mockCall: okhttp3.Call = mockk()
        val mockResponse: okhttp3.Response = mockk(relaxed = true)
        val mockClient = NetworkModule.okHttpClient
        
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.enqueue(any()) } answers {
            val callback = it.invocation.args[0] as okhttp3.Callback
            callback.onResponse(mockCall, mockResponse)
        }
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.header("Last-Modified") } returns "Mon, 10 Feb 2026 12:00:00 GMT"
        every { mockResponse.code } returns 200

        // When
        val result = repository.getRemoteCatalogInfo()

        // Then
        assertTrue(result == "Mon, 10 Feb 2026 12:00:00 GMT")
    }
}
