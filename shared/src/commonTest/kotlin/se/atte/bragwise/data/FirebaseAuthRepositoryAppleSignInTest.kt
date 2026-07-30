package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.platform.Analytics
import se.atte.bragwise.platform.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class FakeAuthLocalDataSource : AuthLocalDataSource {
    override var pendingSignInEmail: String? = null
}

private class FakeLocalPredictionStore : LocalPredictionStore {
    override fun forChallenge(challengeId: String) = emptyMap<String, se.atte.bragwise.domain.PredictionPayload>()
    override fun put(challengeId: String, predictions: Map<String, se.atte.bragwise.domain.PredictionPayload>) {}
    override fun deleteForChallenge(challengeId: String) {}
    override fun snapshot(): List<LocalPrediction> = emptyList()
    override fun clear() {}
}

private class FakeChallengeRemote : ChallengeRemote {
    override fun observePromoted() = throw NotImplementedError()
    override fun observeCreatedBy(uid: String) = throw NotImplementedError()
    override fun observeJoined(uid: String) = throw NotImplementedError()
    override fun observePendingInvites(uid: String) = throw NotImplementedError()
    override fun observeChallengesByIds(ids: List<String>) = throw NotImplementedError()
    override fun observeChallengeDetail(challengeId: String, myUid: String) = throw NotImplementedError()
    override fun observeLeaderboard(challengeId: String): Flow<List<LeaderboardEntry>> = throw NotImplementedError()
    override fun observeFromFriends(friendUids: List<String>) = throw NotImplementedError()
    override suspend fun createChallenge(challenge: se.atte.bragwise.domain.Challenge) = throw NotImplementedError()
    override fun observeParticipantPredictions(challengeId: String, uid: String) = throw NotImplementedError()
    override suspend fun submitPredictions(challengeId: String, predictions: List<se.atte.bragwise.domain.Prediction>) = throw NotImplementedError()
    override suspend fun postResults(challengeId: String, results: Map<String, se.atte.bragwise.domain.PredictionPayload>) = throw NotImplementedError()
    override suspend fun inviteFriends(challengeId: String, uids: List<String>) = throw NotImplementedError()
    override suspend fun deleteChallenge(challengeId: String) = throw NotImplementedError()
    override fun observeReactions(challengeId: String) = throw NotImplementedError()
    override suspend fun setReaction(challengeId: String, emoji: String?) = throw NotImplementedError()
    override suspend fun migrateGuestData(predictions: List<LocalPrediction>) = throw NotImplementedError()
}

private class FakeAnalytics : Analytics {
    val logged = mutableListOf<AnalyticsEvent>()
    override fun log(event: AnalyticsEvent) { logged.add(event) }
    override fun setIsGuest(isGuest: Boolean) {}
}

private class FakeProfileRemote(
    /** Display name already on the profile doc; blank means "no name yet". */
    private val existingDisplayName: String = "",
) : ProfileRemote {
    var updateProfileCalls = mutableListOf<String?>()
    override fun observePlayer(uid: String): Flow<se.atte.bragwise.domain.Player?> = MutableStateFlow(
        se.atte.bragwise.domain.Player(
            uid = uid,
            username = "",
            displayName = existingDisplayName,
            avatarSeed = "",
            createdAt = kotlin.time.Instant.DISTANT_PAST,
        ),
    )
    override fun observePublicProfile(uid: String) = throw NotImplementedError()
    override fun observeNotificationPrefs(uid: String) = throw NotImplementedError()
    override suspend fun setMasterNotification(enabled: Boolean) = throw NotImplementedError()
    override suspend fun setCategoryNotification(key: String, enabled: Boolean) = throw NotImplementedError()
    override suspend fun recordActivity() = throw NotImplementedError()
    override suspend fun claimUsername(username: String) = throw NotImplementedError()
    override suspend fun updateProfile(displayName: String?, username: String?, avatarSeed: String?) {
        updateProfileCalls.add(displayName)
    }
}

private class FakeAuthRemote : AuthRemote {
    var isNewUserResult: Boolean = true
    var lastSignInCredential: AppleIdCredential? = null

    // Signed in, so the repository can resolve a uid to read the existing
    // profile name with. Apple sign-in is only reachable from a signed-out or
    // anonymous session, but the uid exists either way by the time we write.
    override val currentUser: AuthUser? = AuthUser(uid = "uid-1", email = null, isAnonymous = false)
    override val authStateChanged: Flow<AuthUser?> = MutableStateFlow(currentUser)
    override suspend fun sendSignInLink(email: String) = throw NotImplementedError()
    override fun isSignInWithEmailLink(link: String) = false
    override suspend fun signInAnonymously() = throw NotImplementedError()
    override suspend fun completeSignIn(email: String, link: String) = throw NotImplementedError()
    override suspend fun signInWithEmailLink(email: String, link: String) = throw NotImplementedError()

    override suspend fun signInWithApple(credential: AppleIdCredential): Boolean {
        lastSignInCredential = credential
        return isNewUserResult
    }

    override suspend fun signOut() = throw NotImplementedError()
    override suspend fun deleteAccount() = throw NotImplementedError()
}

private class FakeApplePresenter(
    private val result: Result<AppleIdCredential>,
) : AppleSignInPresenter {
    override suspend fun present(): AppleIdCredential = result.getOrThrow()
}

private fun buildRepository(
    remote: FakeAuthRemote,
    profileRemote: FakeProfileRemote,
    presenter: AppleSignInPresenter?,
    analytics: FakeAnalytics,
) = FirebaseAuthRepository(
    remote = remote,
    local = FakeAuthLocalDataSource(),
    localPredictions = FakeLocalPredictionStore(),
    challengeRemote = FakeChallengeRemote(),
    analytics = analytics,
    profileRemote = profileRemote,
    applePresenter = presenter,
)

class FirebaseAuthRepositoryAppleSignInTest {

    private val credentialWithName = AppleIdCredential(
        identityToken = "token",
        rawNonce = "nonce",
        fullName = "Ada Lovelace",
        email = "ada@example.com",
    )
    private val credentialWithoutName = credentialWithName.copy(fullName = null)

    @Test
    fun `writes Apple-supplied name for a brand-new account`() = runTest {
        val remote = FakeAuthRemote().apply { isNewUserResult = true }
        val profileRemote = FakeProfileRemote()
        val repo = buildRepository(remote, profileRemote, FakeApplePresenter(Result.success(credentialWithName)), FakeAnalytics())

        val result = repo.signInWithApple()

        assertEquals(true, result.isSuccess)
        assertEquals(listOf<String?>("Ada Lovelace"), profileRemote.updateProfileCalls)
    }

    @Test
    fun `does not write a name when Apple returns none`() = runTest {
        val remote = FakeAuthRemote().apply { isNewUserResult = true }
        val profileRemote = FakeProfileRemote()
        val repo = buildRepository(remote, profileRemote, FakeApplePresenter(Result.success(credentialWithoutName)), FakeAnalytics())

        repo.signInWithApple()

        assertEquals(emptyList(), profileRemote.updateProfileCalls)
    }

    /**
     * Regression: `isNewUser` came back false on a first Apple authorization
     * (observed on the simulator), which dropped the name and pushed the user
     * into the name gate. Apple only ever sends `fullName` on a first
     * authorization, so its presence — not `isNewUser` — is what gates the write.
     */
    @Test
    fun `writes Apple-supplied name even when isNewUser is false`() = runTest {
        val remote = FakeAuthRemote().apply { isNewUserResult = false }
        val profileRemote = FakeProfileRemote()
        val repo = buildRepository(remote, profileRemote, FakeApplePresenter(Result.success(credentialWithName)), FakeAnalytics())

        repo.signInWithApple()

        assertEquals(listOf<String?>("Ada Lovelace"), profileRemote.updateProfileCalls)
    }

    @Test
    fun `never overwrites a name the profile already has`() = runTest {
        val remote = FakeAuthRemote().apply { isNewUserResult = true }
        val profileRemote = FakeProfileRemote(existingDisplayName = "Chosen Earlier")
        val repo = buildRepository(remote, profileRemote, FakeApplePresenter(Result.success(credentialWithName)), FakeAnalytics())

        repo.signInWithApple()

        assertEquals(emptyList(), profileRemote.updateProfileCalls)
    }

    @Test
    fun `cancellation fails the result and never writes a name`() = runTest {
        val remote = FakeAuthRemote()
        val profileRemote = FakeProfileRemote()
        val repo = buildRepository(remote, profileRemote, FakeApplePresenter(Result.failure(AppleSignInCancelledException())), FakeAnalytics())

        val result = repo.signInWithApple()

        assertIs<AppleSignInCancelledException>(result.exceptionOrNull())
        assertEquals(emptyList(), profileRemote.updateProfileCalls)
    }

    @Test
    fun `fails when no presenter is registered for this platform`() = runTest {
        val remote = FakeAuthRemote()
        val profileRemote = FakeProfileRemote()
        val repo = buildRepository(remote, profileRemote, presenter = null, analytics = FakeAnalytics())

        val result = repo.signInWithApple()

        assertNull(remote.lastSignInCredential)
        assertEquals(emptyList(), profileRemote.updateProfileCalls)
        assertEquals(true, result.isFailure)
    }
}
