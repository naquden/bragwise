package se.atte.bragwise.domain

/**
 * Phase 1 bet types: SinglePick, Ranking, BooleanProp.
 * Phase 1.5 addition: `optionType` controls per-bet option semantics.
 *   NONE = free text; COUNTRY = country autocomplete + flag rendering.
 * Phase 2: Guess (time-of-day or calendar-day free guess with closest-wins scoring).
 * Phase 3: Bracket, ExactScore (stubs intentionally absent).
 */

enum class OptionType { NONE, COUNTRY }

/** Granularity of a [Bet.Guess] — TIME = time-of-day, DAY = calendar date. */
enum class GuessGranularity { TIME, DAY }

sealed interface Bet {
    val id: String
    val title: String
    val optionType: OptionType

    data class SinglePick(
        override val id: String,
        override val title: String,
        override val optionType: OptionType = OptionType.NONE,
        val options: List<BetOption>,
    ) : Bet

    data class Ranking(
        override val id: String,
        override val title: String,
        override val optionType: OptionType = OptionType.NONE,
        val topN: Int,
        val options: List<BetOption>,
    ) : Bet

    data class BooleanProp(
        override val id: String,
        override val title: String,
    ) : Bet {
        override val optionType: OptionType = OptionType.NONE
    }

    /**
     * Free-value guess bet — each predictor picks their own value from a picker.
     * [granularity] controls the picker and value encoding:
     *   TIME → minutes since local midnight (0..1439)
     *   DAY  → UTC epoch-day (days since 1970-01-01)
     * [closest] = true → closest-wins (cross-player, scored by leaderboard aggregator);
     *           = false → exact match only (pairwise, scored by ScoringEngine.score).
     */
    data class Guess(
        override val id: String,
        override val title: String,
        val granularity: GuessGranularity,
        val closest: Boolean = true,
    ) : Bet {
        override val optionType: OptionType = OptionType.NONE
    }
}

data class BetOption(
    val id: String,
    val label: String,
    /** ISO-3166 alpha-2 country code; null means free-text / custom entry. */
    val countryCode: String? = null,
)

sealed interface PredictionPayload {
    data class SinglePick(val optionId: String) : PredictionPayload
    data class Ranking(val orderedOptionIds: List<String>) : PredictionPayload
    data class BooleanProp(val value: Boolean) : PredictionPayload
    /**
     * Guess payload.
     * TIME: [value] = minutes since local midnight (0..1439).
     * DAY:  [value] = UTC epoch-day (days since 1970-01-01).
     */
    data class Guess(val value: Long) : PredictionPayload
}
