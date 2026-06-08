package se.atte.bragwise.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * iOS push channel. Swift app delegate forwards the APNs device token
 * via [onNewToken]; tap deep-links via [onIncomingDeepLink].
 *
 * [requestPermission] is a placeholder; a real implementation calls
 * `UNUserNotificationCenter.currentNotificationCenter()
 *  .requestAuthorizationWithOptions(...)` and bridges the callback.
 *
 * Both flows use replay=1 so that cold-start tokens/deep-links arriving
 * before AppNav subscribes are not silently dropped.
 */
actual class PushNotifications {
    private val _tokens = MutableSharedFlow<PushToken>(replay = 1)
    private val _links = MutableSharedFlow<String>(replay = 1)

    actual val tokens: SharedFlow<PushToken> = _tokens.asSharedFlow()
    actual val incomingDeepLinks: SharedFlow<String> = _links.asSharedFlow()

    actual suspend fun requestPermission(): Boolean = true

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    actual fun markDeepLinkConsumed() {
        _links.resetReplayCache()
    }

    fun onNewToken(token: String) {
        _tokens.tryEmit(PushToken(value = token, platform = PushPlatform.APNS))
    }

    fun onIncomingDeepLink(url: String) {
        _links.tryEmit(url)
    }
}
