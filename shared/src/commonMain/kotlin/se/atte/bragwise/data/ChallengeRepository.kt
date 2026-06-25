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
import se.atte.bragwise.domain.InviteCard
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.Reaction
import se.atte.bragwise.platform.AnalyticsEvent

interface ChallengeRepository {
    fun observeMine(): Flow<List<Challenge>>
    fun observePromoted(): Flow<List<Challenge>>
    fun observeFromFriends(): Flow<List<Challenge>>
    fun observePendingInvites(): Flow<List<InviteCard>>
    /** Ids of challenges the current user has predicted on. Empty when signed out. */
    fun observeJoinedIds(): Flow<Set<String>>
    fun observeChallengeDetail(id: String): Flow<ChallengeDetail>
    fun observeLeaderboard(challengeId: String): Flow<List<LeaderboardEntry>>
    fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>>
    /** All finished (RESULTS_POSTED) challenges the current user participated in, newest first. */
    fun observeFinished(): Flow<List<Challenge>>

    /** Persist [challenge] as a local draft. Assigns a local UUID if [challenge.id] is blank. */
    suspend fun saveDraft(challenge: Challenge): Result<Challenge>
    /** Returns the local draft with [id], or null if not found. */
    fun getDraft(id: String): Challenge?
    /** Deletes the local draft with [id]. */
    suspend fun deleteDraft(id: String): Result<Unit>
    /** Publishes [challenge] to the server as OPEN in one call. Deletes the local draft on success. */
    suspend fun publish(challenge: Challenge): Result<Challenge>

    suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>): Result<Unit>
    suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>): Result<Unit>
    suspend fun inviteFriends(challengeId: String, uids: List<String>): Result<Unit>
    suspend fun dismissInviteLocally(challengeId: String): Result<Unit>
    suspend fun deleteChallenge(challengeId: String): Result<Unit>

    fun observeReactions(challengeId: String): Flow<List<Reaction>>
    suspend fun setReaction(challengeId: String, emoji: String?): Result<Unit>
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
    private val localDrafts: LocalDraftStore,
    private val auth: AuthRepository,
    private val social: SocialRepository,
    private val analytics: se.atte.bragwise.platform.Analytics,
) : ChallengeRepository {
    private val currentUid: String?
        get() = (auth.authState.value as? AuthState.SignedIn)?.uid

    /**
     * Combines local drafts with server challenges (created by + joined).
     * Local drafts appear in the list for all auth states.
     */
    override fun observeMine(): Flow<List<Challenge>> =
        auth.authState.flatMapLatest { state ->
            crDbg("observeMine.authState type=${state::class.simpleName}")
            when (state) {
                is AuthState.SignedIn -> combine(
                    remote.observeCreatedBy(state.uid).tagCR("createdBy"),
                    remote.observeJoined(state.uid).tagCR("joined"),
                    localDrafts.observeDrafts(),
                ) { created, joined, drafts ->
                    (drafts + created + joined).distinctBy { it.id }
                        .sortedByDescending { it.createdAt }
                }
                else -> localDrafts.observeDrafts()
            }
        }

    override fun observePromoted(): Flow<List<Challenge>> = remote.observePromoted()
        .catch { emit(emptyList()) }

    override fun observeFromFriends(): Flow<List<Challenge>> =
        auth.authState.flatMapLatest { state ->
            val myUid = (state as? AuthState.SignedIn)?.uid ?: return@flatMapLatest localDrafts.observeDrafts().map { emptyList() }
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

    override fun observePendingInvites(): Flow<List<InviteCard>> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> {
                    val uid = state.uid
                    remote.observePendingInvites(uid)
                        .catch { e ->
                            println("observePendingInvites.error: ${e::class.simpleName} ${e.message}")
                            emit(emptyList())
                        }
                        .flatMapLatest { invitations ->
                            val openInvites = invitations.filter { it.invitedUid == uid }
                            if (openInvites.isEmpty()) return@flatMapLatest flowOf(emptyList())
                            val ids = openInvites.map { it.challengeId }
                            val inviteByChallenge = openInvites.associate { it.challengeId to it.invitedBy }
                            val joinedIds = remote.observeJoined(uid).map { joined -> joined.map { it.id }.toSet() }
                            combine(
                                remote.observeChallengesByIds(ids),
                                joinedIds,
                            ) { challenges, joined ->
                                challenges
                                    .filter { it.status == ChallengeStatus.OPEN && it.id !in joined }
                                    .mapNotNull { challenge ->
                                        val invitedBy = inviteByChallenge[challenge.id] ?: return@mapNotNull null
                                        InviteCard(challenge = challenge, invitedByUid = invitedBy)
                                    }
                            }
                        }
                }
                else -> flowOf(emptyList())
            }
        }

    override fun observeJoinedIds(): Flow<Set<String>> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn ->
                    remote.observeJoined(state.uid).map { it.map { c -> c.id }.toSet() }
                        .catch { emit(emptySet()) }
                else -> flowOf(emptySet())
            }
        }

    override fun observeChallengeDetail(id: String): Flow<ChallengeDetail> =
        auth.authState.flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.uid ?: ""
            remote.observeChallengeDetail(challengeId = id, myUid = uid)
        }

    override fun observeLeaderboard(challengeId: String): Flow<List<LeaderboardEntry>> =
        remote.observeLeaderboard(challengeId)

    override fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>> =
        remote.observeParticipantPredictions(challengeId = challengeId, uid = uid)

    override fun observeFinished(): Flow<List<Challenge>> =
        observeMine().map { challenges ->
            challenges
                .filter { it.status == se.atte.bragwise.domain.ChallengeStatus.RESULTS_POSTED }
                .sortedByDescending { it.resultsPostedAt }
        }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun saveDraft(challenge: Challenge): Result<Challenge> = runCatching {
        localDrafts.save(challenge)
    }

    override fun getDraft(id: String): Challenge? = localDrafts.get(id)

    override suspend fun deleteDraft(id: String): Result<Unit> = runCatching {
        localDrafts.delete(id)
    }

    override suspend fun publish(challenge: Challenge): Result<Challenge> = runCatching {
        val serverId = remote.createChallenge(challenge)
        localDrafts.delete(challenge.id)
        analytics.log(
            AnalyticsEvent.ChallengeCreated(
                betCount = challenge.bets.size,
                visibility = challenge.visibility.name.lowercase(),
                category = challenge.category,
                invitedCount = challenge.invitedUids.size,
            ),
        )
        challenge.copy(id = serverId, status = se.atte.bragwise.domain.ChallengeStatus.OPEN)
    }

    override suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>): Result<Unit> =
        runCatching {
            remote.submitPredictions(challengeId, predictions)
            analytics.log(
                AnalyticsEvent.PredictionSubmitted(
                    predictionCount = predictions.size,
                    isGuest = auth.authState.value.let { it is AuthState.SignedIn && it.isAnonymous },
                    offline = false,
                ),
            )
        }

    override suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>): Result<Unit> =
        runCatching {
            remote.postResults(challengeId, results)
            analytics.log(AnalyticsEvent.ResultsPosted(resultCount = results.size))
        }

    override suspend fun inviteFriends(challengeId: String, uids: List<String>): Result<Unit> =
        runCatching { remote.inviteFriends(challengeId, uids) }

    override suspend fun dismissInviteLocally(challengeId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteChallenge(challengeId: String): Result<Unit> = runCatching {
        remote.deleteChallenge(challengeId)
    }

    override fun observeReactions(challengeId: String): Flow<List<Reaction>> =
        remote.observeReactions(challengeId)

    override suspend fun setReaction(challengeId: String, emoji: String?): Result<Unit> =
        runCatching { remote.setReaction(challengeId, emoji) }
}
