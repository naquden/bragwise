package se.atte.bragwise.ui.screens.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.preview.sampleDetail

/**
 * CR-04 Manage challenge — owner-only post-publish view. Surfaces invite,
 * post-results, and delete actions; also shows status + member counts.
 */
@Composable
fun ManageChallengeScreen(
    viewModel: ManageChallengeViewModel,
    snackbarHostState: SnackbarHostState,
    onInvite: (String) -> Unit,
    onPostResults: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ManageChallengeViewModel.Effect.Deleted -> onDeleted()
            is ManageChallengeViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(effect.text)
        }
    }

    when (val ui = state.ui) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> Unit
        is UiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ui.cause.toUserMessage())
        }
        is UiState.Ready -> Content(
            detail = ui.data,
            isOwner = state.isOwner,
            onInvite = { onInvite(ui.data.challenge.id) },
            onPostResults = { onPostResults(ui.data.challenge.id) },
            onRequestDelete = { viewModel.onIntent(ManageChallengeViewModel.Intent.RequestDelete) },
        )
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ManageChallengeViewModel.Intent.CancelDelete) },
            title = { Text("Delete challenge?") },
            text = {
                Text(
                    "This permanently removes the challenge, all predictions, and invitations. This cannot be undone.",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            confirmButton = {
                AppButton(onClick = { viewModel.onIntent(ManageChallengeViewModel.Intent.ConfirmDelete) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(ManageChallengeViewModel.Intent.CancelDelete) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun Content(
    detail: ChallengeDetail,
    isOwner: Boolean,
    onInvite: () -> Unit,
    onPostResults: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val canDelete = detail.challenge.resultsPostedAt == null
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = detail.challenge.title) {
                    Text(
                        text = "Status: ${detail.challenge.status.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Members: ${detail.challenge.joinedCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Bets: ${detail.challenge.bets.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (!isOwner) {
                item {
                    SectionCard(title = "View only") {
                        Text("You're not the owner of this challenge.")
                    }
                }
            }
        }
        if (isOwner) {
            BottomActionBar {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onInvite,
                    ) { Text("Invite friends") }
                    val canPost = detail.challenge.status == ChallengeStatus.LOCKED ||
                        detail.challenge.status == ChallengeStatus.OPEN
                    AppButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onPostResults,
                        enabled = canPost,
                    ) {
                        Text(
                            if (detail.challenge.status == ChallengeStatus.RESULTS_POSTED) "Results posted"
                            else "Post results",
                        )
                    }
                    if (canDelete) {
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
    }
}

// region Previews

@Preview
@Composable
private fun ManageChallenge_Owner_Preview() {
    ThemePreview {
        Content(
            detail = sampleDetail(),
            isOwner = true,
            onInvite = {},
            onPostResults = {},
            onRequestDelete = {},
        )
    }
}

@Preview
@Composable
private fun ManageChallenge_NotOwner_Preview() {
    ThemePreview {
        Content(
            detail = sampleDetail(),
            isOwner = false,
            onInvite = {},
            onPostResults = {},
            onRequestDelete = {},
        )
    }
}

// endregion
