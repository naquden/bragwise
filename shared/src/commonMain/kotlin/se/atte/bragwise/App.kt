package se.atte.bragwise

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import se.atte.bragwise.data.ActivityRegistrar
import se.atte.bragwise.data.LanguagePrefs
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.push.PushTokenRegistrar
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.theme.ThemeMode
import se.atte.bragwise.theme.webFallbackFamilies
import se.atte.bragwise.ui.LocalAppLocale
import se.atte.bragwise.ui.nav.AppNav

@Composable
@Preview
fun App() {
    val themePrefs: ThemePrefs = koinInject()
    val languagePrefs: LanguagePrefs = koinInject()
    val pushTokenRegistrar: PushTokenRegistrar = koinInject()
    val activityRegistrar: ActivityRegistrar = koinInject()

    // App-lifetime scope: registrars gate on AuthState.SignedIn internally, so
    // this is a no-op for signed-out users. PushTokenRegistrar uploads FCM
    // tokens; ActivityRegistrar stamps lastSeen (guests included) for reaping.
    val appScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        pushTokenRegistrar.start(appScope)
        activityRegistrar.start(appScope)
    }

    // Preload web fallback fonts (emoji, Devanagari, Han) as Skia resolver fallbacks
    // so all Text nodes resolve glyphs without a per-Text fontFamily.  Empty on mobile.
    val fontFamilyResolver = LocalFontFamilyResolver.current
    var fontsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        webFallbackFamilies().forEach { fontFamilyResolver.preload(it) }
        fontsLoaded = true
    }

    val language by languagePrefs.language.collectAsState()

    val mode by themePrefs.mode.collectAsState()
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // Gate first render until web fallback fonts are registered to avoid first-paint tofu.
    // Fonts are bundled in the wasm module so preload is fast — no visible delay.
    if (!fontsLoaded) {
        Box {}
        return
    }

    CompositionLocalProvider(
        LocalAppLocale provides language.tag.ifEmpty { null },
    ) {
        BragwiseTheme(darkTheme = dark) {
            AppNav()
        }
    }
}
