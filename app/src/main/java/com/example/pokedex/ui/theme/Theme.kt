/* Application Material theme and system-bar policy. / 应用 Material 主题与系统栏策略。 */
package com.example.pokedex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private fun colorScheme(palette: ThemePalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = palette.panel,
    primaryContainer = palette.primaryDark,
    onPrimaryContainer = palette.panel,
    secondary = palette.secondary,
    onSecondary = palette.panel,
    tertiary = palette.signal,
    background = palette.canvas,
    onBackground = palette.panel,
    surface = palette.panel,
    onSurface = palette.outline,
    outline = palette.outline,
    error = ScannerError,
)

@Composable
fun PokedexTheme(
    palette: ThemePalette = ThemeCatalog.byId("default"),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorScheme(palette),
        typography = Typography,
        content = content
    )
}
