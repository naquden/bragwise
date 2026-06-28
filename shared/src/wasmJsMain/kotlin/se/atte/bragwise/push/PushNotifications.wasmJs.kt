package se.atte.bragwise.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Web push channel. No FCM on wasmJs; permission always returns false.
 * Deep-links can be seeded from webApp main.kt via [seedDeepLink].
 */
actual class PushNotifications {
    private val _tokens = MutableSharedFlow<PushToken>(replay = 1)
    private val _incomingDeepLinks = MutableSharedFlow<String>(replay = 1)

    actual val tokens: SharedFlow<PushToken> = _tokens.asSharedFlow()
    actual val incomingDeepLinks: SharedFlow<String> = _incomingDeepLinks.asSharedFlow()

    actual suspend fun requestPermission(): Boolean = false

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    actual fun markDeepLinkConsumed() {
        _incomingDeepLinks.resetReplayCache()
    }

    /** Called by webApp main.kt to seed a URL-bar deep-link on startup. */
    fun seedDeepLink(url: String) {
        _incomingDeepLinks.tryEmit(url)
    }
}
