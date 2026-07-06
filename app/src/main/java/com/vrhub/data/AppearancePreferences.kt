package com.vrhub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vrhub.ui.theme.AppFont
import com.vrhub.ui.theme.AppearanceSettings
import com.vrhub.ui.theme.BackgroundStyle
import com.vrhub.ui.theme.CardCorner
import com.vrhub.ui.theme.CatalogLayout
import com.vrhub.ui.theme.Density
import com.vrhub.ui.theme.FONT_SCALE_MAX
import com.vrhub.ui.theme.FONT_SCALE_MIN
import com.vrhub.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "appearance_prefs"
)

/**
 * Persists [AppearanceSettings], following the same DataStore pattern as [ConsentPreferences].
 *
 * Every read is defensive: a corrupted or unrecognized stored value (e.g. from a future
 * app version, or a manually edited datastore file) falls back to that field's default
 * instead of crashing.
 */
class AppearancePreferences(private val context: Context) {

    private val dataStore = context.appearanceDataStore

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_ARGB = intPreferencesKey("accent_argb")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val APP_FONT = stringPreferencesKey("app_font")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val DENSITY = stringPreferencesKey("density")
        val CARD_CORNER = stringPreferencesKey("card_corner")
        val CATALOG_LAYOUT = stringPreferencesKey("catalog_layout")
    }

    val settings: Flow<AppearanceSettings> = dataStore.data.map { it.toAppearanceSettings() }

    /**
     * Reads the current settings, applies [transform], and persists the result.
     */
    suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toAppearanceSettings())
            prefs[THEME_MODE] = next.themeMode.name
            prefs[ACCENT_ARGB] = next.accentArgb
            prefs[BACKGROUND_STYLE] = next.background.name
            prefs[APP_FONT] = next.font.name
            prefs[FONT_SCALE] = next.fontScale.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)
            prefs[DENSITY] = next.density.name
            prefs[CARD_CORNER] = next.cardCorner.name
            prefs[CATALOG_LAYOUT] = next.catalogLayout.name
        }
    }

    /**
     * Clears all stored keys, reverting to [AppearanceSettings] defaults.
     */
    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.remove(THEME_MODE)
            prefs.remove(ACCENT_ARGB)
            prefs.remove(BACKGROUND_STYLE)
            prefs.remove(APP_FONT)
            prefs.remove(FONT_SCALE)
            prefs.remove(DENSITY)
            prefs.remove(CARD_CORNER)
            prefs.remove(CATALOG_LAYOUT)
        }
    }

    private fun Preferences.toAppearanceSettings(): AppearanceSettings {
        val defaults = AppearanceSettings()
        return AppearanceSettings(
            themeMode = this[THEME_MODE].toEnumOrDefault(defaults.themeMode),
            accentArgb = this[ACCENT_ARGB] ?: defaults.accentArgb,
            background = this[BACKGROUND_STYLE].toEnumOrDefault(defaults.background),
            font = this[APP_FONT].toEnumOrDefault(defaults.font),
            fontScale = (this[FONT_SCALE] ?: defaults.fontScale).coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX),
            density = this[DENSITY].toEnumOrDefault(defaults.density),
            cardCorner = this[CARD_CORNER].toEnumOrDefault(defaults.cardCorner),
            catalogLayout = this[CATALOG_LAYOUT].toEnumOrDefault(defaults.catalogLayout),
        )
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T {
    if (this == null) return default
    return runCatching { enumValueOf<T>(this) }.getOrDefault(default)
}
