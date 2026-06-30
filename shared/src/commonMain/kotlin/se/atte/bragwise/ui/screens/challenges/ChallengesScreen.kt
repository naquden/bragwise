package se.atte.bragwise.ui.screens.challenges

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingLarge
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.Visibility
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.cl_active_count
import bragwise.shared.generated.resources.cl_active_count_one
import bragwise.shared.generated.resources.cl_empty_create_first
import bragwise.shared.generated.resources.cl_intro_create_alt
import bragwise.shared.generated.resources.cl_intro_create_body
import bragwise.shared.generated.resources.cl_intro_create_title
import bragwise.shared.generated.resources.cl_intro_predict_body
import bragwise.shared.generated.resources.cl_intro_predict_title
import bragwise.shared.generated.resources.cl_intro_title
import bragwise.shared.generated.resources.cl_intro_win_body
import bragwise.shared.generated.resources.cl_intro_win_title
import bragwise.shared.generated.resources.cl_invited_by
import bragwise.shared.generated.resources.cl_menu_clone
import bragwise.shared.generated.resources.cl_section_from_friends
import bragwise.shared.generated.resources.cl_section_invites
import bragwise.shared.generated.resources.cl_section_mine
import bragwise.shared.generated.resources.cl_section_promoted
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.LocalSectionColors
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.ChallengeCard
import se.atte.bragwise.ui.components.SectionCard
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.UsersRound
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Star
import se.atte.bragwise.ui.components.CardGrid
import se.atte.bragwise.ui.components.ColoredSection
import se.atte.bragwise.ui.components.WaveSeparator
import se.atte.bragwise.ui.contentColumns
import se.atte.bragwise.ui.icons.BragIcon
import kotlin.time.Instant

@Composable
fun ChallengesScreen(
    viewModel: ChallengesViewModel,
    onNavigateToChallenge: (String) -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToDraft: (String) -> Unit,
    onNavigateToClone: (String) -> Unit,
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
            onClone = onNavigateToClone,
        )
    }
}

@Composable
private fun ChallengesContentRoot(
    ui: UiState<ChallengesViewModel.Sections>,
    onCreate: () -> Unit,
    onChallenge: (String) -> Unit,
    onDraft: (String) -> Unit,
    onClone: (String) -> Unit,
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
            onCreate = onCreate,
            onClone = onClone,
        )
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IntroBanner(onCreate = onCreate, topInset = false)
    }
}

@Composable
private fun IntroBanner(onCreate: () -> Unit, topInset: Boolean) {
    val sc = LocalSectionColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (topInset) Modifier.statusBarsPadding() else Modifier)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 480.dp)
            .padding(horizontal = standardPadding, vertical = standardPaddingLarge),
    ) {
        SectionCard(title = stringResource(Res.string.cl_intro_title)) {
            Spacer(Modifier.height(standardPaddingSmall))
            IntroStep(
                icon = Lucide.Plus,
                tint = MaterialTheme.colorScheme.tertiary,
                badgeBg = sc.mineCard,
                title = stringResource(Res.string.cl_intro_create_title),
                body = stringResource(Res.string.cl_intro_create_body),
                subBody = stringResource(Res.string.cl_intro_create_alt),
            )
            Spacer(Modifier.height(standardPadding))
            IntroStep(
                icon = Lucide.CircleCheck,
                tint = MaterialTheme.colorScheme.primary,
                badgeBg = sc.friendsCard,
                title = stringResource(Res.string.cl_intro_predict_title),
                body = stringResource(Res.string.cl_intro_predict_body),
            )
            Spacer(Modifier.height(standardPadding))
            IntroStep(
                icon = Lucide.Trophy,
                tint = MaterialTheme.colorScheme.secondary,
                badgeBg = sc.promotedCard,
                title = stringResource(Res.string.cl_intro_win_title),
                body = stringResource(Res.string.cl_intro_win_body),
            )
        }
        Spacer(Modifier.height(standardPaddingLarge))
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCreate,
        ) { Text(stringResource(Res.string.cl_empty_create_first)) }
    }
}

@Composable
private fun IntroStep(
    icon: ImageVector,
    tint: Color,
    badgeBg: Color,
    title: String,
    body: String,
    subBody: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(badgeBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
        Spacer(Modifier.width(standardPadding))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subBody != null) {
                Text(
                    text = subBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private data class SectionEntry(val bg: Color, val render: @Composable (topInset: Boolean) -> Unit)

@Composable
private fun ChallengesContent(
    sections: ChallengesViewModel.Sections,
    onChallenge: (String) -> Unit,
    onDraft: (String) -> Unit,
    onCreate: () -> Unit,
    onClone: (String) -> Unit,
) {
    val columns = contentColumns()
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
                CardGrid(items = sections.mine, columns = columns) { c ->
                    val isDraft = c.status == ChallengeStatus.DRAFT
                    val onClick = if (isDraft) ({ onDraft(c.id) }) else ({ onChallenge(c.id) })
                    CloneableCard(
                        challenge = c,
                        predicted = c.id in joinedIds,
                        surfaceColor = sc.mineCard,
                        onClick = onClick,
                        showClone = !isDraft,
                        onClone = { onClone(c.id) },
                    )
                }
            }
        })
        if (sections.promoted.isNotEmpty()) add(SectionEntry(sc.promotedBg) { topInset ->
            ColoredSection(bg = sc.promotedBg, title = stringResource(Res.string.cl_section_promoted), icon = "", iconVector = Lucide.Star, onTitleColor = sc.onPromoted, topInset = topInset) {
                CardGrid(items = sections.promoted, columns = columns) { c ->
                    CloneableCard(
                        challenge = c,
                        predicted = c.id in joinedIds,
                        surfaceColor = sc.promotedCard,
                        accent = true,
                        onClick = { onChallenge(c.id) },
                        showClone = true,
                        onClone = { onClone(c.id) },
                    )
                }
            }
        })
        if (sections.fromFriends.isNotEmpty()) add(SectionEntry(sc.friendsBg) { topInset ->
            ColoredSection(bg = sc.friendsBg, title = stringResource(Res.string.cl_section_from_friends), icon = "👥", onTitleColor = sc.onFriends, iconVector = Lucide.UsersRound, topInset = topInset) {
                CardGrid(items = sections.fromFriends, columns = columns) { c ->
                    CloneableCard(
                        challenge = c,
                        predicted = c.id in joinedIds,
                        surfaceColor = sc.friendsCard,
                        onClick = { onChallenge(c.id) },
                        showClone = true,
                        onClone = { onClone(c.id) },
                    )
                }
            }
        })
        if (sections.invites.isNotEmpty()) add(SectionEntry(sc.invitesBg) { topInset ->
            ColoredSection(bg = sc.invitesBg, title = stringResource(Res.string.cl_section_invites), icon = "", iconVector = Lucide.Mail, onTitleColor = sc.onInvites, topInset = topInset) {
                CardGrid(items = sections.invites, columns = columns) { entry ->
                    ChallengeCard(
                        challenge = entry.challenge,
                        surfaceColor = sc.invitesCard,
                        onClick = { onChallenge(entry.challenge.id) },
                        caption = stringResource(Res.string.cl_invited_by, entry.invitedByName),
                    )
                }
            }
        })
    }

    val showIntro = sections.mine.isEmpty()
    val bgColor = MaterialTheme.colorScheme.background
    val lastBg = entries.lastOrNull()?.bg ?: bgColor
    Box(modifier = Modifier.fillMaxSize().background(lastBg)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (showIntro) {
                IntroBanner(onCreate = onCreate, topInset = entries.isEmpty())
            }
            entries.forEachIndexed { i, entry ->
                val prevBg = if (i > 0) entries[i - 1].bg else null
                if (prevBg != null) WaveSeparator(topColor = prevBg, bottomColor = entry.bg)
                entry.render(i == 0 && !showIntro)
            }
        }
    }
}

@Composable
private fun CloneableCard(
    challenge: Challenge,
    predicted: Boolean = false,
    surfaceColor: Color,
    accent: Boolean = false,
    onClick: () -> Unit,
    showClone: Boolean,
    onClone: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ChallengeCard(
            challenge = challenge,
            predicted = predicted,
            surfaceColor = surfaceColor,
            accent = accent,
            onClick = onClick,
            onLongClick = if (showClone) ({ menuOpen = true }) else null,
        )
        if (showClone) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.cl_menu_clone)) },
                    onClick = {
                        onClone()
                        menuOpen = false
                    },
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
    ThemePreview { ChallengesContentRoot(ui = UiState.Empty(), onCreate = {}, onChallenge = {}, onDraft = {}, onClone = {}) }
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
            ChallengesViewModel.InviteEntry(
                challenge = previewChallenge("c4", "Invite Challenge"),
                invitedByName = "Alice",
            ),
        ),
    )
    ThemePreview {
        ChallengesContentRoot(ui = UiState.Ready(sections), onCreate = {}, onChallenge = {}, onDraft = {}, onClone = {})
    }
}

// endregion
