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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.platform.PlatformShare
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.predictedCount
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import androidx.compose.foundation.clickable
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.AvatarBubble
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.PointsPill
import se.atte.bragwise.ui.components.RankChip
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.preview.sampleDetail

@Composable
fun ChallengeDetailScreen(
    viewModel: ChallengeDetailViewModel,
    platformShare: PlatformShare,
    snackbarHostState: SnackbarHostState,
    onNavigateToBet: (String) -> Unit,
    onNavigateToSummary: (String) -> Unit,
    onNavigateToPostResults: (String) -> Unit,
    onNavigateToParticipant: (challengeId: String, uid: String) -> Unit,
    onNavigateToInvite: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is ChallengeDetailViewModel.Effect.GoToBet -> onNavigateToBet(effect.betId)
            is ChallengeDetailViewModel.Effect.GoToSummary -> onNavigateToSummary(effect.challengeId)
            is ChallengeDetailViewModel.Effect.GoToPostResults -> onNavigateToPostResults(effect.challengeId)
            is ChallengeDetailViewModel.Effect.GoToParticipant -> onNavigateToParticipant(effect.challengeId, effect.uid)
            is ChallengeDetailViewModel.Effect.Deleted -> onDeleted()
            is ChallengeDetailViewModel.Effect.ShareLink -> {
                val (title, subject) = when (val msg = effect.message) {
                    is ChallengeDetailViewModel.ShareMessage.ChallengeShare ->
                        msg.challengeTitle to "${msg.challengeTitle} on Bragwise"
                }
                platformShare.send(effect.url, title, subject)
            }
            is ChallengeDetailViewModel.Effect.Snackbar -> {
                val text = when (val msg = effect.message) {
                    ChallengeDetailViewModel.SnackbarMessage.ShareFailed -> "Couldn't share challenge"
                    is ChallengeDetailViewModel.SnackbarMessage.DeleteFailed -> "Delete failed: ${msg.message}"
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
            if (ui.cause == se.atte.bragwise.mvi.Cause.NoAccess) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = "This challenge is invite-only",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Ask the creator to invite you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = ui.cause.toUserMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is UiState.Ready -> {
            DetailContent(
                data = ui.data,
                isOwner = state.isOwner,
                myUid = state.myUid,
                confirmingDelete = state.confirmingDelete,
                onPredict = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenPredict) },
                onSummary = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenSummary) },
                onPostResults = { viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenPostResults) },
                onParticipant = { uid -> viewModel.onIntent(ChallengeDetailViewModel.Intent.OpenParticipant(uid)) },
                onShare = { viewModel.onIntent(ChallengeDetailViewModel.Intent.Share) },
                onInvite = { (state.ui as? UiState.Ready)?.data?.challenge?.id?.let { onNavigateToInvite(it) } },
                onRequestDelete = { viewModel.onIntent(ChallengeDetailViewModel.Intent.RequestDelete) },
                onCancelDelete = { viewModel.onIntent(ChallengeDetailViewModel.Intent.CancelDelete) },
                onConfirmDelete = { viewModel.onIntent(ChallengeDetailViewModel.Intent.ConfirmDelete) },
            )
        }
    }
}

@Composable
private fun DetailContent(
    data: ChallengeDetail,
    isOwner: Boolean,
    myUid: String,
    confirmingDelete: Boolean,
    onPredict: () -> Unit,
    onSummary: () -> Unit,
    onPostResults: () -> Unit,
    onParticipant: (String) -> Unit,
    onShare: () -> Unit,
    onInvite: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val joined = data.myPredictions.isNotEmpty()
    val challenge = data.challenge

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete challenge?") },
            text = {
                Text(
                    "This permanently removes the challenge, all predictions, and invitations. This cannot be undone.",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            confirmButton = {
                AppButton(onClick = onConfirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("Cancel") }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
            verticalArrangement = Arrangement.spacedBy(standardPadding),
        ) {
            item {
                SectionCard {
                    Text(text = challenge.title, style = MaterialTheme.typography.headlineLarge)
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
                                RankChip(rank = data.myRank)
                            } else {
                                Text(
                                    text = "—— / ${challenge.joinedCount}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val hasPredicted = data.predictedCount() == challenge.bets.size && challenge.bets.isNotEmpty()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (hasPredicted) Lucide.CircleCheck else Lucide.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (hasPredicted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (hasPredicted) "Predicted" else "Not predicted",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (hasPredicted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (challenge.participants.isNotEmpty()) {
                item {
                    Text(
                        text = "Participants",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    val shown = challenge.participants.take(MAX_VISIBLE_PARTICIPANTS)
                    val hasMore = challenge.participants.size > MAX_VISIBLE_PARTICIPANTS
                    ListGroup {
                        shown.forEachIndexed { index, participant ->
                            val points = challenge.leaderboard?.get(participant.uid)
                            val canViewBets = participant.uid == myUid ||
                                (challenge.betsVisible && challenge.status != ChallengeStatus.OPEN)
                            ParticipantRow(
                                participant = participant,
                                points = points,
                                canViewBets = canViewBets,
                                onClick = if (canViewBets) ({ onParticipant(participant.uid) }) else null,
                            )
                            if (index < shown.size - 1 || hasMore) ListGroupDivider()
                        }
                        if (hasMore) {
                            ShowMoreRow(
                                remaining = challenge.participants.size - shown.size,
                                onClick = onSummary,
                            )
                        }
                    }
                }
            }

            if (isOwner && challenge.status != ChallengeStatus.DRAFT) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
                    ) {
                        AppOutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onInvite,
                        ) { Text("Invite friends") }
                        AppOutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onShare,
                        ) { Text("Share") }
                    }
                }
            }

            if (isOwner) {
                val canPost = challenge.status == ChallengeStatus.LOCKED
                item {
                    AppOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onPostResults,
                        enabled = canPost,
                    ) {
                        Text(
                            if (challenge.status == ChallengeStatus.RESULTS_POSTED) "Results posted"
                            else "Post results",
                        )
                    }
                }
                val canDelete = challenge.resultsPostedAt == null
                if (canDelete) {
                    item {
                        AppTextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRequestDelete,
                        ) {
                            Text(
                                "Delete challenge",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        val canPredict = challenge.status == ChallengeStatus.OPEN
        BottomActionBar {
            AppButton(
                modifier = Modifier.fillMaxWidth().testTag("detail_make_predictions"),
                onClick = onPredict,
                enabled = canPredict,
            ) {
                Text(
                    when {
                        canPredict && joined -> "Edit predictions"
                        canPredict -> "Make predictions"
                        else -> "View predictions"
                    }
                )
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantInfo,
    points: Int?,
    canViewBets: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = standardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarBubble(
            displayName = participant.displayName,
            avatarSeed = participant.avatarSeed,
            size = 32.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!canViewBets) {
                Text(
                    text = "Bets hidden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (points != null) {
            PointsPill(points = points)
        }
        if (canViewBets) {
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShowMoreRow(remaining: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = standardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Show more ($remaining)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_VISIBLE_PARTICIPANTS = 5

// region Previews

@Preview
@Composable
private fun Detail_Ready_NotJoined_Preview() {
    ThemePreview {
        DetailContent(
            data = sampleDetail(),
            isOwner = false,
            myUid = "u1",
            confirmingDelete = false,
            onPredict = {},
            onSummary = {},
            onPostResults = {},
            onParticipant = {},
            onShare = {},
            onInvite = {},
            onRequestDelete = {},
            onCancelDelete = {},
            onConfirmDelete = {},
        )
    }
}

@Preview
@Composable
private fun Detail_Ready_Owner_Preview() {
    ThemePreview {
        DetailContent(
            data = sampleDetail(
                myPredictions = mapOf(
                    "b1" to PredictionPayload.BooleanProp(true),
                ),
                myRank = 1,
            ),
            isOwner = true,
            myUid = "u1",
            confirmingDelete = false,
            onPredict = {},
            onSummary = {},
            onPostResults = {},
            onParticipant = {},
            onShare = {},
            onInvite = {},
            onRequestDelete = {},
            onCancelDelete = {},
            onConfirmDelete = {},
        )
    }
}

@Preview
@Composable
private fun Detail_Ready_BetsVisible_Preview() {
    ThemePreview {
        DetailContent(
            data = sampleDetail(
                myPredictions = mapOf("b1" to PredictionPayload.BooleanProp(true)),
                myRank = 2,
            ),
            isOwner = false,
            myUid = "u2",
            confirmingDelete = false,
            onPredict = {},
            onSummary = {},
            onPostResults = {},
            onParticipant = {},
            onShare = {},
            onInvite = {},
            onRequestDelete = {},
            onCancelDelete = {},
            onConfirmDelete = {},
        )
    }
}

// endregion
