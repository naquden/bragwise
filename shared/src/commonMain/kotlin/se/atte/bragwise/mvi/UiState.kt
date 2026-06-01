package se.atte.bragwise.mvi

import androidx.compose.runtime.Composable
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.error_auth
import bragwise.shared.generated.resources.error_challenge_cap
import bragwise.shared.generated.resources.error_email_unverified
import bragwise.shared.generated.resources.error_network
import bragwise.shared.generated.resources.error_rate_limited
import bragwise.shared.generated.resources.error_unknown
import org.jetbrains.compose.resources.stringResource

/**
 * Sealed envelope for screen-level state. Every screen handles all four.
 * Locked / Scored — the bet-level extra states from plan §4 — live in
 * screen-specific state types, not here.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Empty(val cta: String? = null) : UiState<Nothing>
    data class Failed(val cause: Cause) : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
}

/** Typed exception cause; mapped at the screen layer to a user-facing string. */
sealed interface Cause {
    data object Network : Cause
    data object RateLimited : Cause
    data object Auth : Cause
    data object EmailUnverified : Cause
    data object CapReached : Cause
    data class Unknown(val message: String? = null) : Cause

    @Composable
    fun toUserMessage(): String = when (this) {
        Auth -> stringResource(Res.string.error_auth)
        Network -> stringResource(Res.string.error_network)
        RateLimited -> stringResource(Res.string.error_rate_limited)
        EmailUnverified -> stringResource(Res.string.error_email_unverified)
        CapReached -> stringResource(Res.string.error_challenge_cap)
        is Unknown -> stringResource(Res.string.error_unknown)
    }
}

fun Throwable.toCause(): Cause = when {
    this is kotlin.coroutines.cancellation.CancellationException -> throw this
    message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> Cause.Auth
    message?.contains("resource-exhausted", ignoreCase = true) == true ||
        message?.contains("challenge-cap-reached", ignoreCase = true) == true -> Cause.CapReached
    message?.contains("UNAVAILABLE", ignoreCase = true) == true ||
        message?.contains("network", ignoreCase = true) == true -> Cause.Network
    else -> Cause.Unknown(message)
}
