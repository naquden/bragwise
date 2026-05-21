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
)

enum class Visibility { FRIENDS, INVITE_ONLY, PROMOTED }

/**
 * Stored values: DRAFT, OPEN, RESULTS_POSTED, ARCHIVED.
 * LOCKED is client-computed from (now > locksAt && resultsPostedAt == null).
 */
enum class ChallengeStatus { DRAFT, OPEN, LOCKED, RESULTS_POSTED, ARCHIVED }
