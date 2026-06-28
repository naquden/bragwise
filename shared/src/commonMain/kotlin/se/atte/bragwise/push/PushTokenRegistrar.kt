package se.atte.bragwise.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.PushTokenRemote

/**
 * Registers fresh FCM/APNs tokens with the `registerPushToken` callable.
 *
 * Re-registers whenever either the token or the signed-in identity changes.
 * The callable requires auth, so calls are gated on [AuthState.SignedIn] —
 * a token that arrives before sign-in is queued in [PushNotifications.tokens]
 * (replay=1) and delivered once the user authenticates.
 */
class PushTokenRegistrar(
    private val push: PushNotifications,
    private val auth: AuthRepository,
    private val pushRemote: PushTokenRemote,
) {
    fun start(scope: CoroutineScope) {
        combine(
            push.tokens,
            auth.authState.filterIsInstance<AuthState.SignedIn>(),
        ) { token, _ -> token }
            .distinctUntilChanged()
            .onEach { token ->
                runCatching {
                    pushRemote.registerPushToken(
                        token = token.value,
                        platform = when (token.platform) {
                            PushPlatform.FCM -> "fcm"
                            PushPlatform.APNS -> "apns"
                        },
                    )
                }
            }
            .launchIn(scope)
    }
}
