/* Central Pokédex color tokens. / 集中的宝可梦图鉴配色令牌。 */
package com.example.pokedex.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

val DefaultScannerRed = Color(0xFFE73F49)
val DefaultScannerRedDark = Color(0xFFB82432)
val DefaultScannerRedLight = Color(0xFFF36A70)
val DefaultScannerOutline = Color(0xFF17191D)
val DefaultScannerPanel = Color(0xFFF4F5F1)
val DefaultScannerCanvas = Color(0xFF202328)
val DefaultScannerLensBlue = Color(0xFF2F75B5)
val DefaultScannerSignalGreen = Color(0xFF4E8D69)

internal object ThemeRuntime { val palette: MutableState<ThemePalette> = mutableStateOf(ThemeCatalog.byId("default")) }
val ScannerRed get() = ThemeRuntime.palette.value.primary
val ScannerRedDark get() = ThemeRuntime.palette.value.primaryDark
val ScannerRedLight get() = ThemeRuntime.palette.value.primaryLight
val ScannerOutline get() = ThemeRuntime.palette.value.outline
val ScannerBorder get() = ThemeRuntime.palette.value.border
val ScannerGraphite get() = ThemeRuntime.palette.value.canvas.copy(red = 0.20f, green = 0.21f, blue = 0.23f)
val ScannerGraphiteLight get() = ThemeRuntime.palette.value.outline.copy(alpha = 0.72f)
val ScannerPanel get() = ThemeRuntime.palette.value.panel
val ScannerCanvas get() = ThemeRuntime.palette.value.canvas
val ScannerLensBlue get() = ThemeRuntime.palette.value.secondary
val ScannerLensHighlight get() = ThemeRuntime.palette.value.secondary.copy(alpha = 0.35f)
val ScannerSignalGreen get() = ThemeRuntime.palette.value.signal
val ScannerSuccess get() = Color(0xFF68D391)
val ScannerWarning get() = Color(0xFFF5C451)
val ScannerError get() = Color(0xFFFF6B6B)
