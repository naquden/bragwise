package se.atte.bragwise.verify

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Visibility
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Debug / agent verification hooks. Only invoked from Android debug intents —
 * not wired to any production UI.
 */
object VerifyAutomation {
    private val openPredictChallengeIdFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val openPredictChallengeId: SharedFlow<String> = openPredictChallengeIdFlow.asSharedFlow()

    /** Pre-filled ranking order for the seeded Eurovision bet (`b1`). */
    private var pendingRankingFill: Pair<String, List<String>>? = null

    var autoSubmitPredictions: Boolean = false
        private set

    fun requestOpenPredict(challengeId: String) {
        openPredictChallengeIdFlow.tryEmit(challengeId)
    }

    /** Returns and clears a one-shot ranking fill for [PredictContent]. */
    fun consumePendingRankingFill(): Pair<String, List<String>>? {
        val pending = pendingRankingFill
        pendingRankingFill = null
        return pending
    }

    fun clearAutoSubmitPredictions() {
        autoSubmitPredictions = false
    }

    /** Creates and publishes a Eurovision-style country ranking challenge; returns its id. */
    suspend fun seedEurovisionRankingChallenge(challenges: ChallengeRepository): Result<String> {
        val countryOptions = listOf(
            BetOption(id = "o0", label = "Sweden", countryCode = "SE"),
            BetOption(id = "o1", label = "Ukraine", countryCode = "UA"),
            BetOption(id = "o2", label = "Italy", countryCode = "IT"),
            BetOption(id = "o3", label = "France", countryCode = "FR"),
        )
        val rankingBet = Bet.Ranking(
            id = "b1",
            title = "Top 3 Eurovision results",
            optionType = OptionType.COUNTRY,
            topN = 3,
            options = countryOptions,
        )
        val draft = Challenge(
            id = "",
            title = "Eurovision 2026 Top 3",
            description = "",
            category = "Other",
            visibility = Visibility.FRIENDS,
            createdBy = "",
            createdAt = Clock.System.now(),
            locksAt = Clock.System.now() + 7.days,
            resultsPostedAt = null,
            status = ChallengeStatus.DRAFT,
            joinedCount = 0,
            promoted = false,
            trusted = false,
            bets = listOf(rankingBet),
            results = null,
            leaderboard = null,
        )
        return challenges.createDraft(draft).mapCatching { saved ->
            challenges.publish(challengeId = saved.id).getOrThrow()
            pendingRankingFill = rankingBet.id to listOf("o2", "o0", "o1")
            autoSubmitPredictions = true
            saved.id
        }
    }
}
