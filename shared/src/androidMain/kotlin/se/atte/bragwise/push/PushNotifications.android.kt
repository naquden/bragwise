package se.atte.bragwise.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android push channel. The `BragwiseFirebaseMessagingService` (in
 * `androidApp/`) calls [onNewToken] / [onIncomingDeepLink] from FCM
 * callbacks. [requestPermission] returns true unconditionally for now —
 * UI should request POST_NOTIFICATIONS at the call site (Android 13+).
 *
 * Both flows use replay=1 so that a cold-start token/deep-link arriving
 * before AppNav subscribes is not silently dropped.
 * tryEmit is correct here — these methods are called from Android system
 * callbacks without a coroutine scope. With replay=1, the latest value
 * is always held and delivered to any late subscriber.
 */
actual class PushNotifications {
    private val _tokens = MutableSharedFlow<PushToken>(replay = 1)
    private val _links = MutableSharedFlow<String>(replay = 1)

    actual val tokens: SharedFlow<PushToken> = _tokens.asSharedFlow()
    actual val incomingDeepLinks: SharedFlow<String> = _links.asSharedFlow()

    actual suspend fun requestPermission(): Boolean = true

    fun onNewToken(token: String) {
        _tokens.tryEmit(PushToken(value = token, platform = PushPlatform.FCM))
    }

    fun onIncomingDeepLink(url: String) {
        _links.tryEmit(url)
    }
}
