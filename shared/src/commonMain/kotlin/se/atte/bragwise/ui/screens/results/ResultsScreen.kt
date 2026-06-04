package se.atte.bragwise.ui.screens.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.LocalSectionColors
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.ui.components.ChallengeCard
import se.atte.bragwise.ui.components.ColoredSection
import kotlin.time.Instant

@Composable
fun ResultsScreen(viewModel: ResultsViewModel, onNavigateToReveal: (challengeId: String) -> Unit) {
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is ResultsViewModel.Effect.GoToReveal -> onNavigateToReveal(effect.challengeId)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultsBody(
        state = state,
        onChallenge = { viewModel.onIntent(ResultsViewModel.Intent.OpenReveal(challengeId = it)) },
    )
}

@Composable
private fun ResultsBody(state: ResultsViewModel.State, onChallenge: (String) -> Unit) {
    when (val ui = state.ui) {
        UiState.Loading -> Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🏆", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(standardPadding))
                Text(
                    text = "No results yet",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(standardPaddingSmall))
                Text(
                    text = "Finished challenges will appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is UiState.Failed -> Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ui.cause.toUserMessage(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is UiState.Ready -> ResultsContent(sections = ui.data, onChallenge = onChallenge)
    }
}

@Composable
private fun ResultsContent(sections: ResultsViewModel.Sections, onChallenge: (String) -> Unit) {
    val sc = LocalSectionColors.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        if (sections.unseen.isNotEmpty()) {
            ColoredSection(
                bg = sc.mineBg,
                title = "Results are in!",
                icon = "🏆",
                onTitleColor = sc.onMine,
                trailing = "${sections.unseen.size} new",
                topInset = true,
            ) {
                sections.unseen.forEach { challenge ->
                    ChallengeCard(
                        challenge = challenge,
                        rank = myRankFor(challenge = challenge, myUid = sections.myUid),
                        onClick = { onChallenge(challenge.id) },
                        surfaceColor = sc.mineCard,
                    )
                }
            }
        }

        if (sections.history.isNotEmpty()) {
            ColoredSection(
                bg = sc.historyBg,
                title = "History",
                icon = "🕐",
                onTitleColor = sc.onHistory,
                trailing = "${sections.history.size} finished",
                topInset = sections.unseen.isEmpty(),
            ) {
                sections.history.forEach { challenge ->
                    ChallengeCard(
                        challenge = challenge,
                        rank = myRankFor(challenge = challenge, myUid = sections.myUid),
                        onClick = { onChallenge(challenge.id) },
                        surfaceColor = sc.historyCard,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun myRankFor(challenge: Challenge, myUid: String): Int? {
    if (myUid.isEmpty()) return null
    val board = challenge.leaderboard ?: return null
    return board.entries
        .sortedByDescending { it.value }
        .indexOfFirst { it.key == myUid }
        .takeIf { it >= 0 }
        ?.let { it + 1 }
}

// region Previews

private fun previewChallenge(id: String, title: String) = Challenge(
    id = id,
    title = title,
    description = "",
    category = "sport",
    visibility = Visibility.FRIENDS,
    createdBy = "u1",
    createdAt = Instant.fromEpochSeconds(0),
    locksAt = null,
    resultsPostedAt = Instant.fromEpochSeconds(1000),
    status = ChallengeStatus.RESULTS_POSTED,
    joinedCount = 4,
    promoted = false,
    trusted = false,
    bets = emptyList(),
    results = mapOf("b1" to se.atte.bragwise.domain.PredictionPayload.BooleanProp(value = true)),
    leaderboard = mapOf("u1" to 3, "u2" to 2, "u3" to 1),
)

@Preview
@Composable
private fun ResultsContent_Preview() {
    ThemePreview {
        ResultsContent(
            sections = ResultsViewModel.Sections(
                unseen = listOf(previewChallenge(id = "c1", title = "Champions League Final")),
                history = listOf(previewChallenge(id = "c2", title = "Oscars 2026")),
                myUid = "u1",
            ),
            onChallenge = {},
        )
    }
}

// endregion
