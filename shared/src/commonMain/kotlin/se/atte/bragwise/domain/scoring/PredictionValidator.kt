package se.atte.bragwise.domain.scoring

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.PredictionPayload

fun isValidPayload(bet: Bet, payload: PredictionPayload): Boolean {
    if (payload::class != bet.expectedPayloadClass()) return false
    return when (bet) {
        is Bet.SinglePick -> {
            val p = payload as? PredictionPayload.SinglePick ?: return false
            bet.options.any { it.id == p.optionId }
        }
        is Bet.BooleanProp -> payload is PredictionPayload.BooleanProp
        is Bet.Ranking -> {
            val p = payload as? PredictionPayload.Ranking ?: return false
            val ordered = p.orderedOptionIds
            if (ordered.size != bet.topN) return false
            val ids = bet.options.map { it.id }.toSet()
            val seen = mutableSetOf<String>()
            for (id in ordered) {
                if (id !in ids) return false
                if (!seen.add(id)) return false
            }
            true
        }
    }
}

sealed interface MapValidation {
    data object Valid : MapValidation
    data class Invalid(val code: String) : MapValidation
}

fun validatePredictionMap(bets: List<Bet>, predMap: Map<String, PredictionPayload>): MapValidation {
    val betById = bets.associateBy { it.id }
    for ((betId, payload) in predMap) {
        val bet = betById[betId] ?: return MapValidation.Invalid("unknown-bet-id")
        if (!isValidPayload(bet, payload)) return MapValidation.Invalid("invalid-payload")
    }
    if (predMap.size != bets.size) return MapValidation.Invalid("incomplete-predictions")
    return MapValidation.Valid
}

private fun Bet.expectedPayloadClass() = when (this) {
    is Bet.SinglePick -> PredictionPayload.SinglePick::class
    is Bet.Ranking -> PredictionPayload.Ranking::class
    is Bet.BooleanProp -> PredictionPayload.BooleanProp::class
}
