package se.atte.bragwise.push

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState

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
    private val functions: FirebaseFunctions = Firebase.functions("europe-west1"),
) {
    fun start(scope: CoroutineScope) {
        combine(
            push.tokens,
            auth.authState.filterIsInstance<AuthState.SignedIn>(),
        ) { token, _ -> token }
            .distinctUntilChanged()
            .onEach { token ->
                runCatching {
                    functions
                        .httpsCallable("registerPushToken")
                        .invoke(
                            RegisterPushTokenPayload(
                                token = token.value,
                                platform = when (token.platform) {
                                    PushPlatform.FCM -> "fcm"
                                    PushPlatform.APNS -> "apns"
                                },
                            ),
                        )
                }
            }
            .launchIn(scope)
    }
}

@Serializable
private data class RegisterPushTokenPayload(val token: String, val platform: String)
