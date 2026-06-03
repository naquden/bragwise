package se.atte.bragwise.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.platform.PlatformShare
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.betPoints
import se.atte.bragwise.ui.compactPick
import se.atte.bragwise.ui.predictedCount
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.components.RankChip
import se.atte.bragwise.ui.components.SectionCard
import kotlin.time.Instant

@Composable
fun ChallengeDetailScreen(
    viewModel: ChallengeDetailViewModel,
    platformShare: PlatformShare,
    snackbarHostState: SnackbarHostState,
    onNavigateToBet: (String) -> Unit,
    onNavigateToLeaderboard: (String) -> Unit,
    onNavigateToBetList: (String) -> Unit,
    onNavigateToSummary: (String) -> Unit,
    onNavigateToManage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is ChallengeDetailViewModel.Effect.GoToBet -> onNavigateToBet(effect.betId)
            is ChallengeDetailViewModel.Effect.GoToLeaderboard -> onNavigateToLeaderboard(effect.challengeId)
            is ChallengeDetailViewModel.Effect.GoToBetList -> onNavigateToBetList(effect.challengeId)
            is ChallengeDetailViewModel.Effect.GoToSummary -> onNavigateToSummary(effect.challengeId)
            is ChallengeDetailViewModel.Effect.GoToManage -> onNavigateToManage(effect.challengeId)
            is ChallengeDetailViewModel.Effect.ShareLink -> {
                val (title, subject) = when (val msg = effect.message) {
                    is ChallengeDetailViewModel.ShareMessage.ChallengeShare ->
                        msg.challengeTitle to "${msg.challengeTitle} on Bragwise"
                }
                platformShare.send(effect.url, title, subject)
            }
            is ChallengeDetailViewModel.Effect.Snackbar -> {
                val text = when (effect.message) {
                    ChallengeDetailViewModel.SnackbarMessage.ShareFailed -> "Couldn't share challenge"
                }
                snackbarHostState.showSnackbar(text)
            }
        }
    }

    when (val ui = state.ui) {
        UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No challenge")
        }
        is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                    text = ui.cause.toUserMessage(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is UiState.Ready -> DetailContent(
            data = ui.data,
            isOwner = state.isOwner,
            onPredict = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenPredict) },
            onBet = { id -> viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenBet(id)) },
            onLeaderboard = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenLeaderboard) },
            onBetList = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenBetList) },
            onSummary = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenSummary) },
            onManage = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenManage) },
            onShare = { viewModel.onIntent(ChallengeDetailViewModel.Intent.Share) },
        )
    }
}

@Composable
private fun DetailContent(
    data: ChallengeDetail,
    isOwner: Boolean,
    onPredict: () -> Unit,
    onBet: (String) -> Unit,
    onLeaderboard: () -> Unit,
    onBetList: () -> Unit,
    onSummary: () -> Unit,
    onManage: () -> Unit,
    onShare: () -> Unit,
) {
    val joined = data.myPredictions.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
            verticalArrangement = Arrangement.spacedBy(standardPadding),
        ) {
            item {
                SectionCard {
                    Text(text = data.challenge.title, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(standardPaddingSmall))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Your rank",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            if (data.myRank != null) {
                                RankChip(
                                    rank = data.myRank,
                                    total = data.challenge.joinedCount,
                                )
                            } else {
                                Text(
                                    text = "—— / ${data.challenge.joinedCount}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Predicted",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${data.predictedCount()} / ${data.challenge.bets.size}",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }

            if (data.challenge.bets.isNotEmpty()) {
                item {
                    Text(
                        text = "Bets",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    ListGroup {
                        data.challenge.bets.forEachIndexed { index, bet ->
                            BetListRow(
                                number = index + 1,
                                bet = bet,
                                detail = data,
                                onClick = { onBet(bet.id) },
                            )
                            if (index < data.challenge.bets.size - 1) ListGroupDivider()
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
                ) {
                    AppOutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onLeaderboard,
                    ) { Text("Leaderboard") }
                    AppOutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onShare,
                    ) { Text("Share") }
                }
            }

            if (data.challenge.bets.size > 1) {
                item {
                    AppOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onBetList,
                    ) { Text("See all bets") }
                }
            }

            if (joined) {
                item {
                    AppOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSummary,
                    ) { Text("See summary") }
                }
            }

            if (isOwner) {
                item {
                    AppOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onManage,
                    ) { Text("Manage challenge") }
                }
            }
        }

        BottomActionBar {
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPredict,
            ) { Text(if (joined) "Edit predictions" else "Make predictions") }
        }
    }
}

@Composable
private fun BetListRow(number: Int, bet: Bet, detail: ChallengeDetail, onClick: () -> Unit) {
    val pick = compactPick(bet = bet, payload = detail.myPredictions[bet.id])
    val points = betPoints(bet = bet, detail = detail)
    val status = detail.challenge.status

    val subtitle = when {
        pick != null -> pick
        status == ChallengeStatus.LOCKED -> "Locked · no prediction"
        else -> "Not predicted yet"
    }
    val trailing = points?.let { "$it pt" } ?: "›"

    ListRow(
        title = bet.title,
        subtitle = subtitle,
        leading = number.toString(),
        trailing = trailing,
        titleFontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        onClick = onClick,
    )
}

// region Previews

private val previewChallenge = Challenge(
    id = "c1",
    title = "World Cup 2026 Predictions",
    description = "Predict the outcomes",
    category = "sport",
    visibility = Visibility.FRIENDS,
    createdBy = "u1",
    createdAt = Instant.fromEpochSeconds(0),
    locksAt = null,
    resultsPostedAt = null,
    status = ChallengeStatus.OPEN,
    joinedCount = 12,
    promoted = false,
    trusted = false,
    bets = listOf(
        Bet.BooleanProp(id = "b1", title = "Will Argentina win the final?"),
        Bet.SinglePick(
            id = "b2",
            title = "Top scorer",
            options = listOf(BetOption("o1", "Mbappe"), BetOption("o2", "Messi"), BetOption("o3", "Haaland")),
        ),
        Bet.Ranking(
            id = "b3",
            title = "Group A - top 2",
            topN = 2,
            options = listOf(
                BetOption("g1", "France"),
                BetOption("g2", "Belgium"),
                BetOption("g3", "Croatia"),
                BetOption("g4", "Senegal"),
            ),
        ),
    ),
    results = null,
    leaderboard = null,
)

@Preview
@Composable
private fun Detail_Loading_Preview() {
    ThemePreview {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Preview
@Composable
private fun Detail_Ready_NotJoined_Preview() {
    ThemePreview {
        DetailContent(
            data = ChallengeDetail(challenge = previewChallenge, myPredictions = emptyMap(), myRank = null),
            isOwner = false,
            onPredict = {},
            onBet = {},
            onLeaderboard = {},
            onBetList = {},
            onSummary = {},
            onManage = {},
            onShare = {},
        )
    }
}

@Preview
@Composable
private fun Detail_Ready_Joined_Preview() {
    ThemePreview {
        DetailContent(
            data = ChallengeDetail(
                challenge = previewChallenge,
                myPredictions = mapOf(
                    "b1" to PredictionPayload.BooleanProp(true),
                    "b3" to PredictionPayload.Ranking(listOf("g1", "g2")),
                ),
                myRank = 3,
            ),
            isOwner = true,
            onPredict = {},
            onBet = {},
            onLeaderboard = {},
            onBetList = {},
            onSummary = {},
            onManage = {},
            onShare = {},
        )
    }
}

// endregion

