package se.atte.bragwise

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.atte.bragwise.ui.nav.AppDeps

/**
 * Bridge from Swift into Kotlin's [AppDeps] for things that have to happen
 * outside a Compose view body — chiefly inbound Universal Links carrying a
 * Firebase email sign-in URL. Mirrors the work Android's `MainActivity` does
 * directly in `onCreate` / `onNewIntent`.
 *
 * Both [getOrInitAppDeps] and [handleSignInLinkFromIos] are idempotent and
 * race-safe enough for the single-process iOS lifecycle: the first caller
 * (whichever of `MainViewController` or the `.onContinueUserActivity`
 * modifier wins) constructs the singleton `AppDeps`; subsequent calls reuse
 * it.
 */

private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

@Volatile
private var deps: AppDeps? = null

/**
 * Idempotent singleton accessor for the process-wide `AppDeps`. Exposed to
 * Swift as `IosAuthBridgeKt.getOrInitAppDeps()`. Called from
 * [MainViewController] so that the Compose root and the deep-link handler
 * share the same instance.
 *
 * App Check must already be installed (Swift side: `iOSApp.init()`) before
 * this runs, otherwise the first FirebaseAuth call will go out without a
 * token.
 */
fun getOrInitAppDeps(): AppDeps =
    deps ?: AppDeps.stub().also { deps = it }

/**
 * Hand a URL to AuthRepository if it looks like a Firebase email sign-in
 * link. No-op otherwise. Mirrors `MainActivity.handleAuthLink` on Android.
 */
fun handleSignInLinkFromIos(url: String) {
    val current = getOrInitAppDeps()
    if (!current.auth.isSignInLink(url)) return
    bridgeScope.launch { current.auth.completeSignInWithLink(url) }
}
