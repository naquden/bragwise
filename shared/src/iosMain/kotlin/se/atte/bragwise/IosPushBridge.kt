package se.atte.bragwise

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.ui.nav.parseDeepLink

/**
 * Bridge from the Swift `AppDelegate` into Kotlin's [PushNotifications] /
 * [AuthRepository]. Mirrors the work Android's `MainActivity` +
 * `BragwiseFirebaseMessagingService` do via `by inject()`.
 *
 * Koin is started in `iOSApp.init()` before the delegate fires, so these are
 * safe to call from APNs/FCM callbacks.
 */
private object IosPushBridge : KoinComponent {
    val push: PushNotifications by inject()
    val auth: AuthRepository by inject()
}

private val pushBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private val TRUSTED_HOSTS = setOf("bragwise.firebaseapp.com", "bragwise.app")

/** Swift `MessagingDelegate` forwards the FCM registration token here. */
fun handlePushTokenFromIos(token: String) {
    IosPushBridge.push.onNewToken(token)
}

/**
 * Swift forwards the `deepLink` from a tapped notification's userInfo here.
 * Only trusted https hosts with a parseable path are accepted — mirrors
 * Android's `MainActivity.handleDeepLink` + the messaging service's host check.
 */
fun handlePushDeepLinkFromIos(url: String) {
    val host = hostOf(url) ?: return
    if (host !in TRUSTED_HOSTS) return
    if (parseDeepLink(url) == null) return
    IosPushBridge.push.onIncomingDeepLink(url)
}

/**
 * Prompts for notification permission on the first `SignedIn` transition —
 * guests never register a token, so the prompt is deferred. Mirrors Android's
 * `MainActivity.requestNotificationsOnFirstSignIn`. Idempotent to call on
 * every launch; collects exactly one sign-in then stops.
 *
 * On grant it invokes [onGranted] — the Swift side calls
 * `UIApplication.registerForRemoteNotifications()` there (UIKit main-thread API
 * that doesn't resolve cleanly from Kotlin/Native), which kicks off the
 * APNs → FCM → [handlePushTokenFromIos] chain.
 */
fun requestPushPermissionOnFirstSignInFromIos(onGranted: () -> Unit) {
    pushBridgeScope.launch {
        IosPushBridge.auth.authState
            .filterIsInstance<AuthState.SignedIn>()
            .take(1)
            .collect {
                if (IosPushBridge.push.requestPermission()) {
                    onGranted()
                }
            }
    }
}

private fun hostOf(url: String): String? {
    if (!url.startsWith("https://")) return null
    val afterScheme = url.substring("https://".length)
    val end = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val host = if (end < 0) afterScheme else afterScheme.substring(0, end)
    return host.ifEmpty { null }
}
