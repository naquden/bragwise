package se.atte.bragwise

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point — invoked from Swift's `ContentView` via the
 * `UIViewControllerRepresentable` bridge. Koin is already started by
 * `iOSApp.init()` before this is called, so `koinViewModel` / `koinInject`
 * in the Compose tree resolve immediately.
 */
fun MainViewController() = ComposeUIViewController { App() }
