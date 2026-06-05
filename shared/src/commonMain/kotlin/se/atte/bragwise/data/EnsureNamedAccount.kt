package se.atte.bragwise.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val MAX_DISPLAY_NAME = 40
private const val USERNAME_CANDIDATE_TRIES = 5

/**
 * Lazily bootstraps a named player account the first time the user takes an
 * action that needs one (placing bets, creating a challenge).
 *
 * [name] emits the current cloud display name; null/blank means the user
 * hasn't named themselves yet and the gate should prompt.
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
) {
    private val scope = CoroutineScope(SupervisorJob())

    val name: StateFlow<String?> = profile.observeMe()
        .map { player -> player?.displayName?.takeIf { it.isNotBlank() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            // Seed from the local pref so returning users get an immediate non-null value
            // before the first Firestore emission arrives, preventing a spurious name prompt.
            initialValue = onboardingPrefs.chosenName?.takeIf { it.isNotBlank() },
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
