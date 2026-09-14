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

/**
 * Bridge from the Swift `AppDelegate` into Kotlin's [PushNotifications] /
 * [AuthRepository]. Mirrors the work Android's `MainActivity` +
 * `BragwiseFirebaseMessagingService` do via `by inject()`.
 *
 * Koin is started in `iOSApp.init()` before the delegate fires, so these are
 * safe to call from APNs/FCM callbacks.
 *
 * Deep-link routing used to live here too; it moved to `IosLinkBridge.kt` once
 * it stopped being push-only (the generated Obj-C class name derives from the
 * file name, so a push-named file would have been misleading).
 */
private object IosPushBridge : KoinComponent {
    val push: PushNotifications by inject()
    val auth: AuthRepository by inject()
}

private val pushBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Swift `MessagingDelegate` forwards the FCM registration token here. */
fun handlePushTokenFromIos(token: String) {
    IosPushBridge.push.onNewToken(token)
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
