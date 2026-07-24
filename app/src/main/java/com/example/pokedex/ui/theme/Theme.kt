/* Application Material theme and system-bar policy. / 应用 Material 主题与系统栏策略。 */
package com.example.pokedex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ScannerColorScheme = lightColorScheme(
    primary = ScannerRed,
    onPrimary = ScannerPanel,
    primaryContainer = ScannerRedDark,
    onPrimaryContainer = ScannerPanel,
    secondary = ScannerLensBlue,
    onSecondary = ScannerPanel,
    tertiary = ScannerSignalGreen,
    background = ScannerCanvas,
    onBackground = ScannerPanel,
    surface = ScannerPanel,
    onSurface = ScannerOutline,
    outline = ScannerOutline,
    error = ScannerError,
)

@Composable
fun PokedexTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ScannerColorScheme,
        typography = Typography,
        content = content
    )
}
