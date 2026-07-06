package com.vrhub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.vrhub.ui.theme.AppFont
import com.vrhub.ui.theme.AppearanceSettings
import com.vrhub.ui.theme.BackgroundStyle
import com.vrhub.ui.theme.CardCorner
import com.vrhub.ui.theme.Density
import com.vrhub.ui.theme.CatalogLayout
import com.vrhub.ui.theme.FONT_SCALE_MAX
import com.vrhub.ui.theme.FONT_SCALE_MIN
import com.vrhub.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AppearancePreferencesTest {

    private lateinit var appContext: Context
    private lateinit var preferences: AppearancePreferences

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        preferences = AppearancePreferences(appContext)
    }

    @Test
    fun `settings defaults match AppearanceSettings defaults when nothing stored`() = runTest {
        assertEquals(AppearanceSettings(), preferences.settings.first())
    }

    @Test
    fun `update persists every field and settings reflects it`() = runTest {
        preferences.update {
            AppearanceSettings(
                themeMode = ThemeMode.LIGHT,
                accentArgb = 0xFF8B5CF6.toInt(),
                background = BackgroundStyle.SLATE,
                font = AppFont.SERIF,
                fontScale = 1.15f,
                density = Density.COMPACT,
                cardCorner = CardCorner.SHARP,
                catalogLayout = CatalogLayout.GRID,
            )
        }

        val result = preferences.settings.first()
        assertEquals(ThemeMode.LIGHT, result.themeMode)
        assertEquals(0xFF8B5CF6.toInt(), result.accentArgb)
        assertEquals(BackgroundStyle.SLATE, result.background)
        assertEquals(AppFont.SERIF, result.font)
        assertEquals(1.15f, result.fontScale)
        assertEquals(Density.COMPACT, result.density)
        assertEquals(CardCorner.SHARP, result.cardCorner)
        assertEquals(CatalogLayout.GRID, result.catalogLayout)
    }

    @Test
    fun `fontScale is coerced into bounds on write`() = runTest {
        preferences.update { AppearanceSettings(fontScale = 5.0f) }
        assertEquals(FONT_SCALE_MAX, preferences.settings.first().fontScale)

        preferences.update { AppearanceSettings(fontScale = 0.1f) }
        assertEquals(FONT_SCALE_MIN, preferences.settings.first().fontScale)
    }

    @Test
    fun `reset clears all keys back to defaults`() = runTest {
        preferences.update {
            AppearanceSettings(themeMode = ThemeMode.LIGHT, font = AppFont.MONO, fontScale = 1.3f)
        }
        preferences.reset()

        assertEquals(AppearanceSettings(), preferences.settings.first())
    }

    @Test
    fun `corrupted enum value falls back to default instead of crashing`() = runTest {
        val themeModeKey = stringPreferencesKey("theme_mode")
        appContext.appearanceDataStore.edit { prefs ->
            prefs[themeModeKey] = "NOT_A_REAL_ENUM_VALUE"
        }

        assertEquals(AppearanceSettings().themeMode, preferences.settings.first().themeMode)
    }
}
