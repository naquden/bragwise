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
import kotlin.time.Instant

class MockChallengeRepository(
    private val auth: AuthRepository,
) : ChallengeRepository {
    private val _challenges = MutableStateFlow(mockChallenges)
    private val _localDrafts = MutableStateFlow<List<Challenge>>(emptyList())

    // Keyed by challengeId → (betId → payload)
    private val _predictions = MutableStateFlow<Map<String, Map<String, PredictionPayload>>>(emptyMap())

    private val currentUid: String
        get() = (auth.authState.value as? AuthState.SignedIn)?.uid ?: MOCK_UID

    override fun observeMine(): Flow<List<Challenge>> =
        combine(_challenges, _localDrafts) { server, drafts ->
            val mine = server.filter { it.createdBy == currentUid && it.status != ChallengeStatus.RESULTS_POSTED }
            (drafts + mine).distinctBy { it.id }.sortedByDescending { it.createdAt }
        }

    override fun observePromoted(): Flow<List<Challenge>> =
        _challenges.map { list -> list.filter { it.promoted && it.status == ChallengeStatus.OPEN } }

    override fun observeFromFriends(): Flow<List<Challenge>> =
        _challenges.map { list ->
            list.filter { it.visibility == Visibility.FRIENDS && it.createdBy != currentUid && it.status == ChallengeStatus.OPEN }
                .sortedByDescending { it.createdAt }
        }

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
            val board = challenge?.leaderboard ?: return@map emptyList()
            val sortedEntries = board.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            buildMockLeaderboard(sortedEntries = sortedEntries)
        }

    override fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>> =
        _predictions.map { it[challengeId] ?: emptyMap() }

    override fun observeFinished(): Flow<List<Challenge>> =
        _challenges.map { list ->
            list.filter { it.status == ChallengeStatus.RESULTS_POSTED }
                .sortedByDescending { it.resultsPostedAt }
        }

    override suspend fun saveDraft(challenge: Challenge): Result<Challenge> = runCatching {
        val id = challenge.id.ifBlank { randomUuid() }
        val saved = challenge.copy(id = id, status = ChallengeStatus.DRAFT, createdAt = if (challenge.id.isBlank()) Clock.System.now() else challenge.createdAt)
        _localDrafts.update { drafts ->
            val existing = drafts.indexOfFirst { it.id == id }
            if (existing >= 0) drafts.toMutableList().also { it[existing] = saved } else drafts + saved
        }
        saved
    }

    override fun getDraft(id: String): Challenge? = _localDrafts.value.firstOrNull { it.id == id }

    override suspend fun deleteDraft(id: String): Result<Unit> = runCatching {
        _localDrafts.update { it.filterNot { d -> d.id == id } }
    }

    override suspend fun publish(challenge: Challenge): Result<Challenge> = runCatching {
        _localDrafts.update { it.filterNot { d -> d.id == challenge.id } }
        val saved = challenge.copy(
            id = randomUuid(),
            createdBy = currentUid,
            createdAt = Clock.System.now(),
            status = ChallengeStatus.OPEN,
        )
        _challenges.update { it + saved }
        saved
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

private fun buildMockLeaderboard(sortedEntries: List<Map.Entry<String, Int>>): List<LeaderboardEntry> {
    val result = mutableListOf<LeaderboardEntry>()
    var rank = 1
    var i = 0
    while (i < sortedEntries.size) {
        val points = sortedEntries[i].value
        var j = i
        while (j < sortedEntries.size && sortedEntries[j].value == points) j++
        val isTied = j - i > 1
        for (k in i until j) {
            val uid = sortedEntries[k].key
            val name = mockName(uid = uid)
            result += LeaderboardEntry(
                uid = uid,
                displayName = name,
                avatarSeed = mockAvatarSeed(uid = uid),
                points = points,
                rank = rank,
                isTied = isTied,
            )
        }
        rank += j - i
        i = j
    }
    return result
}

private fun mockName(uid: String): String = when (uid) {
    MOCK_UID -> "Demo Player"
    "uid-alice" -> "Alice"
    "uid-bob" -> "Bob"
    "uid-carol" -> "Carol"
    "uid-dave" -> "Dave"
    else -> uid
}

private fun mockAvatarSeed(uid: String): String = when (uid) {
    MOCK_UID -> "a1"
    "uid-alice" -> "a3"
    "uid-bob" -> "a5"
    "uid-carol" -> "a7"
    "uid-dave" -> "a9"
    else -> "a2"
}
