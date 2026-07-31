package se.atte.bragwise.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

private const val MAX_DISPLAY_NAME = 40
private const val USERNAME_CANDIDATE_TRIES = 5

/**
 * Tri-state result of the name-readiness check.
 *
 * [Loading] — auth or the player doc is still resolving; the name gate must
 *             NOT show (we don't know yet whether the user has a name).
 * [Present] — resolved and the user has a non-blank display name; gate hidden.
 * [Absent]  — resolved and the user genuinely has no display name; show gate.
 */
sealed interface NameState {
    data object Loading : NameState
    data class Present(val name: String) : NameState
    data object Absent : NameState
}

/**
 * Lazily bootstraps a named player account the first time the user takes an
 * action that needs one (placing bets, creating a challenge).
 *
 * [nameState] is the authoritative tri-state signal. Consumers should observe
 * it reactively and only show the name gate when the value is [NameState.Absent].
 * [name] is a convenience projection (non-null only when [NameState.Present]).
 *
 * [ensure] is called with the user-chosen name. It:
 *  1. Creates an anonymous Firebase account if the user isn't signed in at all.
 *  2. Writes the display name via `updateProfile`.
 *  3. Auto-claims a username derived from the name (best-effort; failure is silent).
 *  4. Caches the name locally for prefill on next launch.
 */
class EnsureNamedAccount(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
    private val onboardingPrefs: OnboardingPrefs,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    val nameState: StateFlow<NameState> = auth.authState
        .flatMapLatest { authState ->
            when (authState) {
                is AuthState.Loading -> flowOf(NameState.Loading)
                is AuthState.SignedOut -> flowOf(NameState.Absent)
                is AuthState.SignedIn -> profile.observeMe()
                    .map { player ->
                        val cloudName = player?.displayName?.takeIf { it.isNotBlank() }
                        if (cloudName != null) NameState.Present(cloudName) else NameState.Absent
                    }
                    .onEach { state ->
                        // Cache the cloud name locally so future launches seed instantly.
                        if (state is NameState.Present && onboardingPrefs.chosenName != state.name) {
                            onboardingPrefs.chosenName = state.name
                        }
                    }
                    .onStart {
                        // While waiting for the player doc emit Loading, unless the local
                        // pref already has a name (returning user: skip the flash).
                        val cached = onboardingPrefs.chosenName?.takeIf { it.isNotBlank() }
                        emit(if (cached != null) NameState.Present(cached) else NameState.Loading)
                    }
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = onboardingPrefs.chosenName
                ?.takeIf { it.isNotBlank() }
                ?.let { NameState.Present(it) }
                ?: NameState.Loading,
        )

    /** Convenience projection — non-null only when [nameState] is [NameState.Present]. */
    val name: StateFlow<String?> = nameState
        .map { (it as? NameState.Present)?.name }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = (nameState.value as? NameState.Present)?.name,
        )

    suspend fun ensure(displayName: String): Result<Unit> = runCatching {
        val trimmed = displayName.trim().take(MAX_DISPLAY_NAME)
        require(trimmed.isNotBlank()) { "Display name must not be blank" }

        if (auth.authState.value.signedInUid == null) {
            auth.continueAsGuest().getOrThrow()
        }

        profile.updateProfile(displayName = trimmed).getOrThrow()
        onboardingPrefs.chosenName = trimmed

        runCatching { claimGeneratedUsername(trimmed) }
    }

    /** Best-effort: auto-claims a username derived from [displayName] when the user has none. */
    suspend fun ensureUsername(displayName: String) {
        runCatching { claimGeneratedUsername(displayName) }
    }

    private suspend fun claimGeneratedUsername(displayName: String) {
        val base = UsernameGenerator.base(displayName)
        val candidates = UsernameGenerator.candidates(base)
        for (candidate in candidates.take(USERNAME_CANDIDATE_TRIES)) {
            val result = profile.claimUsername(candidate)
            if (result.isSuccess) return
        }
    }
}

object UsernameGenerator {
    private val ILLEGAL = Regex("[^a-z0-9_]")
    private const val MIN = 3
    private const val MAX = 20

    fun base(displayName: String): String {
        val sanitized = displayName.lowercase().replace(ILLEGAL, "_")
        val trimmed = sanitized.trimStart('_').trimEnd('_')
        return when {
            trimmed.length < MIN -> trimmed.padEnd(MIN, '_')
            trimmed.length > MAX -> trimmed.take(MAX)
            else -> trimmed
        }.ifEmpty { "player" }
    }

    fun candidates(base: String): Sequence<String> = sequence {
        yield(base)
        var i = 0
        while (true) {
            val suffix = (100..999).random().toString()
            val candidate = if (base.length + suffix.length <= MAX) {
                "$base$suffix"
            } else {
                base.take(MAX - suffix.length) + suffix
            }
            yield(candidate)
            i++
        }
    }
}
