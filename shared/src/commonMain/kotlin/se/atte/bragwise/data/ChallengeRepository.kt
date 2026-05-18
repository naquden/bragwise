package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.PredictionPayload

open class ChallengeRepository(
    private val remote: ChallengeRemoteDataSource,
    private val local: ChallengeLocalDataSource,
    private val auth: AuthRepository,
) {
    fun observeMine(): Flow<List<Challenge>> = flowOf(emptyList())
    fun observePromoted(): Flow<List<Challenge>> = flowOf(emptyList())
    fun observeFromFriends(): Flow<List<Challenge>> = flowOf(emptyList())
    fun observePendingInvites(): Flow<List<Invitation>> = flowOf(emptyList())

    fun observeChallengeDetail(id: String): Flow<ChallengeDetail> = flowOf()
    fun observeLeaderboard(challengeId: String, friendsOnly: Boolean = false): Flow<List<LeaderboardEntry>> =
        flowOf(emptyList())

    suspend fun createDraft(challenge: Challenge): Result<Challenge> =
        Result.failure(NotImplementedError("createChallenge not wired"))
    suspend fun updateDraft(challenge: Challenge): Result<Unit> =
        Result.failure(NotImplementedError("updateDraft not wired"))
    suspend fun publish(challengeId: String): Result<Unit> =
        Result.failure(NotImplementedError("publishChallenge not wired"))

    /**
     * First call must cover every bet in the challenge — joining is implicit.
     * Subsequent calls may carry any subset and patch via dot-path update.
     */
    suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>): Result<Unit> =
        Result.failure(NotImplementedError("submitPredictions not wired"))

    suspend fun postResults(
        challengeId: String,
        results: Map<String, PredictionPayload>,
    ): Result<Unit> = Result.failure(NotImplementedError("postResults not wired"))

    suspend fun inviteFriends(challengeId: String, uids: List<String>): Result<Unit> =
        Result.failure(NotImplementedError("inviteFriends not wired"))

    /** Local-only — invitation row stays server-side. */
    suspend fun dismissInviteLocally(challengeId: String): Result<Unit> = Result.success(Unit)
}
