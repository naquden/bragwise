package se.atte.bragwise

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.theme.ThemeMode
import se.atte.bragwise.ui.nav.AppNav

@Composable
@Preview
fun App() {
    val themePrefs: ThemePrefs = koinInject()
    val mode by themePrefs.mode.collectAsState()
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    BragwiseTheme(darkTheme = dark) {
        AppNav()
    }
}
