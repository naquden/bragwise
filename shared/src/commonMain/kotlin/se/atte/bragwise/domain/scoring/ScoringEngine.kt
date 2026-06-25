package se.atte.bragwise.domain.scoring

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.PredictionPayload

/**
 * Phase 1 scoring is uniform: 1 point per correct.
 * Mirrored as a TypeScript port in Cloud Functions for authoritative scoring;
 * fixture-driven parity test enforces agreement.
 *
 * NOTE: Bet.Guess with closest=true is NOT scored here — it requires all players'
 * predictions and is handled by the leaderboard aggregator (computeLeaderboard in
 * leaderboard.ts / the Kotlin equivalent). This function returns 0 for such bets
 * so the aggregator can handle them without double-counting.
 */
object ScoringEngine {
    fun score(
        bet: Bet,
        prediction: PredictionPayload,
        result: PredictionPayload,
    ): Int = when (bet) {
        is Bet.SinglePick -> {
            val p = prediction as PredictionPayload.SinglePick
            val r = result as PredictionPayload.SinglePick
            if (p.optionId == r.optionId) 1 else 0
        }
        is Bet.BooleanProp -> {
            val p = prediction as PredictionPayload.BooleanProp
            val r = result as PredictionPayload.BooleanProp
            if (p.value == r.value) 1 else 0
        }
        is Bet.Ranking -> {
            val p = (prediction as PredictionPayload.Ranking).orderedOptionIds
            val r = (result as PredictionPayload.Ranking).orderedOptionIds
            p.zip(r).count { (a, b) -> a == b }
        }
        is Bet.Guess -> {
            if (bet.closest) {
                // Closest-wins is cross-player; scored by leaderboard aggregator, not here.
                0
            } else {
                val p = prediction as PredictionPayload.Guess
                val r = result as PredictionPayload.Guess
                if (p.value == r.value) 1 else 0
            }
        }
        is Bet.MultiSelect -> {
            val p = (prediction as PredictionPayload.MultiSelect).selectedOptionIds.toSet()
            val r = (result as PredictionPayload.MultiSelect).selectedOptionIds.toSet()
            val correct = p.count { it in r }
            val wrong = p.count { it !in r }
            correct - wrong
        }
        is Bet.OverUnder -> {
            val p = prediction as PredictionPayload.OverUnder
            val r = result as PredictionPayload.OverUnder
            val actualValue = r.actualValue
            val predictedOver = p.over
            if (actualValue == null || predictedOver == null) {
                0
            } else when {
                actualValue == bet.line -> 0  // push
                predictedOver && actualValue > bet.line -> 1
                !predictedOver && actualValue < bet.line -> 1
                else -> 0
            }
        }
    }
}
