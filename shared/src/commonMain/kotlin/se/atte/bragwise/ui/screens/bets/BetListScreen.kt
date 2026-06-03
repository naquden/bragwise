package se.atte.bragwise.ui.screens.bets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.preview.sampleDetail

/**
 * MC-02 Bet list — flat view of every bet in a challenge with prediction
 * status. Tapping a row routes to MC-03 Predict.
 */
@Composable
fun BetListScreen(
    viewModel: BetListViewModel,
    onOpenPredict: (challengeId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val ui = state.ui) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No bets yet")
        }
        is UiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ui.cause.toUserMessage())
        }
        is UiState.Ready -> BetListContent(ui.data) {
            onOpenPredict(ui.data.challenge.id)
        }
    }
}

@Composable
private fun BetListContent(detail: ChallengeDetail, onOpenPredict: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = detail.challenge.bets, key = { it.id }) { bet ->
            ListGroup {
                ListRow(
                    title = bet.title,
                    subtitle = statusLabel(bet, detail),
                    titleFontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    onClick = onOpenPredict,
                )
            }
        }
    }
}

private fun statusLabel(bet: Bet, detail: ChallengeDetail): String {
    val predicted = detail.myPredictions.containsKey(bet.id)
    val status = detail.challenge.status
    return when {
        status == ChallengeStatus.RESULTS_POSTED -> "Resolved"
        status == ChallengeStatus.LOCKED -> if (predicted) "Locked · predicted" else "Locked · no prediction"
        predicted -> "Predicted"
        else -> "Open"
    }
}

// region Previews

@Preview
@Composable
private fun BetList_Preview() {
    ThemePreview {
        BetListContent(detail = sampleDetail(), onOpenPredict = {})
    }
}

// endregion
