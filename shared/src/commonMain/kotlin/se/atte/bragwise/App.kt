package se.atte.bragwise

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.push.PushTokenRegistrar
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.theme.ThemeMode
import se.atte.bragwise.theme.emojiFallbackFamily
import se.atte.bragwise.ui.nav.AppNav

@Composable
@Preview
fun App() {
    val themePrefs: ThemePrefs = koinInject()
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

    // Register NotoColorEmoji as a resolver fallback so any Text containing
    // emoji codepoints resolves them — without setting a per-Text fontFamily.
    // On wasmJs the composable Font(Res.font.x) wrapper does not produce a
    // LoadedFont, so preload() was a no-op and emojis tofu'd.  emojiFallbackFamily()
    // uses the skiko-only Font(identity, ByteArray) constructor (LoadedFont) which
    // the Skia font cache actually registers.  On Android/iOS it returns null
    // (native emoji support; no custom fallback needed).
    val fontFamilyResolver = LocalFontFamilyResolver.current
    var fontsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val fam = emojiFallbackFamily()
        if (fam != null) fontFamilyResolver.preload(fam)
        fontsLoaded = true
    }

    val mode by themePrefs.mode.collectAsState()
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // Gate first render until emoji font is registered to avoid first-paint tofu.
    // The preload is fast (font bytes are bundled in the wasm module), so this
    // produces no visible delay.
    if (!fontsLoaded) {
        Box {}
        return
    }

    BragwiseTheme(darkTheme = dark) {
        AppNav()
    }
}
