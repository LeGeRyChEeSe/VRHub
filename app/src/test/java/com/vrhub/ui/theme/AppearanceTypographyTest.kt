package com.vrhub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceTypographyTest {

    private val base = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 32.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    )

    @Test
    fun `appFontFamily maps every AppFont to a distinct family`() {
        assertEquals(FontFamily.Default, appFontFamily(AppFont.SYSTEM))
        assertEquals(FontFamily.Serif, appFontFamily(AppFont.SERIF))
        assertEquals(FontFamily.Monospace, appFontFamily(AppFont.MONO))
    }

    @Test
    fun `scale 1x and Default family reproduces base sizes exactly`() {
        val result = buildTypography(base, FontFamily.Default, 1.0f)
        assertEquals(base.headlineLarge.fontSize, result.headlineLarge.fontSize)
        assertEquals(base.bodyLarge.fontSize, result.bodyLarge.fontSize)
    }

    @Test
    fun `family is applied to every style`() {
        val result = buildTypography(base, FontFamily.Serif, 1.0f)
        assertEquals(FontFamily.Serif, result.headlineLarge.fontFamily)
        assertEquals(FontFamily.Serif, result.bodyLarge.fontFamily)
        assertEquals(FontFamily.Serif, result.labelSmall.fontFamily)
    }

    @Test
    fun `scale multiplies every font size`() {
        val result = buildTypography(base, FontFamily.Default, 1.2f)
        assertEquals(base.headlineLarge.fontSize * 1.2f, result.headlineLarge.fontSize)
        assertEquals(base.bodyLarge.fontSize * 1.2f, result.bodyLarge.fontSize)
    }
}
