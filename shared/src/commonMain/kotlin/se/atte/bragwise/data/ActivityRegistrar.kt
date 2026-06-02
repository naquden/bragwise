package se.atte.bragwise.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Pings the `recordActivity` callable whenever a (re)authenticated session
 * appears, stamping `lastSeen` on the player doc. Anonymous guests are
 * included on purpose — `lastSeen` + the anonymous flag are what let the
 * backend reap abandoned guest accounts after 90 days.
 *
 * Mirrors [se.atte.bragwise.push.PushTokenRegistrar]: started once at app
 * launch with an app-lifetime scope; gates internally on sign-in and wraps
 * the callable in `runCatching`, so it is inert for signed-out users and
 * under the mock repositories.
 */
class ActivityRegistrar(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
) {
    fun start(scope: CoroutineScope) {
        auth.authState
            .filterIsInstance<AuthState.SignedIn>()
            .map { it.uid }
            .distinctUntilChanged()
            .onEach { profile.recordActivity() }
            .launchIn(scope)
    }
}
