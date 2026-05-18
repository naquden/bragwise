package se.atte.bragwise.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Vibrant palette inspired by Duolingo UX reference (plan §phase-1.5).
 * Primary = sky-blue (selection highlight), tertiary = leaf-green (CTA),
 * secondary = streak-yellow (points), error = coral-red (losing).
 * Dynamic color (Android 12+) intentionally OFF — the brand palette wins.
 */
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1CB0F6),          // sky-blue — selection, chips, links
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EFFE),
    onPrimaryContainer = Color(0xFF003A52),
    secondary = Color(0xFFFFC800),         // streak-yellow — points pill, badges
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = Color(0xFFFFEA80),
    onSecondaryContainer = Color(0xFF3A2E00),
    tertiary = Color(0xFF58CC02),          // leaf-green — primary CTA button
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F5A8),
    onTertiaryContainer = Color(0xFF1A4200),
    error = Color(0xFFFF4B4B),             // coral-red — losing, error states
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5C0000),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFE5E5E5),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF74D0FF),
    onPrimary = Color(0xFF003A52),
    primaryContainer = Color(0xFF005478),
    onPrimaryContainer = Color(0xFFD0EFFE),
    secondary = Color(0xFFFFD83D),
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = Color(0xFF574400),
    onSecondaryContainer = Color(0xFFFFEA80),
    tertiary = Color(0xFF7EE03A),
    onTertiary = Color(0xFF1A4200),
    tertiaryContainer = Color(0xFF265E00),
    onTertiaryContainer = Color(0xFFD4F5A8),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF5C0000),
    errorContainer = Color(0xFF8B1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF282C31),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF2E2E2E),
)
