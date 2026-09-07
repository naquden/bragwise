package se.atte.bragwise

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point — invoked from Swift's `ContentView` via the
 * `UIViewControllerRepresentable` bridge. Koin is already started by
 * `iOSApp.init()` before this is called, so `koinViewModel` / `koinInject`
 * in the Compose tree resolve immediately.
 */
fun MainViewController() = ComposeUIViewController(
    configure = {
        // Stop UIKit from panning the whole scene to keep the focused rect above the
        // keyboard — Compose window insets (imePadding on the root Scaffold in AppNav)
        // are the single source of truth instead. See ComposeSceneMediator.ios.kt:621.
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) { App() }
