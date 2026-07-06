package com.vrhub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Builds a Material3 [ColorScheme] from user [settings].
 *
 * With the default [AppearanceSettings] (dark, true black, Meta Blue accent), this
 * reproduces the app's original hardcoded VrpColorScheme exactly.
 */
fun buildColorScheme(settings: AppearanceSettings, systemDark: Boolean): ColorScheme {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val accent = Color(settings.accentArgb)

    return if (dark) {
        val background = when (settings.background) {
            BackgroundStyle.TRUE_BLACK -> Color(0xFF0A0A0A)
            BackgroundStyle.SLATE -> Color(0xFF12151A)
            BackgroundStyle.ACCENT_TINTED -> accent.copy(alpha = 0.08f).compositeOver(Color(0xFF0A0A0A))
        }
        val surface = when (settings.background) {
            BackgroundStyle.TRUE_BLACK -> Color(0xFF121212)
            BackgroundStyle.SLATE -> Color(0xFF1A1E24)
            BackgroundStyle.ACCENT_TINTED -> accent.copy(alpha = 0.10f).compositeOver(Color(0xFF121212))
        }
        darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF1E1E1E),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = Color.White,
            background = background,
            surface = surface,
            onBackground = Color(0xFFE1E1E1),
            onSurface = Color(0xFFE1E1E1),
            error = Color(0xFFFF5252),
            outline = Color(0xFF333333),
        )
    } else {
        val background = when (settings.background) {
            BackgroundStyle.TRUE_BLACK -> Color(0xFFF7F7F7)
            BackgroundStyle.SLATE -> Color(0xFFECEFF3)
            BackgroundStyle.ACCENT_TINTED -> accent.copy(alpha = 0.06f).compositeOver(Color.White)
        }
        val surface = when (settings.background) {
            BackgroundStyle.TRUE_BLACK -> Color.White
            BackgroundStyle.SLATE -> Color(0xFFF3F5F8)
            BackgroundStyle.ACCENT_TINTED -> accent.copy(alpha = 0.08f).compositeOver(Color.White)
        }
        lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E0E0),
            onPrimaryContainer = Color.Black,
            secondary = accent,
            onSecondary = Color.White,
            background = background,
            surface = surface,
            onBackground = Color(0xFF1A1A1A),
            onSurface = Color(0xFF1A1A1A),
            error = Color(0xFFB00020),
            outline = Color(0xFFCCCCCC),
        )
    }
}
