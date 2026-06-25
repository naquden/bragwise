package se.atte.bragwise.ui.screens.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import bragwise.shared.generated.resources.Res
import se.atte.bragwise.ui.icons.BragIconWithRing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.REACTION_EMOJIS
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trophy
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.ui.components.AvatarBubble
import se.atte.bragwise.ui.components.Confetti
import se.atte.bragwise.ui.components.Podium
import se.atte.bragwise.ui.components.PointsPill
import se.atte.bragwise.ui.components.RankChip
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ResultsRevealScreen(
    viewModel: ResultsRevealViewModel,
    onParticipantClick: (uid: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showConfetti by remember { mutableStateOf(false) }
    ObserveEffects(effects = viewModel.effects) { effect ->
        when (effect) {
            ResultsRevealViewModel.Effect.PlayConfetti -> showConfetti = true
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when (val ui = state.ui) {
            UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No results yet")
            }
            is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = ui.cause.toUserMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is UiState.Ready -> ResultsRevealBody(
                data = ui.data,
                friendsOnly = state.friendsOnly,
                onToggleFriendsFilter = { viewModel.onIntent(ResultsRevealViewModel.Intent.ToggleFriendsFilter) },
                onReact = { emoji -> viewModel.onIntent(ResultsRevealViewModel.Intent.React(emoji)) },
                onParticipantClick = onParticipantClick,
            )
        }
        if (showConfetti) Confetti(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ResultsRevealBody(
    data: ResultsRevealViewModel.RevealData,
    friendsOnly: Boolean,
    onToggleFriendsFilter: () -> Unit,
    onReact: (emoji: String) -> Unit,
    onParticipantClick: (uid: String) -> Unit,
) {
    var animationPlayed by rememberSaveable { mutableStateOf(false) }
    val settled = data.alreadySeen || animationPlayed

    var showBanner by remember { mutableStateOf(settled) }
    var showField by remember { mutableStateOf(settled) }

    LaunchedEffect(Unit) {
        if (!settled) {
            animationPlayed = true
            delay(1700.milliseconds)
            showBanner = true
            delay(200.milliseconds)
            showField = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = standardPadding, vertical = standardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Lucide.Trophy, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Results are in!",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = data.challengeTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                Podium(
                    entries = data.leaderboard,
                    myUid = data.myUid,
                    alreadySeen = settled,
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = standardPadding,
                        vertical = standardPadding,
                    ),
                )
            }

            if (data.hasFriendsToFilter) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = standardPadding, vertical = standardPaddingSmall),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = !friendsOnly,
                            onClick = onToggleFriendsFilter,
                            label = { Text("Everyone") },
                        )
                        Spacer(Modifier.padding(horizontal = standardPaddingSmall / 2))
                        FilterChip(
                            selected = friendsOnly,
                            onClick = onToggleFriendsFilter,
                            label = { Text("Friends") },
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = showBanner,
                    enter = slideInVertically(
                        animationSpec = tween(durationMillis = 400),
                        initialOffsetY = { it / 2 },
                    ) + fadeIn(animationSpec = tween(durationMillis = 400)),
                ) {
                    YourResultBanner(
                        myRank = data.myRank,
                        myPoints = data.myPoints,
                        participantCount = data.displayedParticipantCount,
                        iAmWinner = data.iAmWinner,
                        iAmCreator = data.iAmCreator,
                        modifier = Modifier.padding(horizontal = standardPadding, vertical = standardPaddingSmall),
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = showBanner,
                    enter = slideInVertically(
                        animationSpec = tween(durationMillis = 400),
                        initialOffsetY = { it / 2 },
                    ) + fadeIn(animationSpec = tween(durationMillis = 400)),
                ) {
                    ReactionBar(
                        reactionCounts = data.reactionCounts,
                        myReaction = data.myReaction,
                        onReact = onReact,
                        modifier = Modifier.padding(horizontal = standardPadding, vertical = standardPaddingSmall),
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = showField,
                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = standardPadding, vertical = standardPaddingSmall))
                        val baseLabel = if (data.displayedLeaderboard.size > 10) "Top 10" else "All results"
                        val label = if (friendsOnly) "$baseLabel (friends)" else baseLabel
                        Text(
                            text = "$label (${data.displayedLeaderboard.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = standardPadding, vertical = standardPaddingSmall),
                        )
                    }
                }
            }

            if (showField) {
                itemsIndexed(items = data.fieldEntries, key = { _, entry -> entry.uid }) { index, entry ->
                    LeaderboardRow(
                        entry = entry,
                        isMe = entry.uid == data.myUid,
                        onClick = { onParticipantClick(entry.uid) },
                    )
                }

                data.myEntryOutsideField?.let { pinnedEntry ->
                    item(key = "pinned_self") {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = standardPadding))
                        LeaderboardRow(entry = pinnedEntry, isMe = true, onClick = { onParticipantClick(pinnedEntry.uid) })
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun YourResultBanner(
    myRank: Int?,
    myPoints: Int?,
    participantCount: Int,
    iAmWinner: Boolean,
    iAmCreator: Boolean,
    modifier: Modifier = Modifier,
) {
    data class BannerData(val emoji: String?, val headline: String, val subtitle: String)
    val (emoji, headline, subtitle) = when {
        iAmWinner -> BannerData("🥇", "You won!", "You topped the leaderboard")
        myRank == 2 -> BannerData("🥈", "Runner-up!", "#2 of $participantCount")
        myRank == 3 -> BannerData("🥉", "3rd place!", "#3 of $participantCount")
        myRank != null -> BannerData(null, "Your result", "#$myRank of $participantCount")
        iAmCreator -> BannerData("🎬", "You hosted this!", "Here's how your challenge played out")
        else -> BannerData(null, "You didn't predict", "Join next time to compete")
    }
    val surfaceColor = when {
        iAmWinner -> MaterialTheme.colorScheme.primaryContainer
        iAmCreator && myRank == null -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = surfaceColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(all = standardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(standardPadding),
        ) {
            if (emoji != null) {
                Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
            } else {
                Icon(
                    imageVector = BragIconWithRing,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (myPoints != null) PointsPill(points = myPoints)
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, isMe: Boolean, onClick: () -> Unit) {
    val backgroundModifier = if (isMe) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
    } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(backgroundModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = standardPadding, vertical = standardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        RankChip(rank = entry.rank)
        AvatarBubble(
            displayName = entry.displayName,
            avatarSeed = entry.avatarSeed,
            size = 36.dp,
            isHighlighted = isMe,
        )
        Text(
            text = if (isMe) "${entry.displayName} (you)" else entry.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        PointsPill(points = entry.points)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionBar(
    reactionCounts: Map<String, Int>,
    myReaction: String?,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
    ) {
        REACTION_EMOJIS.forEach { emoji ->
            val count = reactionCounts[emoji] ?: 0
            val selected = emoji == myReaction
            FilterChip(
                selected = selected,
                onClick = { onReact(emoji) },
                label = {
                    val label = if (count > 0) "$emoji $count" else emoji
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                },
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun ResultsRevealBody_Preview() {
    val entries = listOf(
        LeaderboardEntry(uid = "u1", displayName = "Atte", avatarSeed = "a1", points = 92, rank = 1),
        LeaderboardEntry(uid = "u2", displayName = "Alice", avatarSeed = "a3", points = 78, rank = 2),
        LeaderboardEntry(uid = "u3", displayName = "Bob", avatarSeed = "a5", points = 65, rank = 3),
        LeaderboardEntry(uid = "u4", displayName = "Carol", avatarSeed = "a7", points = 50, rank = 4),
    )
    ThemePreview {
        ResultsRevealBody(
            data = ResultsRevealViewModel.RevealData(
                challengeTitle = "Champions League Final",
                leaderboard = entries,
                allLeaderboard = entries,
                myUid = "u1",
                myRank = 1,
                myPoints = 92,
                participantCount = 4,
                friendUids = setOf("u2", "u3"),
                alreadySeen = true,
            ),
            friendsOnly = false,
            onToggleFriendsFilter = {},
            onReact = {},
            onParticipantClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultsRevealBody_CreatorNoPrediction_Preview() {
    val entries = listOf(
        LeaderboardEntry(uid = "u2", displayName = "Alice", avatarSeed = "a3", points = 78, rank = 1),
        LeaderboardEntry(uid = "u3", displayName = "Bob", avatarSeed = "a5", points = 65, rank = 2),
        LeaderboardEntry(uid = "u4", displayName = "Carol", avatarSeed = "a7", points = 50, rank = 3),
    )
    ThemePreview {
        ResultsRevealBody(
            data = ResultsRevealViewModel.RevealData(
                challengeTitle = "Champions League Final",
                leaderboard = entries,
                allLeaderboard = entries,
                myUid = "u1",
                myRank = null,
                myPoints = null,
                participantCount = 3,
                friendUids = setOf("u2"),
                alreadySeen = true,
                iAmCreator = true,
            ),
            friendsOnly = false,
            onToggleFriendsFilter = {},
            onReact = {},
            onParticipantClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewTie() {
    val entries = listOf(
        LeaderboardEntry(uid = "u1", displayName = "Atte", avatarSeed = "a1", points = 92, rank = 1),
        LeaderboardEntry(uid = "u2", displayName = "Alice", avatarSeed = "a3", points = 92, rank = 1),
        LeaderboardEntry(uid = "u4", displayName = "Carol", avatarSeed = "a7", points = 92, rank = 1),
        LeaderboardEntry(uid = "u3", displayName = "Bob", avatarSeed = "a5", points = 76, rank = 4),
    )
    ThemePreview {
        ResultsRevealBody(
            data = ResultsRevealViewModel.RevealData(
                challengeTitle = "Champions League Final",
                leaderboard = entries,
                allLeaderboard = entries,
                myUid = "u1",
                myRank = 1,
                myPoints = 92,
                participantCount = 4,
                friendUids = setOf("u2", "u3"),
                alreadySeen = true,
            ),
            friendsOnly = false,
            onToggleFriendsFilter = {},
            onReact = {},
            onParticipantClick = {},
        )
    }
}

// endregion
