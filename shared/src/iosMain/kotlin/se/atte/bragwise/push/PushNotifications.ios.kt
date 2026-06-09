package se.atte.bragwise.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS push channel. The Swift app delegate forwards the FCM registration
 * token via [onNewToken] and notification-tap deep-links via [onIncomingDeepLink].
 *
 * Both flows use replay=1 so that cold-start tokens/deep-links arriving
 * before AppNav subscribes are not silently dropped.
 */
actual class PushNotifications {
    private val _tokens = MutableSharedFlow<PushToken>(replay = 1)
    private val _links = MutableSharedFlow<String>(replay = 1)

    actual val tokens: SharedFlow<PushToken> = _tokens.asSharedFlow()
    actual val incomingDeepLinks: SharedFlow<String> = _links.asSharedFlow()

    /**
     * Prompts via `UNUserNotificationCenter` and returns the OS authorization
     * decision. The caller (Swift `AppDelegate`) registers with APNs on grant —
     * `UIApplication.registerForRemoteNotifications` is UIKit-main-thread API and
     * lives on the Swift side, where the APNs→FCM→[onNewToken] chain is wired.
     */
    actual suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionSound or
            UNAuthorizationOptionBadge
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { granted, _ ->
                cont.resume(granted)
            }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    actual fun markDeepLinkConsumed() {
        _links.resetReplayCache()
    }

    /**
     * Receives the **FCM registration token** (not the raw APNs token) from the
     * Swift `MessagingDelegate`. The server multicasts via FCM
     * (`messaging.sendEachForMulticast`, functions/src/push.ts:46), so iOS
     * registers an FCM token just like Android.
     */
    fun onNewToken(token: String) {
        _tokens.tryEmit(PushToken(value = token, platform = PushPlatform.FCM))
    }

    fun onIncomingDeepLink(url: String) {
        _links.tryEmit(url)
    }
}
