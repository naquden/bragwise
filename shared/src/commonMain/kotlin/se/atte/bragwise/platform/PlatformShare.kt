package se.atte.bragwise.platform

/**
 * OS share-sheet bridge. Android = `Intent.ACTION_SEND` wrapped in
 * `Intent.createChooser`; iOS = `UIActivityViewController`. Consumed at the
 * screen-composable layer (not the ViewModel) — VMs emit a typed
 * `ShareLink(url, message)` effect, the screen resolves the message to
 * plain title/subject strings via Compose Resources, then calls `send(...)`.
 *
 * Platform implementations are provided via Koin's platformModule:
 * - Android: [AndroidPlatformShare] using androidContext()
 * - iOS: [IosPlatformShare] using UIKit directly
 */
interface PlatformShare {
    fun send(url: String, title: String, subject: String)
}
