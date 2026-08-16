package com.example.pokedex.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ThemePalette(
    val id: String, val name: String, val primary: Color, val primaryDark: Color, val primaryLight: Color,
    val canvas: Color, val panel: Color, val outline: Color, val secondary: Color, val signal: Color,
    val border: Color = outline,
)

object ThemeCatalog {
    val themes = listOf(
        ThemePalette("default", "默认", DefaultScannerRed, DefaultScannerRedDark, DefaultScannerRedLight, DefaultScannerCanvas, DefaultScannerPanel, DefaultScannerOutline, DefaultScannerLensBlue, DefaultScannerSignalGreen),
        ThemePalette("great", "超级球", Color(0xFF2978C8), Color(0xFF174D86), Color(0xFF62A9E8), Color(0xFF152536), DefaultScannerPanel, Color(0xFF14202D), Color(0xFFE84B3C), Color(0xFF50B99A)),
        ThemePalette("ultra", "高级球", Color(0xFF24272C), Color(0xFF080A0C), Color(0xFFF5D24E), Color(0xFF18191B), Color(0xFFFFF8E5), Color(0xFF151515), Color(0xFFF5D24E), Color(0xFFE6B93C)),
        ThemePalette("luxury", "豪华球", Color(0xFF221F28), Color(0xFF0C0A0F), Color(0xFFC9A04E), Color(0xFF171319), Color(0xFFFFF8EC), Color(0xFF2A2027), Color(0xFFC9A04E), Color(0xFFBD8D38), border = Color(0xFFC9A04E)),
        ThemePalette("dream", "梦境球", Color(0xFFCA619F), Color(0xFF79345F), Color(0xFFE8A5CC), Color(0xFF2E1C38), Color(0xFFFFF4FB), Color(0xFF3D2347), Color(0xFF7454C8), Color(0xFFB968B0)),
        ThemePalette("quick", "先机球", Color(0xFFF0A521), Color(0xFF9A5A09), Color(0xFFFFD766), Color(0xFF211B12), Color(0xFFFFFAED), Color(0xFF332511), Color(0xFF3A87D8), Color(0xFFDD7D20)),
        ThemePalette("master", "大师球", Color(0xFF7651A7), Color(0xFF39215D), Color(0xFFB79AE3), Color(0xFF1D1626), Color(0xFFF9F3FF), Color(0xFF2E1C41), Color(0xFFE1B847), Color(0xFFB88AE7)),
        ThemePalette("beast", "究极球", Color(0xFF203B8A), Color(0xFF111B4D), Color(0xFF55D8F0), Color(0xFF11142A), Color(0xFFF1F7FF), Color(0xFF17255A), Color(0xFFFFC72D), Color(0xFF4BD2EA)),
        ThemePalette("moon", "月亮球", Color(0xFF3B78BD), Color(0xFF1B3F78), Color(0xFF93C3EE), Color(0xFF152132), Color(0xFFF5FAFF), Color(0xFF1E3D67), Color(0xFFF1C94E), Color(0xFF7EACE3)),
        ThemePalette("cherish", "贵重球", Color(0xFFB63A3C), Color(0xFF641D22), Color(0xFFF0A15B), Color(0xFF261719), Color(0xFFFFF6EE), Color(0xFF4A2223), Color(0xFFE3B74B), Color(0xFFD05B4D)),
    )
    fun byId(id: String) = themes.firstOrNull { it.id == id } ?: themes.first()
}

val activeThemePalette: ThemePalette get() = ThemeRuntime.palette.value

fun ThemePalette.controlForeground(): Color =
    if (canvas.luminance() > 0.45f) Color(0xFF121417) else Color(0xFFF8FAF7)

class ThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    private val _palette = MutableStateFlow(ThemeCatalog.byId(preferences.getString("selected_theme", "default") ?: "default"))
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()
    init { ThemeRuntime.palette.value = _palette.value }
    fun select(id: String) { _palette.value = ThemeCatalog.byId(id); ThemeRuntime.palette.value = _palette.value; preferences.edit().putString("selected_theme", id).apply() }
}
