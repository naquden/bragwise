package se.atte.bragwise.domain

import kotlin.time.Instant

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val visibility: Visibility,
    val createdBy: String,
    val createdAt: Instant,
    val locksAt: Instant?,
    val resultsPostedAt: Instant?,
    val status: ChallengeStatus,
    val joinedCount: Int,
    val promoted: Boolean,
    val trusted: Boolean,
    val bets: List<Bet>,
    val results: Map<String, PredictionPayload>?,
    val leaderboard: Map<String, Int>?,
    val betsVisible: Boolean = false,
    val participants: List<ParticipantInfo> = emptyList(),
)

data class ParticipantInfo(
    val uid: String,
    val displayName: String,
    val avatarSeed: String,
)

enum class Visibility { FRIENDS, INVITE_ONLY, PROMOTED }

/**
 * Stored values: DRAFT, OPEN, RESULTS_POSTED.
 * LOCKED is client-computed from (now > locksAt && resultsPostedAt == null).
 * Challenges are hard-deleted 90 days after resultsPostedAt by the purgeOldChallenges Cloud Function.
 */
enum class ChallengeStatus { DRAFT, OPEN, LOCKED, RESULTS_POSTED }
