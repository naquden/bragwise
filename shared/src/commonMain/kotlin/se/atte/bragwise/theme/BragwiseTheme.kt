package se.atte.bragwise.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The resolved dark/light state for the *applied* theme. Reads
 * `isSystemInDarkTheme()` by default, but `BragwiseTheme` overrides it with the
 * in-app [ThemeMode] choice. Components MUST read this instead of calling
 * `isSystemInDarkTheme()` directly — otherwise a manual Light/Dark override
 * diverges from the system setting (e.g. dark theme on a light-mode device).
 */
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun BragwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    val sectionColors = if (darkTheme) DarkSectionColors else LightSectionColors

    CompositionLocalProvider(
        LocalSectionColors provides sectionColors,
        LocalIsDark provides darkTheme,
    ) {
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
