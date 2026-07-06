package com.vrhub.ui.theme

/**
 * How the app resolves light vs dark colors.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Bundled font families. ROUNDED/CONDENSED are deferred to a follow-up: they need
 * licensed .ttf assets bundled under res/font/, which this change does not add.
 */
enum class AppFont { SYSTEM, SERIF, MONO }

/**
 * Background treatment applied under the accent color.
 */
enum class BackgroundStyle { TRUE_BLACK, SLATE, ACCENT_TINTED }

/**
 * Vertical spacing/padding scale for list items.
 */
enum class Density { COMFORTABLE, COMPACT }

/**
 * Corner radius preset for cards and surfaces.
 */
enum class CardCorner { SHARP, ROUNDED, EXTRA_ROUNDED }

/**
 * Forces a specific catalog layout, or lets the existing width-based heuristic decide.
 */
enum class CatalogLayout { AUTO, LIST, GRID }

const val FONT_SCALE_MIN = 0.85f
const val FONT_SCALE_MAX = 1.30f

/**
 * User-configurable appearance settings. Defaults reproduce the app's original,
 * hardcoded look exactly, so an unconfigured install and "Reset appearance" both
 * render identically to before this feature existed.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentArgb: Int = 0xFF00B2FF.toInt(), // Meta Blue, the original hardcoded accent
    val background: BackgroundStyle = BackgroundStyle.TRUE_BLACK,
    val font: AppFont = AppFont.SYSTEM,
    val fontScale: Float = 1.0f,
    val density: Density = Density.COMFORTABLE,
    val cardCorner: CardCorner = CardCorner.ROUNDED,
    val catalogLayout: CatalogLayout = CatalogLayout.AUTO,
)

/**
 * Accent color presets offered to the user, in display order.
 */
val ACCENT_PRESETS: List<Pair<String, Int>> = listOf(
    "Meta Blue" to 0xFF00B2FF.toInt(),
    "Violet" to 0xFF8B5CF6.toInt(),
    "Emerald" to 0xFF10B981.toInt(),
    "Amber" to 0xFFF59E0B.toInt(),
    "Pink" to 0xFFEC4899.toInt(),
    "Cyan" to 0xFF06B6D4.toInt(),
    "Red" to 0xFFEF4444.toInt(),
)
