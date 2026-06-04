package se.atte.bragwise.ui.screens.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.ui.components.AvatarBubble
import se.atte.bragwise.ui.components.Confetti
import se.atte.bragwise.ui.components.Podium
import se.atte.bragwise.ui.components.PointsPill
import se.atte.bragwise.ui.components.RankChip

@Composable
fun ResultsRevealScreen(viewModel: ResultsRevealViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        is UiState.Ready -> ResultsRevealBody(data = ui.data)
    }
}

@Composable
private fun ResultsRevealBody(data: ResultsRevealViewModel.RevealData) {
    var showBanner by remember { mutableStateOf(data.alreadySeen) }
    var showField by remember { mutableStateOf(data.alreadySeen) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(data.leaderboard) {
        if (!data.alreadySeen) {
            delay(1700)
            showBanner = true
            delay(200)
            showField = true
            if (data.iAmWinner) showConfetti = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = standardPadding, vertical = standardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "🏆", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Results are in!",
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
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
                    alreadySeen = data.alreadySeen,
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = standardPadding,
                        vertical = standardPadding,
                    ),
                )
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
                        participantCount = data.participantCount,
                        iAmWinner = data.iAmWinner,
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
                        val label = if (data.participantCount > 20) "Top 10" else "All results"
                        Text(
                            text = "$label (${data.participantCount})",
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
                        animationDelay = index * 60,
                    )
                }

                data.myEntryOutsideField?.let { pinnedEntry ->
                    item(key = "pinned_self") {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = standardPadding))
                        LeaderboardRow(entry = pinnedEntry, isMe = true, animationDelay = 0)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }

        if (showConfetti) {
            Confetti(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun YourResultBanner(
    myRank: Int?,
    myPoints: Int?,
    participantCount: Int,
    iAmWinner: Boolean,
    modifier: Modifier = Modifier,
) {
    val (emoji, headline, subtitle) = when {
        iAmWinner -> Triple("🥇", "You won!", "You topped the leaderboard")
        myRank == 2 -> Triple("🥈", "Runner-up!", "#2 of $participantCount")
        myRank == 3 -> Triple("🥉", "3rd place!", "#3 of $participantCount")
        myRank != null -> Triple("🎯", "Your result", "#$myRank of $participantCount")
        else -> Triple("🎯", "You didn't predict", "Join next time to compete")
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (iAmWinner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(all = standardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(standardPadding),
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
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
private fun LeaderboardRow(entry: LeaderboardEntry, isMe: Boolean, animationDelay: Int) {
    var visible by remember { mutableStateOf(animationDelay == 0) }
    LaunchedEffect(entry.uid) {
        if (animationDelay > 0) {
            delay(animationDelay.toLong())
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 200)) + slideInVertically(
            animationSpec = tween(durationMillis = 200),
            initialOffsetY = { it / 3 },
        ),
    ) {
        val backgroundModifier = if (isMe) {
            Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
        } else Modifier
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(backgroundModifier)
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
                myUid = "u1",
                myRank = 1,
                myPoints = 92,
                participantCount = 4,
                alreadySeen = true,
            ),
        )
    }
}

// endregion
