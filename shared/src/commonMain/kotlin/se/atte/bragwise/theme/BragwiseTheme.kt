package se.atte.bragwise.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun BragwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    val sectionColors = if (darkTheme) DarkSectionColors else LightSectionColors
    CompositionLocalProvider(LocalSectionColors provides sectionColors) {
        MaterialTheme(
            colorScheme = colors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

@Composable
fun ThemePreview(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    BragwiseTheme(darkTheme = darkTheme) {
        Surface {
            content()
        }
    }
}