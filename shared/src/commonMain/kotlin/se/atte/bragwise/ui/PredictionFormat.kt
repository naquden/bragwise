package se.atte.bragwise.ui

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ScoringMode
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.GuessGranularity
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
        is Bet.Guess -> (payload as? PredictionPayload.Guess)?.let { formatGuessValue(it.value, bet.granularity) }
        is Bet.MultiSelect -> {
            val ids = (payload as? PredictionPayload.MultiSelect)?.selectedOptionIds.orEmpty()
            val labels = ids.mapNotNull { id -> bet.options.firstOrNull { it.id == id }?.label }
            if (labels.isEmpty()) "None" else {
                val shown = labels.take(maxItems).joinToString(", ")
                val remaining = labels.size - maxItems
                if (remaining > 0) "$shown +$remaining more" else shown
            }
        }
        is Bet.OverUnder -> {
            val p = payload as? PredictionPayload.OverUnder
            p?.actualValue?.let { "Actual: $it" }
                ?: p?.over?.let { if (it) "Over" else "Under" }
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
        is Bet.Guess -> (payload as? PredictionPayload.Guess)?.let { formatGuessValue(it.value, bet.granularity) } ?: "—"
        is Bet.MultiSelect -> {
            val ids = (payload as? PredictionPayload.MultiSelect)?.selectedOptionIds.orEmpty()
            val labels = ids.mapNotNull { id -> bet.options.firstOrNull { it.id == id }?.label }
            if (labels.isEmpty()) "None" else labels.joinToString(", ")
        }
        is Bet.OverUnder -> {
            val p = payload as? PredictionPayload.OverUnder
            p?.actualValue?.let { "Actual: $it" }
                ?: p?.over?.let { if (it) "Over" else "Under" }
                ?: "—"
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
    // Placement challenges award N..1 pts cross-player; the client cannot compute
    // these without all players' predictions. Leaderboard/podium are the source of truth.
    if (detail.challenge.scoringMode == ScoringMode.PLACEMENT) return null
    // Closest-wins Guess bets are scored cross-player by the leaderboard aggregator;
    // the client cannot compute the point without all players' predictions.
    if (bet is Bet.Guess && bet.closest) return null
    return ScoringEngine.score(bet = bet, prediction = prediction, result = result)
}

/**
 * True when a payload is present and valid for [bet].
 * Ranking requires all [Bet.Ranking.topN] slots filled with known, distinct option ids.
 */
fun PredictionPayload?.isCompleteFor(bet: Bet, isResult: Boolean = false): Boolean = when (bet) {
    is Bet.Ranking -> {
        val ids = (this as? PredictionPayload.Ranking)?.orderedOptionIds.orEmpty()
        val validIds = bet.options.map { it.id }.toSet()
        ids.count { it.isNotEmpty() && it in validIds } == bet.topN
    }
    is Bet.SinglePick -> this is PredictionPayload.SinglePick && bet.options.any { it.id == this.optionId }
    is Bet.BooleanProp -> this is PredictionPayload.BooleanProp
    is Bet.Guess -> this is PredictionPayload.Guess
    is Bet.MultiSelect -> this is PredictionPayload.MultiSelect  // empty selection is valid
    is Bet.OverUnder -> {
        val p = this as? PredictionPayload.OverUnder ?: return false
        if (isResult) p.actualValue != null else p.over != null
    }
}

/** Number of bets the user has a valid complete prediction for. */
fun ChallengeDetail.predictedCount(): Int = challenge.bets.count { myPredictions[it.id].isCompleteFor(it) }

/** Strips empty-slot ("") sentinels from a Ranking payload before persistence. Identity for other kinds. */
fun PredictionPayload.withoutEmptySlots(): PredictionPayload = when (this) {
    is PredictionPayload.Ranking -> PredictionPayload.Ranking(orderedOptionIds.filter { it.isNotEmpty() })
    else -> this
}

/** Formats a Guess value for display. TIME: "HH:mm"; DAY: "yyyy-MM-dd" (UTC); NUMBER: plain integer string. */
fun formatGuessValue(value: Long, granularity: GuessGranularity): String = when (granularity) {
    GuessGranularity.NUMBER -> value.toString()
    GuessGranularity.TIME -> {
        val h = (value / 60).toInt()
        val m = (value % 60).toInt()
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }
    GuessGranularity.DAY -> {
        val totalDays = value
        // Compute year/month/day from epoch-day using proleptic Gregorian calendar.
        val z = totalDays + 719468L
        val era = (if (z >= 0) z else z - 146096L) / 146097L
        val doe = (z - era * 146097L).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400L
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val yr = if (m <= 2) y + 1 else y
        "${yr}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }
}
