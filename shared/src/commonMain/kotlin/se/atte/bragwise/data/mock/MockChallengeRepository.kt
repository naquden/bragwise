package se.atte.bragwise.data.mock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.util.randomUuid
import kotlin.time.Clock

class MockChallengeRepository(
    private val auth: AuthRepository,
) : ChallengeRepository {
    private val _challenges = MutableStateFlow(mockChallenges)

    // Keyed by challengeId → (betId → payload)
    private val _predictions = MutableStateFlow<Map<String, Map<String, PredictionPayload>>>(emptyMap())

    private val currentUid: String
        get() = (auth.authState.value as? AuthState.SignedIn)?.uid ?: MOCK_UID

    override fun observeMine(): Flow<List<Challenge>> =
        _challenges.map { list ->
            list.filter { it.createdBy == currentUid || it.visibility == Visibility.FRIENDS }
                .sortedByDescending { it.createdAt }
        }

    override fun observePromoted(): Flow<List<Challenge>> =
        _challenges.map { list -> list.filter { it.promoted && it.status == ChallengeStatus.OPEN } }

    override fun observeFromFriends(): Flow<List<Challenge>> = flowOf(emptyList())

    override fun observePendingInvites(): Flow<List<Invitation>> = flowOf(emptyList())

    override fun observeChallengeDetail(id: String): Flow<ChallengeDetail> =
        combine(_challenges, _predictions) { challenges, predictions ->
            val challenge = challenges.first { it.id == id }
            val myPredictions = predictions[id] ?: emptyMap()
            val myPoints = challenge.leaderboard?.get(currentUid)
            val rank = challenge.leaderboard
                ?.entries
                ?.sortedByDescending { it.value }
                ?.indexOfFirst { it.key == currentUid }
                ?.takeIf { it >= 0 }
                ?.let { it + 1 }
            ChallengeDetail(challenge = challenge, myPredictions = myPredictions, myRank = rank)
        }

    override fun observeLeaderboard(challengeId: String, friendsOnly: Boolean): Flow<List<LeaderboardEntry>> =
        _challenges.map { challenges ->
            val challenge = challenges.firstOrNull { it.id == challengeId }
            challenge?.leaderboard
                ?.entries
                ?.sortedByDescending { it.value }
                ?.mapIndexed { idx, (uid, pts) ->
                    val name = when (uid) {
                        MOCK_UID -> "Demo Player"
                        "uid-alice" -> "Alice"
                        "uid-bob" -> "Bob"
                        else -> uid
                    }
                    LeaderboardEntry(uid = uid, displayName = name, points = pts, rank = idx + 1)
                }
                ?: emptyList()
        }

    override suspend fun createDraft(challenge: Challenge): Result<Challenge> = runCatching {
        val saved = challenge.copy(id = randomUuid(), createdBy = currentUid, createdAt = Clock.System.now())
        _challenges.update { it + saved }
        saved
    }

    override suspend fun updateDraft(challenge: Challenge): Result<Unit> = runCatching {
        _challenges.update { list -> list.map { if (it.id == challenge.id) challenge else it } }
    }

    override suspend fun publish(challengeId: String): Result<Unit> = runCatching {
        _challenges.update { list ->
            list.map { if (it.id == challengeId) it.copy(status = ChallengeStatus.OPEN) else it }
        }
    }

    override suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>): Result<Unit> =
        runCatching {
            val payloadMap = predictions.associate { it.betId to it.payload }
            _predictions.update { existing ->
                existing + (challengeId to ((existing[challengeId] ?: emptyMap()) + payloadMap))
            }
        }

    override suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>): Result<Unit> =
        runCatching {
            _challenges.update { list ->
                list.map { c ->
                    if (c.id == challengeId) c.copy(
                        results = results,
                        status = ChallengeStatus.RESULTS_POSTED,
                        resultsPostedAt = Clock.System.now(),
                    ) else c
                }
            }
        }

    override suspend fun inviteFriends(challengeId: String, uids: List<String>): Result<Unit> =
        Result.success(Unit)

    override suspend fun dismissInviteLocally(challengeId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteChallenge(challengeId: String): Result<Unit> =
        Result.success(Unit)
}
