package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.Reaction

interface ChallengeRemote {
    fun observePromoted(): Flow<List<Challenge>>
    fun observeCreatedBy(uid: String): Flow<List<Challenge>>
    fun observeJoined(uid: String): Flow<List<Challenge>>
    fun observePendingInvites(uid: String): Flow<List<Invitation>>
    fun observeChallengesByIds(ids: List<String>): Flow<List<Challenge>>
    fun observeChallengeDetail(challengeId: String, myUid: String): Flow<ChallengeDetail>
    fun observeLeaderboard(challengeId: String): Flow<List<LeaderboardEntry>>
    fun observeFromFriends(friendUids: List<String>): Flow<List<Challenge>>
    suspend fun createChallenge(challenge: Challenge): String
    fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>>
    suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>)
    suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>)
    suspend fun inviteFriends(challengeId: String, uids: List<String>)
    suspend fun deleteChallenge(challengeId: String)
    fun observeReactions(challengeId: String): Flow<List<Reaction>>
    suspend fun setReaction(challengeId: String, emoji: String?)
    suspend fun migrateGuestData(predictions: List<LocalPrediction>): MigrationSummary
}
