package se.atte.bragwise.ui.screens.create

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.EnsureNamedAccount
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.mvi.ErrorReporter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private class FakeOnboardingPrefs : OnboardingPrefs {
    override var hasSeenWelcome: Boolean = false
    override var chosenName: String? = null
}

private class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedIn(uid = "uid-1", email = null, isAnonymous = true))
    override val authState = _authState.asStateFlow()
    override val pendingSignInEmail = MutableStateFlow<String?>(null).asStateFlow()
    override fun isSignInLink(link: String) = throw NotImplementedError()
    override suspend fun continueAsGuest(): Result<Unit> = Result.success(Unit)
    override suspend fun sendSignInLink(email: String) = throw NotImplementedError()
    override suspend fun completeSignInWithLink(link: String) = throw NotImplementedError()
    override suspend fun signInWithApple() = throw NotImplementedError()
    override suspend fun signOut() = throw NotImplementedError()
    override suspend fun deleteAccount() = throw NotImplementedError()
    override suspend fun migrateLocalToCloud() = throw NotImplementedError()
}

/**
 * Controls what `observeMe()` emits, so the test controls `nameState` in
 * `EnsureNamedAccount` without needing to fake that (final) class directly.
 * `emitPlayer` defaults to never emitting — the shape that reproduces
 * `NameState.Loading`/`Absent` never resolving to `Present`.
 */
private class FakeProfileRepository(
    private val me: Flow<Player?> = MutableStateFlow(null),
) : ProfileRepository {
    var updateProfileCalls = mutableListOf<String?>()
    var updateProfileResult: Result<Unit> = Result.success(Unit)
    override fun observeMe(): Flow<Player?> = me
    override fun observePublicProfile(uid: String) = throw NotImplementedError()
    override fun observeNotificationPrefs() = throw NotImplementedError()
    override suspend fun claimUsername(username: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateProfile(displayName: String?, username: String?, avatarSeed: String?): Result<Unit> {
        updateProfileCalls.add(displayName)
        return updateProfileResult
    }
    override suspend fun setMasterNotification(enabled: Boolean) = throw NotImplementedError()
    override suspend fun setCategoryNotification(key: String, enabled: Boolean) = throw NotImplementedError()
    override suspend fun recordActivity() = throw NotImplementedError()
}

private class FakeSocialRepository : SocialRepository {
    override fun observeFriends(): Flow<List<Friend>> = flowOf(emptyList())
    override fun observeFriendRequests() = throw NotImplementedError()
    override fun observeHeadToHead() = throw NotImplementedError()
    override suspend fun sendFriendRequest(username: String) = throw NotImplementedError()
    override suspend fun acceptFriendRequest(requesterUid: String) = throw NotImplementedError()
    override suspend fun declineFriendRequest(requesterUid: String) = throw NotImplementedError()
    override suspend fun withdrawFriendRequest(otherUid: String) = throw NotImplementedError()
    override suspend fun unfriend(otherUid: String) = throw NotImplementedError()
}

private class FakeChallengeRepository : ChallengeRepository {
    val published = mutableListOf<Challenge>()
    val drafts = mutableListOf<Challenge>()
    var publishResult: Result<Challenge>? = null
    var publishDeferred: CompletableDeferred<Result<Challenge>>? = null

    override fun observeMine() = throw NotImplementedError()
    override fun observePromoted() = throw NotImplementedError()
    override fun observeFromFriends() = throw NotImplementedError()
    override fun observePendingInvites() = throw NotImplementedError()
    override fun observeJoinedIds() = throw NotImplementedError()
    override fun observeChallengeDetail(id: String) = throw NotImplementedError()
    override fun observeLeaderboard(challengeId: String) = throw NotImplementedError()
    override fun observeParticipantPredictions(challengeId: String, uid: String) = throw NotImplementedError()
    override fun observeFinished() = throw NotImplementedError()
    override suspend fun saveDraft(challenge: Challenge): Result<Challenge> {
        drafts.add(challenge)
        return Result.success(challenge.copy(id = "draft-1"))
    }
    override fun getDraft(id: String): Challenge? = null
    override suspend fun deleteDraft(id: String) = throw NotImplementedError()
    override suspend fun publish(challenge: Challenge): Result<Challenge> {
        val deferred = publishDeferred
        val result = if (deferred != null) deferred.await() else (publishResult ?: Result.success(challenge.copy(id = "server-1")))
        result.onSuccess { published.add(it) }
        return result
    }
    override suspend fun submitPredictions(challengeId: String, predictions: List<se.atte.bragwise.domain.Prediction>) = throw NotImplementedError()
    override suspend fun postResults(challengeId: String, results: Map<String, se.atte.bragwise.domain.PredictionPayload>) = throw NotImplementedError()
    override suspend fun inviteFriends(challengeId: String, uids: List<String>) = throw NotImplementedError()
    override suspend fun dismissInviteLocally(challengeId: String) = throw NotImplementedError()
    override suspend fun deleteChallenge(challengeId: String) = throw NotImplementedError()
    override fun observeReactions(challengeId: String) = throw NotImplementedError()
    override suspend fun setReaction(challengeId: String, emoji: String?) = throw NotImplementedError()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreateChallengeViewModelTest {

    private val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * `EnsureNamedAccount.nameState` is a `stateIn(..., SharingStarted.Eagerly, ...)`
     * StateFlow: constructing it only *schedules* its collector on `scope` — on a
     * `StandardTestDispatcher` that collector does not actually run until the
     * scheduler is advanced. Without draining it here, every caller would see the
     * constructor's `initialValue` (`Loading`) regardless of what `me` emits.
     */
    private fun buildViewModel(
        challenges: FakeChallengeRepository = FakeChallengeRepository(),
        me: Flow<Player?> = MutableStateFlow(null),
    ): Triple<CreateChallengeViewModel, FakeChallengeRepository, FakeProfileRepository> {
        val profile = FakeProfileRepository(me = me)
        val ensureNamedAccount = EnsureNamedAccount(
            auth = FakeAuthRepository(),
            profile = profile,
            onboardingPrefs = FakeOnboardingPrefs(),
            scope = TestScope(scheduler),
        )
        val vm = CreateChallengeViewModel(
            challenges = challenges,
            social = FakeSocialRepository(),
            ensureNamedAccount = ensureNamedAccount,
            errorReporter = ErrorReporter(),
        )
        scheduler.advanceUntilIdle()
        return Triple(vm, challenges, profile)
    }

    private fun validState(vm: CreateChallengeViewModel) {
        vm.onIntent(CreateChallengeViewModel.Intent.SetTitle("Who wins?"))
        vm.onIntent(CreateChallengeViewModel.Intent.AddBoolean("Will it rain?"))
        vm.onIntent(CreateChallengeViewModel.Intent.SetLocksAt(Instant.DISTANT_FUTURE))
    }

    /**
     * Regression for the stuck "Saving challenge…" spinner: `ConfirmName` set
     * `submitting = true` then called `publish()`, whose very first line was
     * `if (state.value.submitting) return` — a guaranteed no-op. `nameState`
     * never re-emits here (the fake `observeMe()` never emits a signal), which
     * also pins the second, latent bug: the confirm path must not re-consult
     * `nameState` after `ensure()` succeeds.
     */
    @Test
    fun `confirming a name during publish creates the challenge and clears the spinner`() = runTest(scheduler) {
        val (vm, challenges, _) = buildViewModel(me = MutableStateFlow(null))
        validState(vm)

        vm.onIntent(CreateChallengeViewModel.Intent.Publish)
        scheduler.runCurrent()
        assertTrue(vm.state.value.needsName, "name gate should show when nameState is Absent")
        assertFalse(vm.state.value.submitting)
        assertEquals(0, challenges.published.size)

        vm.onIntent(CreateChallengeViewModel.Intent.ConfirmName("Atte"))
        scheduler.advanceUntilIdle()

        assertEquals(1, challenges.published.size, "challenge should be published after confirming a name")
        assertFalse(vm.state.value.submitting, "spinner must clear after publish resolves")
    }

    @Test
    fun `failed name write clears the spinner and reports the error`() = runTest(scheduler) {
        val (vm, challenges, profile) = buildViewModel(me = MutableStateFlow(null))
        profile.updateProfileResult = Result.failure(RuntimeException("boom"))
        validState(vm)

        vm.onIntent(CreateChallengeViewModel.Intent.Publish)
        scheduler.runCurrent()
        vm.onIntent(CreateChallengeViewModel.Intent.ConfirmName("Atte"))
        scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.submitting)
        assertEquals(0, challenges.published.size)
    }

    @Test
    fun `publishing with an already-named account publishes once`() = runTest(scheduler) {
        val player = Player(uid = "uid-1", username = "atte", displayName = "Atte", avatarSeed = "", createdAt = Instant.DISTANT_PAST)
        val (vm, challenges, _) = buildViewModel(me = MutableStateFlow(player))
        validState(vm)

        vm.onIntent(CreateChallengeViewModel.Intent.Publish)
        scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.needsName)
        assertEquals(1, challenges.published.size)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `double-tapping publish only publishes once`() = runTest(scheduler) {
        val player = Player(uid = "uid-1", username = "atte", displayName = "Atte", avatarSeed = "", createdAt = Instant.DISTANT_PAST)
        val challenges = FakeChallengeRepository()
        val deferred = CompletableDeferred<Result<Challenge>>()
        challenges.publishDeferred = deferred
        val (vm, _, _) = buildViewModel(challenges = challenges, me = MutableStateFlow(player))
        validState(vm)

        vm.onIntent(CreateChallengeViewModel.Intent.Publish)
        scheduler.runCurrent()
        assertTrue(vm.state.value.submitting)
        vm.onIntent(CreateChallengeViewModel.Intent.Publish)
        scheduler.runCurrent()

        deferred.complete(Result.success(Challenge(
            id = "server-1", title = "x", description = "", category = "Other",
            visibility = Visibility.FRIENDS, createdBy = "", createdAt = Instant.DISTANT_PAST,
            locksAt = Instant.DISTANT_FUTURE, resultsPostedAt = null,
            status = se.atte.bragwise.domain.ChallengeStatus.OPEN, joinedCount = 0, promoted = false,
            bets = emptyList(), results = null, leaderboard = null, betsVisible = false,
            invitedUids = emptySet(), scoringMode = se.atte.bragwise.domain.ScoringMode.STANDARD,
        )))
        scheduler.advanceUntilIdle()

        assertEquals(1, challenges.published.size)
    }

    @Test
    fun `dismissing the name gate does not publish`() = runTest(scheduler) {
        val (vm, challenges, _) = buildViewModel(me = MutableStateFlow(null))
        validState(vm)

        vm.onIntent(CreateChallengeViewModel.Intent.Publish)
        scheduler.runCurrent()
        vm.onIntent(CreateChallengeViewModel.Intent.DismissName)
        scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.needsName)
        assertFalse(vm.state.value.submitting)
        assertEquals(0, challenges.published.size)
    }
}
