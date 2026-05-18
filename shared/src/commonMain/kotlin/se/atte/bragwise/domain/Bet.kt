package se.atte.bragwise.domain

/**
 * Phase 1 bet types: SinglePick, Ranking, BooleanProp.
 * Phase 2: Bracket, ExactScore (stubs intentionally absent).
 *
 * Phase 1.5 addition: `optionType` controls per-bet option semantics.
 * NONE = free text; COUNTRY = country autocomplete + flag rendering.
 */

enum class OptionType { NONE, COUNTRY }

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
}
