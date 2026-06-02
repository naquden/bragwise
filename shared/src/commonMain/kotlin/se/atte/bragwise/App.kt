package se.atte.bragwise

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import se.atte.bragwise.data.ActivityRegistrar
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.push.PushTokenRegistrar
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.theme.ThemeMode
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
