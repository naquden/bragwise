package se.atte.bragwise

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import se.atte.bragwise.data.AuthRepository

/**
 * Bridge from Swift into Kotlin's [AuthRepository] for things that have to
 * happen outside a Compose view body — chiefly inbound Universal Links
 * carrying a Firebase email sign-in URL. Mirrors the work Android's
 * `MainActivity` does via `by inject()`.
 *
 * [handleSignInLinkFromIos] is safe to call before Compose is displayed;
 * Koin is started in `iOSApp.init()` before any Composable is hosted.
 */
private object IosAuthBridge : KoinComponent {
    val auth: AuthRepository by inject()
}

private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

fun handleSignInLinkFromIos(url: String) {
    val auth = IosAuthBridge.auth
    if (!auth.isSignInLink(url)) return
    bridgeScope.launch { auth.completeSignInWithLink(url) }
}
