package se.atte.bragwise.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import se.atte.bragwise.platform.Analytics
import se.atte.bragwise.platform.AnalyticsEvent

/** Grep tag for the Sign in with Apple flow; see `signInWithApple`. */
private const val APPLE_DBG = "BRAGWISE_APPLE_7f31a2"

/** Upper bound on the profile-doc read that guards the Apple name write. */
private const val DISPLAY_NAME_READ_TIMEOUT_MS = 3_000L

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState

    /**
     * Carries identity only (uid + email). Full `Player` profile data
     * is owned by `ProfileRepository.observeMe()`, which reads
     * `/publicProfiles/{uid}` + `/players/{uid}` separately.
     *
     * [isAnonymous] is true for guests: they have a real Firebase uid and
     * an online player doc (so they appear on leaderboards under the name
     * they chose) but are not bound to an email/person. Feature gates that
     * require a "real" account (create challenge, add friends, online
     * predictions) must check [isFullyAuthed], not `is SignedIn`.
     */
    data class SignedIn(
        val uid: String,
        val email: String?,
        val isAnonymous: Boolean = false,
    ) : AuthState
}

/** uid for any signed-in session (anonymous guest included), else null. */
val AuthState.signedInUid: String? get() = (this as? AuthState.SignedIn)?.uid

/**
 * True only for a non-anonymous (email-backed) account. Anonymous guests
 * are signed in but NOT fully authed — they keep the guest feature set.
 */
val AuthState.isFullyAuthed: Boolean
    get() = this is AuthState.SignedIn && !isAnonymous

sealed interface AuthPayload {
    /** First leg: user types email, we send them a link. */
    data class EmailLinkRequest(val email: String) : AuthPayload

    /** Second leg: user clicks the link, the app receives it via App Links. */
    data class EmailLinkComplete(val email: String, val link: String) : AuthPayload
}

data class MigrationSummary(
    val migrated: Int,
    val skipped: Int,
    val failed: Int,
)

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val pendingSignInEmail: StateFlow<String?>

    /** True iff this link looks like a Firebase email sign-in link. */
    fun isSignInLink(link: String): Boolean

    /**
     * Sign in anonymously so a guest gets a stable random uid and an online
     * (but person-unbound) player record. The chosen display name is written
     * separately via the `updateProfile` callable by the caller.
     */
    suspend fun continueAsGuest(): Result<Unit>

    /**
     * Send the sign-in link. Persists the email locally so the deep-link
     * leg (which may come back after process death) can replay it into
     * `signInWithEmailLink`.
     */
    suspend fun sendSignInLink(email: String): Result<Unit>

    /**
     * Complete sign-in from the deep-link return. Pulls the email from
     * local storage; fails fast if the user opened the link on a different
     * device (no pending email locally).
     */
    suspend fun completeSignInWithLink(link: String): Result<Unit>

    /**
     * iOS only. Presents the native Apple sign-in sheet, then signs in (or
     * upgrades an anonymous guest via `linkWithCredential`). Whenever Apple
     * supplies a name — which it does only on the first authorization for a
     * given Apple ID, so its presence alone marks a first sign-in — and the
     * profile has no name yet, persists it via `updateProfile` so
     * `EnsureNamedAccount`'s name gate never has to ask. Cancelling the sheet
     * fails with [AppleSignInCancelledException] — callers must not surface
     * that as an error.
     */
    suspend fun signInWithApple(): Result<Unit>

    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
    suspend fun migrateLocalToCloud(): Result<MigrationSummary>
}

/**
 * Wraps the Firebase Auth lifecycle for the rest of the app. Auth-state is
 * observed off `FirebaseAuth.authStateChanged`; the pending email (typed at
 * OB-02 before the link request) survives process death via
 * [AuthLocalDataSource].
 *
 * `signUp` does not exist — `signInWithEmailLink` auto-creates accounts on
 * first link click. Profile bootstrap (handle, displayName) happens via the
 * `updateProfile` callable after the first sign-in, not here.
 */
class FirebaseAuthRepository(
    val remote: AuthRemote,
    private val local: AuthLocalDataSource,
    private val localPredictions: LocalPredictionStore,
    private val challengeRemote: ChallengeRemote,
    private val analytics: Analytics,
    private val profileRemote: ProfileRemote,
    private val applePresenter: AppleSignInPresenter? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) : AuthRepository {
    private val _pendingSignInEmail = MutableStateFlow(local.pendingSignInEmail)
    override val pendingSignInEmail: StateFlow<String?> = _pendingSignInEmail

    /**
     * Emits `Loading` once then switches to `SignedOut` / `SignedIn` based
     * on Firebase auth state.
     */
    override val authState: StateFlow<AuthState> = remote.authStateChanged
        .map<_, AuthState> { user ->
            if (user == null) AuthState.SignedOut
            else AuthState.SignedIn(uid = user.uid, email = user.email, isAnonymous = user.isAnonymous)
        }
        .onStart { emit(AuthState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading,
        )

    override fun isSignInLink(link: String): Boolean = remote.isSignInWithEmailLink(link)

    override suspend fun continueAsGuest(): Result<Unit> = runCatching {
        remote.signInAnonymously()
        analytics.log(AnalyticsEvent.OnboardingComplete("guest"))
        analytics.setIsGuest(true)
    }

    override suspend fun sendSignInLink(email: String): Result<Unit> = runCatching {
        local.pendingSignInEmail = email
        _pendingSignInEmail.value = email
        remote.sendSignInLink(email)
    }

    override suspend fun completeSignInWithLink(link: String): Result<Unit> = runCatching {
        val email = local.pendingSignInEmail
            ?: error("no pending email — link may have been opened on a different device")
        remote.completeSignIn(email = email, link = link)
        local.pendingSignInEmail = null
        _pendingSignInEmail.value = null
        analytics.log(AnalyticsEvent.OnboardingComplete("email"))
        analytics.setIsGuest(false)
    }

    override suspend fun signInWithApple(): Result<Unit> = runCatching {
        val presenter = applePresenter ?: error("apple sign-in unavailable on this platform")
        val credential = presenter.present()
        val isNewUser = remote.signInWithApple(credential)
        val appleName = credential.fullName?.trim()?.takeIf { it.isNotBlank() }
        println("$APPLE_DBG signIn.done isNewUser=$isNewUser hasName=${appleName != null} hasEmail=${credential.email != null}")

        // Apple returns `fullName` ONLY on the first authorization for a given
        // Apple ID + app pair, so receiving one at all means this IS the first
        // authorization — whatever `additionalUserInfo.isNewUser` says. Gating on
        // isNewUser (it comes back false/nil on some link-vs-sign-in paths)
        // silently dropped the name and pushed the user into the name gate.
        //
        // Only fill a name we don't already have, so an existing profile name
        // (e.g. one a guest chose before upgrading) is never overwritten.
        if (appleName != null) {
            val existing = currentDisplayName()
            println("$APPLE_DBG name.check apple='$appleName' existing='${existing ?: ""}'")
            if (existing.isNullOrBlank()) {
                runCatching { profileRemote.updateProfile(appleName, null, null) }
                    .onSuccess { println("$APPLE_DBG name.written '$appleName'") }
                    .onFailure { println("$APPLE_DBG name.write.failed class=${it::class.simpleName} message=${it.message}") }
            } else {
                println("$APPLE_DBG name.kept.existing")
            }
        }
        analytics.log(AnalyticsEvent.OnboardingComplete("apple"))
        analytics.setIsGuest(false)
    }.onFailure { e ->
        if (e !is AppleSignInCancelledException) {
            println("$APPLE_DBG signIn.failed class=${e::class.simpleName} message=${e.message}")
        }
    }

    /**
     * Current profile display name, or null if there is none / it can't be read
     * in time. Bounded so a cold Firestore listener right after sign-in can
     * never hang the sign-in flow; on timeout we treat the name as absent,
     * which at worst re-writes the same name.
     */
    private suspend fun currentDisplayName(): String? {
        val uid = authState.value.signedInUid ?: remote.currentUser?.uid ?: return null
        return withTimeoutOrNull(DISPLAY_NAME_READ_TIMEOUT_MS) {
            profileRemote.observePlayer(uid).firstOrNull()?.displayName
        }
    }

    override suspend fun signOut() {
        remote.signOut()
        local.pendingSignInEmail = null
        _pendingSignInEmail.value = null
        localPredictions.clear()
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        remote.deleteAccount()
        local.pendingSignInEmail = null
        _pendingSignInEmail.value = null
        localPredictions.clear()
    }

    /**
     * Always-merge: replays local guest predictions into the signed-in account via
     * `migrateGuestData`. Account's existing predictions are never overwritten
     * (skipIfExists server-side). Clears only the resolved (migrated + skipped)
     * challenges locally; failed ones are also cleared after counting so the store
     * doesn't accumulate permanently-unmigrateable rows.
     */
    override suspend fun migrateLocalToCloud(): Result<MigrationSummary> = runCatching {
        val pending = localPredictions.snapshot()
        if (pending.isEmpty()) {
            MigrationSummary(migrated = 0, skipped = 0, failed = 0)
        } else {
            val summary = challengeRemote.migrateGuestData(pending)
            localPredictions.clear()
            summary
        }
    }
}
