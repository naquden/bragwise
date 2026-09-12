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

/**
 * Debug / agent verification hooks. Only invoked from Android debug intents —
 * not wired to any production UI.
 */
object VerifyAutomation {
    private val openPredictChallengeIdFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val openPredictChallengeId: SharedFlow<String> = openPredictChallengeIdFlow.asSharedFlow()

    /** Pre-filled ranking order for the seeded country ranking bet (`b1`). */
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

    /** Creates and publishes a country ranking challenge for QA verification; returns its id. */
    suspend fun seedEurovisionRankingChallenge(challenges: ChallengeRepository): Result<String> {
        val countryOptions = listOf(
            BetOption(id = "o0", label = "Sweden", countryCode = "SE"),
            BetOption(id = "o1", label = "Ukraine", countryCode = "UA"),
            BetOption(id = "o2", label = "Italy", countryCode = "IT"),
            BetOption(id = "o3", label = "France", countryCode = "FR"),
        )
        val rankingBet = Bet.Ranking(
            id = "b1",
            title = "Top 3 Song Contest results",
            optionType = OptionType.COUNTRY,
            topN = 3,
            options = countryOptions,
        )
        val draft = Challenge(
            id = "",
            title = "Song Contest 2026 Top 3",
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
            bets = listOf(rankingBet),
            results = null,
            leaderboard = null,
        )
        return challenges.publish(draft).mapCatching { saved ->
            pendingRankingFill = rankingBet.id to listOf("o2", "o0", "o1")
            autoSubmitPredictions = true
            saved.id
        }
    }

    /**
     * Creates and publishes an 8-option / topN=8 country ranking challenge — the exact
     * case that used to fall into the HorizontalPager and render a near-blank Predict
     * screen. No pendingRankingFill and no auto-submit: the point is to drag by hand
     * and screenshot the result.
     */
    suspend fun seedRankingTop8Challenge(challenges: ChallengeRepository): Result<String> {
        val countryOptions = listOf(
            BetOption(id = "o0", label = "Sweden", countryCode = "SE"),
            BetOption(id = "o1", label = "Ukraine", countryCode = "UA"),
            BetOption(id = "o2", label = "Italy", countryCode = "IT"),
            BetOption(id = "o3", label = "France", countryCode = "FR"),
            BetOption(id = "o4", label = "Netherlands", countryCode = "NL"),
            BetOption(id = "o5", label = "Czechia", countryCode = "CZ"),
            // NOT "GB": the backend's SUPPORTED_COUNTRY_CODES (functions/src/countries.ts)
            // only lists the subdivisions GB-ENG/GB-NIR/GB-SCT/GB-WLS, so a bare "GB"
            // fails publish with unsupported-country → invalid-argument.
            BetOption(id = "o6", label = "England", countryCode = "GB-ENG"),
            BetOption(id = "o7", label = "Bosnia and Herzegovina", countryCode = "BA"),
        )
        val rankingBet = Bet.Ranking(
            id = "b1",
            title = "Top 8 Song Contest results",
            optionType = OptionType.COUNTRY,
            topN = 8,
            options = countryOptions,
        )
        val draft = Challenge(
            id = "",
            title = "Song Contest 2026 Top 8",
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
            bets = listOf(rankingBet),
            results = null,
            leaderboard = null,
        )
        return challenges.publish(draft).mapCatching { saved ->
            // Explicitly clear leftovers from a previous eurovision_ranking seed.
            pendingRankingFill = null
            autoSubmitPredictions = false
            saved.id
        }
    }
}
