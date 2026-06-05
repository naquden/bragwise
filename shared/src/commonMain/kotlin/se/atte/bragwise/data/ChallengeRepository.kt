@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.PredictionPayload

interface ChallengeRepository {
    fun observeMine(): Flow<List<Challenge>>
    fun observePromoted(): Flow<List<Challenge>>
    fun observeFromFriends(): Flow<List<Challenge>>
    fun observePendingInvites(): Flow<List<Invitation>>
    fun observeChallengeDetail(id: String): Flow<ChallengeDetail>
    fun observeLeaderboard(challengeId: String, friendsOnly: Boolean = false): Flow<List<LeaderboardEntry>>
    fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>>
    /** All finished (RESULTS_POSTED) challenges the current user participated in, newest first. */
    fun observeFinished(): Flow<List<Challenge>>

    suspend fun createDraft(challenge: Challenge): Result<Challenge>
    suspend fun updateDraft(challenge: Challenge): Result<Unit>
    suspend fun publish(challengeId: String): Result<Unit>
    suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>): Result<Unit>
    suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>): Result<Unit>
    suspend fun inviteFriends(challengeId: String, uids: List<String>): Result<Unit>
    suspend fun dismissInviteLocally(challengeId: String): Result<Unit>
    suspend fun deleteChallenge(challengeId: String): Result<Unit>
}

// #region agent log
private const val CR_DBG = "BRAGWISE_DBG_9c95cf"
private fun crDbg(msg: String) { println("$CR_DBG $msg") }
private fun <T> Flow<T>.tagCR(name: String): Flow<T> = this
    .onStart { crDbg("$name.start") }
    .onEach { v ->
        val size = (v as? Collection<*>)?.size
        crDbg("$name.value size=$size")
    }
    .catch { e ->
        crDbg("$name.error type=${e::class.simpleName} msg=${e.message}")
        throw e
    }
// #endregion

class FirebaseChallengeRepository(
    val remote: ChallengeRemoteDataSource,
    private val local: ChallengeLocalDataSource,
    private val auth: AuthRepository,
    private val social: SocialRepository,
) : ChallengeRepository {
    private val currentUid: String?
        get() = (auth.authState.value as? AuthState.SignedIn)?.uid

    /**
     * Combines challenges created by the user (including drafts) with challenges
     * the user has joined as a participant. Deduplicates by id.
     */
    override fun observeMine(): Flow<List<Challenge>> =
        auth.authState.flatMapLatest { state ->
            // #region agent log
            crDbg("observeMine.authState type=${state::class.simpleName}")
            // #endregion
            when (state) {
                is AuthState.SignedIn -> combine(
                    remote.observeCreatedBy(state.uid).tagCR("createdBy"),
                    remote.observeJoined(state.uid).tagCR("joined"),
                ) { created, joined ->
                    (created + joined).distinctBy { it.id }
                        .sortedByDescending { it.createdAt }
                }
                else -> flowOf(emptyList())
            }
        }

    override fun observePromoted(): Flow<List<Challenge>> = remote.observePromoted()
        .catch { emit(emptyList()) }

    override fun observeFromFriends(): Flow<List<Challenge>> =
        auth.authState.flatMapLatest { state ->
            val myUid = (state as? AuthState.SignedIn)?.uid ?: return@flatMapLatest flowOf(emptyList())
            combine(
                social.observeFriends().map { friends -> friends.filterIsInstance<CloudFriend>().map { it.id } },
                remote.observeJoined(myUid).map { joined -> joined.map { it.id }.toSet() },
            ) { friendUids, joinedIds ->
                Pair(friendUids, joinedIds)
            }.flatMapLatest { (friendUids, joinedIds) ->
                remote.observeFromFriends(friendUids)
                    .map { challenges -> challenges.filter { it.id !in joinedIds } }
                    .catch { emit(emptyList()) }
            }
        }

    override fun observePendingInvites(): Flow<List<Invitation>> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observePendingInvites(state.uid)
                    .catch { emit(emptyList()) }
                else -> flowOf(emptyList())
            }
        }

    override fun observeChallengeDetail(id: String): Flow<ChallengeDetail> =
        auth.authState.flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.uid ?: ""
            remote.observeChallengeDetail(challengeId = id, myUid = uid)
        }

    override fun observeLeaderboard(challengeId: String, friendsOnly: Boolean): Flow<List<LeaderboardEntry>> =
        remote.observeLeaderboard(challengeId)

    override fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>> =
        remote.observeParticipantPredictions(challengeId = challengeId, uid = uid)

    override fun observeFinished(): Flow<List<Challenge>> =
        observeMine().map { challenges ->
            challenges
                .filter { it.status == ChallengeStatus.RESULTS_POSTED }
                .sortedByDescending { it.resultsPostedAt }
        }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun createDraft(challenge: Challenge): Result<Challenge> = runCatching {
        val id = remote.createChallenge(challenge)
        challenge.copy(id = id)
    }

    override suspend fun updateDraft(challenge: Challenge): Result<Unit> = runCatching {
        remote.updateDraft(challenge)
    }

    override suspend fun publish(challengeId: String): Result<Unit> = runCatching {
        remote.publishChallenge(challengeId)
    }

    override suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>): Result<Unit> =
        runCatching { remote.submitPredictions(challengeId, predictions) }

    override suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>): Result<Unit> =
        runCatching { remote.postResults(challengeId, results) }

    override suspend fun inviteFriends(challengeId: String, uids: List<String>): Result<Unit> =
        runCatching { remote.inviteFriends(challengeId, uids) }

    override suspend fun dismissInviteLocally(challengeId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteChallenge(challengeId: String): Result<Unit> = runCatching {
        remote.deleteChallenge(challengeId)
    }
}
