package com.vrhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrhub.ui.theme.ACCENT_PRESETS
import com.vrhub.ui.theme.AppFont
import com.vrhub.ui.theme.AppearanceSettings
import com.vrhub.ui.theme.CardCorner
import com.vrhub.ui.theme.CatalogLayout
import com.vrhub.ui.theme.Density
import com.vrhub.ui.theme.FONT_SCALE_MAX
import com.vrhub.ui.theme.FONT_SCALE_MIN
import com.vrhub.ui.theme.appFontFamily
import com.vrhub.ui.theme.cardCornerShape
import java.util.Locale

/**
 * Settings screen for accent color, font family and text size.
 *
 * Theme mode (light/system), background style, density, card corner radius and
 * catalog layout are deferred: exposing them now would be visibly broken, since
 * large parts of the UI still read hardcoded colors instead of MaterialTheme
 * (see docs/sonnet5-specs/02-feature-appearance-customization.md §6/§10). They're
 * wired in once the hardcoded-color migration lots land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var showCustomColorPicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppearancePreviewCard(settings)

            Spacer(Modifier.height(24.dp))
            SectionTitle("Accent Color")
            AccentPicker(
                settings = settings,
                onAccentSelected = { argb -> viewModel.update { it.copy(accentArgb = argb) } },
                onCustomClick = { showCustomColorPicker = true }
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("Font")
            FontPicker(
                settings = settings,
                onFontSelected = { font -> viewModel.update { it.copy(font = font) } }
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Text Size")
            FontScaleSlider(
                fontScale = settings.fontScale,
                onFontScaleChange = { scale -> viewModel.update { it.copy(fontScale = scale) } }
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("Density")
            DensityPicker(
                settings = settings,
                onDensitySelected = { density -> viewModel.update { it.copy(density = density) } }
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Card Corners")
            CardCornerPicker(
                settings = settings,
                onCardCornerSelected = { corner -> viewModel.update { it.copy(cardCorner = corner) } }
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Catalog View")
            CatalogLayoutPicker(
                settings = settings,
                onCatalogLayoutSelected = { layout -> viewModel.update { it.copy(catalogLayout = layout) } }
            )

            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Appearance")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCustomColorPicker) {
        CustomColorPickerDialog(
            initialArgb = settings.accentArgb,
            onDismiss = { showCustomColorPicker = false },
            onConfirm = { argb ->
                viewModel.update { it.copy(accentArgb = argb) }
                showCustomColorPicker = false
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Appearance?") },
            text = { Text("This restores the default look: dark theme, Meta Blue accent, system font, 1.0x text size.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * Small mock game entry + install button reflecting the accent/font/scale picks
 * in real time, without depending on the real GameListItem (queue state, Coil
 * images, callbacks) which would drag in unrelated data plumbing.
 */
@Composable
private fun AppearancePreviewCard(settings: AppearanceSettings) {
    val family = appFontFamily(settings.font)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(settings.accentArgb))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Sample Game",
                    fontFamily = family,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * settings.fontScale,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "com.example.samplegame",
                    fontFamily = family,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * settings.fontScale,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(settings.accentArgb))
            ) {
                Text("Install", fontFamily = family, color = Color.White)
            }
        }
    }
}

@Composable
private fun AccentPicker(
    settings: AppearanceSettings,
    onAccentSelected: (Int) -> Unit,
    onCustomClick: () -> Unit
) {
    val isCustom = ACCENT_PRESETS.none { it.second == settings.accentArgb }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ACCENT_PRESETS) { (name, argb) ->
            AccentSwatch(
                color = Color(argb),
                selected = !isCustom && settings.accentArgb == argb,
                onClick = { onAccentSelected(argb) },
                label = name
            )
        }
        item {
            AccentSwatch(
                color = if (isCustom) Color(settings.accentArgb) else Color(0xFF888888),
                selected = isCustom,
                onClick = onCustomClick,
                label = "Custom",
                showAddIcon = !isCustom
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    showAddIcon: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            when {
                showAddIcon -> Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                selected -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FontPicker(settings: AppearanceSettings, onFontSelected: (AppFont) -> Unit) {
    Column {
        AppFont.entries.forEach { font ->
            val label = when (font) {
                AppFont.SYSTEM -> "System (Default)"
                AppFont.SERIF -> "Serif"
                AppFont.MONO -> "Monospace"
            }
            ListItem(
                headlineContent = { Text(label, fontFamily = appFontFamily(font)) },
                trailingContent = {
                    if (settings.font == font) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onFontSelected(font) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DensityPicker(settings: AppearanceSettings, onDensitySelected: (Density) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Density.entries.forEach { density ->
            val label = when (density) {
                Density.COMFORTABLE -> "Comfortable"
                Density.COMPACT -> "Compact"
            }
            FilterChip(
                selected = settings.density == density,
                onClick = { onDensitySelected(density) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardCornerPicker(settings: AppearanceSettings, onCardCornerSelected: (CardCorner) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardCorner.entries.forEach { corner ->
            val label = when (corner) {
                CardCorner.SHARP -> "Sharp"
                CardCorner.ROUNDED -> "Rounded"
                CardCorner.EXTRA_ROUNDED -> "Extra Rounded"
            }
            FilterChip(
                selected = settings.cardCorner == corner,
                onClick = { onCardCornerSelected(corner) },
                label = { Text(label) },
                shape = cardCornerShape(corner)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogLayoutPicker(settings: AppearanceSettings, onCatalogLayoutSelected: (CatalogLayout) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CatalogLayout.entries.forEach { layout ->
            val label = when (layout) {
                CatalogLayout.AUTO -> "Auto"
                CatalogLayout.LIST -> "List"
                CatalogLayout.GRID -> "Grid"
            }
            FilterChip(
                selected = settings.catalogLayout == layout,
                onClick = { onCatalogLayoutSelected(layout) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun FontScaleSlider(fontScale: Float, onFontScaleChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = fontScale,
            onValueChange = onFontScaleChange,
            valueRange = FONT_SCALE_MIN..FONT_SCALE_MAX,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Aa",
            fontSize = 20.sp * fontScale,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialHsv = remember(initialArgb) { colorToHsv(Color(initialArgb)) }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(colorToHex(Color(initialArgb))) }

    val currentColor = remember(hue, saturation, value) { hsvToColor(hue, saturation, value) }

    fun syncFromHsv() {
        hexInput = colorToHex(hsvToColor(hue, saturation, value))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Accent Color") },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                syncFromHsv()
                            }
                        }
                        .pointerInput(hue) {
                            detectTapGestures { offset ->
                                saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                syncFromHsv()
                            }
                        }
                ) {
                    SaturationValueCanvas(hue = hue, saturation = saturation, value = value)
                }

                Spacer(Modifier.height(16.dp))
                Text("Hue", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        syncFromHsv()
                    },
                    valueRange = 0f..360f
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { text ->
                        hexInput = text
                        parseHexColor(text)?.let { parsed ->
                            val hsv = colorToHsv(parsed)
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                        }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentColor)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor.toArgb()) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SaturationValueCanvas(hue: Float, saturation: Float, value: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val hueColor = hsvToColor(hue, 1f, 1f)
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val markerX = saturation * size.width
        val markerY = (1f - value) * size.height
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = Offset(markerX, markerY),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
        hsv
    )
    return hsv
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

private fun colorToHex(color: Color): String =
    String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)

private fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 || cleaned.any { it.digitToIntOrNull(16) == null }) return null
    return try {
        Color((0xFF000000 or cleaned.toLong(16)).toInt())
    } catch (e: NumberFormatException) {
        null
    }
}
