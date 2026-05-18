package se.atte.bragwise.ui.screens.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingLarge
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.ChallengeCard
import kotlin.time.Instant

@Composable
fun ChallengesScreen(
    viewModel: ChallengesViewModel,
    onNavigateToChallenge: (String) -> Unit,
    onNavigateToCreate: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChallengesViewModel.Effect.GoToChallenge -> onNavigateToChallenge(effect.id)
                ChallengesViewModel.Effect.GoToCreate -> onNavigateToCreate()
            }
        }
    }

    androidx.compose.animation.Crossfade(
        targetState = state.ui,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "challenges-ui",
    ) { ui ->
        ChallengesContentRoot(
            ui = ui,
            onCreate = onNavigateToCreate,
            onChallenge = { viewModel.onIntent(ChallengesViewModel.Intent.OpenChallenge(it)) },
        )
    }
}

@Composable
private fun ChallengesContentRoot(
    ui: UiState<ChallengesViewModel.Sections>,
    onCreate: () -> Unit,
    onChallenge: (String) -> Unit,
) {
    when (ui) {
        UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> EmptyState(onCreate = onCreate)
        is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = ui.cause.toUserMessage(authMessage = "Sign in to see challenges."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is UiState.Ready -> ChallengesContent(
            sections = ui.data,
            onChallenge = onChallenge,
        )
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(standardPaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "🎯", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(standardPadding))
        Text(
            text = "No challenges yet",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(standardPaddingSmall))
        Text(
            text = "Create one to start predicting with friends.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCreate,
        ) { Text("Create your first challenge") }
    }
}

@Composable
private fun ChallengesContent(
    sections: ChallengesViewModel.Sections,
    onChallenge: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
    ) {
        if (sections.mine.isNotEmpty()) {
            item { SectionHeader("My Challenges") }
            items(items = sections.mine, key = { it.id }) { ChallengeCard(challenge = it, onClick = { onChallenge(it.id) }) }
        }
        if (sections.promoted.isNotEmpty()) {
            item { SectionHeader("Promoted") }
            items(items = sections.promoted, key = { it.id }) { ChallengeCard(challenge = it, onClick = { onChallenge(it.id) }) }
        }
        if (sections.fromFriends.isNotEmpty()) {
            item { SectionHeader("From friends") }
            items(items = sections.fromFriends, key = { it.id }) { ChallengeCard(challenge = it, onClick = { onChallenge(it.id) }) }
        }
        if (sections.invites.isNotEmpty()) {
            item { SectionHeader("Invites") }
            items(items = sections.invites, key = { it.challengeId }) { invitation ->
                InvitationRow(invitation = invitation, onClick = onChallenge)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = standardPaddingSmall, bottom = 4.dp),
    )
}

@Composable
private fun InvitationRow(invitation: Invitation, onClick: (String) -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Column(Modifier.padding(standardPadding)) {
            Text(text = "Invitation to ${invitation.challengeId}", style = MaterialTheme.typography.titleLarge)
            Text(text = "from ${invitation.invitedBy}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// region Previews

private fun previewChallenge(id: String, title: String, promoted: Boolean = false) = Challenge(
    id = id,
    title = title,
    description = "",
    category = "sport",
    visibility = Visibility.FRIENDS,
    createdBy = "u1",
    createdAt = Instant.fromEpochSeconds(0),
    locksAt = Instant.DISTANT_FUTURE,
    resultsPostedAt = null,
    status = ChallengeStatus.OPEN,
    joinedCount = 4,
    promoted = promoted,
    trusted = false,
    bets = listOf(
        Bet.BooleanProp(id = "b1", title = "Will team A win?"),
        Bet.SinglePick(id = "b2", title = "Top scorer", options = listOf(
            BetOption("o1", "Alice"), BetOption("o2", "Bob"),
        )),
    ),
    results = null,
    leaderboard = null,
)

@Preview
@Composable
private fun Challenges_Loading_Preview() {
    ThemePreview { ChallengesContentRoot(ui = UiState.Loading, onCreate = {}, onChallenge = {}) }
}

@Preview
@Composable
private fun Challenges_Empty_Preview() {
    ThemePreview { ChallengesContentRoot(ui = UiState.Empty(), onCreate = {}, onChallenge = {}) }
}

@Preview
@Composable
private fun Challenges_Ready_Preview() {
    val sections = ChallengesViewModel.Sections(
        mine = listOf(previewChallenge("c1", "My Challenge")),
        promoted = listOf(previewChallenge("c2", "Promoted Challenge", promoted = true)),
        fromFriends = listOf(previewChallenge("c3", "Friend's Challenge")),
        invites = listOf(
            Invitation(
                challengeId = "c4",
                invitedUid = "u2",
                invitedBy = "u3",
                invitedAt = Instant.fromEpochSeconds(0),
            ),
        ),
    )
    ThemePreview {
        ChallengesContentRoot(ui = UiState.Ready(sections), onCreate = {}, onChallenge = {})
    }
}

// endregion
