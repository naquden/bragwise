package se.atte.bragwise

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point — invoked from Swift's `ContentView` via the
 * `UIViewControllerRepresentable` bridge. Uses [getOrInitAppDeps] so the
 * same `AppDeps` instance is shared with the Universal Links handler
 * installed in `iOSApp.swift`.
 */
fun MainViewController() = ComposeUIViewController {
    App(deps = getOrInitAppDeps())
}
