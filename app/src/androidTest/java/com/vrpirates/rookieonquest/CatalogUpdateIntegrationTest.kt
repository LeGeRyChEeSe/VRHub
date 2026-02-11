package com.vrpirates.rookieonquest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vrpirates.rookieonquest.data.*
import com.vrpirates.rookieonquest.logic.CatalogUtils
import com.vrpirates.rookieonquest.network.PublicConfig
import com.vrpirates.rookieonquest.network.VrpService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

@RunWith(AndroidJUnit4::class)
class CatalogUpdateIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var gameDao: GameDao
    private lateinit var repository: MainRepository
    private val server = MockWebServer()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        server.start()
        
        // Use in-memory database for testing
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = db.gameDao()

        // Mock AppDatabase.getDatabase to return our in-memory DB
        mockkStatic(AppDatabase::class)
        every { AppDatabase.getDatabase(any()) } returns db

        // Mock NetworkModule to point to our mock server
        mockkObject(NetworkModule)
        val testRetrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        every { NetworkModule.retrofit } returns testRetrofit
        every { NetworkModule.okHttpClient } returns testRetrofit.callFactory() as okhttp3.OkHttpClient

        repository = MainRepository(context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        unmockkAll()
    }

    @Test
    fun testGetRemoteCatalogInfo() = runBlocking {
        // 1. Mock public config response
        val configJson = """{"baseUri": "${server.url("/")}", "password": "cGFzcw=="}"""
        server.enqueue(MockResponse().setBody(configJson).setResponseCode(200))
        
        // 2. Mock HEAD response for meta.7z
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Last-Modified", "Wed, 11 Feb 2026 12:00:00 GMT"))

        val lastModified = repository.getRemoteCatalogInfo()
        
        assertEquals("Wed, 11 Feb 2026 12:00:00 GMT", lastModified)
        
        // Verify requests
        val configRequest = server.takeRequest()
        assertTrue(configRequest.path?.contains("vrp-public.json") == true)
        
        val headRequest = server.takeRequest()
        assertEquals("HEAD", headRequest.method)
        assertTrue(headRequest.path?.contains("meta.7z") == true)
    }

    @Test
    fun testIsCatalogUpdateAvailable() = runBlocking {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(Constants.PreferenceKeys.META_LAST_MODIFIED, "old_date").apply()
        
        // Insert a dummy game to ensure currentItemCount > 0
        gameDao.insertGames(listOf(GameEntity("game1", "Game 1", "pkg1", "1")))
        
        val available = repository.isCatalogUpdateAvailable("new_date")
        assertTrue(available)
    }
}
