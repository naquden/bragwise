package se.atte.bragwise.domain

/**
 * Phase 1 bet types: SinglePick, Ranking, BooleanProp.
 * Phase 1.5 addition: `optionType` controls per-bet option semantics.
 *   NONE = free text; COUNTRY = country autocomplete + flag rendering.
 * Phase 2: Guess (time-of-day or calendar-day free guess with closest-wins scoring).
 * Phase 2.5: MultiSelect, Numeric Guess (NUMBER granularity), Over/Under.
 * Phase 3: Bracket, ExactScore (stubs intentionally absent).
 */

enum class OptionType { NONE, COUNTRY }

/** Granularity of a [Bet.Guess] — TIME = time-of-day, DAY = calendar date, NUMBER = free integer. */
enum class GuessGranularity { TIME, DAY, NUMBER }

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
     *   TIME   → minutes since local midnight (0..1439)
     *   DAY    → UTC epoch-day (days since 1970-01-01)
     *   NUMBER → any integer
     * [closest] = true → closest-wins (cross-player, scored by leaderboard aggregator);
     *           = false → exact match only (pairwise, scored by ScoringEngine.score).
     * [placement] = true → closest-wins ranked descending across the full field;
     *             requires closest=true. Scored server-side by the leaderboard aggregator.
     */
    data class Guess(
        override val id: String,
        override val title: String,
        val granularity: GuessGranularity,
        val closest: Boolean = true,
        val placement: Boolean = false,
    ) : Bet {
        override val optionType: OptionType = OptionType.NONE
    }

    /**
     * Pick any subset of options. Score = +1 per correct selected, −1 per wrong selected.
     * Score can be negative. Empty selection is valid ("none of these").
     */
    data class MultiSelect(
        override val id: String,
        override val title: String,
        override val optionType: OptionType = OptionType.NONE,
        val options: List<BetOption>,
    ) : Bet

    /**
     * Creator sets a numeric [line]. Predictor picks Over or Under.
     * At result time the creator enters the actual value; server resolves:
     *   actual > line → Over wins, actual < line → Under wins, actual == line → push (0 pts).
     */
    data class OverUnder(
        override val id: String,
        override val title: String,
        val line: Long,
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

/**
 * True when this bet uses COUNTRY options but at least one option is not a
 * recognised country (typed free text that was never resolved to a code).
 * Applies to the option-bearing types (SinglePick / Ranking / MultiSelect).
 */
fun Bet.hasUnsupportedCountryOption(): Boolean {
    if (optionType != OptionType.COUNTRY) return false
    val opts = when (this) {
        is Bet.SinglePick -> options
        is Bet.Ranking -> options
        is Bet.MultiSelect -> options
        else -> return false
    }
    return opts.any { !isSupportedCountryCode(it.countryCode) }
}

/** True when every COUNTRY option across all bets resolves to a supported code. */
fun List<Bet>.allCountryOptionsResolved(): Boolean = none { it.hasUnsupportedCountryOption() }

sealed interface PredictionPayload {
    data class SinglePick(val optionId: String) : PredictionPayload
    data class Ranking(val orderedOptionIds: List<String>) : PredictionPayload
    data class BooleanProp(val value: Boolean) : PredictionPayload
    /**
     * Guess payload.
     * TIME:   [value] = minutes since local midnight (0..1439).
     * DAY:    [value] = UTC epoch-day (days since 1970-01-01).
     * NUMBER: [value] = any integer.
     */
    data class Guess(val value: Long) : PredictionPayload

    /** Subset of selected option ids. Empty list = "none of these" (valid prediction). */
    data class MultiSelect(val selectedOptionIds: List<String>) : PredictionPayload

    /**
     * Over/Under payload — dual-purpose.
     * Prediction: [over] is non-null (true = Over, false = Under); [actualValue] is null.
     * Result:     [actualValue] is non-null; [over] is null.
     */
    data class OverUnder(
        val over: Boolean? = null,
        val actualValue: Long? = null,
    ) : PredictionPayload
}
