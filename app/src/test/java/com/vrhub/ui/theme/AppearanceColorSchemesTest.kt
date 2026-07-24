package com.vrhub.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppearanceColorSchemesTest {

    @Test
    fun `default settings reproduce the original dark scheme exactly`() {
        val scheme = buildColorScheme(AppearanceSettings(), systemDark = false)

        assertEquals(Color(0xFFFFFFFF), scheme.primary)
        assertEquals(Color(0xFF000000), scheme.onPrimary)
        assertEquals(Color(0xFF00B2FF), scheme.secondary)
        assertEquals(Color(0xFF0A0A0A), scheme.background)
        assertEquals(Color(0xFF121212), scheme.surface)
        assertEquals(Color(0xFFFF5252), scheme.error)
    }

    @Test
    fun `accent color maps to secondary`() {
        val violet = 0xFF8B5CF6.toInt()
        val scheme = buildColorScheme(AppearanceSettings(accentArgb = violet), systemDark = false)
        assertEquals(Color(violet), scheme.secondary)
    }

    @Test
    fun `theme mode DARK ignores systemDark and stays dark`() {
        val scheme = buildColorScheme(AppearanceSettings(themeMode = ThemeMode.DARK), systemDark = false)
        assertEquals(Color(0xFF0A0A0A), scheme.background)
    }

    @Test
    fun `theme mode LIGHT ignores systemDark and stays light`() {
        val scheme = buildColorScheme(AppearanceSettings(themeMode = ThemeMode.LIGHT), systemDark = true)
        assertEquals(Color(0xFFF7F7F7), scheme.background)
    }

    @Test
    fun `theme mode SYSTEM follows systemDark`() {
        val dark = buildColorScheme(AppearanceSettings(themeMode = ThemeMode.SYSTEM), systemDark = true)
        val light = buildColorScheme(AppearanceSettings(themeMode = ThemeMode.SYSTEM), systemDark = false)

        assertEquals(Color(0xFF0A0A0A), dark.background)
        assertEquals(Color(0xFFF7F7F7), light.background)
    }

    @Test
    fun `ACCENT_TINTED background differs from TRUE_BLACK for the same accent`() {
        val trueBlack = buildColorScheme(
            AppearanceSettings(background = BackgroundStyle.TRUE_BLACK),
            systemDark = false
        )
        val tinted = buildColorScheme(
            AppearanceSettings(background = BackgroundStyle.ACCENT_TINTED),
            systemDark = false
        )

        assertNotEquals(trueBlack.background, tinted.background)
    }
}
