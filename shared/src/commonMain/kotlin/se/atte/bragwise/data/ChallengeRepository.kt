@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.PredictionPayload

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

open class ChallengeRepository(
    val remote: ChallengeRemoteDataSource,
    private val local: ChallengeLocalDataSource,
    private val auth: AuthRepository,
) {
    private val currentUid: String?
        get() = (auth.authState.value as? AuthState.SignedIn)?.uid

    /**
     * Combines challenges created by the user (including drafts) with challenges
     * the user has joined as a participant. Deduplicates by id.
     */
    fun observeMine(): Flow<List<Challenge>> =
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

    fun observePromoted(): Flow<List<Challenge>> = remote.observePromoted()
        .catch { emit(emptyList()) }

    /**
     * Challenges from friends — Phase 1 deferred: requires reading the friend
     * list and then querying challenges by each friend uid, which is a fan-out
     * that sits above the single-shot auth state. Returns empty for now; a full
     * implementation will use SocialRepository.observeCloudFriends() → challenge query.
     */
    fun observeFromFriends(): Flow<List<Challenge>> = flowOf(emptyList())

    fun observePendingInvites(): Flow<List<Invitation>> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observePendingInvites(state.uid)
                    .catch { emit(emptyList()) }
                else -> flowOf(emptyList())
            }
        }

    fun observeChallengeDetail(id: String): Flow<ChallengeDetail> =
        auth.authState.flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.uid ?: ""
            remote.observeChallengeDetail(challengeId = id, myUid = uid)
        }

    fun observeLeaderboard(challengeId: String, friendsOnly: Boolean = false): Flow<List<LeaderboardEntry>> =
        remote.observeLeaderboard(challengeId)

    // ── Writes ────────────────────────────────────────────────────────────────

    suspend fun createDraft(challenge: Challenge): Result<Challenge> = runCatching {
        val id = remote.createChallenge(challenge)
        challenge.copy(id = id)
    }

    suspend fun updateDraft(challenge: Challenge): Result<Unit> = runCatching {
        remote.updateDraft(challenge)
    }

    suspend fun publish(challengeId: String): Result<Unit> = runCatching {
        remote.publishChallenge(challengeId)
    }

    suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>): Result<Unit> =
        runCatching { remote.submitPredictions(challengeId, predictions) }

    suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>): Result<Unit> =
        runCatching { remote.postResults(challengeId, results) }

    suspend fun inviteFriends(challengeId: String, uids: List<String>): Result<Unit> =
        runCatching { remote.inviteFriends(challengeId, uids) }

    suspend fun dismissInviteLocally(challengeId: String): Result<Unit> = Result.success(Unit)
}
