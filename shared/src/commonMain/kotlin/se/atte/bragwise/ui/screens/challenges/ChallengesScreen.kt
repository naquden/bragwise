package se.atte.bragwise.ui.screens.challenges

import androidx.compose.foundation.background
import se.atte.bragwise.theme.LocalIsDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.cl_active_count
import se.atte.bragwise.ui.icons.BragIconWithRing
import bragwise.shared.generated.resources.cl_active_count_one
import bragwise.shared.generated.resources.cl_empty
import bragwise.shared.generated.resources.cl_empty_body
import bragwise.shared.generated.resources.cl_empty_create_first
import bragwise.shared.generated.resources.cl_section_from_friends
import bragwise.shared.generated.resources.cl_section_invites
import bragwise.shared.generated.resources.cl_section_mine
import bragwise.shared.generated.resources.cl_section_promoted
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.Elevation
import se.atte.bragwise.theme.LocalSectionColors
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.theme.appShadow
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.ChallengeCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UsersRound
import se.atte.bragwise.ui.components.ColoredSection
import se.atte.bragwise.ui.components.WaveSeparator
import se.atte.bragwise.ui.icons.BragIcon
import kotlin.time.Instant

@Composable
fun ChallengesScreen(
    viewModel: ChallengesViewModel,
    onNavigateToChallenge: (String) -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToDraft: (String) -> Unit,
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
            onDraft = onNavigateToDraft,
        )
    }
}

@Composable
private fun ChallengesContentRoot(
    ui: UiState<ChallengesViewModel.Sections>,
    onCreate: () -> Unit,
    onChallenge: (String) -> Unit,
    onDraft: (String) -> Unit,
) {
    when (ui) {
        UiState.Loading -> Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> EmptyState(onCreate = onCreate)
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
        is UiState.Ready -> ChallengesContent(
            sections = ui.data,
            onChallenge = onChallenge,
            onDraft = onDraft,
        )
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(standardPaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = BragIconWithRing, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(standardPadding))
        Text(
            text = stringResource(Res.string.cl_empty),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(standardPaddingSmall))
        Text(
            text = stringResource(Res.string.cl_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCreate,
        ) { Text(stringResource(Res.string.cl_empty_create_first)) }
    }
}

private data class SectionEntry(val bg: Color, val render: @Composable (topInset: Boolean) -> Unit)

@Composable
private fun ChallengesContent(
    sections: ChallengesViewModel.Sections,
    onChallenge: (String) -> Unit,
    onDraft: (String) -> Unit,
) {
    val joinedIds = sections.joinedIds
    val sc = LocalSectionColors.current
    val entries = buildList {
        if (sections.mine.isNotEmpty()) add(SectionEntry(sc.mineBg) { topInset ->
            ColoredSection(
                bg = sc.mineBg,
                title = stringResource(Res.string.cl_section_mine),
                icon = "",
                iconVector = BragIcon,
                onTitleColor = sc.onMine,
                trailing = if (sections.mine.size == 1) stringResource(Res.string.cl_active_count_one) else stringResource(Res.string.cl_active_count, sections.mine.size),
                topInset = topInset,
            ) {
                sections.mine.forEach { c ->
                    val onClick = if (c.status == ChallengeStatus.DRAFT) {
                        { onDraft(c.id) }
                    } else {
                        { onChallenge(c.id) }
                    }
                    ChallengeCard(challenge = c, predicted = c.id in joinedIds, onClick = onClick, surfaceColor = sc.mineCard)
                }
            }
        })
        if (sections.promoted.isNotEmpty()) add(SectionEntry(sc.promotedBg) { topInset ->
            ColoredSection(bg = sc.promotedBg, title = stringResource(Res.string.cl_section_promoted), icon = "⭐", onTitleColor = sc.onPromoted, topInset = topInset) {
                sections.promoted.forEach { c ->
                    ChallengeCard(challenge = c, predicted = c.id in joinedIds, onClick = { onChallenge(c.id) }, accent = true, surfaceColor = sc.promotedCard)
                }
            }
        })
        if (sections.fromFriends.isNotEmpty()) add(SectionEntry(sc.friendsBg) { topInset ->
            ColoredSection(bg = sc.friendsBg, title = stringResource(Res.string.cl_section_from_friends), icon = "👥", onTitleColor = sc.onFriends, iconVector = Lucide.UsersRound, topInset = topInset) {
                sections.fromFriends.forEach { c ->
                    ChallengeCard(challenge = c, predicted = c.id in joinedIds, onClick = { onChallenge(c.id) }, surfaceColor = sc.friendsCard)
                }
            }
        })
        if (sections.invites.isNotEmpty()) add(SectionEntry(sc.invitesBg) { topInset ->
            ColoredSection(bg = sc.invitesBg, title = stringResource(Res.string.cl_section_invites), icon = "✉️", onTitleColor = sc.onInvites, topInset = topInset) {
                sections.invites.forEach { inv ->
                    InvitationRow(invitation = inv, onClick = onChallenge, surfaceColor = sc.invitesCard)
                }
            }
        })
    }

    val lastBg = entries.lastOrNull()?.bg ?: MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize().background(lastBg)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            entries.forEachIndexed { i, entry ->
                if (i > 0) {
                    WaveSeparator(topColor = entries[i - 1].bg, bottomColor = entry.bg)
                }
                entry.render(i == 0)
            }
        }
    }
}

@Composable
private fun InvitationRow(invitation: Invitation, onClick: (String) -> Unit, surfaceColor: Color) {
    val isDark = LocalIsDark.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .appShadow(Elevation.Card, isDark = isDark, shape = shape)
            .clickable { onClick(invitation.challengeId) },
        color = surfaceColor,
        shape = shape,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Spacer(Modifier.width(standardPadding))
            Text(
                text = "✉️",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.width(standardPaddingSmall))
            Column(Modifier.padding(vertical = standardPadding, horizontal = 0.dp)) {
                Text(
                    text = "Invitation to ${invitation.challengeId}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "from ${invitation.invitedBy}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    locksAt = null,
    resultsPostedAt = null,
    status = ChallengeStatus.OPEN,
    joinedCount = 4,
    promoted = promoted,
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
private fun Challenges_Empty_Preview() {
    ThemePreview { ChallengesContentRoot(ui = UiState.Empty(), onCreate = {}, onChallenge = {}, onDraft = {}) }
}

@Preview
@Composable
private fun Challenges_Ready_Preview() {
    val sections = ChallengesViewModel.Sections(
        mine = listOf(previewChallenge("c1", "My Challenge")),
        promoted = listOf(previewChallenge("c2", "Promoted Challenge", promoted = true)),
        fromFriends = listOf(previewChallenge("c3", "Friend's Challenge")),
        joinedIds = setOf("c1"),
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
        ChallengesContentRoot(ui = UiState.Ready(sections), onCreate = {}, onChallenge = {}, onDraft = {})
    }
}

// endregion
