package se.atte.bragwise.ui

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.scoring.ScoringEngine

/**
 * Shared rendering of a user's prediction for a bet. Used by the challenge
 * detail entry screen (compact, single-line) and the summary recap (full).
 */

/**
 * Compact, single-line preview of the user's pick. Ranking picks are truncated
 * to [maxItems] labels with a "+N more" suffix so a long list stays one line.
 * Returns null when the user has not predicted this bet.
 */
fun compactPick(bet: Bet, payload: PredictionPayload?, maxItems: Int = 2): String? {
    payload ?: return null
    return when (bet) {
        is Bet.SinglePick -> {
            val optionId = (payload as? PredictionPayload.SinglePick)?.optionId
            bet.options.firstOrNull { it.id == optionId }?.label
        }
        is Bet.BooleanProp -> if ((payload as? PredictionPayload.BooleanProp)?.value == true) "Yes" else "No"
        is Bet.Ranking -> {
            val orderedIds = (payload as? PredictionPayload.Ranking)?.orderedOptionIds.orEmpty()
            val labels = orderedIds.mapNotNull { id -> bet.options.firstOrNull { it.id == id }?.label }
            if (labels.isEmpty()) {
                null
            } else {
                val shown = labels.take(maxItems).joinToString(", ")
                val remaining = labels.size - maxItems
                if (remaining > 0) "$shown +$remaining more" else shown
            }
        }
    }
}

/**
 * Full pick rendering with ordinal prefixes for ranking bets, used by the
 * read-only summary recap. Returns "Not predicted" when there is no payload.
 */
fun fullPick(bet: Bet, payload: PredictionPayload?): String {
    payload ?: return "Not predicted"
    return when (bet) {
        is Bet.SinglePick -> {
            val optionId = (payload as? PredictionPayload.SinglePick)?.optionId
            bet.options.firstOrNull { it.id == optionId }?.label ?: "—"
        }
        is Bet.BooleanProp -> if ((payload as? PredictionPayload.BooleanProp)?.value == true) "Yes" else "No"
        is Bet.Ranking -> {
            val orderedIds = (payload as? PredictionPayload.Ranking)?.orderedOptionIds.orEmpty()
            orderedIds.mapIndexedNotNull { index, id ->
                val label = bet.options.firstOrNull { it.id == id }?.label ?: return@mapIndexedNotNull null
                "${index + 1}. $label"
            }.joinToString(", ")
        }
    }
}

/**
 * Points scored on a resolved bet, or null when results are not posted yet or
 * the specific bet has no posted result. Returns 0 when the user did not predict.
 */
fun betPoints(bet: Bet, detail: ChallengeDetail): Int? {
    if (detail.challenge.status != ChallengeStatus.RESULTS_POSTED) return null
    val results = detail.challenge.results ?: return null
    val prediction = detail.myPredictions[bet.id] ?: return 0
    val result = results[bet.id] ?: return null
    return ScoringEngine.score(bet = bet, prediction = prediction, result = result)
}

/** Number of bets in the challenge the user has predicted. */
fun ChallengeDetail.predictedCount(): Int = challenge.bets.count { myPredictions.containsKey(it.id) }
