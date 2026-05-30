package se.atte.bragwise.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState

    /**
     * Carries identity only (uid + email). Full `Player` profile data
     * is owned by `ProfileRepository.observeMe()`, which reads
     * `/publicProfiles/{uid}` + `/players/{uid}` separately.
     */
    data class SignedIn(val uid: String, val email: String?) : AuthState
}

/**
 * Phase 1 ships email-link passwordless only. Google + Apple are Phase 2
 * (must ship together on iOS due to App Store guideline 4.8). See
 * `temp/plan.md` § "Auth providers".
 */
enum class AuthProvider { EMAIL_LINK }

sealed interface AuthPayload {
    /** First leg: user types email, we send them a link. */
    data class EmailLinkRequest(val email: String) : AuthPayload

    /** Second leg: user clicks the link, the app receives it via App Links. */
    data class EmailLinkComplete(val email: String, val link: String) : AuthPayload
}

enum class MigrationMode { RESTORE, SYNC, SKIP }

data class MigrationSummary(
    val migrated: Int,
    val deferredKeptLocal: Int,
    val droppedLocked: Int,
)

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val pendingSignInEmail: StateFlow<String?>

    /**
     * Whether the most recently completed sign-in created a brand-new account
     * (`true`) or returned to an existing one (`false`). Drives OB-05 mode
     * selection: a new account SYNCs guest predictions up; an existing account
     * RESTOREs from the cloud, dropping local guest data. `null` until the
     * first sign-in completes this process.
     */
    val lastSignInCreatedNewUser: Boolean?

    /** True iff this link looks like a Firebase email sign-in link. */
    fun isSignInLink(link: String): Boolean

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

    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
    suspend fun migrateLocalToCloud(mode: MigrationMode): Result<MigrationSummary>
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
    val remote: AuthRemoteDataSource,
    private val local: AuthLocalDataSource,
    private val localPredictions: LocalPredictionStore,
    private val challengeRemote: ChallengeRemoteDataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) : AuthRepository {
    private val _pendingSignInEmail = MutableStateFlow(local.pendingSignInEmail)
    override val pendingSignInEmail: StateFlow<String?> = _pendingSignInEmail

    override var lastSignInCreatedNewUser: Boolean? = null
        private set

    /**
     * Emits `Loading` once then switches to `SignedOut` / `SignedIn` based
     * on Firebase auth state.
     */
    override val authState: StateFlow<AuthState> = remote.authStateChanged
        .map<_, AuthState> { user ->
            if (user == null) AuthState.SignedOut
            else AuthState.SignedIn(uid = user.uid, email = user.email)
        }
        .onStart { emit(AuthState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading,
        )

    override fun isSignInLink(link: String): Boolean = remote.isSignInWithEmailLink(link)

    override suspend fun sendSignInLink(email: String): Result<Unit> = runCatching {
        local.pendingSignInEmail = email
        _pendingSignInEmail.value = email
        remote.sendSignInLink(email)
    }

    override suspend fun completeSignInWithLink(link: String): Result<Unit> = runCatching {
        val email = local.pendingSignInEmail
            ?: error("no pending email — link may have been opened on a different device")
        val result = remote.signInWithEmailLink(email = email, link = link)
        lastSignInCreatedNewUser = result.additionalUserInfo?.isNewUser
        local.pendingSignInEmail = null
        _pendingSignInEmail.value = null
    }

    override suspend fun signOut() {
        remote.signOut()
        local.pendingSignInEmail = null
        _pendingSignInEmail.value = null
    }

    override suspend fun deleteAccount(): Result<Unit> =
        Result.failure(NotImplementedError("deleteAccount callable not wired"))

    /**
     * OB-05 Restore / Sync / Skip. RESTORE drops local guest predictions and
     * loads the cloud account as-is; SYNC replays them through the
     * `migrateGuestData` callable; SKIP is a no-op (caller aborts auth before
     * this runs). On a successful SYNC the local store is cleared so the same
     * predictions are never migrated twice.
     */
    override suspend fun migrateLocalToCloud(mode: MigrationMode): Result<MigrationSummary> = runCatching {
        when (mode) {
            MigrationMode.SKIP -> MigrationSummary(migrated = 0, deferredKeptLocal = 0, droppedLocked = 0)
            MigrationMode.RESTORE -> {
                localPredictions.clear()
                MigrationSummary(migrated = 0, deferredKeptLocal = 0, droppedLocked = 0)
            }
            MigrationMode.SYNC -> {
                val pending = localPredictions.snapshot()
                if (pending.isEmpty()) {
                    MigrationSummary(migrated = 0, deferredKeptLocal = 0, droppedLocked = 0)
                } else {
                    val summary = challengeRemote.migrateGuestData(pending)
                    localPredictions.clear()
                    summary
                }
            }
        }
    }
}
