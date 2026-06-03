package se.atte.bragwise.domain

import kotlin.time.Instant

data class Prediction(
    val betId: String,
    val payload: PredictionPayload,
)

data class ChallengePlayer(
    val uid: String,
    val joinedAt: Instant,
    val updatedAt: Instant,
    val predictions: Map<String, PredictionPayload>,
)

data class ChallengeDetail(
    val challenge: Challenge,
    val myPredictions: Map<String, PredictionPayload>,
    val myRank: Int?,
) {
    val title: String get() = challenge.title
}

data class LeaderboardEntry(
    val uid: String,
    val displayName: String,
    val avatarSeed: String = "",
    val points: Int,
    val rank: Int,
    val isTied: Boolean = false,
)
