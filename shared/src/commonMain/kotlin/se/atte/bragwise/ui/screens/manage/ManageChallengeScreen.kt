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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.SectionCard

/**
 * CR-04 Manage challenge — owner-only post-publish view. Surfaces invite,
 * post-results, and (eventually) edit-bets actions; also shows status +
 * member counts.
 */
@Composable
fun ManageChallengeScreen(
    viewModel: ManageChallengeViewModel,
    onInvite: (String) -> Unit,
    onPostResults: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        )
    }
}

@Composable
private fun Content(
    detail: ChallengeDetail,
    isOwner: Boolean,
    onInvite: () -> Unit,
    onPostResults: () -> Unit,
) {
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
                }
            }
        }
    }
}
