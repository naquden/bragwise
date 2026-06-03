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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.betPoints
import se.atte.bragwise.ui.fullPick
import se.atte.bragwise.ui.preview.sampleDetail
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
                        subtitle = fullPick(bet = bet, payload = detail.myPredictions[bet.id]),
                        trailing = betPoints(bet = bet, detail = detail)?.toString() ?: "›",
                        titleFontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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

// region Previews

@Preview
@Composable
private fun ChallengeSummary_Open_Preview() {
    ThemePreview {
        Content(detail = sampleDetail(), onEdit = {}, onLeaderboard = {})
    }
}

@Preview
@Composable
private fun ChallengeSummary_ResultsPosted_Preview() {
    ThemePreview {
        Content(
            detail = sampleDetail(
                status = ChallengeStatus.RESULTS_POSTED,
                results = mapOf(
                    "b1" to se.atte.bragwise.domain.PredictionPayload.BooleanProp(true),
                    "b3" to se.atte.bragwise.domain.PredictionPayload.Ranking(listOf("g1", "g2")),
                ),
            ),
            onEdit = {},
            onLeaderboard = {},
        )
    }
}

// endregion
