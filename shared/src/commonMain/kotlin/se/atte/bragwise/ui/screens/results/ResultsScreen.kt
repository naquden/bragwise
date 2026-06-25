package se.atte.bragwise.ui.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.theme.LocalIsDark
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.domain.scoring.competitionRanks
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.mvi.UiState
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.results_are_in
import bragwise.shared.generated.resources.results_empty_body
import bragwise.shared.generated.resources.results_empty_title
import bragwise.shared.generated.resources.results_finished_count
import bragwise.shared.generated.resources.results_history
import bragwise.shared.generated.resources.results_menu_archive
import bragwise.shared.generated.resources.results_menu_mark_unseen
import bragwise.shared.generated.resources.results_new_count
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trophy
import se.atte.bragwise.theme.LocalSectionColors
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.ui.components.ChallengeCard
import se.atte.bragwise.ui.components.ColoredSection
import se.atte.bragwise.ui.components.PlatinumBackground
import se.atte.bragwise.ui.icons.LucideSparkles
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
        onArchive = { viewModel.onIntent(ResultsViewModel.Intent.Archive(challengeId = it)) },
        onMarkUnseen = { viewModel.onIntent(ResultsViewModel.Intent.MarkUnseen(challengeId = it)) },
    )
}

@Composable
private fun ResultsBody(
    state: ResultsViewModel.State,
    onChallenge: (String) -> Unit,
    onArchive: (String) -> Unit,
    onMarkUnseen: (String) -> Unit,
) {
    val isDark = LocalIsDark.current
    val scrim = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.40f)
    Box(modifier = Modifier.fillMaxSize()) {
        PlatinumBackground(modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(scrim))
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
                    Icon(imageVector = Lucide.Trophy, contentDescription = null, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(standardPadding))
                    Text(
                        text = stringResource(Res.string.results_empty_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(standardPaddingSmall))
                    Text(
                        text = stringResource(Res.string.results_empty_body),
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
            is UiState.Ready -> ResultsContent(
                sections = ui.data,
                onChallenge = onChallenge,
                onArchive = onArchive,
                onMarkUnseen = onMarkUnseen,
            )
        }
    }
}

@Composable
private fun ResultsContent(
    sections: ResultsViewModel.Sections,
    onChallenge: (String) -> Unit,
    onArchive: (String) -> Unit,
    onMarkUnseen: (String) -> Unit,
) {
    val sc = LocalSectionColors.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            if (sections.unseen.isNotEmpty()) {
                ColoredSection(
                    bg = sc.resultsBgHeader,
                    title = stringResource(Res.string.results_are_in),
                    icon = "",
                    iconVector = Lucide.Trophy,
                    onTitleColor = sc.onResultsBgHeader,
                    trailing = stringResource(Res.string.results_new_count, sections.unseen.size),
                    topInset = true,
                ) {
                    sections.unseen.forEach { challenge ->
                        ResultCardWithMenu(
                            challenge = challenge,
                            rank = myRankFor(challenge = challenge, myUid = sections.myUid),
                            surfaceColor = sc.resultsCardFrost,
                            showMarkUnseen = false,
                            onClick = { onChallenge(challenge.id) },
                            onArchive = { onArchive(challenge.id) },
                            onMarkUnseen = { onMarkUnseen(challenge.id) },
                        )
                    }
                }
            }

            if (sections.history.isNotEmpty()) {
                ColoredSection(
                    bg = sc.resultsBgHeader,
                    title = stringResource(Res.string.results_history),
                    icon = "",
                    iconVector = LucideSparkles,
                    onTitleColor = sc.onResultsBgHeader,
                    trailing = stringResource(Res.string.results_finished_count, sections.history.size),
                    topInset = sections.unseen.isEmpty(),
                ) {
                    sections.history.forEach { challenge ->
                        ResultCardWithMenu(
                            challenge = challenge,
                            rank = myRankFor(challenge = challenge, myUid = sections.myUid),
                            surfaceColor = sc.resultsCardFrost,
                            showMarkUnseen = true,
                            onClick = { onChallenge(challenge.id) },
                            onArchive = { onArchive(challenge.id) },
                            onMarkUnseen = { onMarkUnseen(challenge.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ResultCardWithMenu(
    challenge: Challenge,
    rank: Int?,
    surfaceColor: androidx.compose.ui.graphics.Color,
    showMarkUnseen: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onMarkUnseen: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ChallengeCard(
            challenge = challenge,
            rank = rank,
            surfaceColor = surfaceColor,
            onClick = onClick,
            onLongClick = { menuOpen = true },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.results_menu_archive)) },
                onClick = {
                    onArchive()
                    menuOpen = false
                },
            )
            if (showMarkUnseen) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.results_menu_mark_unseen)) },
                    onClick = {
                        onMarkUnseen()
                        menuOpen = false
                    },
                )
            }
        }
    }
}

private fun myRankFor(challenge: Challenge, myUid: String): Int? {
    if (myUid.isEmpty()) return null
    val board = challenge.leaderboard ?: return null
    return competitionRanks(board).firstOrNull { it.uid == myUid }?.rank
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
            onArchive = {},
            onMarkUnseen = {},
        )
    }
}

// endregion
