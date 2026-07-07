package com.vrhub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Maps a bundled [AppFont] to its [FontFamily].
 */
fun appFontFamily(font: AppFont): FontFamily = when (font) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONO -> FontFamily.Monospace
}

/**
 * Applies [family] and a [scale] factor (bounded to [FONT_SCALE_MIN]..[FONT_SCALE_MAX]
 * by the caller) to every style of [base].
 *
 * With family = FontFamily.Default and scale = 1.0 (the defaults), this reproduces
 * [base] exactly.
 */
fun buildTypography(base: Typography, family: FontFamily, scale: Float): Typography {
    fun TextStyle.scaled() = copy(fontFamily = family, fontSize = fontSize * scale)

    return base.copy(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}
