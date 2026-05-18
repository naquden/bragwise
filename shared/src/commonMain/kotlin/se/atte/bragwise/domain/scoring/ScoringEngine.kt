package se.atte.bragwise.domain.scoring

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.PredictionPayload

/**
 * Phase 1 scoring is uniform: 1 point per correct.
 * Mirrored as a TypeScript port in Cloud Functions for authoritative scoring;
 * fixture-driven parity test enforces agreement.
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
    }
}
