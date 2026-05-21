package se.atte.bragwise.ui.screens.bets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.components.SectionCard

/**
 * MC-04 Challenge summary — read-only recap of the user's predictions
 * and (when results are posted) per-bet points.
 */
@Composable
fun ChallengeSummaryScreen(
    viewModel: BetListViewModel,
    onEdit: (challengeId: String) -> Unit,
    onLeaderboard: (challengeId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val ui = state.ui) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty, is UiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text((ui as? UiState.Failed)?.cause?.toUserMessage() ?: "No predictions yet")
        }
        is UiState.Ready -> Content(
            detail = ui.data,
            onEdit = { onEdit(ui.data.challenge.id) },
            onLeaderboard = { onLeaderboard(ui.data.challenge.id) },
        )
    }
}

@Composable
private fun Content(
    detail: ChallengeDetail,
    onEdit: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = detail.challenge.title) {
                    val total = detail.challenge.leaderboard?.values?.maxOrNull() ?: 0
                    Text(
                        text = "Bets: ${detail.challenge.bets.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Your rank: ${detail.myRank?.toString() ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (detail.challenge.status == ChallengeStatus.RESULTS_POSTED) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Top score: $total",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(items = detail.challenge.bets, key = { it.id }) { bet ->
                ListGroup {
                    ListRow(
                        title = bet.title,
                        subtitle = renderPick(bet, detail.myPredictions[bet.id]),
                        trailing = renderPoints(bet, detail),
                    )
                }
            }
            // BetCard ranking summary deferred — keep table-row form for now.
        }
        BottomActionBar {
            val isOpen = detail.challenge.status == ChallengeStatus.OPEN
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = if (isOpen) onEdit else onLeaderboard,
            ) {
                Text(if (isOpen) "Edit predictions" else "View leaderboard")
            }
        }
    }
}

private fun renderPick(bet: Bet, payload: PredictionPayload?): String {
    payload ?: return "Not predicted"
    return when (bet) {
        is Bet.SinglePick -> {
            val id = (payload as? PredictionPayload.SinglePick)?.optionId
            bet.options.firstOrNull { it.id == id }?.label ?: "—"
        }
        is Bet.BooleanProp -> if ((payload as? PredictionPayload.BooleanProp)?.value == true) "Yes" else "No"
        is Bet.Ranking -> {
            val ids = (payload as? PredictionPayload.Ranking)?.orderedOptionIds.orEmpty()
            ids.mapIndexedNotNull { i, oid ->
                val label = bet.options.firstOrNull { it.id == oid }?.label ?: return@mapIndexedNotNull null
                "${i + 1}. $label"
            }.joinToString(", ")
        }
    }
}

private fun renderPoints(bet: Bet, detail: ChallengeDetail): String? {
    if (detail.challenge.status != ChallengeStatus.RESULTS_POSTED) return "›"
    val results = detail.challenge.results ?: return "›"
    val pred = detail.myPredictions[bet.id] ?: return "0"
    val result = results[bet.id] ?: return "—"
    val pts = se.atte.bragwise.domain.scoring.ScoringEngine.score(bet, pred, result)
    return pts.toString()
}
