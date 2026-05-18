package se.atte.bragwise.mvi

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
    data class Unknown(val message: String? = null) : Cause
}

fun Throwable.toCause(): Cause = when {
    this is kotlin.coroutines.cancellation.CancellationException -> throw this
    message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> Cause.Auth
    message?.contains("UNAVAILABLE", ignoreCase = true) == true ||
        message?.contains("network", ignoreCase = true) == true -> Cause.Network
    else -> Cause.Unknown(message)
}
